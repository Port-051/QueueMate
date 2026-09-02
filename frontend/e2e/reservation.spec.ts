import { expect, test } from '@playwright/test';
import { login } from './helpers';

async function openNewReservation(page: import('@playwright/test').Page) {
  await page.locator('.side-nav a[href="/app/reservations"]').click();
  await expect(page.getByRole('heading', { name: '예약 매칭 관리' })).toBeVisible();
  await page.getByRole('button', { name: '새 예약' }).click();
  await expect(page.getByRole('heading', { name: '예약 매칭 설정' })).toBeVisible();
}

test('예약 등록 후 목록에 진행 중으로 남는다', async ({ page }) => {
  await login(page);
  await openNewReservation(page);

  await page.getByRole('button', { name: '2판 이상' }).click();
  await page.getByRole('button', { name: '예약 등록' }).click();

  await expect(page).toHaveURL(/\/app\/reservations$/);
  await expect(page.getByText('2판 이상')).toBeVisible();
  await expect(page.getByText('대기 중')).toBeVisible();
});

test('시간이 겹치는 예약은 등록할 수 없다 (INV-9)', async ({ page }) => {
  await login(page);
  await openNewReservation(page);
  await page.getByRole('button', { name: '예약 등록' }).click();
  await expect(page).toHaveURL(/\/app\/reservations$/);

  await page.getByRole('button', { name: '새 예약' }).click();
  await page.getByRole('button', { name: '예약 등록' }).click();

  await expect(page.locator('.toast.error')).toContainText('시간이 겹치는 예약');
  await expect(page).toHaveURL(/\/app\/reservations\/new/);
});

test('예약을 취소하면 지난 예약으로 이동한다', async ({ page }) => {
  await login(page);
  await openNewReservation(page);
  await page.getByRole('button', { name: '예약 등록' }).click();
  await expect(page).toHaveURL(/\/app\/reservations$/);

  await page.getByRole('button', { name: '취소' }).click();
  await expect(page.getByText('진행 중인 예약이 없습니다')).toBeVisible();

  await page.getByRole('button', { name: /지난 예약/ }).click();
  await expect(page.getByText('취소됨')).toBeVisible();
});

test('종료 시간이 시작보다 빠르면 등록 버튼이 잠긴다', async ({ page }) => {
  await login(page);
  await openNewReservation(page);

  await page.locator('.time-range select').first().selectOption('22:00');
  await page.locator('.time-range select').nth(1).selectOption('20:00');

  await expect(page.getByText('종료 시간이 시작 시간보다 늦어야 합니다')).toBeVisible();
  await expect(page.getByRole('button', { name: '예약 등록' })).toBeDisabled();
});
