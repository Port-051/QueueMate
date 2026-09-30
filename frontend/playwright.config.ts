import { defineConfig, devices } from '@playwright/test';

/**
 * 실제 백엔드 셋에 붙어 도는 시나리오 e2e(2026-09-30 소유자 결정 — `CLAUDE.md` §3-21 · `START_HERE.md` §6).
 *
 * - **서버를 띄우지 않는다(`webServer` 없음).** 돌리기 전에 platform 8082(`DEV_LOGIN_ENABLED=true`) · notification 8081 ·
 *   matching 8080 · Vite 5173 · PostgreSQL 5433 · Redis 6380(gameconfig seed) 이 떠 있어야 한다.
 * - **늘 `http://localhost:5173` 으로 부른다** — 백엔드의 `Origin` 검사가 `http://localhost:5173` 을 허용하고(127.0.0.1 은 403),
 *   Vite 프록시가 경로별로 세 앱에 나눈다(운영의 같은 출처와 같은 모양).
 * - **한 줄로, 다시 하지 않는다**(`workers: 1` · `retries: 0`) — 시나리오들이 같은 테스트 사용자(`e2e-a` · `e2e-b` · `e2e-c`)와
 *   같은 게시판 · 대기열을 쓰고, LoL 계정 연결(시나리오 9)은 Riot 호출 14번이라 되풀이하면 개발용 키의 한도(2분에 100번)를 태운다.
 * - 가짜 마이크(`--use-fake-*-for-media-stream`) — 시나리오 10 의 음성이 권한 창 없이 가짜 소리를 낸다.
 * - **끝나면 e2e 사람의 끝난 글을 지운다**(`globalTeardown` — `e2e/support/cleanup.ts` · 2026-09-30 소유자 지시). 지우는 API 가 없어 로컬 테스트 DB 에
 *   `docker.exe exec qm-platform-test-pg psql` 로 직접 지운다 — 안 되면 경고만 남긴다. `E2E_KEEP_POSTS=1` 이면 남긴다.
 */
export default defineConfig({
  testDir: 'e2e',
  globalTeardown: './e2e/support/cleanup.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        permissions: ['microphone'],
        launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] },
      },
    },
  ],
});
