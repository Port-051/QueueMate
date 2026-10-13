import type { BrowserContext, Page } from '@playwright/test';
import { expect, test, NICKNAMES } from './support/fixtures';

/**
 * 시나리오 24 — 로그인하지 않은 사람이 보는 화면(공개 홈 · 로그인 · 법적 문서 · 콜백 경로)과 개발용 로그인(`TEMP-DEV-LOGIN`).
 * 근거 — `frontend/CLAUDE.md` §3-10(로그인 화면은 소셜 셋 + 개발용 로그인 · "최근 사용" 배지) · §3-40(`/privacy` · `/terms` · `/` 는 로그인 없이 · 리디렉트 없음 · 모든 화면 아래 푸터와 Riot 고지문) ·
 * §3-2(콜백 경로 `/signup/social` · `/login?error=OAUTH_FAILED` 는 백엔드의 것) · platform-api.md "소셜 로그인"(`GET /auth/social/pending` 401 `NO_PENDING_SOCIAL_SIGNUP`).
 *
 * **실제 소셜 OAuth 는 돌리지 않는다**(앱 키가 없다 — `START_HERE.md` §6). 소셜 버튼이 **브라우저 이동**(`GET /api/v1/auth/oauth/{P}/start`)인 것은 그 경로를 `page.route` 로 받아 확인한다 —
 * 백엔드까지 가면 제공자 설정에 따라 302 또는 404 라 결과를 가를 수 없어서다.
 * 로그인하지 않은 상태가 필요해 `crew` 가 아니라 **빈 컨텍스트**를 따로 연다(쿠키 없음). 개발용 로그인 단계만 `e2e-a` 로 들어간다(새 사람을 만들지 않는다).
 */
