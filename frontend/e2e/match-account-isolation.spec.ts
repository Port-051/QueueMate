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

test('매칭 중 계정을 바꾸면 이전 매칭과 제안을 표시하지 않고 본인 계정에서만 복원한다', async ({ page }) => {
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

test('성사된 매칭과 메시지는 계정별로만 복원한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page, true);
  await page.clock.fastForward(7000);
  await page.locator('.duo-offer').getByRole('button', { name: '같이 할래요' }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await page.clock.fastForward(22000);
  await page.getByRole('button', { name: '메시지로 이동' }).click();
  await expect(page.getByRole('note')).toContainText('매칭 성사');
  await logout(page); await signupSecondAccount(page);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toHaveCount(0);
  await expect(page.locator('.duo-offer')).toHaveCount(0);
  await expect(page.locator('.my-recruitment')).toHaveCount(0);
  await page.locator('.side-nav a[href="/app/messages"]').click();
  await expect(page.getByRole('note')).toHaveCount(0);
  await logout(page); await loginOriginalAccount(page);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '메시지로 이동' }).click();
  await expect(page.getByRole('note')).toContainText('매칭 성사');
});
