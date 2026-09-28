import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('오른쪽 친구 관리에서 받은 요청을 수락하고 대화로 돌아온다', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/messages"]').click();

  await page.getByRole('button', { name: '친구 관리', exact: true }).click();
  const management = page.getByRole('region', { name: '친구 관리', exact: true });
  await expect(management).toBeVisible();
  await expect(page.getByRole('dialog', { name: '친구 관리', exact: true })).toHaveCount(0);
  const listBounds = (await page.locator('.dm-sidebar').boundingBox())!;
  const managementBounds = (await management.boundingBox())!;
  expect(managementBounds.x).toBeGreaterThanOrEqual(listBounds.x + listBounds.width - 1);
  await expect(management.getByRole('tab', { name: /^친구/ })).toHaveAttribute('aria-selected', 'true');
  await management.getByRole('tab', { name: /받은 요청/ }).click();
  const requester = page.locator('.dm-friend-row').first();
  const nickname = (await requester.locator('b').innerText()).trim();
  await requester.getByRole('button', { name: '수락' }).click();

  await expect(page.locator('.dm-friend-row b').filter({ hasText: nickname })).toHaveCount(0);
  await page.getByRole('button', { name: '친구 관리 닫기', exact: true }).click();
  await page.locator('.dm-contact-select').filter({ has: page.locator('b', { hasText: nickname }) }).click();
  await expect(page.locator('.dm-thread-person h2')).toHaveText(nickname);
  await expect(page.locator('.dm-thread-person span')).toHaveText('친구');
  await expect(page.getByRole('log')).toBeVisible();
});

test('차단하면 친구 목록에서 빠지고 차단 목록에 남는다 (INV-6)', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/messages"]').click();

  await page.getByRole('button', { name: '추천', exact: true }).click();
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  await page.locator('.dm-thread-header summary').click();
  await page.locator('.dm-thread-header').getByRole('button', { name: '차단', exact: true }).click();
  await page.getByRole('button', { name: '차단하기', exact: true }).click();

  await expect(page.locator('.dm-contact-top b').filter({ hasText: /^GankFlow$/ })).toHaveCount(0);
  await page.getByRole('button', { name: '친구 관리', exact: true }).click();
  await page.getByRole('tab', { name: /차단 목록/ }).click();
  await expect(page.locator('.dm-friend-row b').filter({ hasText: /^GankFlow$/ })).toBeVisible();
});

test('최근 함께한 팀원과의 대화에서 신고를 접수할 수 있다', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/messages"]').click();

  await page.locator('.dm-contact-select').filter({ has: page.locator('b', { hasText: /^BlueOcean$/ }) }).click();
  await page.locator('.dm-thread-header summary').click();
  await page.locator('.dm-thread-header').getByRole('button', { name: '신고' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: '신고 접수' }).click();
  await expect(page.locator('.toast.ok')).toContainText('신고가 접수되었습니다');
});
