import { expect, type Page } from '@playwright/test';
import type { QmUser } from './api';

/** 알림 봉투 `{type, eventId, occurredAt, payload}`(`notification/CLAUDE.md` §3) + 받은 시각(이 테스트가 붙인다). */
export interface PushEvent {
  type: string;
  eventId: string;
  occurredAt: string;
  payload: any;
  receivedAt: number;
}

/**
 * 사용자 한 명의 SSE 수집기 — 그 사람의 컨텍스트 안의 **빈 페이지**(REST 를 부르는 그 페이지 — `api.ts`)에서 `new EventSource('/api/v1/events')` 를 열고
 * 받은 봉투를 `window.__events` 에 쌓는다. 쿠키(`qm_access`)는 같은 출처라 브라우저가 싣는다(notification §5.1).
 *
 * **앱 페이지를 쓰지 않는 이유** — 앱은 `MATCH_CONFIRMED` 를 받으면 조작 없이 `POST /match-parties/{partyId}/room` 을 부르고
 * 방 heartbeat 를 돈다. REST 로 흐름을 검증하는 시나리오에 앱이 끼어들면 무엇을 누가 불렀는지 가를 수 없다.
 */
export class SseCollector {
  private constructor(readonly user: QmUser, readonly page: Page) {}

  static async open(user: QmUser, settleMs = 1_500): Promise<SseCollector> {
    const page = await user.page();
    await page.evaluate(() => new Promise<void>((resolve, reject) => {
      const w = window as unknown as { __events: unknown[]; __sse: EventSource };
      w.__events = [];
      const source = new EventSource('/api/v1/events');
      w.__sse = source;
      const timer = window.setTimeout(() => reject(new Error('SSE 가 10초 안에 열리지 않았다')), 10_000);
      source.onopen = () => { window.clearTimeout(timer); resolve(); };
      source.onmessage = (event) => {
        try {
          w.__events.push({ ...JSON.parse(event.data), receivedAt: Date.now() });
        } catch {
          w.__events.push({ type: '__UNPARSEABLE__', payload: event.data, receivedAt: Date.now() });
        }
      };
      source.onerror = () => {
        if (source.readyState === EventSource.CLOSED) { window.clearTimeout(timer); reject(new Error('SSE 가 닫혔다(401?)')); }
      };
    }));
    // 연결 응답(`: connected`)이 먼저 나가고 사용자 채널 구독(`qm:pubsub:push:{userId}`)은 그 뒤에 걸린다 — 잠깐 기다린다.
    await page.waitForTimeout(settleMs);
    return new SseCollector(user, page);
  }

  async events(): Promise<PushEvent[]> {
    return this.page.evaluate(() => (window as unknown as { __events: PushEvent[] }).__events.slice());
  }

  /** 지금까지 받은 수 — `since` 로 넘기면 그 뒤에 온 것만 본다. */
  async mark(): Promise<number> {
    return (await this.events()).length;
  }

  async isOpen(): Promise<boolean> {
    return this.page.evaluate(() => (window as unknown as { __sse: EventSource }).__sse.readyState === EventSource.OPEN);
  }
}

export interface WaitOptions {
  /** 이 순번 뒤에 온 것만 본다(`mark()` 의 값). 기본 0 — 연 뒤 전부. */
  since?: number;
  timeout?: number;
}

/** `type` 이 같고 `predicate` 를 만족하는 알림이 올 때까지 기다린다. 오지 않으면 받은 것 전부를 실패 메시지에 싣는다. */
export async function waitForEvent(
  sse: SseCollector, type: string, predicate: (event: PushEvent) => boolean = () => true, options: WaitOptions = {},
): Promise<PushEvent> {
  const { since = 0, timeout = 10_000 } = options;
  const deadline = Date.now() + timeout;
  for (;;) {
    const all = await sse.events();
    const found = all.slice(since).find((event) => event.type === type && predicate(event));
    if (found) return found;
    if (Date.now() > deadline) {
      const seen = all.slice(since).map((event) => `${event.type} ${JSON.stringify(event.payload)}`).join('\n  ');
      throw new Error(`${sse.user.nickname} 에게 ${type} 이 ${timeout}ms 안에 오지 않았다. 받은 것:\n  ${seen || '(없음)'}`);
    }
    await sse.page.waitForTimeout(100);
  }
}

/** `within` 동안 기다린 뒤 그런 알림이 **없었는지** 본다. */
export async function expectNoEvent(
  sse: SseCollector, type: string, predicate: (event: PushEvent) => boolean = () => true,
  options: { since?: number; within?: number } = {},
): Promise<void> {
  const { since = 0, within = 2_000 } = options;
  await sse.page.waitForTimeout(within);
  const unexpected = (await sse.events()).slice(since).filter((event) => event.type === type && predicate(event));
  expect(unexpected, `${sse.user.nickname} 에게 오면 안 되는 ${type} 이 왔다`).toEqual([]);
}
