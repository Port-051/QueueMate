import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('받은 친구 요청을 수락하면 통합 대화 목록에 친구로 표시된다', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/messages"]').click();

  await page.getByRole('button', { name: '친구 관리', exact: true }).click();
  await page.getByRole('tab', { name: /받은 요청/ }).click();
  const requester = page.locator('.dm-management-person').first();
  const nickname = (await requester.locator('b').innerText()).trim();
  await requester.getByRole('button', { name: '수락' }).click();

  await expect(page.locator('.dm-management-person b').filter({ hasText: nickname })).toHaveCount(0);
  await page.getByRole('button', { name: '친구 관리 닫기', exact: true }).click();
  await page.locator('.dm-contact-select').filter({ has: page.locator('b', { hasText: nickname }) }).click();
  await expect(page.locator('.dm-thread-person h2')).toHaveText(nickname);
  await expect(page.locator('.dm-thread-person span')).toHaveText('친구');
  await expect(page.getByRole('button', { name: `${nickname} 상단 고정`, exact: true })).toBeVisible();
});

test('차단하면 친구 목록에서 빠지고 차단 목록에 남는다 (INV-6)', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/messages"]').click();

  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  await page.locator('.dm-thread-header summary').click();
  await page.locator('.dm-thread-header').getByRole('button', { name: '차단', exact: true }).click();
  await page.getByRole('button', { name: '차단하기', exact: true }).click();

  await expect(page.locator('.dm-contact-top b').filter({ hasText: /^GankFlow$/ })).toHaveCount(0);
  await page.getByRole('button', { name: '친구 관리', exact: true }).click();
  await page.getByRole('tab', { name: /차단 목록/ }).click();
  await expect(page.locator('.dm-management-person b').filter({ hasText: /^GankFlow$/ })).toBeVisible();
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
