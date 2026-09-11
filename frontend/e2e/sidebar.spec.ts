import { expect, test } from '@playwright/test';
import { DEMO, login } from './helpers';

test('사이드바는 호버와 키보드 탐색 때 펼쳐지고 마우스가 떠나면 다시 접힌다', async ({ page }) => {
  await login(page);
  const sidebar = page.locator('.sidebar');
  const wordmark = sidebar.locator('.brand-wordmark');
  const label = sidebar.locator('.nav-profile .nav-label');
  await page.mouse.move(900, 100);
  await expect(sidebar).toHaveCSS('width', '80px');
  await expect(wordmark).toBeHidden();
  await expect(label).toBeHidden();
  await expect(sidebar.locator('.avatar-status.online')).toBeVisible();
  await expect(sidebar).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');

  await sidebar.hover();
  await expect(sidebar).toHaveCSS('width', '232px');
  await expect(wordmark).toBeVisible();
  await expect(label).toBeVisible();
  await sidebar.getByRole('link', { name: '프로필', exact: true }).click();
  await page.mouse.move(900, 100);
  await expect(sidebar).toHaveCSS('width', '80px');
  await expect(label).toBeHidden();
  await expect(wordmark).toBeHidden();

  await sidebar.getByRole('link', { name: '홈', exact: true }).focus();
  await page.keyboard.press('Tab');
  await expect(sidebar).toHaveCSS('width', '232px');
  await expect(sidebar.getByRole('link', { name: '친구', exact: true })).toBeFocused();
  await expect(sidebar.locator('.nav-label').first()).toBeVisible();
});

test('기존 설정 주소는 프로필의 설정 영역으로 연결된다', async ({ page }) => {
  await page.goto('/app/settings');
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
  await page.getByPlaceholder('비밀번호를 입력하세요').fill(DEMO.password);
  await page.locator('.auth-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/me#settings$/);
  await expect(page.getByRole('heading', { name: '설정', exact: true })).toBeInViewport();
  await expect(page.getByRole('region', { name: '설정', exact: true }).getByRole('button', { name: '즐겜', exact: true })).toBeVisible();
});
