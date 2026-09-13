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
  const feedBounds = await page.locator('.board-feed').boundingBox();

  await brandLink.hover();
  await expect(sidebar).toHaveCSS('width', '232px');
  await expect(wordmark).toBeVisible();
  await expect(label).toBeVisible();
  await expect(label).toHaveText('프로필');
  expect(await main.boundingBox()).toEqual(homeBounds);
  expect(await page.locator('.board-feed').boundingBox()).toEqual(feedBounds);
  await page.mouse.move(900, 100);
  await expect(sidebar).toHaveCSS('width', '80px');
  expect(await main.boundingBox()).toEqual(homeBounds);
  expect(await page.locator('.board-feed').boundingBox()).toEqual(feedBounds);
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

test('로고 아래 게임 아이콘은 선택 링을 표시하고 게임을 바꾸면 해당 홈 필터로 이동한다', async ({ page }) => {
  await login(page);
  const sidebar = page.locator('.sidebar');
  const games = sidebar.getByRole('group', { name: '게임 선택', exact: true });
  const lol = games.getByRole('button', { name: '리그 오브 레전드 매칭', exact: true });
  const valorant = games.getByRole('button', { name: '발로란트 매칭', exact: true });
  await page.mouse.move(900, 100);
  await expect(sidebar).toHaveCSS('width', '80px');
  await expect(games.getByRole('button')).toHaveCount(3);
  await expect(games.locator('.nav-label').first()).toBeHidden();
  await expect(page.locator('.board-home .board-games')).toHaveCount(0);
  const brandBox = (await sidebar.locator('.sidebar-brand-link').boundingBox())!;
  const gamesBox = (await games.boundingBox())!;
  const navBox = (await sidebar.getByRole('navigation', { name: '주 메뉴', exact: true }).boundingBox())!;
  expect(gamesBox.y).toBeGreaterThanOrEqual(brandBox.y + brandBox.height);
  expect(gamesBox.y + gamesBox.height).toBeLessThanOrEqual(navBox.y);
  await expect(lol).toHaveAttribute('aria-pressed', 'true');
  await expect(lol.locator('.game-nav-logo')).toHaveCSS('outline-width', '2px');
  await expect(lol.locator('.game-nav-logo')).toHaveCSS('outline-color', 'rgb(124, 77, 255)');

  await valorant.click();
  await expect(valorant).toHaveAttribute('aria-pressed', 'true');
  await expect(lol).toHaveAttribute('aria-pressed', 'false');
  await expect(valorant.locator('.game-nav-logo')).toHaveCSS('outline-width', '2px');
  await expect(valorant.locator('.game-nav-logo')).toHaveCSS('outline-color', 'rgb(124, 77, 255)');
  const roleFilter = page.getByRole('combobox', { name: '찾는 상대 포지션', exact: true });
  await expect(roleFilter.locator('option[value="DUELIST"]')).toHaveText('타격대');
  await expect(roleFilter.locator('option[value="TOP"]')).toHaveCount(0);

  await sidebar.getByRole('link', { name: '프로필', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/me$/);
  await games.getByRole('button', { name: '배틀그라운드 매칭', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/home(?:\?|$)/);
  await expect(roleFilter.locator('option[value="AGGRESSIVE"]')).toHaveText('공격적');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '메뉴 열기', exact: true }).click();
  const menu = page.getByRole('dialog', { name: '메뉴', exact: true });
  const mobileGames = menu.getByRole('group', { name: '게임 선택', exact: true });
  await expect(mobileGames).toBeVisible();
  const mobileGamesBox = (await mobileGames.boundingBox())!;
  const mobileNavBox = (await menu.getByRole('navigation', { name: '주 메뉴', exact: true }).boundingBox())!;
  expect(mobileGamesBox.y + mobileGamesBox.height).toBeLessThanOrEqual(mobileNavBox.y);
  await mobileGames.getByRole('button', { name: '리그 오브 레전드 매칭', exact: true }).click();
  await expect(menu).toHaveCount(0);
  await expect(roleFilter.locator('option[value="TOP"]')).toHaveText('탑');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
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
