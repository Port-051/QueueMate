import { expect, test } from '@playwright/test';
import { DEMO, login } from './helpers';

test('랜딩에서 로그인하면 홈으로 들어간다', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /지금, 바로/ })).toBeVisible();
  await page.getByRole('link', { name: '로그인' }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
  await page.getByPlaceholder('비밀번호를 입력하세요').fill(DEMO.password);
  await page.locator('.auth-form button[type="submit"]').click();

  await expect(page).toHaveURL(/\/app\/home/);
  await expect(page.getByRole('heading', { name: '예약 매칭', exact: true })).toBeVisible();
});

test('잘못된 비밀번호는 오류를 보여주고 로그인되지 않는다', async ({ page }) => {
  await page.goto('/login');
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
  await page.getByPlaceholder('비밀번호를 입력하세요').fill('wrongpassword');
  await page.locator('.auth-form button[type="submit"]').click();

  await expect(page.locator('.field .err')).toContainText('올바르지 않습니다');
  await expect(page).toHaveURL(/\/login/);
});

test('로그인하지 않으면 앱 화면 대신 로그인으로 보낸다', async ({ page }) => {
  await page.goto('/app/home');
  await expect(page).toHaveURL(/\/login/);
});

test('프로필에서 설정을 바꾸고 매칭 기본값에 반영한 뒤 로그아웃한다', async ({ page }) => {
  await login(page);
  const navigation = page.locator('.side-nav');
  const labels = await navigation.getByRole('link').evaluateAll((links) => links.map((link) => link.getAttribute('aria-label')));
  expect(labels).toEqual([
    '홈', '파티룸', '친구', '최근 함께한 사람', 'QueueMaster 프로필',
  ]);
  await expect(page.locator('.sidebar-account')).toHaveCount(0);
  await expect(navigation.getByRole('link', { name: '설정', exact: true })).toHaveCount(0);
  await navigation.getByRole('link', { name: 'QueueMaster 프로필', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/me$/);
  await expect(page.getByRole('heading', { name: 'QueueMaster', exact: true })).toBeVisible();
  await expect(navigation.getByRole('link', { name: 'QueueMaster 프로필', exact: true })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByText('팀원에게 보여줄 프로필과 게임 ID를 관리하세요.')).toHaveCount(0);
  const settings = page.getByRole('region', { name: '매칭 기본값', exact: true });
  await settings.getByRole('button', { name: '사용 안 함', exact: true }).click();
  await settings.getByRole('button', { name: '즐겜', exact: true }).click();
  await navigation.getByRole('link', { name: '홈', exact: true }).click();
  await page.getByRole('button', { name: 'League of Legends 매칭', exact: true }).click();
  await expect(page.getByRole('dialog').getByRole('button', { name: '사용 안 함', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('dialog').getByRole('button', { name: '즐겜', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.keyboard.press('Escape');
  await navigation.getByRole('link', { name: 'QueueMaster 프로필', exact: true }).click();
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await expect(page).toHaveURL('/');
  await page.goto('/app/me');
  await expect(page).toHaveURL(/\/login/);
});
