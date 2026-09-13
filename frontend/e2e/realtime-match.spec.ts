import { expect, test } from '@playwright/test';
import { manageRecruitment, login, startRealtimeMatch } from './helpers';

test('자동 찾기는 오케이 후 상호 수락하면 메시지로 연결한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page, true);
  await page.clock.fastForward(7000);
  await page.locator('.duo-offer').getByRole('button', { name: '같이 할래요' }).click();
  await expect(page.locator('.my-recruitment')).toContainText('모집 중');
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await page.clock.fastForward(22000);
  await page.getByRole('button', { name: '메시지로 이동' }).click();
  await expect(page).toHaveURL(/\/app\/messages\?user=/);
  await expect(page.getByRole('note')).toContainText('매칭 성사');
});
test('활성 실시간 모집은 하나만 가능하지만 예약 모집은 별도로 유지한다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await expect(page.locator('.intro-launch > button')).toHaveCount(0);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await expect(page.getByRole('button', { name: '예약하기' })).toBeEnabled();
  await expect(page.locator('.my-recruitment')).toBeVisible();
});
test('모집을 종료한 뒤 새로 시작해도 티어·상대 조건을 유지한다', async ({ page }) => {
  await login(page);
  await page.locator('.intro-launch > button').click();
  await page.locator('.recruitment-composer-shell').getByLabel('내 티어', { exact: true }).selectOption('GOLD');
  await page.locator('.recruitment-composer-shell').getByRole('button', { name: '정글', exact: true }).click();
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  await manageRecruitment(page, '모집 종료');
  await expect(page.locator('.my-recruitment')).toHaveCount(0);
  await page.locator('.intro-launch > button').click();
  await expect(page.locator('.recruitment-composer-shell').getByLabel('내 티어', { exact: true })).toHaveValue('GOLD');
  await expect(page.locator('.recruitment-composer-shell').getByRole('button', { name: '정글', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
});
test('매칭 후 작성한 메시지는 다른 메뉴를 다녀와도 유지된다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.getByRole('button', { name: 'PlayMaker 모집 상세' }).click();
  await page.getByRole('button', { name: '같이 할래요' }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await page.clock.fastForward(22000);
  await page.getByRole('button', { name: '메시지로 이동' }).click();
  const input = page.getByRole('textbox', { name: 'PlayMaker에게 메시지', exact: true });
  await input.fill('이동해도 대화를 유지해요'); await input.press('Enter');
  await expect(page.locator('.dm-message-entry')).toContainText('이동해도 대화를 유지해요');
  await page.locator('.side-nav a[href="/app/home"]').click();
  await page.getByRole('button', { name: '메시지로 이동' }).click();
  await expect(page.locator('.dm-message-entry')).toContainText('이동해도 대화를 유지해요');
});
