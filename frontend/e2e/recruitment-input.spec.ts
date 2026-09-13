import { expect, test } from '@playwright/test';
import { login } from './helpers';
test.use({ timezoneId: 'Asia/Seoul' });

test('상대 티어 하나를 고르면 바로 필터링되고 전체 티어로 돌아갈 수 있다', async ({ page }) => {
  await login(page);
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const initialCount = await page.locator('.recruitment-row').count();
  await expect(page.getByLabel('상대 최소 티어')).toHaveCount(0);
  await expect(page.getByLabel('상대 최대 티어')).toHaveCount(0);
  await page.getByLabel('찾는 상대 티어').selectOption('GOLD');
  await expect.poll(async () => {
    const tiers = await page.locator('.recruitment-row .row-tier').allTextContents();
    return tiers.length > 0 && tiers.every(tier => tier.includes('골드'));
  }).toBe(true);
  await page.getByLabel('찾는 상대 티어').selectOption('DIAMOND');
  await expect(page.locator('.recruitment-row')).toHaveCount(0);
  await expect(page.locator('.board-empty')).toContainText('조건에 맞는 모집이 없어요');
  await page.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(page.getByLabel('찾는 상대 티어')).toHaveValue('');
  await expect(page.locator('.recruitment-row')).toHaveCount(initialCount);
});

test('예약 필터의 잘못된 시간을 고쳐야 목록에 적용된다', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-14T18:00:00+09:00') });
  await login(page);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const before = await page.locator('.recruitment-row').evaluateAll(rows => rows.map(row => row.getAttribute('data-recruitment-id')));
  await page.getByLabel('검색 시작 시각').fill('2026-10-01T22:00');
  await page.getByLabel('검색 종료 시각').fill('2026-10-01T21:00');
  await expect(page.locator('.board-filter-bar').getByRole('alert')).toContainText('종료 시간이 시작 시간보다 늦어야 합니다');
  expect(await page.locator('.recruitment-row').evaluateAll(rows => rows.map(row => row.getAttribute('data-recruitment-id')))).toEqual(before);
  await page.getByLabel('검색 종료 시각').fill('');
  await expect(page.locator('.board-filter-bar').getByRole('alert')).toContainText('예약 시간을 선택');
  expect(await page.locator('.recruitment-row').evaluateAll(rows => rows.map(row => row.getAttribute('data-recruitment-id')))).toEqual(before);
  await page.getByLabel('검색 종료 시각').fill('2026-10-01T23:00');
  await expect(page.locator('.board-filter-bar').getByRole('alert')).toHaveCount(0);
  await expect(page.locator('.recruitment-row')).toHaveCount(0);
  await expect(page.getByLabel('검색 시작 시각')).toHaveValue('2026-10-01T22:00');
  await expect(page.getByLabel('검색 종료 시각')).toHaveValue('2026-10-01T23:00');
});

test('예약 작성 중 시작 시각이 지나면 별도 입력 없이 저장을 막고 시간을 바꿀 수 있다', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-14T18:29:50+09:00') });
  await login(page);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await page.locator('.intro-launch').getByRole('button', { name: '예약하기' }).click();
  await page.getByLabel('시작 가능 시각').fill('2026-09-14T18:30');
  await page.getByLabel('마지막 종료 시각').fill('2026-09-14T20:00');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeEnabled();
  await page.clock.fastForward(15_000);
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('시작 시각은 현재 이후여야 합니다');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeDisabled();
  await page.getByLabel('시작 가능 시각').fill('2026-09-14T19:00');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeEnabled();
});
