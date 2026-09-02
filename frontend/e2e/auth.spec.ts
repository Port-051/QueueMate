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
  await expect(page.getByRole('link', { name: '예약 매칭' })).toBeVisible();
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

test('로그인 후 좌측 탭이 docs/01 순서대로 노출된다', async ({ page }) => {
  await login(page);
  const labels = await page.locator('.side-nav .nav-link').allInnerTexts();
  expect(labels.map((t) => t.trim().split('\n')[0])).toEqual([
    '홈', '매칭', '예약 매칭', '파티룸', '친구', '최근 함께한 사람', '내 정보', '설정',
  ]);
});
