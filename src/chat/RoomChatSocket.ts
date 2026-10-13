import type { PartyChatMessage } from '../webrtc/types';

/**
 * 방 채팅 소켓 — `chat` 서비스(8083 · `GET /ws/chat/rooms/{roomId}` · D-58)에 붙어 글을 주고받는다. 2026-10-13 소유자 지시로 WebRTC 데이터 채널 채팅을 이것으로 바꿨다(⑦).
 *
 * - **같은 출처**다 — `ws(s)://{location.host}/ws/chat/rooms/{roomId}`. 개발은 Vite 프록시(`/ws/chat` → 8083), 운영은 CloudFront `/ws/*` → ALB. 쿠키 `qm_access` 가 핸드셰이크에 실려 인증된다
 *   (`chat/CLAUDE.md` §5) — 구성원이 아니면 403 이라 소켓이 열리지 않는다(브라우저에는 close 1006 으로 보인다).
 * - **봉투**(`chat/CLAUDE.md` §3.1) — 보내기 `{type:"SEND", cid, nick, text}` · 받기 `{type:"MSG", roomId, from, nick, text, cid, sentAt}` · 보낸 소켓에만 `{type:"ACK", cid}` ·
 *   `{type:"ERROR", cid, code}`(`EMPTY` · `TOO_LONG` · `TOO_FAST` · `BAD_JSON` · `RETRY`). **내 글도 서버가 뿌린 `MSG` 로 받아 그린다**(C-2 — 보낸 서버도 구독으로 받아 쏜다) — 낙관적으로 먼저 그리지 않는다.
 * - **저장이 없다** — 들어오기 전의 글은 못 보고 새로 고치면 기록이 빈다(D-58 결정 3).
 * - **다시 잇기** — 끊기면(서버 재배포의 1001 · 네트워크) 1 · 2 · 4 · 8 · 15초 간격으로 다시 연다. 방을 나가면(`close()`) 멈춘다. 403 도 같은 간격으로 다시 시도한다(방 세션이 끝나면 어차피 닫힌다).
 * - 서버가 20초마다 ping 을 보내고 브라우저가 스스로 pong 한다 — 여기서 할 일이 없다.
 */
export type RoomChatStatus = 'connecting' | 'open' | 'reconnecting' | 'closed';

export interface RoomChatHandlers {
  onMessage(message: PartyChatMessage): void;
  onStatus(status: RoomChatStatus): void;
  /** 서버가 거절한 내 글(`ERROR`) — `code` 는 `EMPTY` · `TOO_LONG` · `TOO_FAST` · `BAD_JSON` · `RETRY`. */
  onError(code: string, cid: string | null): void;
}

const BACKOFF_MS = [1_000, 2_000, 4_000, 8_000, 15_000];
/** 서버의 한 글 상한(`chat` 의 `max-text-length` 기본 500) — 보내기 전에 자른다. 서버도 같은 값으로 거절한다. */
export const CHAT_MAX_TEXT_LENGTH = 500;

interface Inbound {
  type?: string;
  roomId?: string;
  from?: string;
  nick?: string | null;
  text?: string;
  cid?: string | null;
  sentAt?: string;
  code?: string;
}

export class RoomChatSocket {
  private ws: WebSocket | null = null;
  private closed = false;
  private retries = 0;
  private timer: number | null = null;

  constructor(
    private readonly roomId: string,
    private readonly selfNickname: string,
    private readonly handlers: RoomChatHandlers,
  ) {}

  connect(): void {
    if (this.closed) return;
    this.handlers.onStatus(this.retries === 0 ? 'connecting' : 'reconnecting');
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(`${scheme}://${window.location.host}/ws/chat/rooms/${encodeURIComponent(this.roomId)}`);
    this.ws = ws;
    ws.onopen = () => {
      if (this.closed || this.ws !== ws) return;
      this.retries = 0;
      this.handlers.onStatus('open');
    };
    ws.onmessage = (event) => {
      if (this.closed || this.ws !== ws) return;
      let data: Inbound;
      try { data = JSON.parse(String(event.data)) as Inbound; } catch { return; }
      if (data.type === 'MSG') {
        if (data.roomId && data.roomId !== this.roomId) return;
        if (typeof data.from !== 'string' || typeof data.text !== 'string') return;
        this.handlers.onMessage({
          id: data.cid || crypto.randomUUID(),
          userId: data.from,
          nickname: data.nick ?? '',
          text: data.text,
          at: data.sentAt ?? new Date().toISOString(),
        });
      } else if (data.type === 'ERROR') {
        this.handlers.onError(data.code ?? 'UNKNOWN', data.cid ?? null);
      }
      // ACK 는 받기만 한다 — 글은 곧 MSG 로 온다.
    };
    ws.onclose = () => {
      if (this.ws !== ws) return;
      this.ws = null;
      if (this.closed) return;
      this.scheduleReconnect();
    };
    ws.onerror = () => { /* onclose 가 뒤따른다 */ };
  }

  /** 보낸다 — 열려 있으면 `cid` 를, 아니면 `null` 을 돌려준다. 빈 글은 보내지 않는다. */
  send(text: string): string | null {
    const trimmed = text.trim().slice(0, CHAT_MAX_TEXT_LENGTH);
    if (!trimmed || this.closed || !this.ws || this.ws.readyState !== WebSocket.OPEN) return null;
    const cid = crypto.randomUUID();
    try {
      this.ws.send(JSON.stringify({ type: 'SEND', cid, nick: this.selfNickname, text: trimmed }));
      return cid;
    } catch {
      return null;
    }
  }

  get open(): boolean {
    return !this.closed && this.ws?.readyState === WebSocket.OPEN;
  }

  close(): void {
    this.closed = true;
    if (this.timer !== null) { window.clearTimeout(this.timer); this.timer = null; }
    const ws = this.ws;
    this.ws = null;
    try { ws?.close(1000, 'leave'); } catch { /* 이미 닫힘 */ }
    this.handlers.onStatus('closed');
  }

  private scheduleReconnect(): void {
    if (this.closed || this.timer !== null) return;
    const delay = BACKOFF_MS[Math.min(this.retries, BACKOFF_MS.length - 1)];
    this.retries += 1;
    this.handlers.onStatus('reconnecting');
    this.timer = window.setTimeout(() => { this.timer = null; this.connect(); }, delay);
  }
}
