import { USE_MOCK, WS_BEARER_PREFIX, WS_PATH, WS_PROTOCOL_VERSION } from '../config';
import { subscribeMockEvents } from '../mocks/bus';
import type { ServerEvent, WebRtcSignalMessage } from './types';

export type EventHandler = (event: ServerEvent) => void;

export interface EventStream {
  subscribe(handler: EventHandler): () => void;
  /** contracts/events.md: client → server 는 WebRTC signaling만 보낸다. */
  sendSignal(message: WebRtcSignalMessage): void;
  close(): void;
}

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

  const url = () => {
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
   */
  const protocols = () =>
    token ? [WS_PROTOCOL_VERSION, `${WS_BEARER_PREFIX}${token}`] : [WS_PROTOCOL_VERSION];

  const connect = () => {
    if (closed) return;
    socket = new WebSocket(url(), protocols());

    socket.onopen = () => {
      retry = 0;
      queued.splice(0).forEach((raw) => socket?.send(raw));
    };
    socket.onmessage = (raw) => {
      try {
        const event = JSON.parse(String(raw.data)) as ServerEvent;
        handlers.forEach((h) => h(event));
      } catch {
        /* 형식이 깨진 프레임은 무시한다 */
      }
    };
    socket.onclose = () => {
      if (closed) return;
      retry += 1;
      window.setTimeout(connect, Math.min(1000 * 2 ** retry, 15_000));
    };
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
      socket?.close();
      socket = null;
    },
  };
}

export function createEventStream(token: string | null): EventStream {
  return USE_MOCK ? createMockStream() : createSocketStream(token);
}
