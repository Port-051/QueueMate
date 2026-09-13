import { expect, test } from '@playwright/test';
import { manageRecruitment, login, startRealtimeMatch } from './helpers';

test('자동 찾기: 같은 공개 모집 풀에서 제안과 파티까지 홈에서 완결한다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page, true);
  await expect(page.locator('.my-recruitment')).toContainText('모집 중');
  await expect(page.locator('.board-proposal')).toBeVisible({ timeout: 15000 });
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.locator('.proposal-person')).toHaveCount(2);
  await page.getByRole('button', { name: '함께할게요' }).click();
  await expect(page.locator('.compact-party')).toBeVisible();
  await expect(page.getByRole('heading', { name: '채팅', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/app\/home$/);
});
test('활성 실시간 모집은 하나만 가능하지만 예약 모집은 별도로 유지한다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await expect(page.locator('.intro-launch > button')).toHaveCount(0);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await expect(page.getByRole('button', { name: '예약하기' })).toBeEnabled();
  await expect(page.locator('.my-recruitment')).toBeVisible();
});
test('모집을 종료한 뒤 새로 시작해도 티어·상대 조건을 유지한다', async ({ page }) => {
  await login(page);
  await page.locator('.intro-launch > button').click();
  await page.locator('.recruitment-composer-shell').getByLabel('내 티어', { exact: true }).selectOption('GOLD');
  await page.locator('.recruitment-composer-shell').getByRole('button', { name: '정글', exact: true }).click();
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  await manageRecruitment(page, '모집 종료');
  await expect(page.locator('.my-recruitment')).toHaveCount(0);
  await page.locator('.intro-launch > button').click();
  await expect(page.locator('.recruitment-composer-shell').getByLabel('내 티어', { exact: true })).toHaveValue('GOLD');
  await expect(page.locator('.recruitment-composer-shell').getByRole('button', { name: '정글', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
});
test('직접 신청·수락 후 메뉴를 이동해도 채팅과 마이크가 유지된다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await page.getByRole('button', { name: 'PlayMaker 모집 상세' }).click();
  await page.getByRole('button', { name: '이 모집에 참여 신청' }).click();
  await expect(page.locator('.board-proposal')).toBeVisible();
  await page.getByRole('button', { name: '함께할게요' }).click();
  await expect(page.locator('.compact-party')).toBeVisible();
  await expect(page.locator('.compact-chat').getByRole('button', { name: '연결 다시 시도' })).toHaveCount(0);
  await page.getByRole('button', { name: '마이크 켜기' }).click();
  await page.getByPlaceholder('메시지를 입력하세요').fill('이동해도 대화를 유지해요');
  await page.getByPlaceholder('메시지를 입력하세요').press('Enter');
  await expect(page.getByRole('log')).toContainText('이동해도 대화를 유지해요');
  await page.locator('.side-nav a[href="/app/messages"]').click();
  await page.locator('.side-nav a[href="/app/home"]').click();
  await expect(page.getByRole('button', { name: '음소거', exact: true })).toBeEnabled();
  await expect(page.getByRole('log')).toContainText('이동해도 대화를 유지해요');
  await page.getByRole('button', { name: '게임 준비 완료' }).click();
  await expect(page.getByRole('button', { name: '준비 해제' })).toBeVisible();
});
