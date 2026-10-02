import { API_BASE } from '../config';
import { refreshSession } from './http';
import type { ServerEvent, ServerEventType } from './types';

/**
 * 실시간은 SSE 하나다 — `GET /api/v1/events`(notification · 8081). WebSocket(`/ws`)은 어느 앱에도 없다(D-9).
 * 규칙의 원본은 `notification/CLAUDE.md` §5 · §5.1 과 `matching/contracts/events.md` "Envelope" · "재연결" · "heartbeat".
 *
 * - 인증은 쿠키 `qm_access` — `EventSource` 는 헤더를 못 붙이지만 같은 출처의 쿠키는 저절로 싣는다. 쿼리 파라미터는 없다.
 * - 알림은 **이름 없는 이벤트**(`onmessage`)로 오고 `data:` 가 봉투 `{type, eventId, occurredAt, payload}` 다. `id:` 는 `eventId`.
 * - `event: heartbeat` 는 20초마다 오는 **이름 있는 이벤트**다(`data` 는 뜻이 없다). 60초 동안 아무것도 안 오면 죽은 연결로 보고 새로 연다.
 * - `retry:` 는 서버가 연결할 때 한 번 내려 준다(1000~2000ms 무작위) — 네트워크가 끊긴 재접속은 브라우저가 그 값으로 스스로 한다.
 * - **401 로 닫히면(`readyState === CLOSED`) 브라우저는 재접속을 멈춘다** — 연결할 때만 검증하므로 access 가 만료된 뒤의 재접속이 이것이다.
 *   그때는 `refreshSession()`(재발급) 뒤 `EventSource` 를 새로 만든다(백오프 1s·2^n · 상한 15s). 재발급도 실패하면 `onAuthLost` 가 불려
 *   AuthContext 가 익명이 되고 이 스트림은 닫힌다.
 * - 놓친 알림은 다시 오지 않는다(`Last-Event-ID` 재개 없음). 연결 직후 상태를 REST 로 맞추는 것은 구독하는 쪽의 일이다(`subscribeStatus` 의 `connected`).
 */

export type EventHandler = (event: ServerEvent) => void;
export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting' | 'closed';

export interface EventStream {
  subscribe(handler: EventHandler): () => void;
  subscribeStatus(handler: (status: ConnectionStatus) => void): () => void;
  close(): void;
}

/**
 * 우리 백엔드가 실제로 발행하는 `type` 14종 — matching 5(`events.md`) · platform 9(`platform-api.md` "방" 의 "알림" · "이 앱이 내는 알림" ·
 * "게시판 채널 신호"). 모르는 type 의 프레임은 버린다. 원본 프런트의 이름(`SESSION_SNAPSHOT` · `PARTY_*` · `ROOMS_UPDATED` · `RECRUITMENT_UPDATED` …)은 여기도 타입에도 없다(4단계에서 마지막 사용처를 지웠다).
 */
const SERVER_EVENT_TYPES = new Set<string>([
  'MATCH_QUEUE_UPDATED', 'MATCH_PROPOSAL_CREATED', 'MATCH_PROPOSAL_EXPIRED', 'MATCH_CONFIRMED', 'MATCH_CANCELLED',
  'FRIEND_REQUEST_RECEIVED', 'FRIEND_REQUEST_ACCEPTED',
  'ROOM_MEMBER_ENTERED', 'ROOM_MEMBER_LEFT', 'ROOM_CLOSED', 'ROOM_MEMBER_KICKED', 'ROOM_CONFIRMED',
  'WEBRTC_SIGNAL',
  'BOARD_CHANGED', 'DIRECT_MESSAGE_RECEIVED',
]);

export const isServerEventType = (value: unknown): value is ServerEventType =>
  typeof value === 'string' && SERVER_EVENT_TYPES.has(value);

/** 서버 간격의 상한이 30초라 두 번 연속 놓치면(60초) 죽은 연결로 본다(`events.md` "heartbeat" 의 권장값). */
const SILENCE_LIMIT_MS = 60_000;
const SILENCE_CHECK_MS = 5_000;
const MAX_BACKOFF_MS = 15_000;

export const EVENTS_PATH = `${API_BASE}/events`;

