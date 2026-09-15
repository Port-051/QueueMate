import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('포지션 필터는 작성자를 조회하고 네 아이콘은 한 줄이다', async ({ page }) => {
  await login(page);
  const form = page.locator('.recruitment-composer-shell');
  const own = form.getByRole('group', { name: '내 포지션', exact: true });
  for (const button of await own.locator('[aria-pressed=true]').all()) await button.click();
  for (const name of ['탑', '정글', '미드', '바텀']) await own.getByRole('button', { name, exact: true }).click();
  await form.getByRole('group', { name: '찾는 포지션', exact: true }).getByRole('button', { name: '탑', exact: true }).click();
  await startRealtimeMatch(page);
  const row = page.getByRole('button', { name: 'QueueMaster 매칭 글 상세', exact: true });
  const icons = row.locator('.recruitment-role-own .recruitment-role-icon');
  await expect(icons).toHaveCount(4);
  const first = await icons.nth(0).boundingBox();
  const fourth = await icons.nth(3).boundingBox();
  expect(Math.abs(fourth!.y - first!.y)).toBeLessThan(1);
  await expect(row.locator('.recruitment-role-arrow')).toHaveCount(0);
  await page.locator('.board-filter-line').getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '미드', exact: true }).click();
  await expect(row).toBeVisible();
  await expect(page.locator('.board-result-count')).toContainText('명이 매칭 중이에요');
});
