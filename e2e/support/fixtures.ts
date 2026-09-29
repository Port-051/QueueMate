import { test as base, type Browser, type BrowserContextOptions, type Page } from '@playwright/test';
import { QmUser } from './api';
import { reset } from './reset';
import { SseCollector } from './sse';

export { expect } from '@playwright/test';

/**
 * 고정 테스트 닉네임 — 사용자를 지우는 API 가 없어 이름을 고정해 쌓이지 않게 한다(개발용 로그인이 없으면 만들고 있으면 그 사람이다).
 * **`dev-tester`(소유자의 손 테스트 계정)는 쓰지도 건드리지도 않는다.**
 */
export const NICKNAMES = { a: 'e2e-a', b: 'e2e-b', c: 'e2e-c' } as const;
export type UserKey = keyof typeof NICKNAMES;

export interface UserOptions {
  /** 확정된 매칭 요청(60초 잠금)이 풀릴 때까지 기다린 뒤 돌려준다 — 글 쓰기 · 입장 · 매칭을 하는 시나리오. 기본 `true`. */
  waitIdle?: boolean;
  /** SSE 수집기를 연다. 기본 `false`. */
  sse?: boolean;
}

/**
 * 한 시나리오의 등장인물. `user('a')` 가 처음 불릴 때 그 사람의 브라우저 컨텍스트를 만들고 개발용 로그인 → {@link reset} 한다.
 * 시나리오가 끝나면 다시 {@link reset}(실패는 삼키고 적기만 한다) 하고 컨텍스트를 닫는다.
 */
export class Crew {
  private readonly users = new Map<UserKey, QmUser>();
  private readonly collectors = new Map<UserKey, SseCollector>();

  constructor(private readonly browser: Browser, private readonly contextOptions: BrowserContextOptions) {}

  async user(key: UserKey, options: UserOptions = {}): Promise<QmUser> {
    let user = this.users.get(key);
    if (!user) {
      const context = await this.browser.newContext(this.contextOptions);
      user = new QmUser(key, NICKNAMES[key], context);
      this.users.set(key, user);
      await user.login();
      await reset(user, { waitIdle: options.waitIdle ?? true, log: (line) => console.log(`  [reset] ${line}`) });
    }
    if (options.sse) await this.sse(key);
    return user;
  }

  /** 그 사람의 SSE 수집기(없으면 연다). */
  async sse(key: UserKey): Promise<SseCollector> {
    let collector = this.collectors.get(key);
    if (!collector) {
      const user = await this.user(key);
      collector = await SseCollector.open(user);
      this.collectors.set(key, collector);
    }
    return collector;
  }

  /**
   * 그 사람의 컨텍스트에서 앱 화면을 연다(UI 시나리오). 연 뒤 마우스를 화면 가운데로 옮긴다 — 헤드리스의 처음 마우스 자리(0, 0)가
   * 왼쪽 사이드바 위라 `:hover` 로 사이드바가 펼쳐져(80 → 232px) 본문 왼쪽 버튼(자동 매칭 판의 "솔로 랭크" 등)을 덮는다(2026-09-30 확인).
   */
  async appPage(key: UserKey, path: string): Promise<Page> {
    const user = await this.user(key);
    const page = await user.context.newPage();
    await page.goto(path);
    const size = page.viewportSize() ?? { width: 1440, height: 900 };
    await page.mouse.move(size.width / 2, size.height / 2);
    return page;
  }

  async dispose(): Promise<void> {
    for (const user of this.users.values()) {
      // 앱 화면을 먼저 닫는다 — 떠 있는 앱이 방 heartbeat · 매칭 heartbeat 를 계속 보내지 않게.
      for (const page of user.context.pages()) await page.close().catch(() => undefined);
      try {
        await reset(user, { waitIdle: false, log: (line) => console.log(`  [cleanup] ${line}`) });
      } catch (error) {
        console.log(`  [cleanup] ${user.nickname}: 정리 실패 — ${String(error)}`);
      }
      await user.context.close().catch(() => undefined);
    }
  }
}

export const test = base.extend<{ crew: Crew }>({
  crew: async ({ browser, baseURL, viewport, locale, timezoneId, permissions }, use) => {
    const crew = new Crew(browser, { baseURL, viewport, locale, timezoneId, permissions });
    await use(crew);
    await crew.dispose();
  },
});
