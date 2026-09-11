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

test('카카오로 시작하면 로그인된다', async ({ page }) => {
  await page.goto('/login');
  // 서버가 자격 증명이 설정된 제공자만 내려준다. 목록에 없는 버튼은 그리지 않는다.
  await expect(page.getByRole('button', { name: '카카오로 시작하기' })).toBeVisible();

  await page.getByRole('button', { name: '카카오로 시작하기' }).click();

  // 소셜로 처음 들어온 계정은 게임 계정이 없어 온보딩부터 시작한다.
  await expect(page).toHaveURL(/\/onboarding|\/app\/home/);
  await expect(page.getByRole('button', { name: '로그인', exact: true })).toHaveCount(0);
});

test('같은 소셜 계정으로 다시 들어가면 같은 사용자다', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: '네이버로 시작하기' }).click();
  await expect(page).toHaveURL(/\/onboarding|\/app\/home/);

  const first = await page.evaluate(() => localStorage.getItem('qm.tokens'));
  expect(first).not.toBeNull();
});

test('콜백에 코드가 없으면 실패를 알리고 로그인으로 되돌린다', async ({ page }) => {
  await page.goto('/auth/callback?error=ACCESS_DENIED');

  await expect(page.getByRole('heading', { name: '로그인하지 못했습니다' })).toBeVisible();
  await page.getByRole('button', { name: '로그인으로 돌아가기' }).click();
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
