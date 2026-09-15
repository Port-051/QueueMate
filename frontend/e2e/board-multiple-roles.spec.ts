import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('포지션 다중 선택은 합집합으로 검색하고 개별 해제와 초기화가 된다', async ({ page }) => {
  await login(page);
  const filters = page.getByRole('group', { name: '상대 검색 필터', exact: true });
  const roles = filters.getByRole('group', { name: '찾는 상대 포지션', exact: true });
  await filters.getByRole('button', { name: '랭크', exact: true }).click();
  await roles.getByRole('button', { name: '탑', exact: true }).click();
  await roles.getByRole('button', { name: '정글', exact: true }).click();
  await expect(roles.locator('[aria-pressed=true]')).toHaveCount(2);
  const rows = page.locator('.recruitment-row');
  await expect(rows).toHaveCount(5);
  expect(await rows.locator('.row-roles > .recruitment-role-pair').evaluateAll(nodes => nodes.every(node => /주 포지션: (탑|정글|전체),/.test(node.getAttribute('aria-label') ?? '')))).toBe(true);
  await roles.getByRole('button', { name: '탑', exact: true }).click();
  await expect(rows).toHaveCount(3);
  await expect(roles.getByRole('button', { name: '정글', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await filters.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(roles.locator('[aria-pressed=true]')).toHaveCount(0);
  await expect(rows.first()).toBeVisible();
});