test('시나리오 24 — 공개 홈 · 로그인 화면 · 법적 문서 · 콜백 경로(로그인 없이)', async ({ browser, baseURL, crew }) => {
  const anon: BrowserContext = await browser.newContext({ baseURL, locale: 'ko-KR', viewport: { width: 1440, height: 900 } });
  const page: Page = await anon.newPage();
  const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

  try {
    await test.step('/ — 로그인하지 않아도 리디렉트 없이 공개 홈 · 로그인 · 시작하기 링크 · 받는 정보 안내 · 푸터(처리방침 · 약관 · Riot 고지문 영어 원문)', async () => {
      await page.goto('/');
      // 세션 확인(`GET /users/me` 401)이 끝나도 그대로 공개 홈이다 — 로그인으로 보내지 않는다(§3-40).
      await expect(page.getByRole('heading', { level: 1 })).toContainText('함께할 팀원');
      await page.waitForTimeout(1_000);
      expect(new URL(page.url()).pathname).toBe('/');
      await expect(page.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
      await expect(page.getByRole('link', { name: '시작하기' })).toHaveAttribute('href', '/login');
      await expect(page.getByRole('region', { name: '로그인과 개인정보' })).toContainText('카카오 · 디스코드 · 구글 소셜 로그인만');
      // 이메일 · 비밀번호는 받지 않는다 — 폼 자체가 없다(§2 "가입 · 로그인").
      await expect(page.locator('input[type="email"], input[type="password"]')).toHaveCount(0);
      const footer = page.locator('footer.site-footer');
      await expect(footer.getByRole('link', { name: '개인정보 처리방침' })).toHaveAttribute('href', '/privacy');
      await expect(footer.getByRole('link', { name: '이용약관' })).toHaveAttribute('href', '/terms');
      // Riot General Policies 의 고지문 — 글자를 바꾸지 않는다(`domain/legal.ts` `RIOT_NOTICE_EN` · §4).
      await expect(footer.locator('.site-footer-riot[lang="en"]')).toContainText("QueueMate isn't endorsed by Riot Games");
      await expect(footer).toContainText('team.port.051@gmail.com');
    });

    await test.step('/app/home — 로그인 전에는 /login 으로(RequireAuth)', async () => {
      await page.goto('/app/home');
      await page.waitForURL(/\/login$/);
    });

    await test.step('/login — 소셜 버튼 셋만(카카오 · Discord · Google) · 이메일 폼 없음 · 개발 서버라 개발용 로그인 판이 보인다', async () => {
      await expect(page.getByRole('heading', { level: 1, name: '로그인' })).toBeVisible();
      for (const label of ['카카오로 시작하기', 'Discord로 시작하기', 'Google로 계속하기']) {
        await expect(page.getByRole('button', { name: new RegExp(`^${esc(label)}`) })).toBeVisible();
      }
      await expect(page.locator('.social-login button')).toHaveCount(3);
      await expect(page.locator('input[type="email"], input[type="password"]')).toHaveCount(0);
      await expect(page.getByRole('link', { name: /회원가입/ })).toHaveCount(0);
      // TEMP-DEV-LOGIN — `import.meta.env.DEV` 일 때만 그린다(5173 은 dev 서버다).
      const dev = page.getByRole('form', { name: '개발용 로그인' });
      await expect(dev).toBeVisible();
      await expect(dev.getByRole('button', { name: '개발용: 로그인 없이 들어가기' })).toBeEnabled();
      await expect(dev.getByRole('textbox', { name: '개발용 로그인 닉네임' })).toHaveValue('dev-tester');
      // 에러가 없으면 경고 줄도 없다.
      await expect(page.locator('.login-error')).toHaveCount(0);
    });

    await test.step('소셜 버튼은 XHR 이 아니라 브라우저 이동이다 — GET /api/v1/auth/oauth/KAKAO/start 로 간다 · 누른 제공자를 기억해 "최근 사용" 배지', async () => {
      // 백엔드까지 가면 제공자 설정에 따라 302(제공자) · 404 `OAUTH_PROVIDER_NOT_CONFIGURED` 라 여기서 받아 "이동이 일어났다" 만 본다.
      await page.route('**/api/v1/auth/oauth/*/start', (route) => route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: '<!doctype html><title>oauth start</title><p>oauth</p>' }));
      const navigation = page.waitForRequest((r) => r.url().includes('/api/v1/auth/oauth/KAKAO/start') && r.isNavigationRequest());
      await page.getByRole('button', { name: /^카카오로 시작하기/ }).click();
      const request = await navigation;
      expect(request.method()).toBe('GET');
      await expect(page).toHaveURL(/\/api\/v1\/auth\/oauth\/KAKAO\/start$/);
      await page.unroute('**/api/v1/auth/oauth/*/start');
      // 돌아오면 마지막으로 누른 제공자에 "최근 사용" 배지(`localStorage['qm.lastProvider']` — §3-10).
      await page.goto('/login');
      expect(await page.evaluate(() => localStorage.getItem('qm.lastProvider'))).toBe('KAKAO');
      await expect(page.getByRole('button', { name: '카카오로 시작하기 (최근 사용)' })).toBeVisible();
      await expect(page.getByRole('button', { name: /^Discord로 시작하기/ })).not.toHaveClass(/is-recent/);
    });

    await test.step('/login?error=OAUTH_FAILED — 실패 문구(role=alert) · 모르는 코드는 괄호에 그대로', async () => {
      await page.goto('/login?error=OAUTH_FAILED');
      await expect(page.getByRole('alert')).toHaveText('소셜 로그인에 실패했습니다. 다시 시도해주세요');
      await page.goto('/login?error=SOMETHING_ELSE');
      await expect(page.getByRole('alert')).toHaveText('소셜 로그인에 실패했습니다 (SOMETHING_ELSE)');
    });

    await test.step('/privacy · /terms — 로그인 없이 열린다 · 제목 · 문서 탭 · 목차 · 조항 · 푸터의 Riot 고지문 · 페이지 제목', async () => {
      await page.goto('/privacy');
      await expect(page.getByRole('heading', { level: 1, name: '개인정보 처리방침' })).toBeVisible();
      await expect(page).toHaveTitle('개인정보 처리방침 — QueueMate');
      const docNav = page.getByRole('navigation', { name: '문서' });
      await expect(docNav.getByRole('link', { name: '개인정보 처리방침' })).toHaveAttribute('aria-current', 'page');
      await expect(docNav.getByRole('link', { name: '이용약관' })).not.toHaveAttribute('aria-current', 'page');
      // 법정 최소한 13개 조(§3-46).
      await expect(page.getByRole('navigation', { name: '목차' }).getByRole('listitem')).toHaveCount(13);
      await expect(page.getByRole('heading', { level: 2, name: /^제1조 \(개인정보의 처리 목적\)/ })).toBeVisible();
      await expect(page.locator('footer.site-footer .site-footer-riot[lang="en"]')).toContainText('Riot Games, Inc.');
      // 운영자 · 연락처는 소유자가 준 값 그대로다(§3-42).
      await expect(page.locator('.legal-meta')).toContainText('Port-051');
      await page.waitForTimeout(500);
      expect(new URL(page.url()).pathname).toBe('/privacy');

      await page.goto('/terms');
      await expect(page.getByRole('heading', { level: 1, name: '이용약관' })).toBeVisible();
      await expect(page).toHaveTitle('이용약관 — QueueMate');
      // 약관 12개 조 + 외부 서비스 조항의 Riot 고지문(원문 그대로).
      await expect(page.getByRole('navigation', { name: '목차' }).getByRole('listitem')).toHaveCount(12);
      await expect(page.locator('article.legal-doc')).toContainText("QueueMate isn't endorsed by Riot Games");
      await expect(page.getByRole('navigation', { name: '문서' }).getByRole('link', { name: '이용약관' })).toHaveAttribute('aria-current', 'page');
    });

    await test.step('/signup/social — 가입 대기 쿠키가 없으면 /login 으로(GET /auth/social/pending 401)', async () => {
      const pending = page.waitForResponse((r) => r.url().endsWith('/api/v1/auth/social/pending'));
      await page.goto('/signup/social');
      expect((await pending).status()).toBe(401);
      await page.waitForURL(/\/login$/);
    });

    await test.step('옛 경로 — /signup → /login · /auth/callback → / · 모르는 경로 → /', async () => {
      await page.goto('/signup');
      await page.waitForURL(/\/login$/);
      await page.goto('/auth/callback?code=abc');
      await page.waitForURL((url) => url.pathname === '/');
      await page.goto('/no-such-page');
      await page.waitForURL((url) => url.pathname === '/');
    });

    await test.step('개발용 로그인(TEMP-DEV-LOGIN) — e2e-a 로 들어가면 POST /auth/dev-login 200 · 쿠키로 GET /users/me 가 200 · 홈(또는 온보딩)으로', async () => {
      /*
       * 2026-10-13 첫 실행에서 `dev-tester`(소유자의 손 테스트 계정)로 로그인됐다 — 원인: `main.tsx` 의 `MatchProvider` · `SocialProvider` 가 `key={status:userId}` 라
       * 세션 확인(`GET /users/me` 401)이 끝나 `loading → anonymous` 가 되는 순간 `<App/>` 아래가 통째로 다시 마운트되고, 그 전에 채운 입력이 기본값 `dev-tester` 로 돌아갔다.
       * 그래서 ① 세션 확인이 끝난 뒤에 채우고 ② 채운 값이 남아 있는지 보고 ③ 요청 본문의 닉네임이 `e2e-a` 가 아니면 **요청을 끊어** 다른 사람으로 로그인되는 일을 막는다.
       */
      const guard = '**/api/v1/auth/dev-login';
      let bodyNickname: unknown = null;
      await page.route(guard, async (route) => {
        bodyNickname = route.request().postDataJSON()?.nickname;
        if (bodyNickname !== NICKNAMES.a) { await route.abort('blockedbyclient'); return; }
        await route.continue();
      });
      const settled = page.waitForResponse((r) => r.url().endsWith('/api/v1/users/me') && r.request().method() === 'GET');
      await page.goto('/login');
      expect((await settled).status()).toBe(401);
      const dev = page.getByRole('form', { name: '개발용 로그인' });
      await expect(dev).toHaveCount(1);
      const nickname = dev.getByRole('textbox', { name: '개발용 로그인 닉네임' });
      await expect(nickname).toHaveValue('dev-tester');
      await nickname.fill(NICKNAMES.a);
      await expect(nickname).toHaveValue(NICKNAMES.a);
      // 다시 마운트가 늦게 와도 값이 남아 있는지 한 번 더 — 그 뒤에 누른다.
      await page.waitForTimeout(500);
      await expect(nickname).toHaveValue(NICKNAMES.a);
      const response = page.waitForResponse((r) => r.url().endsWith('/api/v1/auth/dev-login') && r.request().method() === 'POST');
      await dev.getByRole('button', { name: '개발용: 로그인 없이 들어가기' }).click();
      const res = await response;
      expect(bodyNickname, '요청 본문의 닉네임이 e2e-a 가 아니다(끊었다)').toBe(NICKNAMES.a);
      expect(res.status(), await res.text()).toBe(200);
      expect((await res.json()).nickname).toBe(NICKNAMES.a);
      await page.unroute(guard);
      // 목적지는 `landingPath` — 게임 계정이 없고 온보딩을 지나간 적이 없으면 /onboarding, 아니면 /app/home(§3-16). 둘 중 하나다.
      await page.waitForURL(/\/(app\/home|onboarding)$/);
      const me = await page.evaluate(async () => { const r = await fetch('/api/v1/users/me', { credentials: 'same-origin' }); return { status: r.status, body: await r.json() }; });
      expect(me.status).toBe(200);
      expect(me.body.nickname).toBe(NICKNAMES.a);
      // 로그인된 채 /login · / 로 가면 앱으로 보낸다.
      await page.goto('/login');
      await page.waitForURL(/\/(app\/home|onboarding)$/);
    });

    await test.step('로그인한 사람도 /privacy · /terms 는 그대로 본다(리디렉트 없음)', async () => {
      const a = await crew.appPage('a', '/privacy');
      await expect(a.getByRole('heading', { level: 1, name: '개인정보 처리방침' })).toBeVisible();
      await a.waitForTimeout(500);
      expect(new URL(a.url()).pathname).toBe('/privacy');
      await a.close();
    });
  } finally {
    await anon.close();
  }
});
