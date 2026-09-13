import { expect, test } from '@playwright/test';
import { DEMO, login } from './helpers';

test('사이드바는 호버와 키보드 탐색 때 펼쳐지고 마우스가 떠나면 다시 접힌다', async ({ page }) => {
  await login(page);
  const sidebar = page.locator('.sidebar');
  const wordmark = sidebar.locator('.brand-wordmark');
  const brandLink = sidebar.getByRole('link', { name: 'QueueMate 홈', exact: true });
  const label = sidebar.locator('.nav-profile .nav-label');
  const profileAvatar = sidebar.locator('.nav-profile .avatar-wrap');
  await page.mouse.move(900, 100);
  await expect(sidebar).toHaveCSS('width', '80px');
  await expect(wordmark).toBeHidden();
  await expect(label).toBeHidden();
  await expect(profileAvatar).toHaveCSS('outline-style', 'none');
  await expect(sidebar.locator('.nav-profile .avatar-status')).toHaveCount(0);
  await expect(label.locator('small')).toHaveCount(0);
  await expect(sidebar).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  const main = page.locator('.main');
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const homeBounds = await main.boundingBox();
  const gameBounds = await page.locator('.board-games').boundingBox();

  await brandLink.hover();
  await expect(sidebar).toHaveCSS('width', '232px');
  await expect(wordmark).toBeVisible();
  await expect(label).toBeVisible();
  await expect(label).toHaveText('프로필');
  expect(await main.boundingBox()).toEqual(homeBounds);
  expect(await page.locator('.board-games').boundingBox()).toEqual(gameBounds);
  await page.mouse.move(900, 100);
  await expect(sidebar).toHaveCSS('width', '80px');
  expect(await main.boundingBox()).toEqual(homeBounds);
  expect(await page.locator('.board-games').boundingBox()).toEqual(gameBounds);
  await sidebar.getByRole('link', { name: '프로필', exact: true }).click();
  await expect(profileAvatar).toHaveCSS('outline-style', 'solid');
  await expect(profileAvatar).toHaveCSS('outline-width', '2px');
  await expect(profileAvatar).toHaveCSS('outline-color', 'rgb(255, 255, 255)');
  await page.mouse.move(900, 100);
  await expect(sidebar).toHaveCSS('width', '80px');
  await expect(label).toBeHidden();
  await expect(wordmark).toBeHidden();
  const profileBounds = await main.boundingBox();

  await sidebar.getByRole('link', { name: '홈', exact: true }).focus();
  await page.keyboard.press('Tab');
  await expect(sidebar).toHaveCSS('width', '232px');
  await expect(sidebar.getByRole('link', { name: '메시지', exact: true })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(sidebar.getByRole('button', { name: '알림', exact: true })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(sidebar.getByRole('link', { name: '프로필', exact: true })).toBeFocused();
  await expect(sidebar.locator('.nav-label').first()).toBeVisible();
  expect(await main.boundingBox()).toEqual(profileBounds);

  await brandLink.click();
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(profileAvatar).toHaveCSS('outline-style', 'none');
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
});

test('기존 설정 주소는 프로필의 설정 영역으로 연결된다', async ({ page }) => {
  await page.goto('/app/settings');
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
  await page.getByPlaceholder('비밀번호를 입력하세요').fill(DEMO.password);
  await page.locator('.auth-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/me#settings$/);
  await expect(page.getByRole('heading', { name: '매칭 기본값', exact: true })).toBeInViewport();
  await expect(page.getByRole('region', { name: '매칭 기본값', exact: true }).getByRole('button', { name: '즐겜', exact: true })).toBeVisible();
});
