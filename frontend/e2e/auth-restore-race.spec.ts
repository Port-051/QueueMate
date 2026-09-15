import { expect, test } from '@playwright/test';
import { DEMO } from './helpers';

test('이전 세션을 확인하는 동안 로그인 입력을 보호한다', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('qm.tokens', JSON.stringify({ accessToken: 'expired-access', refreshToken: 'expired-refresh' })));
  await page.route('**/src/mocks/server.ts*', async route => {
    const response = await route.fetch();
    const body = (await response.text()).replace('await delay(LATENCY_MS);', `
      if (fullPath === '/users/me' && token === 'expired-access') {
        window.__expiredRestoreStarted = true;
        await delay(1500);
        window.__expiredRestoreFinished = true;
        throw new ApiError(401, 'UNAUTHORIZED', '이전 세션 만료');
      }
      await delay(LATENCY_MS);
    `);
    await route.fulfill({ response, body });
  });
  await page.goto('/login');
  await page.waitForFunction(() => (window as unknown as { __expiredRestoreStarted?: boolean }).__expiredRestoreStarted === true);
  await expect(page.getByPlaceholder('이메일 주소를 입력하세요')).toBeDisabled();
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
  await page.getByPlaceholder('비밀번호를 입력하세요').fill(DEMO.password);
  await page.locator('.auth-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/home/);
  await page.waitForFunction(() => (window as unknown as { __expiredRestoreFinished?: boolean }).__expiredRestoreFinished === true);
  await expect(page).toHaveURL(/\/app\/home/);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('qm.tokens')!).accessToken)).toMatch(/^mock-access-/);
  await page.locator('.home-profile-link').click();
  await expect(page.getByRole('heading', { name: 'QueueMaster', exact: true })).toBeVisible();
});
