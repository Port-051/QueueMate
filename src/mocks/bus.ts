import type { ServerEvent, ServerEventType } from '../api/types';

type Listener = (event: ServerEvent) => void;

const listeners = new Set<Listener>();

export function subscribeMockEvents(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function emitMockEvent<T extends Record<string, unknown>>(type: ServerEventType, payload: T): void {
  const event: ServerEvent = {
    type,
    eventId: crypto.randomUUID(),
    occurredAt: new Date().toISOString(),
    payload,
  };
  listeners.forEach((l) => l(event));
}
