import { expect, test } from '@playwright/test';
import { manageRecruitment, login, startRealtimeMatch } from './helpers';
const seconds = (value: string) => value.split(':').reduce((total, part) => total * 60 + Number(part), 0);

test('매칭 타이머는 오른쪽 바에 보이고 목록 위치를 바꾸지 않는다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  const timer = page.getByRole('timer', { name: '매칭 시작 후', exact: true });
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
  await expect(page.locator('.match-progress')).toHaveCount(0);
});
test('조건 수정·멈춤·재개·끌어올림과 메뉴 이동이 경과 시간을 초기화하지 않는다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  const timer = page.getByRole('timer', { name: '매칭 시작 후', exact: true });
  await page.clock.fastForward(65_000);
  await manageRecruitment(page, '잠시 멈춤');
  await expect(page.locator('.recruitment-title')).toContainText('잠시 멈춤');
  await page.clock.fastForward(10_000);
  await page.getByRole('button', { name: '매칭 재개', exact: true }).click();
  await manageRecruitment(page, '조건 수정');
  await page.locator('.recruitment-composer-shell').getByLabel('한마디').fill('타이머 유지 확인');
  await page.getByRole('button', { name: '매칭 조건 저장', exact: true }).click();
  await expect.poll(async () => seconds(await timer.innerText())).toBeGreaterThanOrEqual(75);
  await page.locator('.side-nav a[href="/app/messages"]').click();
  await page.clock.fastForward(10_000);
  await page.locator('.side-nav a[href="/app/home"]').click();
  await expect.poll(async () => seconds(await timer.innerText())).toBeGreaterThanOrEqual(85);
  await page.clock.fastForward(5 * 60_000);
  await page.getByRole('button', { name: '위로 올리기', exact: true }).click();
  await expect.poll(async () => seconds(await timer.innerText())).toBeGreaterThanOrEqual(385);
});
test('매칭 글 상세와 참여 신청은 오른쪽에서 이어지고 닫으면 선택한 행으로 포커스가 돌아간다', async ({ page }) => {
  await login(page);
  const row = page.getByRole('button', { name: 'PlayMaker 매칭 글 상세' });
  await row.click();
  await expect(page.getByRole('region', { name: '매칭 글 상세', exact: true })).toBeVisible();
  await page.keyboard.press('Escape'); await expect(row).toBeFocused(); await row.click();
  await page.getByRole('button', { name: '자기소개 작성하고 오케이 보내기' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.home-profile .recruitment-composer-shell')).toBeVisible();
  await expect(page.locator('.recruitment-composer-shell')).toContainText('실시간 매칭');
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await expect(page.getByRole('timer', { name: '매칭 시작 후', exact: true })).toBeVisible();
});
test('오케이를 취소해도 같은 매칭과 타이머를 유지한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.getByRole('button', { name: 'PlayMaker 매칭 글 상세' }).click();
  await page.getByRole('button', { name: '같이 할래요' }).click();
  await page.getByRole('button', { name: 'PlayMaker 오케이 취소' }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toHaveCount(0);
  await expect(page.getByRole('timer', { name: '매칭 시작 후', exact: true })).toBeVisible();
  await expect(page.locator('.my-recruitment')).toContainText('매칭 중');
});

test('추천에 답하지 않아도 매칭을 잠그지 않고 조건을 수정할 수 있다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page, true);
  await page.clock.fastForward(70_000);
  await expect(page.locator('.duo-offer')).toHaveCount(1);
  await expect(page.locator('.board-proposal')).toHaveCount(0);
  await manageRecruitment(page, '조건 수정');
  await expect(page.getByRole('button', { name: '매칭 조건 저장', exact: true })).toBeEnabled();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('timer', { name: '매칭 시작 후', exact: true })).toBeVisible();
});

test('예약은 시작까지 남은 시간을 표시하고 시간이 되어도 음수로 내려가지 않는다', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-14T18:00:00+09:00') });
  await login(page); await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await page.getByRole('button', { name: '예약하기' }).click();
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  const countdown = page.getByRole('timer', { name: '예약 시작까지' });
  await expect(countdown).toBeVisible(); const before = seconds(await countdown.innerText());
  await page.clock.fastForward(61_000);
  await expect.poll(async () => seconds(await countdown.innerText())).toBeLessThanOrEqual(before - 60);
  await page.clock.fastForward(before * 1000);
  await expect(page.getByRole('timer', { name: '예약 시작 후' })).toBeVisible();
  await expect(page.getByRole('timer', { name: '예약 시작 후' })).not.toContainText('-');
});

