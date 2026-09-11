import { expect, test } from '@playwright/test';
import { login } from './helpers';

test.use({ timezoneId: 'Asia/Seoul' });

async function openNewReservation(page: import('@playwright/test').Page) {
  await page.getByRole('button', { name: 'League of Legends 매칭', exact: true }).click();
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await expect(page.getByRole('button', { name: '예약 등록', exact: true })).toBeVisible();
}

test('예약 등록 후 목록에 진행 중으로 남는다', async ({ page }) => {
  await login(page);
  await openNewReservation(page);

  await page.getByRole('button', { name: '2판 이상' }).click();
  await page.getByRole('button', { name: '예약 등록' }).click();

  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.getByText('2판 이상')).toBeVisible();
  await expect(page.getByText('대기 중')).toBeVisible();
});

test('시간이 겹치는 예약은 등록할 수 없다 (INV-9)', async ({ page }) => {
  await login(page);
  await openNewReservation(page);
  await page.getByRole('button', { name: '예약 등록' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page).toHaveURL(/\/app\/home$/);

  await openNewReservation(page);
  await page.getByRole('button', { name: '예약 등록' }).click();

  await expect(page.getByRole('alert')).toContainText('시간이 겹치는 예약');
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page).toHaveURL(/\/app\/home$/);
});

test('예약을 취소하면 지난 매칭에 결과가 남는다', async ({ page }) => {
  await login(page);
  await openNewReservation(page);
  await page.getByRole('button', { name: '예약 등록' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page).toHaveURL(/\/app\/home$/);

  await page.getByRole('button', { name: '취소', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('이 시간대의 팀원을 더 이상 찾지 않습니다.');
  await page.getByRole('button', { name: '돌아가기', exact: true }).click();
  await expect(page.locator('.reservation-row')).toBeVisible();
  await page.getByRole('button', { name: '취소', exact: true }).click();
  await page.getByRole('button', { name: '예약 취소', exact: true }).click();
  await expect(page.getByText('예정된 예약이 없습니다')).toBeVisible();

  const history = page.getByRole('region', { name: '지난 매칭', exact: true });
  await expect(history).toContainText('예약 매칭');
  await expect(history).toContainText('취소됨');
  await expect(history.locator('.match-history-row')).toHaveCount(1);

  await openNewReservation(page);
  await page.getByRole('button', { name: '예약 등록', exact: true }).click();
  const cancel = page.getByRole('button', { name: '취소', exact: true });
  await cancel.click();
  await expect(page.getByRole('dialog', { name: '예약을 취소할까요?' })).toBeVisible();
  // 스크롤 고정 카드에서 열린 팝업이 과거 기록의 게임 아이콘까지 덮어야 한다.
  const covered = await history.locator('.game-logo').first().evaluate((icon) => {
    const rect = icon.getBoundingClientRect();
    const topElement = document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2);
    return Boolean(topElement?.closest('.modal-scrim'));
  });
  expect(covered).toBe(true);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(cancel).toBeFocused();
});

test('종료 시간이 시작보다 빠르면 등록 버튼이 잠긴다', async ({ page }) => {
  await login(page);
  await openNewReservation(page);

  await page.getByRole('button', { name: /내일/ }).click();
  await page.getByLabel('시작 시간').selectOption('22:00');
  await page.getByLabel('종료 날짜').selectOption('same');
  await page.getByLabel('종료 시간').selectOption('20:00');

  await expect(page.getByText('종료 시간이 시작 시간보다 늦어야 합니다')).toBeVisible();
  await expect(page.getByRole('button', { name: '예약 등록' })).toBeDisabled();
});


test('자정을 넘는 예약은 다음 날 종료로 저장되고 수정해도 유지된다', async ({ page }) => {
  await login(page);
  await openNewReservation(page);
  await page.getByRole('button', { name: /내일/ }).click();
  await page.getByLabel('시작 시간').selectOption('23:30');
  await page.getByLabel('종료 날짜').selectOption('next');
  await page.getByLabel('종료 시간').selectOption('00:30');
  await page.getByRole('button', { name: '예약 등록' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.locator('.reservation-row')).toContainText(/23:30 ~ .+ 00:30/);
  await page.getByRole('button', { name: '수정', exact: true }).click();
  await expect(page.getByLabel('종료 날짜')).toHaveValue('next');
  await expect(page.getByLabel('시작 시간')).toHaveValue('23:30');
  await expect(page.getByLabel('종료 시간')).toHaveValue('00:30');
});

test('오늘의 지난 시간은 선택할 수 없고 초기 예약은 미래다', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-09-12T23:45:00+09:00'));
  await login(page);
  await openNewReservation(page);
  await expect(page.getByRole('button', { name: '예약 등록' })).toBeEnabled();
  await page.getByRole('button', { name: /오늘/ }).click();
  await expect(page.getByLabel('시작 시간').locator('option[value="00:00"]')).toHaveJSProperty('disabled', true);
  await expect(page.getByRole('button', { name: '예약 등록' })).toBeDisabled();
});
