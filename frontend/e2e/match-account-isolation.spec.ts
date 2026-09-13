import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { DEMO, login, startRealtimeMatch } from './helpers';

async function logout(page: Page) {
  await page.locator('.side-nav').getByRole('link', { name: '프로필', exact: true }).click();
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await expect(page.getByRole('link', { name: '시작하기', exact: true })).toBeVisible();
}

async function signupSecondAccount(page: Page) {
  await page.getByRole('link', { name: '시작하기', exact: true }).click();
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill('match-isolation@example.com');
  await page.getByPlaceholder('닉네임을 입력하세요').fill('SeparateMatcher');
  await page.getByPlaceholder('비밀번호를 입력하세요').fill('queuemate2');
  await page.locator('.auth-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/home$/);
}

async function loginOriginalAccount(page: Page) {
  // SPA navigation keeps the mock account registry and active recruitment alive.
  await page.getByRole('link', { name: '로그인', exact: true }).click();
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
  await page.getByPlaceholder('비밀번호를 입력하세요').fill(DEMO.password);
  await page.locator('.auth-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/home$/);
}

test('모집 중 계정을 바꾸면 이전 모집과 제안을 표시하지 않고 본인 계정에서만 복원한다', async ({ page }) => {
  await login(page);
  await startRealtimeMatch(page);
  await expect(page.locator('.my-recruitment')).toBeVisible();
  await logout(page);
  await signupSecondAccount(page);
  await expect(page.locator('.my-recruitment')).toHaveCount(0);
  await expect(page.locator('.board-proposal')).toHaveCount(0);
  await expect(page.locator('.compact-party')).toHaveCount(0);
  await expect(page.locator('.intro-launch > button')).toBeEnabled();
  await logout(page);
  await loginOriginalAccount(page);
  await expect(page.locator('.my-recruitment')).toBeVisible();
  await expect(page.locator('.intro-launch > button')).toHaveCount(0);
});

test('파티는 계정별로 복원하고 이전 공용 저장 키와 파티 채팅을 다른 계정에 가져오지 않는다', async ({ page }) => {
  await login(page);
  await startRealtimeMatch(page, true);
  await expect(page.locator('.board-proposal')).toBeVisible({ timeout: 15000 });
  await page.getByRole('button', { name: '함께할게요' }).click();
  await expect(page.locator('.compact-party')).toBeVisible();
  await expect(page.getByRole('log')).toContainText('데모 파티에 연결되었습니다.');
  await page.getByPlaceholder('메시지를 입력하세요').fill('첫 번째 계정의 파티 대화');
  await page.getByPlaceholder('메시지를 입력하세요').press('Enter');
  await expect(page.getByRole('log')).toContainText('첫 번째 계정의 파티 대화');

  const storedParty = await page.evaluate(() => {
    const key = Object.keys(localStorage).find(item => item.startsWith('qm.activeParty.'));
    return key ? { key, id: localStorage.getItem(key) } : null;
  });
  expect(storedParty?.id).toBeTruthy();
  await page.evaluate(id => localStorage.setItem('qm.activeParty', id!), storedParty!.id);
  await logout(page);
  await signupSecondAccount(page);
  await expect(page.locator('.compact-party')).toHaveCount(0);
  await expect(page.locator('.board-proposal')).toHaveCount(0);
  await expect(page.locator('.my-recruitment')).toHaveCount(0);
  await expect(page.locator('.intro-launch > button')).toBeEnabled();
  await expect(page.getByText('첫 번째 계정의 파티 대화', { exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('qm.activeParty.')))).toEqual([storedParty!.key]);
  await logout(page);
  await loginOriginalAccount(page);
  await expect(page.locator('.compact-party')).toBeVisible();
  expect(await page.evaluate(key => localStorage.getItem(key), storedParty!.key)).toBe(storedParty!.id);
});
