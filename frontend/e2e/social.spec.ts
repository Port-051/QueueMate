import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('받은 친구 요청을 수락하면 친구 목록에 들어간다', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/friends"]').click();

  await page.getByRole('button', { name: /받은 요청/ }).click();
  const requester = page.locator('.list-item').first();
  const nickname = (await requester.locator('.li-main b').innerText()).trim();
  await requester.getByRole('button', { name: '수락' }).click();

  await page.getByRole('button', { name: /친구 목록/ }).click();
  await expect(page.locator('.list-item .li-main b').filter({ hasText: nickname })).toBeVisible();
});

test('차단하면 친구 목록에서 빠지고 차단 목록에 남는다 (INV-6)', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/friends"]').click();

  const first = page.locator('.list-item').first();
  const nickname = (await first.locator('.li-main b').innerText()).trim();
  await first.getByRole('button', { name: '차단' }).click();

  await expect(page.locator('.list-item .li-main b').filter({ hasText: nickname })).toHaveCount(0);
  await page.getByRole('button', { name: /차단 목록/ }).click();
  await expect(page.locator('.list-item .li-main b').filter({ hasText: nickname })).toBeVisible();
});

test('최근 함께한 사람에서 신고를 접수할 수 있다', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/recent"]').click();

  await page.locator('.list-item').first().getByRole('button', { name: '신고' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: '신고 접수' }).click();
  await expect(page.locator('.toast.ok')).toContainText('신고가 접수되었습니다');
});
