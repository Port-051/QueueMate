import { expect, test } from '@playwright/test';
import { manageRecruitment, login, startRealtimeMatch } from './helpers';
const seconds = (value: string) => value.split(':').reduce((total, part) => total * 60 + Number(part), 0);

test('모집 타이머는 오른쪽 바에 보이고 목록 위치를 바꾸지 않는다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  const timer = page.getByRole('timer', { name: '모집 시작 후', exact: true });
  await expect(timer).toBeInViewport();
  expect(parseFloat(await timer.evaluate(el => getComputedStyle(el).fontSize))).toBeGreaterThanOrEqual(44);
  const stage = await page.getByRole('region', { name: '내 매칭 진행' }).boundingBox();
  const list = await page.locator('.board-toolbar').boundingBox();
  expect(stage!.x).toBeGreaterThan(list!.x + list!.width);
  expect(stage!.width).toBeLessThanOrEqual(320);
  expect(list!.y).toBeLessThan(100);
  await expect(page.locator('.board-feed .match-stage')).toHaveCount(0);
  await expect(page.locator('.my-recruitment .action-menu')).toHaveCount(0);
  const before = seconds(await timer.innerText());
  await page.clock.fastForward(6000);
  await expect.poll(async () => seconds(await timer.innerText())).toBeGreaterThanOrEqual(before + 6);
  await expect(page.locator('.board-rail')).toHaveCount(0);
  await expect(page.locator('.match-progress [aria-current="step"]')).toContainText('팀원 모집');
});
test('조건 수정·멈춤·재개·끌어올림과 메뉴 이동이 경과 시간을 초기화하지 않는다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  const timer = page.getByRole('timer', { name: '모집 시작 후', exact: true });
  await page.clock.fastForward(65_000);
  await manageRecruitment(page, '잠시 멈춤');
  await expect(page.locator('.recruitment-title')).toContainText('잠시 멈춤');
  await page.clock.fastForward(10_000);
  await page.getByRole('button', { name: '모집 재개', exact: true }).click();
  await manageRecruitment(page, '조건 수정');
  await page.locator('.recruitment-composer-shell').getByLabel('모집 한마디').fill('타이머 유지 확인');
  await page.getByRole('button', { name: '모집 조건 저장', exact: true }).click();
  await expect.poll(async () => seconds(await timer.innerText())).toBeGreaterThanOrEqual(75);
  await page.locator('.side-nav a[href="/app/messages"]').click();
  await page.clock.fastForward(10_000);
  await page.locator('.side-nav a[href="/app/home"]').click();
  await expect.poll(async () => seconds(await timer.innerText())).toBeGreaterThanOrEqual(85);
  await page.clock.fastForward(5 * 60_000);
  await page.getByRole('button', { name: '위로 올리기', exact: true }).click();
  await expect.poll(async () => seconds(await timer.innerText())).toBeGreaterThanOrEqual(385);
});
test('모집 상세와 참여 신청은 오른쪽에서 이어지고 닫으면 선택한 행으로 포커스가 돌아간다', async ({ page }) => {
  await login(page);
  const row = page.getByRole('button', { name: 'PlayMaker 모집 상세' });
  await row.click();
  await expect(page.getByRole('region', { name: '모집 상세', exact: true })).toBeVisible();
  await page.keyboard.press('Escape'); await expect(row).toBeFocused(); await row.click();
  await page.getByRole('button', { name: '자기소개 입력하고 참여 신청' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.home-profile .recruitment-composer-shell')).toBeVisible();
  await expect(page.locator('.recruitment-composer-shell')).toContainText('실시간 모집');
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.board-proposal')).toBeVisible();
  await expect(page.locator('.board-proposal')).toBeInViewport();
  await expect(page.locator('.match-progress [aria-current="step"]')).toContainText('전원 수락');
});
test('제안을 거절하면 파티가 생기지 않고 같은 모집의 타이머로 복귀한다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await page.getByRole('button', { name: 'PlayMaker 모집 상세' }).click();
  await page.getByRole('button', { name: '이 모집에 참여 신청' }).click();
  await expect(page.locator('.board-proposal')).toBeVisible();
  await page.getByRole('button', { name: '거절', exact: true }).click();
  await expect(page.locator('.board-proposal')).toHaveCount(0);
  await expect(page.locator('.compact-party')).toHaveCount(0);
  await expect(page.getByRole('timer', { name: '모집 시작 후', exact: true })).toBeVisible();
  await expect(page.locator('.my-recruitment')).toContainText('모집 중');
});
test('수락하지 않고 제한 시간이 지나면 대기로 돌아가 다시 행동할 수 있다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.getByRole('button', { name: 'PlayMaker 모집 상세' }).click();
  await page.getByRole('button', { name: '이 모집에 참여 신청' }).click();
  await expect(page.locator('.board-proposal')).toBeVisible();
  const remaining = Number(await page.getByRole('timer', { name: '수락 응답 남은 초' }).innerText());
  expect(remaining).toBeGreaterThan(0);
  await page.clock.fastForward((remaining + 4) * 1000);
  await expect(page.locator('.board-proposal')).toHaveCount(0);
  await expect(page.locator('.compact-party')).toHaveCount(0);
  await manageRecruitment(page, '조건 수정');
  await expect(page.locator('.recruitment-composer-shell').getByRole('button', { name: '모집 조건 저장', exact: true })).toBeEnabled();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('timer', { name: '모집 시작 후', exact: true })).toBeVisible();
});
test('예약은 시작까지 남은 시간을 표시하고 시간이 되어도 음수로 내려가지 않는다', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-14T18:00:00+09:00') });
  await login(page); await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await page.getByRole('button', { name: '예약하기' }).click();
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  const countdown = page.getByRole('timer', { name: '예약 시작까지' });
  await expect(countdown).toBeVisible(); const before = seconds(await countdown.innerText());
  await page.clock.fastForward(61_000);
  await expect.poll(async () => seconds(await countdown.innerText())).toBeLessThanOrEqual(before - 60);
  await page.clock.fastForward(before * 1000);
  await expect(page.getByRole('timer', { name: '예약 시작 후' })).toBeVisible();
  await expect(page.getByRole('timer', { name: '예약 시작 후' })).not.toContainText('-');
});

