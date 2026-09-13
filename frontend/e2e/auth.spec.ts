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
  await expect(page.getByRole('tab', { name: '예약 매치', exact: true })).toBeVisible();
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

test('프로필 설정은 저장되고 새 자기소개는 무관 조건으로 시작하며 로그아웃할 수 있다', async ({ page }) => {
  await login(page);
  const navigation = page.locator('.side-nav');
  const labels = await navigation.getByRole('link').evaluateAll((links) => links.map((link) => link.getAttribute('aria-label')));
  expect(labels).toEqual([
    '홈', '메시지', '프로필',
  ]);
  await expect(navigation.getByRole('button', { name: '알림', exact: true })).toBeVisible();
  await expect(page.locator('.sidebar-account')).toHaveCount(0);
  await expect(navigation.getByRole('link', { name: '설정', exact: true })).toHaveCount(0);
  await navigation.getByRole('link', { name: '프로필', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/me$/);
  await expect(page.getByRole('heading', { name: 'QueueMaster', exact: true })).toBeVisible();
  await expect(navigation.getByRole('link', { name: '프로필', exact: true })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByText('팀원에게 보여줄 프로필과 게임 ID를 관리하세요.')).toHaveCount(0);
  const settings = page.getByRole('region', { name: '매칭 기본값', exact: true });
  await settings.getByRole('button', { name: '사용 안 함', exact: true }).click();
  await settings.getByRole('button', { name: '즐겜', exact: true }).click();
  await navigation.getByRole('link', { name: '홈', exact: true }).click();
  await page.getByRole('button', { name: '자기소개 작성', exact: true }).click();
  await expect(page.getByRole('dialog').getByLabel('음성', { exact: true })).toHaveValue('OPTIONAL');
  await expect(page.getByRole('dialog').getByLabel('주 포지션', { exact: true })).toHaveValue('ANY');
  await expect(page.getByRole('dialog').getByLabel('원하는 큐 타입', { exact: true })).toHaveValue('ANY');
  await expect(page.getByRole('dialog').getByRole('radio', { name: '수동 매칭', exact: true })).toBeChecked();
  await page.keyboard.press('Escape');
  await navigation.getByRole('link', { name: '프로필', exact: true }).click();
  await expect(settings.getByRole('button', { name: '사용 안 함', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(settings.getByRole('button', { name: '즐겜', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await expect(page).toHaveURL('/');
  await page.goto('/app/me');
  await expect(page).toHaveURL(/\/login/);
});
