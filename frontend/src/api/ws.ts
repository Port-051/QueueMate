import { USE_MOCK, WS_PATH } from '../config';
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
    const query = token ? `?access_token=${encodeURIComponent(token)}` : '';
    return `${proto}//${location.host}${WS_PATH}${query}`;
  };

  const connect = () => {
    if (closed) return;
    socket = new WebSocket(url());

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
