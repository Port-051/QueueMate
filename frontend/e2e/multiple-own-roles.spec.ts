import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('본인 포지션 여러 개가 저장되고 목록에 본인과 상대가 각각의 컬럼에 보인다', async ({ page }) => {
  await login(page);
  const form = page.locator('.recruitment-composer-shell');
  const own = form.getByRole('group', { name: '포지션', exact: true });
  const target = form.getByRole('group', { name: '찾는 포지션', exact: true });
  await expect(own.getByRole('button', { name: '무관' })).toHaveCount(0);
  for (const button of await own.locator('[aria-pressed=true]').all()) await button.click();
  await own.getByRole('button', { name: '탑', exact: true }).click();
  await own.getByRole('button', { name: '정글', exact: true }).click();
  await target.getByRole('button', { name: '미드', exact: true }).click();
  await target.getByRole('button', { name: '바텀', exact: true }).click();
  await startRealtimeMatch(page);
  const row = page.getByRole('button', { name: 'QueueMaster 매칭 글 상세', exact: true });
  await expect(row.locator('.recruitment-role-own .recruitment-role-icon')).toHaveCount(2);
  await expect(row.locator('.recruitment-role-targets .recruitment-role-icon')).toHaveCount(2);
  const upper = await row.locator('.recruitment-role-own').boundingBox();
  const lower = await row.locator('.recruitment-role-targets').boundingBox();
  expect(lower!.x).toBeGreaterThan(upper!.x + upper!.width);
  expect(Math.abs(lower!.y - upper!.y)).toBeLessThan(2);
  await page.getByRole('button', { name: '조건 수정', exact: true }).click();
  await expect(own.locator('[aria-pressed=true]')).toHaveCount(2);
  await expect(page.getByRole('button', { name: '매칭 조건 저장' })).toBeDisabled();
  await page.getByRole('button', { name: '취소', exact: true }).click();
  await page.locator('.board-filter-line').getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '정글', exact: true }).click();
  await expect(row).toBeVisible();
});
