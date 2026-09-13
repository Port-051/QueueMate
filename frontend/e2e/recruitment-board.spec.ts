import { expect, test } from '@playwright/test';
import { manageRecruitment, login, selectBoardFilter, startRealtimeMatch } from './helpers';

test('빈 결과에서도 필터 수정과 모집 시작 액션이 남는다', async ({ page }) => {
  await login(page);
  await selectBoardFilter(page, '찾는 상대 티어', '챌린저');
  await expect(page.getByRole('heading', { name: '조건에 맞는 모집이 없어요' })).toBeVisible();
  await expect(page.getByRole('button', { name: '찾는 상대 티어', exact: true })).toBeEnabled();
  await expect(page.locator('.intro-launch > button')).toBeEnabled();
});
test('활동 재확인과 조건 한 개 변경은 사용자 선택 후에만 적용된다', async ({ page }) => {
  await page.clock.install();
  await login(page);
  await page.locator('.intro-launch > button').click();
  await page.getByRole('dialog').getByLabel('주 포지션', { exact: true }).selectOption('TOP');
  await page.getByRole('dialog').getByRole('button', { name: '탑', exact: true }).click();
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  // 시계만 진행한다. 재게시를 대기시간 데이터로 사용하지 않는다.
  await page.clock.fastForward(3 * 60_000 + 2000);
  await expect(page.getByRole('button', { name: /상대 포지션을 넓히면/ })).toBeVisible();
  await page.getByRole('button', { name: /상대 포지션을 넓히면/ }).click();
  await expect(page.getByRole('dialog', { name: '조건 변경 미리 보기' })).toBeVisible();
  await page.getByRole('button', { name: '유지할게요' }).click();
  await manageRecruitment(page, '조건 수정');
  await expect(page.getByRole('dialog').getByRole('button', { name: '탑', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: /상대 포지션을 넓히면/ }).click();
  await page.getByRole('button', { name: '이 조건만 변경' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await manageRecruitment(page, '조건 수정');
  await expect(page.getByRole('dialog').getByRole('button', { name: '탑', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await expect(page.getByRole('dialog').getByLabel('주 포지션')).toHaveValue('TOP');
});
test('위로 올리기 제한과 일시 중지·재개는 같은 모집을 유지한다', async ({ page }) => {
  await login(page);
  const profile = page.getByRole('complementary', { name: '내 정보', exact: true });
  await expect(profile.locator('.intro-launch > button')).toBeEnabled();
  await startRealtimeMatch(page);
  await expect(profile).toBeVisible();
  await expect(profile.locator('.intro-launch > button')).toHaveCount(0);
  await expect(page.locator('.board-feed .match-stage .my-recruitment')).toBeVisible();
  await expect(profile.locator('.match-stage')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '위로 올리기', exact: true })).toHaveCount(0);
  await manageRecruitment(page, '잠시 멈춤');
  await expect(page.locator('.my-recruitment')).toContainText('잠시 멈춤');
  await page.getByRole('button', { name: '모집 재개', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toContainText('모집 중');
  await expect(page.locator('.my-recruitment')).toHaveCount(1);
});
test('새 목록은 확인 버튼으로 반영하며 읽고 있던 행은 움직이지 않는다', async ({ page }) => {
  await login(page);
  const first = await page.locator('.recruitment-row').first().getAttribute('data-recruitment-id');
  await startRealtimeMatch(page);
  await page.getByRole('button', { name: 'PlayMaker 모집 상세' }).click();
  await page.getByRole('button', { name: '이 모집에 참여 신청' }).click();
  await expect(page.locator('.board-new-results')).toBeVisible();
  await expect(page.locator('.recruitment-row').first()).toHaveAttribute('data-recruitment-id', first!);
  await expect(page.locator('.recruitment-row').filter({ hasText: 'PlayMaker' })).toContainText('전원 수락 대기');
  await page.locator('.board-new-results').click();
  await expect(page.getByRole('button', { name: 'PlayMaker 모집 상세' })).toHaveCount(0);
});
