import type { BrowserContext, Page } from '@playwright/test';

/**
 * 앱을 띄우지 않는 빈 페이지의 경로 — `page.route` 가 그 자리에서 응답해 Vite 에 닿지 않는다. 출처는 `http://localhost:5173` 그대로라
 * 백엔드의 `Origin` 검사(`platform-api.md` "공통" — 127.0.0.1 은 403)를 브라우저가 싣는 `Origin` 으로 통과한다.
 */
const BLANK_PATH = '/__e2e/blank';

/** 응답 하나 — 상태 코드 · 본문(JSON 이면 풀어서 — 에러는 `{code, message, details}`) · 헤더. 실패도 던지지 않고 돌려준다(검증은 시나리오가 한다). */
export interface ApiResult<T = any> {
  status: number;
  body: T;
  headers: Record<string, string>;
}

/**
 * 테스트 사용자 한 명 = 브라우저 컨텍스트 하나. REST 는 그 컨텍스트의 **빈 페이지(출처 `http://localhost:5173`)에서 브라우저의 `fetch`** 로 부른다 —
 * 앱과 똑같이 쿠키(`qm_access` · `qm_refresh`)와 `Origin` 을 브라우저가 싣고, Vite 프록시가 경로별로 세 앱에 나눈다. 같은 페이지가 SSE 도 연다(`sse.ts`).
 *
 * `context.request`(Node 쪽 HTTP)를 쓰지 않는 이유 — Playwright 는 `localhost` 를 `::1` 부터 두드리는데 Vite 가 `127.0.0.1` 에만 떠 있고,
 * `::1` 이 곧바로 거절돼도(ECONNREFUSED 4ms) 다음 주소로 넘어가기 전에 300ms 를 채워 기다려 연결마다 300ms 씩 늦었다
 * (2026-09-30 확인 — playwright-core happy eyeballs 의 `connectionAttemptDelayMs`).
 */
export class QmUser {
  /** 사용자 번호의 십진 문자열 — 방 응답 · 알림 `payload` 가 쓰는 모양. 게시판 응답의 숫자와 비교할 때는 {@link id}. */
  userId = '';
  private blank: Page | null = null;

  constructor(readonly key: string, readonly nickname: string, readonly context: BrowserContext) {}

  get id(): number {
    return Number(this.userId);
  }

  /** REST · SSE 를 부르는 빈 페이지(앱을 띄우지 않는다 — 앱은 `MATCH_CONFIRMED` 에 스스로 방을 부르는 등 끼어든다). */
  async page(): Promise<Page> {
    if (this.blank && !this.blank.isClosed()) return this.blank;
    const page = await this.context.newPage();
    await page.route(`**${BLANK_PATH}`, (route) => route.fulfill({
      status: 200,
      contentType: 'text/html; charset=utf-8',
      body: `<!doctype html><meta charset="utf-8"><title>e2e ${this.nickname}</title>`,
    }));
    await page.goto(BLANK_PATH);
    this.blank = page;
    return page;
  }

  /** 개발용 로그인(`TEMP-DEV-LOGIN` — platform `DEV_LOGIN_ENABLED=true`). 없으면 만들고 진짜 쿠키 둘을 받는다. */
  async login(): Promise<void> {
    const r = await this.post<{ userId: number; nickname: string }>('/auth/dev-login', { nickname: this.nickname });
    if (r.status === 404) throw new Error('개발용 로그인이 꺼져 있다 — platform 을 DEV_LOGIN_ENABLED=true 로 띄워야 한다');
    if (r.status !== 200) throw new Error(`개발용 로그인 실패(${this.nickname}): ${r.status} ${JSON.stringify(r.body)}`);
    this.userId = String(r.body.userId);
  }

  async call<T = any>(method: string, path: string, body?: unknown): Promise<ApiResult<T>> {
    const page = await this.page();
    const result = await page.evaluate(async ({ method, url, body }) => {
      const res = await fetch(url, {
        method,
        credentials: 'same-origin',
        headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const headers: Record<string, string> = {};
      res.headers.forEach((value, key) => { headers[key] = value; });
      return { status: res.status, text: await res.text(), headers };
    }, { method, url: `/api/v1${path}`, body });
    let parsed: any = null;
    try {
      parsed = result.text ? JSON.parse(result.text) : null;
    } catch {
      parsed = result.text;
    }
    return { status: result.status, body: parsed as T, headers: result.headers };
  }

  get<T = any>(path: string) { return this.call<T>('GET', path); }
  post<T = any>(path: string, body?: unknown) { return this.call<T>('POST', path, body); }
  put<T = any>(path: string, body?: unknown) { return this.call<T>('PUT', path, body); }
  patch<T = any>(path: string, body?: unknown) { return this.call<T>('PATCH', path, body); }
  del<T = any>(path: string) { return this.call<T>('DELETE', path); }
}

/** 실패 메시지에 넣을 한 줄 — 상태 · 본문. */
export function show(r: ApiResult): string {
  return `${r.status} ${typeof r.body === 'string' ? r.body : JSON.stringify(r.body)}`;
}