test('목록 탐색 중에도 오른쪽 모집 타이머와 관리 버튼이 유지된다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.clock.fastForward(12_000);
  await page.getByRole('button', { name: /^모집 둘러보기/ }).click();
  await expect(page.locator('.recruitment-summary')).toHaveCount(0);
  await expect(page.locator('.home-profile .my-recruitment')).toBeVisible();
  await page.locator('.recruitment-row').last().scrollIntoViewIfNeeded();
  await expect(page.getByRole('timer', { name: '모집 시작 후', exact: true })).toBeInViewport();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  await expect.poll(async () => seconds(await page.getByRole('timer', { name: '모집 시작 후', exact: true }).innerText())).toBeGreaterThanOrEqual(12);
});

test('360px 화면에서도 대기 타이머·모집 상세·수락 화면을 조작할 수 있다', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 780 });
  await login(page); await startRealtimeMatch(page);
  await expect(page.getByRole('timer', { name: '모집 시작 후', exact: true })).toBeInViewport();
  const overflow = () => page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
  expect(await overflow()).toBe(false);
  await page.getByRole('button', { name: /^모집 둘러보기/ }).click();
  await expect(page.locator('.recruitment-summary')).toHaveCount(0);
  await page.getByRole('button', { name: 'PlayMaker 모집 상세' }).click();
  await expect(page.getByRole('region', { name: '모집 상세', exact: true })).toBeInViewport();
  await page.getByRole('button', { name: '이 모집에 참여 신청' }).click();
  await expect(page.locator('.board-proposal')).toBeInViewport();
  await expect(page.getByRole('button', { name: '함께할게요' })).toBeInViewport();
  expect(await overflow()).toBe(false);
  await page.getByRole('button', { name: '함께할게요' }).click();
  await expect(page.locator('.compact-party')).toBeVisible();
  await expect(page.getByRole('button', { name: '게임 준비 완료' })).toBeInViewport();
  expect(await overflow()).toBe(false);
});

