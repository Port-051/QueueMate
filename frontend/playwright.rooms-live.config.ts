import { defineConfig } from '@playwright/test';

// Start the opt-in backend with scripts/room-demo-server.sh before this suite.
export default defineConfig({
  testDir: './e2e-live',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  workers: 1,
  reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:5196', viewport: { width: 1600, height: 1000 }, trace: 'retain-on-failure' },
  webServer: {
    command: 'VITE_API_MODE=real VITE_HOME_LAYOUT=rooms vite --host 127.0.0.1 --port 5196 --strictPort',
    url: 'http://127.0.0.1:5196',
    reuseExistingServer: false,
  },
});
