import { expect, test } from '@playwright/test';
import { login, selectBoardFilter } from './helpers';
test.use({ timezoneId: 'Asia/Seoul' });

test('상대 티어 하나를 고르면 바로 필터링되고 전체 티어로 돌아갈 수 있다', async ({ page }) => {
  await login(page);
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const initialCount = await page.locator('.recruitment-row').count();
  await expect(page.getByRole('button', { name: '초기화', exact: true })).toHaveCount(0);
  await expect(page.getByLabel('상대 최소 티어')).toHaveCount(0);
  await expect(page.getByLabel('상대 최대 티어')).toHaveCount(0);
  await selectBoardFilter(page, '찾는 상대 티어', '골드');
  await expect.poll(async () => {
    const tiers = await page.locator('.recruitment-row .row-tier').allTextContents();
    return tiers.length > 0 && tiers.every(tier => tier.includes('골드'));
  }).toBe(true);
  await selectBoardFilter(page, '찾는 상대 티어', '다이아몬드');
  await expect(page.locator('.recruitment-row')).toHaveCount(0);
  await expect(page.locator('.board-empty')).toContainText('조건에 맞는 모집이 없어요');
  await page.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(page.getByRole('button', { name: '찾는 상대 티어', exact: true })).toContainText('모든 티어');
  await expect(page.getByRole('button', { name: '초기화', exact: true })).toHaveCount(0);
  await expect(page.locator('.recruitment-row')).toHaveCount(initialCount);
});