test('실시간과 예약을 함께 만들었을 때 탭에 맞는 모집을 관리한다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await page.getByRole('button', { name: '예약하기' }).click();
  await page.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.getByRole('heading', { name: '내 예약 모집', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: '실시간 매치', exact: true }).click();
  await expect(page.getByRole('heading', { name: '내 실시간 모집', exact: true })).toBeVisible();
  await manageRecruitment(page, '잠시 멈춤');
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await expect(page.locator('.my-recruitment .recruitment-title')).toContainText('모집 중');
  await page.getByRole('tab', { name: '실시간 매치', exact: true }).click();
  await expect(page.getByRole('button', { name: '모집 재개', exact: true })).toBeVisible();
});

test('한 시간 이상 지난 모집도 활동 재확인 후 원래 경과 시간을 유지한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.clock.fastForward(61 * 60_000);
  await expect(page.getByRole('timer', { name: '모집 시작 후', exact: true })).toHaveText(/^01:01:/);
  await expect(page.getByRole('button', { name: '계속 모집할게요' })).toBeVisible();
  await page.getByRole('button', { name: '계속 모집할게요' }).click();
  await expect(page.locator('.recruitment-title')).toContainText('모집 중');
  await expect(page.getByRole('timer', { name: '모집 시작 후', exact: true })).toHaveText(/^01:01:/);
});

test('조건 수정 중 도착한 제안은 가려지지 않고 거절하면 작성 내용을 복구한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page, true);
  await manageRecruitment(page, '조건 수정');
  await page.locator('.recruitment-composer-shell').getByLabel('모집 한마디').fill('아직 저장하지 않은 모집 문구');
  await page.clock.fastForward(7000);
  await expect(page.locator('.board-proposal')).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 3000 });
  await expect(page.getByRole('button', { name: '함께할게요' })).toBeInViewport();
  await page.getByRole('button', { name: '거절', exact: true }).click();
  await expect(page.locator('.recruitment-composer-shell').getByLabel('모집 한마디')).toHaveValue('아직 저장하지 않은 모집 문구');
  await page.getByRole('button', { name: '모집 조건 저장', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.my-recruitment')).toContainText('아직 저장하지 않은 모집 문구');
});


test('수정 중 온 제안을 수락하면 이전 수정 창 없이 파티가 열리고 다른 모집도 확인할 수 있다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page, true);
  await manageRecruitment(page, '조건 수정');
  await page.locator('.recruitment-composer-shell').getByLabel('모집 한마디').fill('매칭 전 작성 중');
  await page.clock.fastForward(7000);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: '함께할게요' }).click();
  await page.clock.fastForward(5000);
  await expect(page.locator('.compact-party')).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('textbox', { name: '파티 메시지', exact: true }).fill('대화를 작성하는 중');
  await page.getByRole('button', { name: 'PlayMaker 모집 상세' }).click();
  await expect(page.getByRole('region', { name: '모집 상세', exact: true })).toContainText('현재 파티에 참여 중이에요');
  await expect(page.getByRole('region', { name: '모집 상세', exact: true }).getByRole('button', { name: /참여 신청/ })).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: 'PlayMaker 모집 상세' })).toBeFocused();
  await expect(page.getByRole('textbox', { name: '파티 메시지', exact: true })).toHaveValue('대화를 작성하는 중');
});


test('자기소개 작성 중에도 목록 필터를 쓸 수 있고 모집 선택이 초안을 덮어쓰지 않는다', async ({ page }) => {
  await login(page);
  const listTop = () => page.locator('.board-toolbar').evaluate(element => element.getBoundingClientRect().top + document.documentElement.scrollTop);
  const before = await listTop();
  await page.locator('.intro-launch > button').click();
  const composer = page.locator('.home-profile .recruitment-composer-shell');
  await composer.getByLabel('모집 한마디').fill('저장 전 자기소개');
  await page.locator('.board-filter-bar').getByRole('button', { name: '랭크', exact: true }).click();
  await page.getByRole('button', { name: 'PlayMaker 모집 상세' }).click();
  await expect(composer.getByLabel('모집 한마디')).toHaveValue('저장 전 자기소개');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('region', { name: '모집 상세', exact: true })).toHaveCount(0);
  await composer.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(page.locator('.home-profile .my-recruitment')).toContainText('저장 전 자기소개');
  expect(await listTop()).toBeCloseTo(before, 0);
});