export function createEventStream(): EventStream {
  const handlers = new Set<EventHandler>();
  const statusHandlers = new Set<(status: ConnectionStatus) => void>();
  let status: ConnectionStatus = 'connecting';
  const updateStatus = (next: ConnectionStatus) => {
    if (status === next) return;
    status = next;
    statusHandlers.forEach((h) => h(next));
  };

  let source: EventSource | null = null;
  let closed = false;
  let retry = 0;
  let retryTimer: number | null = null;
  let lastSeen = Date.now();
  let watchdog: number | null = null;

  const touch = () => { lastSeen = Date.now(); };

  const disposeSource = () => {
    if (!source) return;
    source.onopen = null;
    source.onmessage = null;
    source.onerror = null;
    source.close();
    source = null;
  };

  const scheduleReopen = () => {
    if (closed || retryTimer !== null) return;
    retry += 1;
    retryTimer = window.setTimeout(open, Math.min(1000 * 2 ** retry, MAX_BACKOFF_MS));
  };

  /** 401 등 200 이 아닌 응답 — 브라우저가 재접속을 포기한 경우. 재발급 뒤 새로 만든다. */
  const reopenAfterRefresh = async () => {
    disposeSource();
    updateStatus('reconnecting');
    const session = await refreshSession();
    if (closed) return;
    // 재발급 실패 — onAuthLost 가 불렸고 AuthContext 가 이 스트림을 닫는다. 여기서 더 붙지 않는다.
    if (!session) { updateStatus('closed'); return; }
    scheduleReopen();
  };

  const open = () => {
    if (closed) return;
    retryTimer = null;
    disposeSource();
    touch();
    const es = new EventSource(EVENTS_PATH, { withCredentials: true });
    source = es;
    es.onopen = () => {
      if (source !== es) return;
      retry = 0;
      touch();
      updateStatus('connected');
    };
    es.onmessage = (raw) => {
      if (source !== es) return;
      touch();
      const event = parseEvent(raw.data);
      if (event) handlers.forEach((h) => h(event));
    };
    // 하트비트는 이름 있는 이벤트라 onmessage 로 오지 않는다. 내용은 읽지 않는다.
    es.addEventListener('heartbeat', () => { if (source === es) touch(); });
    es.onerror = () => {
      if (closed || source !== es) return;
      if (es.readyState === EventSource.CLOSED) {
        void reopenAfterRefresh();
      } else {
        // CONNECTING — 브라우저가 서버가 준 retry: 로 스스로 다시 붙는다. 상태만 알린다.
        updateStatus('reconnecting');
      }
    };
  };

  // 종료 신호 없이 길만 사라진 연결(절전 복귀 · 프록시가 말없이 버림)은 onerror 가 오지 않는다 — 하트비트 침묵으로 알아챈다.
  watchdog = window.setInterval(() => {
    if (closed || !source) return;
    if (Date.now() - lastSeen < SILENCE_LIMIT_MS) return;
    disposeSource();
    updateStatus('reconnecting');
    scheduleReopen();
  }, SILENCE_CHECK_MS);

  open();

  return {
    subscribeStatus(handler) {
      statusHandlers.add(handler);
      handler(status);
      return () => statusHandlers.delete(handler);
    },
    subscribe(handler) {
      handlers.add(handler);
      return () => handlers.delete(handler);
    },
    close() {
      closed = true;
      updateStatus('closed');
      statusHandlers.clear();
      handlers.clear();
      if (retryTimer !== null) window.clearTimeout(retryTimer);
      retryTimer = null;
      if (watchdog !== null) window.clearInterval(watchdog);
      watchdog = null;
      disposeSource();
    },
  };
}

/** 봉투가 계약대로가 아니면 버린다. 화면 상태를 깨뜨리는 것보다 낫다. */
function parseEvent(raw: unknown): ServerEvent | null {
  try {
    const parsed = JSON.parse(String(raw)) as Partial<ServerEvent>;
    if (!isServerEventType(parsed?.type)) return null;
    return {
      type: parsed.type,
      eventId: String(parsed.eventId ?? ''),
      occurredAt: String(parsed.occurredAt ?? ''),
      payload: (parsed.payload ?? {}) as Record<string, unknown>,
    };
  } catch {
    return null;
  }
}
