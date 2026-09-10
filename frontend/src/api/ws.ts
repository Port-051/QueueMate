import { USE_MOCK, WS_BEARER_PREFIX, WS_PATH, WS_PROTOCOL_VERSION, WS_URL_OVERRIDE } from '../config';
import { subscribeMockEvents } from '../mocks/bus';
import type { ServerEvent, ServerEventType, WebRtcSignalMessage } from './types';

export type EventHandler = (event: ServerEvent) => void;

export interface EventStream {
  subscribe(handler: EventHandler): () => void;
  /** contracts/events.md: client → server 는 WebRTC signaling만 보낸다. */
  sendSignal(message: WebRtcSignalMessage): void;
  close(): void;
}

/** 백엔드가 실제로 발행하는 Server → Client 목록. 모르는 type의 프레임은 버린다. */
const SERVER_EVENT_TYPES = new Set<string>([
  'SESSION_SNAPSHOT', 'MATCH_PROPOSAL_CREATED', 'MATCH_PROPOSAL_EXPIRED',
  'MATCH_CONFIRMED', 'MATCH_CANCELLED', 'RESERVATION_PROPOSAL_CREATED',
  'PARTY_MEMBER_LEFT', 'PARTY_READY_CHANGED', 'PARTY_PLAYING', 'PARTY_CLOSED',
  'WEBRTC_SIGNAL',
]);

export const isServerEventType = (value: unknown): value is ServerEventType =>
  typeof value === 'string' && SERVER_EVENT_TYPES.has(value);

function createMockStream(): EventStream {
  const handlers = new Set<EventHandler>();
  const unsubscribe = subscribeMockEvents((event) => handlers.forEach((h) => h(event)));
  return {
    subscribe(handler) {
      handlers.add(handler);
      return () => handlers.delete(handler);
    },
    sendSignal() {
      /* mock 모드에는 원격 peer가 없으므로 signaling은 버린다 */
    },
    close() {
      handlers.clear();
      unsubscribe();
    },
  };
}

function createSocketStream(token: string | null): EventStream {
  const handlers = new Set<EventHandler>();
  const queued: string[] = [];
  let socket: WebSocket | null = null;
  let closed = false;
  let retry = 0;
  let retryTimer: number | null = null;

  const url = () => {
    if (WS_URL_OVERRIDE) return WS_URL_OVERRIDE;
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${location.host}${WS_PATH}`;
  };

  /**
   * token은 subprotocol로 넘긴다 (contracts/events.md).
   *
   * 브라우저 WebSocket API는 커스텀 헤더를 붙일 수 없어서 Authorization을 쓸 수 없다.
   * 그렇다고 query string에 실으면 접근 로그에 token이 그대로 남는다. 로그는 프록시와
   * 수집기마다 복사되고 몇 달을 남는데 access token은 15분짜리다. 15분짜리 비밀이
   * 몇 달짜리 기록이 된다. docs/09가 로그에 token을 남기지 말라고 한 이유다.
   *
   * subprotocol은 원래 프로토콜 협상 자리라 용도를 빌려 쓰는 편법이지만,
   * 브라우저가 헤더로 보내 주는 유일한 값이다.
   * 서버는 선택한 subprotocol로 `queuemate.v1`만 되돌려준다. 토큰은 되돌려주지 않는다.
   */
  const protocols = () =>
    token ? [WS_PROTOCOL_VERSION, `${WS_BEARER_PREFIX}${token}`] : [WS_PROTOCOL_VERSION];

  const connect = () => {
    if (closed) return;
    retryTimer = null;
    socket = new WebSocket(url(), protocols());

    socket.onopen = () => {
      retry = 0;
      // 연결이 맺어지면 서버가 SESSION_SNAPSHOT을 먼저 한 번 보낸다.
      // 끊긴 동안의 이벤트를 이어받는 수단이 없으므로 현재 상태로 대신 복구한다.
      queued.splice(0).forEach((raw) => socket?.send(raw));
    };
    socket.onmessage = (raw) => {
      const event = parseEvent(raw.data);
      if (event) handlers.forEach((h) => h(event));
    };
    socket.onclose = () => {
      socket = null;
      if (closed) return;
      // access token이 만료되면 서버가 끊는다. 재연결은 저장된 token으로 다시 시도하고,
      // 그것도 401이면 REST 쪽 재발급이 끝난 뒤 새 stream이 만들어진다.
      retry += 1;
      retryTimer = window.setTimeout(connect, Math.min(1000 * 2 ** retry, 15_000));
    };
    // onerror는 onclose를 동반한다. 재연결 경로를 하나로 두려고 여기서는 아무것도 하지 않는다.
    socket.onerror = () => undefined;
  };

  connect();

  return {
    subscribe(handler) {
      handlers.add(handler);
      return () => handlers.delete(handler);
    },
    sendSignal(message) {
      const raw = JSON.stringify(message);
      if (socket?.readyState === WebSocket.OPEN) socket.send(raw);
      else queued.push(raw);
    },
    close() {
      closed = true;
      handlers.clear();
      if (retryTimer !== null) window.clearTimeout(retryTimer);
      retryTimer = null;
      socket?.close();
      socket = null;
    },
  };
}

/** envelope가 계약대로가 아니면 버린다. 화면 상태를 깨뜨리는 것보다 낫다. */
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

export function createEventStream(token: string | null): EventStream {
  return USE_MOCK ? createMockStream() : createSocketStream(token);
}
