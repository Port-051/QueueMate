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

/** 게시판의 티어 팝업에서 표시된 선택지를 고른다. */
export async function selectBoardFilter(page: Page, label: '찾는 상대 티어', option: string): Promise<void> {
  await page.locator('.board-filter-bar').getByRole('button', { name: label, exact: true }).click();
  const popup = page.getByRole('listbox', { name: label, exact: true });
  await popup.getByRole('option', { name: option, exact: true }).click();
  await expect(popup).toHaveCount(0);
}

export async function startRealtimeMatch(page: Page, auto = false): Promise<void> {
  await page.locator('.intro-launch > button').click();
  await expect(page.getByRole('dialog')).toBeVisible();
  if (auto) await page.getByRole('dialog').getByRole('radio', { name: '자동 매칭', exact: true }).check();
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.my-recruitment')).toBeVisible();
}

/** 부가 모집 작업은 관리 메뉴에서 선택한다. */
export async function manageRecruitment(page: Page, action: string): Promise<void> {
  const menu = page.locator('.my-recruitment .action-menu');
  if (await menu.getAttribute('open') === null) await menu.locator('summary').click();
  await menu.getByRole('button', { name: action, exact: true }).click();
}