test('목록 탐색 중에도 오른쪽 매칭 타이머와 관리 버튼이 유지된다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.clock.fastForward(12_000);
  await page.getByRole('button', { name: /^매칭 둘러보기/ }).click();
  await expect(page.locator('.recruitment-summary')).toHaveCount(0);
  await expect(page.locator('.home-profile .my-recruitment')).toBeVisible();
  await page.locator('.recruitment-row').last().scrollIntoViewIfNeeded();
  await expect(page.getByRole('timer', { name: '매칭 시작 후', exact: true })).toBeInViewport();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  await expect.poll(async () => seconds(await page.getByRole('timer', { name: '매칭 시작 후', exact: true }).innerText())).toBeGreaterThanOrEqual(12);
});

test('360px 화면에서도 타이머·상세·오케이 발송을 조작할 수 있다', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 780 });
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await expect(page.getByRole('timer', { name: '매칭 시작 후', exact: true })).toBeInViewport();
  await page.getByRole('button', { name: 'PlayMaker 매칭 글 상세' }).click();
  await expect(page.getByRole('region', { name: '매칭 글 상세', exact: true })).toBeInViewport();
  await page.getByRole('button', { name: '같이 할래요' }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.clock.fastForward(22_000);
  await expect(page.getByRole('button', { name: '메시지로 이동' })).toBeVisible();
});

test('실시간과 예약을 함께 만들었을 때 탭에 맞는 매칭을 관리한다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await page.getByRole('button', { name: '예약하기' }).click();
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.getByRole('heading', { name: '내 예약 매칭', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: '실시간 매칭', exact: true }).click();
  await expect(page.getByRole('heading', { name: '내 실시간 매칭', exact: true })).toBeVisible();
  await manageRecruitment(page, '잠시 멈춤');
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await expect(page.locator('.my-recruitment .recruitment-title')).toContainText('매칭 중');
  await page.getByRole('tab', { name: '실시간 매칭', exact: true }).click();
  await expect(page.getByRole('button', { name: '매칭 재개', exact: true })).toBeVisible();
});

test('한 시간 이상 지난 매칭도 활동 재확인 후 원래 경과 시간을 유지한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.clock.fastForward(61 * 60_000);
  await expect(page.getByRole('timer', { name: '매칭 시작 후', exact: true })).toHaveText(/^01:01:/);
  await expect(page.getByRole('button', { name: '계속 매칭할게요' })).toBeVisible();
  await page.getByRole('button', { name: '계속 매칭할게요' }).click();
  await expect(page.locator('.recruitment-title')).toContainText('매칭 중');
  await expect(page.getByRole('timer', { name: '매칭 시작 후', exact: true })).toHaveText(/^01:01:/);
});

test('조건 수정 중 새 후보가 생겨도 작성 내용을 덮어쓰지 않는다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page, true);
  await manageRecruitment(page, '조건 수정');
  await page.getByLabel('한마디').fill('아직 저장하지 않은 매칭 문구');
  await page.clock.fastForward(7000);
  await expect(page.getByLabel('한마디')).toHaveValue('아직 저장하지 않은 매칭 문구');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: '매칭 조건 저장', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toContainText('아직 저장하지 않은 매칭 문구');
});

test('조건 수정 중 이전 오케이가 성사되면 종료된 매칭 편집을 닫는다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.getByRole('button', { name: 'PlayMaker 매칭 글 상세' }).click();
  await page.getByRole('button', { name: '같이 할래요' }).click();
  await manageRecruitment(page, '조건 수정');
  await page.getByLabel('한마디').fill('매칭 전 작성 중');
  await page.clock.fastForward(22_000);
  await expect(page.locator('.recruitment-composer-shell')).toHaveCount(0);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/app\/home$/);
});

test('자기소개 작성 중에도 목록 필터를 쓸 수 있고 매칭 선택이 초안을 덮어쓰지 않는다', async ({ page }) => {
  await login(page);
  const listTop = () => page.locator('.board-toolbar').evaluate(element => element.getBoundingClientRect().top + document.documentElement.scrollTop);
  const before = await listTop();
  await page.locator('.intro-launch > button').click();
  const composer = page.locator('.home-profile .recruitment-composer-shell');
  await composer.getByLabel('한마디').fill('저장 전 자기소개');
  await page.locator('.board-filter-bar').getByRole('button', { name: '랭크', exact: true }).click();
  await page.getByRole('button', { name: 'PlayMaker 매칭 글 상세' }).click();
  await expect(composer.getByLabel('한마디')).toHaveValue('저장 전 자기소개');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('region', { name: '매칭 글 상세', exact: true })).toHaveCount(0);
  await composer.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.locator('.home-profile .my-recruitment')).toContainText('저장 전 자기소개');
  expect(await listTop()).toBeCloseTo(before, 0);
});
