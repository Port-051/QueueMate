import { expect } from '@playwright/test';
import type { Page } from '@playwright/test';

export const DEMO = { email: 'demo@queuemate.gg', password: 'queuemate1' };

/** mock 모드의 데모 계정으로 로그인해 홈까지 들어간다. */
export async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
  await page.getByPlaceholder('비밀번호를 입력하세요').fill(DEMO.password);
  await page.locator('.auth-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/home/);
}

export async function startRealtimeMatch(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'League of Legends 매칭', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'League of Legends', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '매칭 시작' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.locator('.home-active-match')).toBeVisible();
}
