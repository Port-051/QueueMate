import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('사이드바 알림은 현재 화면 위에 열리고 Escape와 바깥 클릭으로 닫힌다', async ({ page }) => {
  await login(page);
  const sidebar = page.locator('.sidebar');
  await expect(sidebar.getByRole('link', { name: '메시지', exact: true })).toBeVisible();
  await expect(sidebar.getByRole('link', { name: '파티룸', exact: true })).toHaveCount(0);
  await expect(sidebar.getByRole('link', { name: '친구', exact: true })).toHaveCount(0);
  const trigger = sidebar.getByRole('button', { name: '알림', exact: true });
  await expect(trigger.locator('.nav-badge')).toBeVisible();
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const bounds = (await page.locator('.board-home').boundingBox())!;
  await trigger.click();
  const panel = page.getByRole('dialog', { name: '알림', exact: true });
  await expect(panel).toBeVisible();
  await expect(panel).toContainText('HealingYou님의 친구 요청');
  await expect(page).toHaveURL(/\/app\/home$/);
  const openedBounds = (await page.locator('.board-home').boundingBox())!;
  expect({ x: openedBounds.x, width: openedBounds.width }).toEqual({ x: bounds.x, width: bounds.width });
  await page.keyboard.press('Escape');
  await expect(panel).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await trigger.click();
  await page.mouse.click(bounds.x + bounds.width - 8, bounds.y + 8);
  await expect(panel).toHaveCount(0);
});

test('모두 읽음은 새로고침 뒤에도 유지되고 친구 알림은 해당 대화로 연결된다', async ({ page }) => {
  await login(page);
  const trigger = page.locator('.sidebar').getByRole('button', { name: '알림', exact: true });
  await trigger.click();
  const panel = page.getByRole('dialog', { name: '알림', exact: true });
  await panel.getByRole('button', { name: '모두 읽음', exact: true }).click();
  await expect(trigger.locator('.nav-badge')).toHaveCount(0);
  await panel.getByRole('button', { name: '안 읽음', exact: true }).click();
  await expect(panel).toContainText('모든 알림을 확인했어요');
  await page.keyboard.press('Escape');
  await login(page);
  await expect(trigger.locator('.nav-badge')).toHaveCount(0);
  await trigger.click();
  await panel.getByRole('button', { name: /HealingYou님의 친구 요청/ }).click();
  await expect(panel).toHaveCount(0);
  await expect(page).toHaveURL(/\/app\/messages\?user=u-healingyou$/);
});

test('새 매칭 제안은 반복 갱신돼도 알림을 중복으로 만들지 않는다', async ({ page }) => {
  await page.clock.install();
  await login(page);
  await startRealtimeMatch(page, true);
  await page.clock.fastForward(7000);
  await expect(page.locator('.board-proposal')).toBeVisible();
  await page.locator('.sidebar').getByRole('button', { name: '알림', exact: true }).click();
  const panel = page.getByRole('dialog', { name: '알림', exact: true });
  await expect(panel.getByText('함께할 팀원을 찾았어요', { exact: true })).toHaveCount(1);
  await page.clock.fastForward(6000);
  await expect(panel.getByText('함께할 팀원을 찾았어요', { exact: true })).toHaveCount(1);
});

test('메시지 알림은 다른 계정과 본인 발신을 제외하고 같은 이벤트를 한 번만 표시한다', async ({ page }) => {
  await login(page);
  await page.evaluate(() => {
    const detail = { ownerId: 'u-me', conversationId: 'u-gankflow', senderId: 'u-gankflow', senderName: 'GankFlow', text: '알림에서 확인하는 새 메시지', createdAt: new Date().toISOString() };
    window.dispatchEvent(new CustomEvent('qm:direct-message', { detail }));
    window.dispatchEvent(new CustomEvent('qm:direct-message', { detail }));
    window.dispatchEvent(new CustomEvent('qm:direct-message', { detail: { ...detail, senderId: 'u-me', text: '내가 보낸 메시지' } }));
    window.dispatchEvent(new CustomEvent('qm:direct-message', { detail: { ...detail, ownerId: 'another-account', text: '다른 계정의 메시지' } }));
  });
  await page.locator('.sidebar').getByRole('button', { name: '알림', exact: true }).click();
  const panel = page.getByRole('dialog', { name: '알림', exact: true });
  await expect(panel.getByText('알림에서 확인하는 새 메시지', { exact: true })).toHaveCount(1);
  await expect(panel).not.toContainText('내가 보낸 메시지');
  await expect(panel).not.toContainText('다른 계정의 메시지');
});

test('모바일에서는 메뉴에서 알림을 열고 원래 메뉴 버튼으로 돌아온다', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 640 });
  await login(page);
  const menu = page.getByRole('button', { name: '메뉴 열기', exact: true });
  await menu.click();
  await page.getByRole('dialog', { name: '메뉴', exact: true }).getByRole('button', { name: '알림', exact: true }).click();
  const panel = page.getByRole('dialog', { name: '알림', exact: true });
  await expect(page.getByRole('dialog')).toHaveCount(1);
  await expect(panel).toBeVisible();
  const box = (await panel.boundingBox())!;
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(360);
  expect(box.y + box.height).toBeLessThanOrEqual(640);
  await panel.getByRole('button', { name: '알림 닫기', exact: true }).click();
  await expect(panel).toHaveCount(0);
  await expect(menu).toBeFocused();
});
