import { defineConfig, devices } from '@playwright/test';

/** desktop web 16:9를 1차 기준으로 한다(CLAUDE.md §6). */
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    viewport: { width: 1600, height: 900 },
    locale: 'ko-KR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      testIgnore: /room-decks\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1600, height: 900 },
        baseURL: 'http://localhost:5194',
      },
    },
    {
      name: 'room-decks',
      testMatch: /room-decks\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1600, height: 900 },
        baseURL: 'http://localhost:5195',
      },
    },
  ],
  // 기존 실시간/예약 흐름과 새 방 UI는 서로 다른 Mock 서버에서 검증한다.
  // 개발 중인 5174 서버를 재사용하지 않아 환경 변수나 체크아웃이 결과에 영향을 주지 않는다.
  webServer: [
    {
      command: 'VITE_HOME_LAYOUT=legacy npm run dev:mock -- --port 5194',
      url: 'http://localhost:5194',
      reuseExistingServer: false,
      timeout: 120_000,
    },
    {
      command: 'npm run dev:mock -- --port 5195',
      url: 'http://localhost:5195',
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
