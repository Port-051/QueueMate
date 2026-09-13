import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('작은 화면에서도 주요 페이지와 매칭 팝업이 잘리지 않고 두 방식으로 전환할 수 있다', async ({ page }) => {
  await login(page);
  for (const width of [390, 768, 1024, 1280]) {
    await page.setViewportSize({ width, height: 844 });
    for (const route of ['home', 'me', 'messages']) {
      if (width < 768) {
        await page.getByRole('button', { name: '메뉴 열기' }).click();
        await page.getByRole('dialog', { name: '메뉴', exact: true }).locator(`a[href="/app/${route}"]`).click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
      } else await page.locator(`.sidebar a[href="/app/${route}"]`).click();
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
      await expect(page.getByRole('tab', { name: '실시간 매치', exact: true })).toBeVisible();
      await expect(page.getByRole('tab', { name: '예약 매치', exact: true })).toBeVisible();
      for (const mode of ['실시간', '예약']) {
        await page.getByRole('tab', { name: `${mode} 매치`, exact: true }).click();
        await page.locator('.intro-launch > button').click();
        await expect(page.getByRole('dialog').getByRole('radio', { name: '수동 매칭', exact: true })).toBeChecked();
        await page.getByRole('dialog').getByRole('radio', { name: '자동 매칭', exact: true }).check();
        await expect(page.getByRole('dialog').getByRole('radio', { name: '자동 매칭', exact: true })).toBeChecked();
        const button = page.getByRole('button', { name: '모집 시작', exact: true });
        await button.scrollIntoViewIfNeeded();
        const box = (await button.boundingBox())!;
        expect(box.x).toBeGreaterThanOrEqual(0);
        expect(box.x + box.width).toBeLessThanOrEqual(width);
        expect(box.y + box.height).toBeLessThanOrEqual(844);
        expect(await page.getByRole('dialog').evaluate(element => element.scrollWidth > element.clientWidth)).toBe(false);
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
  await page.getByRole('dialog').getByRole('link', { name: 'QueueMaster 프로필', exact: true }).click();
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
  await expect(page.locator('.sidebar .nav-profile')).toHaveAttribute('aria-label', '새로운닉네임 프로필');
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
