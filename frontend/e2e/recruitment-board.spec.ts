import { selectButton } from './helpers';
import { expect, test } from '@playwright/test';
import { manageRecruitment, login, selectBoardFilter, startRealtimeMatch } from './helpers';

test('빈 결과에서도 필터 수정과 매칭 시작 액션이 남는다', async ({ page }) => {
  await login(page);
  await selectBoardFilter(page, '찾는 상대 티어', '챌린저');
  await expect(page.getByRole('heading', { name: '조건에 맞는 매칭이 없어요' })).toBeVisible();
  await expect(page.getByRole('button', { name: '찾는 상대 티어', exact: true })).toBeEnabled();
  await expect(page.locator('.recruitment-composer-shell button[type=submit]')).toBeEnabled();
});
test('활동 재확인과 조건 한 개 변경은 사용자 선택 후에만 적용된다', async ({ page }) => {
  await page.clock.install();
  await login(page);
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  await selectButton(page.locator('.recruitment-composer-shell').getByRole('group', { name: '주 포지션', exact: true }), '탑');
  await page.locator('.recruitment-composer-shell').getByRole('group', { name: '찾는 상대 포지션', exact: true }).getByRole('button', { name: '탑', exact: true }).click();
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  // 시계만 진행한다. 재게시를 대기시간 데이터로 사용하지 않는다.
  await page.clock.fastForward(3 * 60_000 + 2000);
  await expect(page.getByRole('button', { name: /상대 포지션을 넓히면/ })).toBeVisible();
  await page.getByRole('button', { name: /상대 포지션을 넓히면/ }).click();
  await expect(page.getByRole('region', { name: '조건 변경 미리 보기' })).toBeVisible();
  await page.getByRole('button', { name: '유지할게요' }).click();
  await manageRecruitment(page, '조건 수정');
  await expect(page.locator('.recruitment-composer-shell').getByRole('group', { name: '찾는 상대 포지션', exact: true }).getByRole('button', { name: '탑', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: /상대 포지션을 넓히면/ }).click();
  await page.getByRole('button', { name: '이 조건만 변경' }).click();
  await expect(page.getByRole('region', { name: '조건 변경 미리 보기' })).toHaveCount(0);
  await manageRecruitment(page, '조건 수정');
  await expect(page.locator('.recruitment-composer-shell').getByRole('group', { name: '찾는 상대 포지션', exact: true }).getByRole('button', { name: '탑', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await expect(page.locator('.recruitment-composer-shell').getByRole('group', { name: '주 포지션', exact: true }).getByRole('button', { name: '탑', exact: true })).toHaveAttribute('aria-pressed', 'true');
});
test('위로 올리기 제한과 일시 중지·재개는 같은 매칭을 유지한다', async ({ page }) => {
  await login(page);
  const profile = page.getByRole('complementary', { name: '내 정보', exact: true });
  await expect(profile.locator('.recruitment-composer-shell button[type=submit]')).toBeEnabled();
  await startRealtimeMatch(page);
  await expect(profile).toBeVisible();
  await expect(profile.locator('.intro-launch > button')).toHaveCount(0);
  await expect(profile.locator('.match-stage .my-recruitment')).toBeVisible();
  await expect(page.locator('.board-feed .match-stage')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '위로 올리기', exact: true })).toHaveCount(0);
  await manageRecruitment(page, '잠시 멈춤');
  await expect(page.locator('.my-recruitment')).toContainText('잠시 멈춤');
  await page.getByRole('button', { name: '매칭 재개', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toContainText('매칭 중');
  await expect(page.locator('.my-recruitment')).toHaveCount(1);
});
test('내 글도 목록에 보이고 마감된 글은 자동으로 사라진다', async ({ page }) => {
  await page.clock.install(); await login(page);
  await startRealtimeMatch(page);
  const ownRow = page.getByRole('button', { name: 'QueueMaster 매칭 글 상세', exact: true });
  await expect(ownRow).toBeVisible();
  await expect(page.locator('.recruitment-row').first()).toHaveAttribute('data-recruitment-id', (await ownRow.getAttribute('data-recruitment-id'))!);
  await ownRow.click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  await expect(page.getByRole('region', { name: '매칭 글 상세', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'PlayMaker 매칭 글 상세' }).click();
  await page.getByRole('button', { name: '같이 할래요' }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await page.clock.fastForward(22000);
  await expect(ownRow).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'PlayMaker 매칭 글 상세' })).toHaveCount(0);
  await expect(page.locator('.board-new-results')).toHaveCount(0);
});
