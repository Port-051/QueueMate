import { expect } from '@playwright/test';
import type { Page, Locator } from '@playwright/test';

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
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  await expect(page.locator('.home-profile .recruitment-composer-shell')).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  if (auto) await page.locator('.recruitment-composer-shell').getByRole('radio', { name: '자동 매칭', exact: true }).check();
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.my-recruitment')).toBeVisible();
}

/** 오른쪽 매칭 영역의 개별 관리 버튼을 누른다. */
export async function manageRecruitment(page: Page, action: string): Promise<void> {
  await page.locator('.my-recruitment').getByRole('button', { name: action, exact: true }).click();
}

/** 버튼 선택 그룹에서 이미 선택된 항목은 해제하지 않는다. */
export async function selectButton(group: Locator, name: string): Promise<void> {
  const button = group.getByRole('button', { name, exact: true });
  if (await button.getAttribute('aria-pressed') !== 'true') await button.click();
}
