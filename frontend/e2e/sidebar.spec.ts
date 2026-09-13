import { expect, test } from '@playwright/test';
import { DEMO, login, selectBoardFilter } from './helpers';

test('사이드바의 위쪽·중간·아래쪽 빈 영역에서도 펼쳐지고 탐색 후 다시 호버할 수 있다', async ({ page }, testInfo) => {
  await login(page);
  const sidebar = page.locator('.sidebar');
  const main = page.locator('.main');
  const feed = page.locator('.board-feed');
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const homeBounds = (await main.boundingBox())!;
  const feedBounds = await feed.boundingBox();
  const outside = { x: homeBounds.x + homeBounds.width / 2, y: 100 };
  await page.mouse.move(outside.x, outside.y);
  await expect(sidebar).toHaveCSS('width', '80px');

  const collapsedBounds = (await sidebar.boundingBox())!;
  const brandBounds = (await sidebar.locator('.sidebar-brand-link').boundingBox())!;
  const gamesBounds = (await sidebar.getByRole('group', { name: '게임 선택', exact: true }).boundingBox())!;
  const navBounds = (await sidebar.getByRole('navigation', { name: '주 메뉴', exact: true }).boundingBox())!;
  const emptyRegions = [
    { name: '위쪽 패딩', start: collapsedBounds.y, end: brandBounds.y },
    { name: '게임과 메뉴 사이', start: gamesBounds.y + gamesBounds.height, end: navBounds.y },
    { name: '메뉴 아래', start: navBounds.y + navBounds.height, end: collapsedBounds.y + collapsedBounds.height },
  ];
  const expectEmptyPoint = async (point: { x: number; y: number }) => {
    const controls = await sidebar.locator('.sidebar-navigation a, .sidebar-navigation button').evaluateAll(elements =>
      elements.map(element => {
        const { x, y, width, height } = element.getBoundingClientRect();
        return { x, y, width, height };
      }));
    expect(controls.some(box => point.x >= box.x && point.x <= box.x + box.width
      && point.y >= box.y && point.y <= box.y + box.height)).toBe(false);
  };

  for (const region of emptyRegions) {
    await test.step(region.name, async () => {
      expect(region.end - region.start).toBeGreaterThan(0);
      const point = { x: collapsedBounds.x + collapsedBounds.width / 2, y: (region.start + region.end) / 2 };
      await expectEmptyPoint(point);
      await page.mouse.move(point.x, point.y);
      await expect(sidebar).toHaveCSS('width', '232px');
      await expect(sidebar.locator('.brand-wordmark')).toBeVisible();
      expect(await main.boundingBox()).toEqual(homeBounds);
      expect(await feed.boundingBox()).toEqual(feedBounds);

      const expandedBounds = (await sidebar.boundingBox())!;
      const expandedPoint = { x: expandedBounds.x + expandedBounds.width / 2, y: point.y };
      expect(expandedPoint.x).toBeGreaterThan(collapsedBounds.x + collapsedBounds.width);
      await expectEmptyPoint(expandedPoint);
      await page.mouse.move(expandedPoint.x, expandedPoint.y);
      await expect(sidebar).toHaveCSS('width', '232px');
      expect(await page.evaluate(({ x, y }) => Boolean(document.elementFromPoint(x, y)?.closest('.sidebar')), expandedPoint)).toBe(true);
      if (region.name === '게임과 메뉴 사이') {
        await page.screenshot({ path: testInfo.outputPath('sidebar-empty-hover.png') });
      }

      await page.mouse.move(outside.x, outside.y);
      await expect(sidebar).toHaveCSS('width', '80px');
      await expect(sidebar.locator('.brand-wordmark')).toBeHidden();
      expect(await main.boundingBox()).toEqual(homeBounds);
      expect(await feed.boundingBox()).toEqual(feedBounds);
    });
  }

  await sidebar.getByRole('link', { name: '프로필', exact: true }).click({ position: { x: 24, y: 24 } });
  await expect(page).toHaveURL(/\/app\/me$/);
  await expect(sidebar).toHaveCSS('width', '80px');
  await page.mouse.move(outside.x, outside.y);
  const profileBounds = await main.boundingBox();
  const bottom = emptyRegions[2];
  const reentry = { x: collapsedBounds.x + collapsedBounds.width / 2, y: (bottom.start + bottom.end) / 2 };
  await expectEmptyPoint(reentry);
  await page.mouse.move(reentry.x, reentry.y);
  await expect(sidebar).toHaveCSS('width', '232px');
  expect(await main.boundingBox()).toEqual(profileBounds);
  await page.mouse.move(outside.x, outside.y);
  await expect(sidebar).toHaveCSS('width', '80px');
  expect(await main.boundingBox()).toEqual(profileBounds);
});

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

  const filters = page.locator('.board-filter-bar');
  const roleFilter = filters.getByRole('group', { name: '찾는 상대 포지션', exact: true });
  const modeFilter = filters.getByRole('group', { name: '찾는 큐 타입', exact: true });
  const tierFilter = filters.getByRole('button', { name: '찾는 상대 티어', exact: true });
  await roleFilter.getByRole('button', { name: '서포터', exact: true }).click();
  await modeFilter.getByRole('button', { name: '칼바람', exact: true }).click();
  await selectBoardFilter(page, '찾는 상대 티어', '에메랄드');

  await valorant.click();
  await expect(valorant).toHaveAttribute('aria-pressed', 'true');
  await expect(lol).toHaveAttribute('aria-pressed', 'false');
  await expect(valorant.locator('.game-nav-logo')).toHaveCSS('outline-width', '2px');
  await expect(valorant.locator('.game-nav-logo')).toHaveCSS('outline-color', 'rgb(124, 77, 255)');
  await expect(roleFilter.getByRole('button', { name: '타격대', exact: true })).toBeVisible();
  await expect(roleFilter.getByRole('button', { name: '탑', exact: true })).toHaveCount(0);
  await expect(roleFilter.getByRole('button')).toHaveCount(4);
  await expect(roleFilter.getByRole('button', { pressed: true })).toHaveCount(0);
  await expect(modeFilter.getByRole('button')).toHaveText(['전체', '경쟁전', '일반전']);
  await expect(modeFilter.getByRole('button', { name: '전체', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(tierFilter).toContainText('모든 티어');
  await tierFilter.click();
  const tierOptions = page.getByRole('listbox', { name: '찾는 상대 티어', exact: true });
  await expect(tierOptions.getByRole('option', { name: '레디언트', exact: true })).toBeVisible();
  await expect(tierOptions.getByRole('option', { name: '에메랄드', exact: true })).toHaveCount(0);
  await page.keyboard.press('Escape');
  await roleFilter.getByRole('button', { name: '전략가', exact: true }).click();
  await modeFilter.getByRole('button', { name: '경쟁전', exact: true }).click();

  await sidebar.getByRole('link', { name: '프로필', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/me$/);
  await games.getByRole('button', { name: '배틀그라운드 매칭', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/home(?:\?|$)/);
  await expect(roleFilter.getByRole('button', { name: '공격적', exact: true })).toBeVisible();
  await expect(roleFilter.getByRole('button')).toHaveCount(3);
  await expect(roleFilter.getByRole('button', { pressed: true })).toHaveCount(0);
  await expect(modeFilter.getByRole('button')).toHaveText(['전체', '듀오', '스쿼드']);
  await expect(modeFilter.getByRole('button', { name: '전체', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await tierFilter.click();
  await expect(tierOptions.getByRole('option', { name: '마스터', exact: true })).toBeVisible();
  await expect(tierOptions.getByRole('option', { name: '레디언트', exact: true })).toHaveCount(0);
  await page.keyboard.press('Escape');

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
  await expect(roleFilter.getByRole('button', { name: '탑', exact: true })).toBeVisible();
  await expect(roleFilter.getByRole('button')).toHaveCount(5);
  await expect(modeFilter.getByRole('button')).toHaveCount(5);
  for (const [index, name] of ['전체', '랭크', '일반', '신속', '칼바람'].entries()) {
    await expect(modeFilter.getByRole('button').nth(index)).toHaveAccessibleName(name);
  }
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