test('포지션 아이콘은 재선택으로 해제되고 큐·음성 조건과 함께 목록을 좁힌다', async ({ page }) => {
  await login(page);
  const filters = page.locator('.board-filter-bar');
  const roles = filters.getByRole('group', { name: '찾는 상대 포지션', exact: true });
  const modes = filters.getByRole('group', { name: '찾는 큐 타입', exact: true });
  const voice = filters.getByRole('switch', { name: '음성 사용 모집만 보기', exact: true });
  const rows = page.locator('.recruitment-row');
  await expect(rows).toHaveCount(10);
  await expect(roles.getByRole('button')).toHaveCount(5);
  await expect(roles.getByRole('button', { pressed: true })).toHaveCount(0);
  await expect(modes.getByRole('button', { name: '전체', exact: true })).toHaveAttribute('aria-pressed', 'true');

  await roles.getByRole('button', { name: '미드', exact: true }).click();
  await expect(roles.getByRole('button', { name: '미드', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(rows).toHaveCount(6);
  await modes.getByRole('button', { name: '칼바람 나락', exact: true }).click();
  await expect(modes.getByRole('button', { name: '전체', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await expect(modes.getByRole('button', { name: '칼바람 나락', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(rows).toHaveCount(2);
  await expect(rows.locator('.row-player b')).toHaveText(['SupportLife · 칼바람', 'SilentJungle · 칼바람']);
  await voice.click();
  await expect(voice).toHaveAttribute('aria-checked', 'true');
  await expect(rows).toHaveCount(0);
  await expect(page.locator('.board-empty')).toContainText('조건에 맞는 모집이 없어요');

  await roles.getByRole('button', { name: '미드', exact: true }).click();
  await expect(roles.getByRole('button', { pressed: true })).toHaveCount(0);
  await expect(modes.getByRole('button', { name: '칼바람 나락', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(rows).toHaveCount(3);
  await expect(rows.locator('.row-player b')).toHaveText(['PlayMaker · 칼바람', 'AimKing · 칼바람', 'HealingYou · 칼바람']);
  await filters.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(modes.getByRole('button', { name: '전체', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(roles.getByRole('button', { pressed: true })).toHaveCount(0);
  await expect(voice).toHaveAttribute('aria-checked', 'false');
  await expect(voice).toHaveAttribute('title', '음성 필터 꺼짐');
  await expect(rows).toHaveCount(10);
});

test('티어 팝업은 키보드로 선택하고 Escape와 바깥 클릭으로 닫을 수 있다', async ({ page }) => {
  await login(page);
  const tier = page.getByRole('button', { name: '찾는 상대 티어', exact: true });
  const tiers = page.getByRole('listbox', { name: '찾는 상대 티어', exact: true });
  await expect(tier).toHaveAttribute('aria-haspopup', 'listbox');
  await expect(tier).toHaveAttribute('aria-expanded', 'false');
  await tier.focus();
  await page.keyboard.press('Enter');
  await expect(tier).toHaveAttribute('aria-expanded', 'true');
  await expect(tiers.getByRole('option', { name: '모든 티어', exact: true })).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await expect(tiers.getByRole('option', { name: '아이언', exact: true })).toBeFocused();
  await page.keyboard.press('End');
  await expect(tiers.getByRole('option', { name: '챌린저', exact: true })).toBeFocused();
  await page.keyboard.press('ArrowUp');
  await expect(tiers.getByRole('option', { name: '그랜드마스터', exact: true })).toBeFocused();
  await page.keyboard.press('Home');
  await expect(tiers.getByRole('option', { name: '모든 티어', exact: true })).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(tiers).toHaveCount(0);
  await expect(tier).toBeFocused();
  await expect(tier).toContainText('아이언');
  await expect(page.locator('.board-empty')).toContainText('조건에 맞는 모집이 없어요');

  await tier.click();
  await expect(tiers.getByRole('option', { name: '아이언', exact: true })).toBeFocused();
  await expect(tiers.getByRole('option', { name: '아이언', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Escape');
  await expect(tiers).toHaveCount(0);
  await expect(tier).toHaveAttribute('aria-expanded', 'false');
  await expect(tier).toBeFocused();
  await expect(tier).toContainText('아이언');

  await tier.click();
  await page.keyboard.press('End');
  await page.keyboard.press('Enter');
  await tier.click();
  const challenger = tiers.getByRole('option', { name: '챌린저', exact: true });
  await expect(challenger).toBeFocused();
  await expect(challenger).toHaveAttribute('aria-selected', 'true');
  const selectedBox = (await challenger.boundingBox())!;
  const listBox = (await tiers.boundingBox())!;
  expect(selectedBox.y).toBeGreaterThanOrEqual(listBox.y);
  expect(selectedBox.y + selectedBox.height).toBeLessThanOrEqual(listBox.y + listBox.height);
  await page.keyboard.press('Tab');
  await expect(tiers).toHaveCount(0);
  await expect(page.locator('.board-filter-bar').getByRole('group', { name: '찾는 큐 타입', exact: true }).getByRole('button', { name: '전체', exact: true })).toBeFocused();
  await expect(tier).toContainText('챌린저');
  await tier.click();
  await expect(tiers).toBeVisible();
  await page.getByRole('tab', { name: '실시간 매치', exact: true }).click();
  await expect(tiers).toHaveCount(0);
  await expect(tier).toContainText('챌린저');
});

test('마이크 아이콘은 클릭·Enter·Space로 바로 켜고 끄며 초기화하면 음성 필터도 꺼진다', async ({ page }) => {
  await login(page);
  const filters = page.locator('.board-filter-bar');
  const voice = filters.getByRole('switch', { name: '음성 사용 모집만 보기', exact: true });
  const rows = page.locator('.recruitment-row');
  const count = page.locator('.board-results-head');
  await expect(rows).toHaveCount(10);
  await expect(voice).toHaveText('');
  await expect(voice.locator('svg')).toHaveCount(1);
  await expect(voice).toHaveAttribute('aria-checked', 'false');
  await expect(voice).toHaveAttribute('title', '음성 필터 꺼짐');

  await voice.click();
  await expect(voice).toHaveAttribute('aria-checked', 'true');
  await expect(voice).toHaveAttribute('title', '음성 필터 켜짐');
  await expect(page.getByRole('listbox')).toHaveCount(0);
  await expect(rows).toHaveCount(9);
  await expect(count).toContainText('9개 모집');
  await expect(rows.locator('.row-fresh > span:first-child')).toHaveText(Array(9).fill('음성 사용'));
  await voice.click();
  await expect(voice).toHaveAttribute('aria-checked', 'false');
  await expect(count).toContainText('30개 모집');
  await expect(rows).toHaveCount(10);

  await voice.focus();
  await page.keyboard.press('Enter');
  await expect(voice).toHaveAttribute('aria-checked', 'true');
  await expect(voice).toBeFocused();
  await expect(rows).toHaveCount(9);
  await page.keyboard.press('Space');
  await expect(voice).toHaveAttribute('aria-checked', 'false');
  await expect(voice).toBeFocused();
  await expect(rows).toHaveCount(10);
  await expect(count).toContainText('30개 모집');
  await expect(page.getByRole('listbox')).toHaveCount(0);

  await page.keyboard.press('Space');
  await expect(voice).toHaveAttribute('aria-checked', 'true');
  await expect(rows).toHaveCount(9);
  await filters.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(voice).toHaveAttribute('aria-checked', 'false');
  await expect(voice).toHaveAttribute('title', '음성 필터 꺼짐');
  await expect(voice).toHaveText('');
  await expect(rows).toHaveCount(10);
  await expect(count).toContainText('30개 모집');
  await expect(filters.getByRole('button', { name: '초기화', exact: true })).toHaveCount(0);
});

test('예약 필터의 잘못된 시간을 고쳐야 목록에 적용된다', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-14T18:00:00+09:00') });
  await login(page);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const before = await page.locator('.recruitment-row').evaluateAll(rows => rows.map(row => row.getAttribute('data-recruitment-id')));
  await page.getByLabel('검색 시작 시각').fill('2026-10-01T22:00');
  await page.getByLabel('검색 종료 시각').fill('2026-10-01T21:00');
  await expect(page.locator('.board-filter-bar').getByRole('alert')).toContainText('종료 시간이 시작 시간보다 늦어야 합니다');
  expect(await page.locator('.recruitment-row').evaluateAll(rows => rows.map(row => row.getAttribute('data-recruitment-id')))).toEqual(before);
  await page.getByLabel('검색 종료 시각').fill('');
  await expect(page.locator('.board-filter-bar').getByRole('alert')).toContainText('예약 시간을 선택');
  expect(await page.locator('.recruitment-row').evaluateAll(rows => rows.map(row => row.getAttribute('data-recruitment-id')))).toEqual(before);
  await page.getByLabel('검색 종료 시각').fill('2026-10-01T23:00');
  await expect(page.locator('.board-filter-bar').getByRole('alert')).toHaveCount(0);
  await expect(page.locator('.recruitment-row')).toHaveCount(0);
  await expect(page.getByLabel('검색 시작 시각')).toHaveValue('2026-10-01T22:00');
  await expect(page.getByLabel('검색 종료 시각')).toHaveValue('2026-10-01T23:00');
});

test('예약 작성 중 시작 시각이 지나면 별도 입력 없이 저장을 막고 시간을 바꿀 수 있다', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-14T18:29:50+09:00') });
  await login(page);
  await page.getByRole('tab', { name: '예약 매치', exact: true }).click();
  await page.locator('.intro-launch').getByRole('button', { name: '예약하기' }).click();
  await page.getByLabel('시작 가능 시각').fill('2026-09-14T18:30');
  await page.getByLabel('마지막 종료 시각').fill('2026-09-14T20:00');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeEnabled();
  await page.clock.fastForward(15_000);
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('시작 시각은 현재 이후여야 합니다');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeDisabled();
  await page.getByLabel('시작 가능 시각').fill('2026-09-14T19:00');
  await expect(page.getByRole('button', { name: '모집 시작', exact: true })).toBeEnabled();
});
