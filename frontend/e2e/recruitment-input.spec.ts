import { expect, test } from '@playwright/test';
import { login } from './helpers';
test.use({ timezoneId: 'Asia/Seoul' });

test('역전된 티어 범위는 필터 적용 전에 알려주고 현재 목록을 유지한다', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: '검색 필터 설정' }).click();
  await page.getByLabel('상대 최소 티어').selectOption('DIAMOND');
  await page.getByLabel('상대 최대 티어').selectOption('GOLD');
  await expect(page.locator('.board-filter').getByRole('alert')).toContainText('최소 티어가 최대 티어보다 높습니다');
  await expect(page.getByRole('button', { name: '필터 적용' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'PlayMaker 모집 상세' })).toBeVisible();
  await page.getByLabel('상대 최대 티어').selectOption('MASTER');
  await expect(page.getByRole('button', { name: '필터 적용' })).toBeEnabled();
  await expect(page.locator('.board-filter').getByRole('alert')).toHaveCount(0);
});

test('예약 필터의 역전된 시간과 비어 있는 시간은 적용할 수 없다', async ({ page }) => {
  await login(page);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await page.getByRole('button', { name: '검색 필터 설정' }).click();
  await page.getByLabel('시작 가능 시각').fill('2026-10-01T22:00');
  await page.getByLabel('마지막 종료 시각').fill('2026-10-01T21:00');
  await expect(page.getByRole('button', { name: '필터 적용' })).toBeDisabled();
  await expect(page.locator('.board-filter').getByRole('alert')).toContainText('종료 시간이 시작 시간보다 늦어야 합니다');
  await page.getByLabel('마지막 종료 시각').fill('');
  await expect(page.locator('.board-filter').getByRole('alert')).toContainText('예약 시간을 선택');
  await page.getByLabel('마지막 종료 시각').fill('2026-10-01T23:00');
  await expect(page.getByRole('button', { name: '필터 적용' })).toBeEnabled();
});

test('예약 작성 중 시작 시각이 지나면 별도 입력 없이 저장을 막고 시간을 바꿀 수 있다', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-14T18:29:50+09:00') });
  await login(page);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await page.getByRole('button', { name: '+ 예약 모집 만들기' }).click();
  await page.getByLabel('시작 가능 시각').fill('2026-09-14T18:30');
  await page.getByLabel('마지막 종료 시각').fill('2026-09-14T20:00');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeEnabled();
  await page.clock.fastForward(15_000);
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('시작 시각은 현재 이후여야 합니다');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeDisabled();
  await page.getByLabel('시작 가능 시각').fill('2026-09-14T19:00');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeEnabled();
});
