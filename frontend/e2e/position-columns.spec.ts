import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('두 포지션 필터는 각각 작성자와 찾는 상대를 조회하고 네 아이콘은 두 줄이다', async ({ page }) => {
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
  expect(fourth!.y).toBeGreaterThan(first!.y);
  await expect(row.locator('.recruitment-role-arrow')).toHaveCount(0);
  const filters = page.locator('.board-position-filters');
  await filters.getByRole('group', { name: '내 포지션', exact: true }).getByRole('button', { name: '미드', exact: true }).click();
  await expect(row).toBeVisible();
  const wanted = filters.getByRole('group', { name: '찾는 포지션', exact: true });
  await wanted.getByRole('button', { name: '정글', exact: true }).click();
  await expect(row).toHaveCount(0);
  await wanted.getByRole('button', { name: '탑', exact: true }).click();
  await expect(row).toBeVisible();
  await expect(page.locator('.board-result-count')).toContainText('명이 매칭 중이에요');
});
