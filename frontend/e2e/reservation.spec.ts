import { expect, test, type Page } from '@playwright/test';
import { manageRecruitment, login } from './helpers';
test.use({ timezoneId: 'Asia/Seoul' });
async function open(page: Page) {
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  if (await page.getByRole('button', { name: '새 예약', exact: true }).isVisible()) await page.getByRole('button', { name: '새 예약', exact: true }).click();
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
}
test('예약은 별도 탭에서 등록하고 겹치는 예약은 서버가 거절한다', async ({ page }) => {
  await login(page); await open(page);
  await page.locator('.recruitment-composer-shell').getByRole('group', { name: '플레이 양' }).getByRole('button', { name: '두 게임 이상' }).click();
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  expect(await page.evaluate(async () => { const path = '/src/api/recruitment.ts'; const api = await import(/* @vite-ignore */ path); return (await api.myRecruitments()).some(row => row.type === 'RESERVATION'); })).toBe(true);
  await open(page); await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('시간이 겹치는 예약');
});
test('자정을 넘는 예약은 수정할 때도 날짜와 플레이 양을 유지한다', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-09-13T10:00:00+09:00'));
  await login(page); await open(page);
  await page.locator('.recruitment-composer-shell').getByLabel('시작 가능 시각').fill('2026-09-14T23:30');
  await page.locator('.recruitment-composer-shell').getByLabel('마지막 종료 시각').fill('2026-09-15T00:30');
  await page.locator('.recruitment-composer-shell').getByRole('group', { name: '플레이 양' }).getByRole('button', { name: '두 게임 이상' }).click();
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await manageRecruitment(page, '조건 수정');
  await expect(page.locator('.recruitment-composer-shell').getByLabel('시작 가능 시각')).toHaveValue('2026-09-14T23:30');
  await expect(page.locator('.recruitment-composer-shell').getByLabel('마지막 종료 시각')).toHaveValue('2026-09-15T00:30');
  await expect(page.locator('.recruitment-composer-shell').getByRole('group', { name: '플레이 양' }).getByRole('button', { name: '두 게임 이상' })).toHaveAttribute('aria-pressed', 'true');
});
test('과거·역전·30분 경계가 아닌 시간은 제출 전에 알려준다', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-09-13T10:00:00+09:00'));
  await login(page); await open(page);
  const start = page.locator('.recruitment-composer-shell').getByLabel('시작 가능 시각');
  const end = page.locator('.recruitment-composer-shell').getByLabel('마지막 종료 시각');
  const submit = page.getByRole('button', { name: '매칭 시작', exact: true });
  await start.fill('2026-09-13T09:00'); await expect(submit).toBeDisabled();
  await start.fill('2026-09-14T22:00'); await end.fill('2026-09-14T20:00'); await expect(submit).toBeDisabled();
  await end.fill('2026-09-14T23:15'); await expect(submit).toBeDisabled();
  await end.fill('2026-09-14T23:30'); await expect(submit).toBeEnabled();
});
test('예약 매칭을 종료하면 신규 예약을 다시 등록할 수 있다', async ({ page }) => {
  await login(page); await open(page); await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await manageRecruitment(page, '매칭 종료');
  await expect(page.locator('.my-recruitment')).toHaveCount(0);
  await open(page); await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  expect(await page.evaluate(async () => { const path = '/src/api/recruitment.ts'; const api = await import(/* @vite-ignore */ path); return (await api.myRecruitments()).some(row => row.type === 'RESERVATION'); })).toBe(true);
});
