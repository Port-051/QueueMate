import { refreshSession } from '../api/http';
import type { PartyChatMessage } from '../webrtc/types';

/**
 * 방 채팅 소켓 — `chat` 서비스(8083 · `GET /ws/chat/rooms/{roomId}` · D-58)에 붙어 글을 주고받는다. 2026-10-13 소유자 지시로 WebRTC 데이터 채널 채팅을 이것으로 바꿨다(⑦).
 *
 * - **같은 출처**다 — `ws(s)://{location.host}/ws/chat/rooms/{roomId}`. 개발은 Vite 프록시(`/ws/chat` → 8083), 운영은 CloudFront `/ws/*` → ALB. 쿠키 `qm_access` 가 핸드셰이크에 실려 인증된다
 *   (`chat/CLAUDE.md` §5) — 구성원이 아니면 403 이라 소켓이 열리지 않는다(브라우저에는 close 1006 으로 보인다).
 * - **봉투**(`chat/CLAUDE.md` §3.1) — 보내기 `{type:"SEND", cid, nick, text}` · 받기 `{type:"MSG", roomId, from, nick, text, cid, sentAt}` · 보낸 소켓에만 `{type:"ACK", cid}` ·
 *   `{type:"ERROR", cid, code}`(`EMPTY` · `TOO_LONG` · `TOO_FAST` · `BAD_JSON` · `RETRY`). **내 글도 서버가 뿌린 `MSG` 로 받아 그린다**(C-2 — 보낸 서버도 구독으로 받아 쏜다) — 낙관적으로 먼저 그리지 않는다.
 * - **저장이 없다** — 들어오기 전의 글은 못 보고 새로 고치면 기록이 빈다(D-58 결정 3).
 * - **다시 잇기** — 끊기면(서버 재배포의 1001 · 네트워크) 1 · 2 · 4 · 8 · 15초 간격으로 다시 연다. 방을 나가면(`close()`) 멈춘다.
 *   **한 번도 열리지 못한 채 닫히면(핸드셰이크 실패 — 브라우저엔 401 · 403 이 다 1006)** access 토큰 만료일 수 있어 `refreshSession()` 을 한 번 부른 뒤 다시 연다(2026-10-13 — 방에 15분 넘게 있다가 끊기면 재발급 없이는 계속 실패했다). 구성원이 아니면(403) 방 세션이 곧 닫는다.
 * - 서버가 20초마다 ping 을 보내고 브라우저가 스스로 pong 한다 — 여기서 할 일이 없다.
 */
export type RoomChatStatus = 'connecting' | 'open' | 'reconnecting' | 'closed';

export interface RoomChatHandlers {
  onMessage(message: PartyChatMessage): void;
  onStatus(status: RoomChatStatus): void;
  /** 서버가 거절한 내 글(`ERROR`) — `code` 는 `EMPTY` · `TOO_LONG` · `TOO_FAST` · `BAD_JSON` · `RETRY`. `text` 는 그 글의 본문(초안으로 되돌리려고 · 모르면 `null`). */
  onError(code: string, cid: string | null, text: string | null): void;
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
  /** 답(ACK · ERROR)을 기다리는 내 글 — cid → 본문. 거절되면 초안으로 돌려준다. 답이 오면 지운다(상한 100). */
  private pending = new Map<string, string>();
  /** 이 소켓이 한 번이라도 열렸는가 — 안 열린 채 닫히면 핸드셰이크 실패다. */
  private opened = false;

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
    this.opened = false;
    ws.onopen = () => {
      if (this.closed || this.ws !== ws) return;
      this.opened = true;
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
        const cid = data.cid ?? null;
        const text = cid ? this.pending.get(cid) ?? null : null;
        if (cid) this.pending.delete(cid);
        this.handlers.onError(data.code ?? 'UNKNOWN', cid, text);
      } else if (data.type === 'ACK') {
        if (data.cid) this.pending.delete(data.cid);   // 글은 곧 MSG 로 온다
      }
    };
    ws.onclose = () => {
      if (this.ws !== ws) return;
      this.ws = null;
      if (this.closed) return;
      if (!this.opened) {
        // 핸드셰이크에서 거절됐다 — 토큰이 만료됐을 수 있다. 재발급을 한 번 시도한 뒤(실패해도) 간격을 두고 다시 연다.
        void refreshSession().catch(() => null).finally(() => { if (!this.closed) this.scheduleReconnect(); });
        return;
      }
      this.scheduleReconnect();
    };
    ws.onerror = () => { /* onclose 가 뒤따른다 */ };
  }

  /**
   * 보낸다 — 열려 있으면 `cid` 를 돌려준다. 빈 글은 보내지 않는다(`null`). **500자를 넘으면 자르지 않고 보내지 않는다**(`'TOO_LONG'` — 2026-10-13: 조용히 잘라 보내면 사용자가
   * 뒷부분이 사라진 걸 모른다). 열려 있지 않으면 `null`.
   */
  send(text: string): string | 'TOO_LONG' | null {
    const trimmed = text.trim();
    if (!trimmed || this.closed || !this.ws || this.ws.readyState !== WebSocket.OPEN) return null;
    if (trimmed.length > CHAT_MAX_TEXT_LENGTH) return 'TOO_LONG';
    const cid = crypto.randomUUID();
    try {
      this.ws.send(JSON.stringify({ type: 'SEND', cid, nick: this.selfNickname, text: trimmed }));
      if (this.pending.size >= 100) this.pending.delete(this.pending.keys().next().value as string);
      this.pending.set(cid, trimmed);
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
    this.pending.clear();
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
