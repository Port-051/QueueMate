import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('빈 결과에서도 필터 수정과 모집 시작 액션이 남는다', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: '검색 필터 설정' }).click();
  await page.locator('.board-filter').getByLabel('상대 최소 티어', { exact: true }).selectOption('CHALLENGER');
  await page.getByRole('button', { name: '필터 적용' }).click();
  await expect(page.getByRole('heading', { name: '이 조건으로 모집 중인 팀원이 없어요' })).toBeVisible();
  await expect(page.getByRole('button', { name: '검색 조건 바꾸기' })).toBeEnabled();
  await expect(page.getByRole('button', { name: '먼저 모집하기' })).toBeEnabled();
});
test('활동 재확인과 조건 한 개 변경은 사용자 선택 후에만 적용된다', async ({ page }) => {
  await page.clock.install();
  await login(page);
  await page.getByRole('button', { name: '+ 실시간 모집 만들기' }).click();
  await page.getByRole('dialog').getByLabel('상대 최소 티어', { exact: true }).selectOption('CHALLENGER');
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  // 시계만 진행한다. 재게시를 대기시간 데이터로 사용하지 않는다.
  await page.clock.fastForward(3 * 60_000 + 2000);
  await expect(page.getByRole('button', { name: /상대 티어 범위를 넓히면/ })).toBeVisible();
  await page.getByRole('button', { name: /상대 티어 범위를 넓히면/ }).click();
  await expect(page.getByRole('dialog', { name: '조건 변경 미리 보기' })).toBeVisible();
  await page.getByRole('button', { name: '유지할게요' }).click();
  await page.getByRole('button', { name: '조건 수정', exact: true }).click();
  await expect(page.getByRole('dialog').getByLabel('상대 최소 티어', { exact: true })).toHaveValue('CHALLENGER');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: /상대 티어 범위를 넓히면/ }).click();
  await page.getByRole('button', { name: '이 조건만 변경' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: '조건 수정', exact: true }).click();
  await expect(page.getByRole('dialog').getByLabel('상대 최소 티어', { exact: true })).toHaveValue('');
  await expect(page.getByRole('dialog').getByLabel('내 포지션 / 역할')).toHaveValue('TOP');
});
test('위로 올리기 제한과 일시 중지·재개는 같은 모집을 유지한다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await expect(page.getByRole('button', { name: '5분 후 위로 올리기' })).toBeDisabled();
  await page.getByRole('button', { name: '잠시 멈춤', exact: true }).click();
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
  await expect(page.locator('.recruitment-row').first()).toContainText('전원 수락 대기');
  await page.locator('.board-new-results').click();
  await expect(page.locator('.recruitment-row').first()).not.toHaveAttribute('data-recruitment-id', first!);
});
