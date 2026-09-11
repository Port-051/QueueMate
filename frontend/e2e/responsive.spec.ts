import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('작은 화면에서도 주요 페이지와 매칭 팝업이 잘리지 않고 두 방식으로 전환할 수 있다', async ({ page }) => {
  await login(page);
  for (const width of [390, 768, 1024, 1280]) {
    await page.setViewportSize({ width, height: 844 });
    for (const route of ['home', 'me', 'friends', 'settings', 'recent']) {
      if (width < 768) {
        await page.getByRole('button', { name: '메뉴 열기' }).click();
        await page.getByRole('dialog', { name: '메뉴', exact: true }).locator(`a[href="/app/${route}"]`).click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
      } else await page.locator(`.sidebar a[href="/app/${route}"]`).click();
      await expect(page).toHaveURL(new RegExp(`/app/${route}$`));
      await expect(page.locator('h1')).toBeVisible();
      const dimensions = await page.evaluate(() => ({ content: document.documentElement.scrollWidth, viewport: innerWidth }));
      expect(dimensions.content, `${route} at ${width}px`).toBeLessThanOrEqual(dimensions.viewport);
      if (route !== 'home') continue;
      const games = (await page.locator('.home-games').boundingBox())!;
      const current = (await page.locator('.home-active-match').boundingBox())!;
      const reservations = (await page.locator('.home-reservations').boundingBox())!;
      const history = (await page.locator('.home-history').boundingBox())!;
      expect(games.y + games.height).toBeLessThan(current.y);
      expect(current.y + current.height).toBeLessThan(reservations.y);
      if (width >= 1024) {
        expect(current.y).toBe(history.y);
        expect(current.x + current.width).toBeLessThan(history.x);
      } else expect(history.y).toBeGreaterThan(reservations.y + reservations.height);
      if (width >= 768) {
        const navigation = (await page.locator('.side-nav').boundingBox())!;
        expect(Math.abs(navigation.y + navigation.height / 2 - 844 / 2)).toBeLessThan(5);
      }
      await page.getByRole('button', { name: 'League of Legends 매칭', exact: true }).click();
      for (const mode of ['바로 매칭', '예약 매칭']) {
        await page.getByRole('tab', { name: mode, exact: true }).click();
        const button = page.getByRole('button', { name: mode === '바로 매칭' ? '매칭 시작' : '예약 등록', exact: true });
        await button.scrollIntoViewIfNeeded();
        const box = (await button.boundingBox())!;
        expect(box.x).toBeGreaterThanOrEqual(0);
        expect(box.x + box.width).toBeLessThanOrEqual(width);
        expect(box.y + box.height).toBeLessThanOrEqual(844);
        const overflow = await page.getByRole('dialog').evaluate((element) => element.scrollWidth > element.clientWidth);
        expect(overflow).toBe(false);
      }
      await page.keyboard.press('Escape');
    }
  }
});

test('모바일 메뉴로 이동하고 닉네임 변경 상태와 연결 해제 대상을 확인한다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await page.getByRole('button', { name: '메뉴 열기' }).click();
  await expect(page.getByRole('dialog', { name: '메뉴', exact: true })).toBeVisible();
  await page.getByRole('dialog').getByRole('link', { name: '내 정보', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/me$/);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const save = page.getByRole('button', { name: '변경 사항 저장', exact: true });
  await expect(save).toBeDisabled();
  const nickname = page.getByRole('textbox', { name: '닉네임', exact: true });
  await nickname.fill(' ');
  await expect(save).toBeDisabled();
  await expect(nickname).toHaveAttribute('aria-invalid', 'true');
  await nickname.fill('새로운닉네임');
  await expect(save).toBeEnabled();
  await save.click();
  await expect(save).toBeDisabled();
  await expect(page.locator('.toast.ok')).toContainText('닉네임을 변경했습니다');
  await expect(page.getByRole('combobox', { name: '등록할 게임' })).toHaveValue('VALORANT');
  await page.getByRole('textbox', { name: '게임 ID', exact: true }).fill('NewGame#KR1');
  await page.getByRole('button', { name: 'ID 등록', exact: true }).click();
  await expect(page.locator('.account-row').filter({ hasText: 'VALORANT' })).toContainText('NewGame#KR1');
  await expect(page.getByRole('combobox', { name: '등록할 게임' })).toHaveValue('PUBG');
  const unlink = page.getByRole('button', { name: 'League of Legends 연결 해제', exact: true });
  await unlink.click();
  await expect(page.getByRole('dialog')).toContainText('이 게임의 ID가 파티원에게 표시되지 않습니다.');
  await page.getByRole('button', { name: '돌아가기', exact: true }).click();
  await expect(unlink).toBeVisible();
  await page.getByRole('link', { name: /차단 목록/ }).click();
  await expect(page).toHaveURL(/tab=blocks/);
  await expect(page.getByRole('button', { name: /차단 목록/ })).toHaveClass('on');
  await page.getByRole('button', { name: /친구 목록/ }).click();
  await page.goBack();
  await expect(page.getByRole('button', { name: /차단 목록/ })).toHaveClass('on');
});
