import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('홈의 내 정보는 큰 화면 오른쪽과 작은 화면 상단에 배치하고 매칭 글자를 읽을 수 있게 유지한다', async ({ page }, testInfo) => {
  await login(page);
  const home = page.getByRole('region', { name: '듀오 찾기', exact: true });
  const profile = home.getByRole('complementary', { name: '내 정보', exact: true });
  const feed = home.locator('.board-feed');
  await expect(profile).toContainText('QueueMaster');
  await expect(feed.locator('.recruitment-row').first()).toBeVisible();

  for (const width of [1600, 1100, 1099, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(profile).toBeVisible();
    const profileBox = (await profile.boundingBox())!;
    const feedBox = (await feed.boundingBox())!;
    if (width >= 1100) {
      expect(profileBox.x, `내 정보는 매칭 글 목록 오른쪽 ${width}px`).toBeGreaterThanOrEqual(feedBox.x + feedBox.width);
    } else {
      expect(profileBox.y + profileBox.height, `내 정보는 매칭 글 목록 위 ${width}px`).toBeLessThanOrEqual(feedBox.y);
    }
    const homeBox = (await home.boundingBox())!;
    const mainBox = (await page.locator('main.main').boundingBox())!;
    expect(homeBox.width).toBeLessThanOrEqual(1320);
    expect(Math.abs((homeBox.x - mainBox.x) - (mainBox.x + mainBox.width - homeBox.x - homeBox.width)), `홈 좌우 여백 ${width}px`).toBeLessThanOrEqual(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `홈 가로 넘침 ${width}px`).toBe(true);
    for (const [selector, minimum] of [
      ['.row-player b', 16],
      ['.row-player p', 14],
      ['.row-introduction-stats', 14],
      ['.board-filter-line button', 14],
      ['.board-tabs button', 16],
    ] as const) {
      const fontSize = await feed.locator(selector).first().evaluate(element => parseFloat(getComputedStyle(element).fontSize));
      expect(fontSize, `${selector} 글자 크기 ${width}px`).toBeGreaterThanOrEqual(minimum);
    }
    if (width === 1600 || width === 390) await page.screenshot({ path: testInfo.outputPath(`home-profile-${width}.png`) });
  }
});

test('모바일 필터는 줄바꿈되고 선택 팝업과 예약 입력은 잘리지 않는다', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  const filters = page.locator('.board-filter-bar');
  const line = filters.locator('.board-filter-line');
  const tier = filters.getByRole('button', { name: '찾는 상대 티어', exact: true });
  const voice = filters.getByRole('button', { name: '마이크', exact: true });
  const roles = filters.getByRole('group', { name: '포지션', exact: true });
  const modes = filters.getByRole('group', { name: '찾는 큐 타입', exact: true });
  await expect(roles.getByRole('button')).toHaveCount(5);
  for (const name of ['탑', '정글', '미드', '바텀', '서포터']) {
    await expect(roles.getByRole('button', { name, exact: true })).toHaveAttribute('aria-pressed', 'false');
  }
  const layout = await line.evaluate(element => ({
    width: element.clientWidth,
    content: element.scrollWidth,
    centers: [...element.querySelectorAll('button')].map(button => {
      const box = button.getBoundingClientRect();
      return box.y + box.height / 2;
    }),
  }));
  expect(layout.content).toBeLessThanOrEqual(layout.width);
  for (const control of [voice, tier, roles, modes]) {
    const box = (await control.boundingBox())!;
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(390);
  }
  await voice.click();
  await page.getByRole('listbox', { name: '마이크', exact: true }).getByRole('option', { name: '사용', exact: true }).click();
  await expect(page.locator('.board-results-head')).toContainText('7명이 매칭 중이에요');

  await tier.scrollIntoViewIfNeeded();
  await tier.click();
  const tiers = page.getByRole('listbox', { name: '찾는 상대 티어', exact: true });
  await expect(tiers).toBeVisible();
  const popupBox = (await tiers.boundingBox())!;
  const lineBox = (await line.boundingBox())!;
  expect(popupBox.x).toBeGreaterThanOrEqual(0);
  expect(popupBox.x + popupBox.width).toBeLessThanOrEqual(390);
  expect(popupBox.y).toBeGreaterThanOrEqual(0);
  expect(popupBox.y + popupBox.height).toBeLessThanOrEqual(844);
  expect(popupBox.height).toBeGreaterThan(lineBox.height);
  const challenger = tiers.getByRole('option', { name: '챌린저', exact: true });
  await challenger.scrollIntoViewIfNeeded();
  await expect(challenger).toBeInViewport();
  expect(await challenger.evaluate(element => {
    const box = element.getBoundingClientRect();
    return element.contains(document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2));
  }), '팝업의 마지막 항목은 필터 스크롤 영역에 잘리지 않는다').toBe(true);
  await page.screenshot({ path: testInfo.outputPath('mobile-board-tier-popup.png') });
  await challenger.click();
  await expect(tier).toContainText('챌린저');
  await filters.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(voice).toContainText('무관');
  await modes.getByRole('button', { name: '일반', exact: true }).click();
  await expect(modes.getByRole('button', { name: '일반', exact: true })).toHaveAttribute('aria-pressed', 'true');

  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  const start = filters.getByLabel('검색 시작 시각', { exact: true });
  const end = filters.getByLabel('검색 종료 시각', { exact: true });
  const amount = filters.getByRole('combobox', { name: '검색 플레이 양', exact: true });
  await expect(start).toBeVisible();
  await expect(end).toBeVisible();
  await expect(amount).toBeVisible();
  await expect(line.getByLabel('검색 시작 시각', { exact: true })).toHaveCount(0);
  const reservationLineBox = (await line.boundingBox())!;
  for (const control of [start, end, amount]) {
    const box = (await control.boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(reservationLineBox.y + reservationLineBox.height);
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(390);
  }
  await amount.selectOption('TWO_PLUS');
  await expect(amount).toHaveValue('TWO_PLUS');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('작은 화면에서도 주요 페이지와 오른쪽 매칭 폼이 잘리지 않고 두 방식으로 전환할 수 있다', async ({ page }) => {
  await login(page);
  for (const width of [390, 768, 1024, 1280]) {
    await page.setViewportSize({ width, height: 844 });
    for (const route of ['home', 'me', 'messages']) {
      if (width < 768) {
        await page.getByRole('button', { name: '메뉴 열기' }).click();
        await page.getByRole('dialog', { name: '메뉴', exact: true }).locator(`a[href="/app/${route}"]`).click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
      } else await page.locator(`.side-nav a[href="/app/${route}"]`).click();
      await expect(page).toHaveURL(new RegExp(`/app/${route}$`));
      await expect(page.getByRole('region', { name: route === 'home' ? '듀오 찾기' : route === 'me' ? '프로필' : '메시지', exact: true })).toBeVisible();
      const dimensions = await page.evaluate(() => ({ content: document.documentElement.scrollWidth, viewport: innerWidth }));
      expect(dimensions.content, `${route} at ${width}px`).toBeLessThanOrEqual(dimensions.viewport);
      if (route === 'messages') {
        await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
        await expect(page.getByRole('textbox', { name: 'SilentJungle에게 메시지', exact: true }), `대화 입력란 ${width}px`).toBeInViewport();
        await expect(page.getByRole('button', { name: '메시지 보내기', exact: true })).toBeInViewport();
        const opened = await page.evaluate(() => ({ content: document.documentElement.scrollWidth, viewport: innerWidth }));
        expect(opened.content, `conversation at ${width}px`).toBeLessThanOrEqual(opened.viewport);
        if (width < 761) {
          await page.getByRole('button', { name: '대화 목록으로', exact: true }).click();
          await expect(page.getByRole('searchbox', { name: '대화 검색', exact: true })).toBeVisible();
        }
        continue;
      }
      if (route !== 'home') continue;
      await expect(page.getByRole('tab', { name: '실시간 매칭', exact: true })).toBeVisible();
      await expect(page.getByRole('tab', { name: '예약 매칭', exact: true })).toBeVisible();
      for (const mode of ['실시간', '예약']) {
        await page.getByRole('tab', { name: `${mode} 매칭`, exact: true }).click();
        await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
        await expect(page.locator('.recruitment-composer-shell').getByRole('radiogroup', { name: '매칭 방식' })).toHaveCount(0);
        const button = page.getByRole('button', { name: '매칭 시작', exact: true });
        await button.scrollIntoViewIfNeeded();
        const box = (await button.boundingBox())!;
        expect(box.x).toBeGreaterThanOrEqual(0);
        expect(box.x + box.width).toBeLessThanOrEqual(width);
        expect(box.y + box.height).toBeLessThanOrEqual(844);
        expect(await page.locator('.recruitment-composer-shell').evaluate(element => element.scrollWidth > element.clientWidth)).toBe(false);
        await page.keyboard.press('Escape');
      }
    }
  }
});

test('모바일 메뉴로 이동하고 닉네임 변경 상태와 연결 해제 대상을 확인한다', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await page.getByRole('button', { name: '메뉴 열기' }).click();
  await expect(page.getByRole('dialog', { name: '메뉴', exact: true })).toBeVisible();
  const mobileProfile = page.getByRole('dialog').getByRole('link', { name: '프로필', exact: true });
  await expect(mobileProfile.locator('.avatar-status')).toHaveCount(0);
  await expect(mobileProfile.locator('small')).toHaveCount(0);
  await mobileProfile.click();
  await expect(page).toHaveURL(/\/app\/me$/);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('textbox', { name: '닉네임', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '닉네임 변경', exact: true }).click();
  const save = page.getByRole('button', { name: '변경 사항 저장', exact: true });
  await expect(save).toBeDisabled();
  const nickname = page.getByRole('textbox', { name: '닉네임', exact: true });
  await nickname.fill(' ');
  await expect(save).toBeDisabled();
  await expect(nickname).toHaveAttribute('aria-invalid', 'true');
  await nickname.fill('새로운닉네임');
  await expect(save).toBeEnabled();
  await save.click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '새로운닉네임', exact: true })).toBeVisible();
  await expect(page.locator('.sidebar .nav-profile')).toHaveAttribute('aria-label', '프로필');
  await expect(page.locator('.toast.ok')).toContainText('닉네임을 변경했습니다');
  await page.getByRole('button', { name: '발로란트 ID 등록', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '발로란트 ID 등록', exact: true })).toBeVisible();
  await page.getByRole('textbox', { name: '게임 ID', exact: true }).fill('NewGame#KR1');
  await page.getByRole('button', { name: 'ID 등록', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.account-row').filter({ hasText: '발로란트' })).toContainText('NewGame#KR1');
  await expect(page.getByRole('button', { name: '발로란트 ID 등록', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '배틀그라운드 ID 등록', exact: true })).toBeVisible();
  const unlink = page.getByRole('button', { name: '리그 오브 레전드 연결 해제', exact: true });
  await unlink.click();
  await expect(page.getByRole('dialog')).toContainText('이 게임의 ID가 파티원에게 표시되지 않습니다.');
  await page.getByRole('button', { name: '돌아가기', exact: true }).click();
  await expect(unlink).toBeVisible();
  await page.getByRole('link', { name: /차단 목록/ }).click();
  await expect(page).toHaveURL(/\/app\/messages\?manage=blocks$/);
  const management = page.getByRole('region', { name: '친구 관리', exact: true });
  await expect(management.getByRole('tab', { name: /차단 목록/ })).toHaveAttribute('aria-selected', 'true');
  await management.getByRole('tab', { name: /받은 요청/ }).click();
  await expect(management.getByRole('tab', { name: /받은 요청/ })).toHaveAttribute('aria-selected', 'true');
  await management.getByRole('tab', { name: /^친구/ }).click();
  await expect(management.locator('.dm-friend-row').first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('messages-friends-390.png') });
  await management.getByRole('button', { name: '친구 관리 닫기', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/messages$/);
  await page.goBack();
  await expect(page).toHaveURL(/\/app\/me$/);
  await expect(page.getByRole('heading', { name: '새로운닉네임', exact: true })).toBeVisible();
});
