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
    baseURL: 'http://localhost:5174',
    viewport: { width: 1600, height: 900 },
    locale: 'ko-KR',
    trace: 'retain-on-failure',
  },
  projects: [{
    name: 'chromium',
    use: { ...devices['Desktop Chrome'], viewport: { width: 1600, height: 900 } },
  }],
  // E2E는 mock 서버를 쓴다. real은 backend 상태에 따라 결과가 흔들려서 회귀 판정이 안 된다.
  // 포트를 나눠 둬서 real 서버가 5173에 떠 있어도 그쪽으로 잘못 붙지 않는다.
  webServer: {
    command: 'npm run dev:mock',
    url: 'http://localhost:5174',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
