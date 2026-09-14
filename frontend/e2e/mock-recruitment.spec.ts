import { expect, test, type Page } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test.use({ timezoneId: 'Asia/Seoul' });
const DAY = 24 * 60 * 60_000;
const startTime = new Date('2026-09-14T18:00:00+09:00');
const seedIds = (page: Page) => page.locator('.recruitment-row').evaluateAll(rows => rows.map(row => row.getAttribute('data-recruitment-id')!).sort());
const readOwn = (page: Page) => page.evaluate(async () => {
  const apiPath = '/src/api/recruitment.ts';
  const api = await import(/* @vite-ignore */ apiPath);
  return (await api.myRecruitments())[0];
});

test('실시간 예시 매칭은 30분과 하루 뒤에도 유지되지만 내 매칭은 활동 확인이 필요하다', async ({ page }) => {
  await page.clock.install({ time: startTime });
  await login(page);
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const before = await seedIds(page);
  await startRealtimeMatch(page);
  const own = await readOwn(page);

  for (const elapsed of [30 * 60_000, DAY]) {
    await page.clock.fastForward(elapsed);
    await expect(page.locator('.recruitment-title')).toContainText('활동 확인 필요');
    await expect(page.locator('.recruitment-row')).toHaveCount(before.length);
    await expect(page.locator('.recruitment-row.unavailable')).toHaveCount(0);
    expect(await seedIds(page)).toEqual(before);
    const current = await readOwn(page);
    expect(current).toMatchObject({ id: own.id, status: 'STALE', createdAt: own.createdAt, confirmedAt: own.confirmedAt });
    await expect(page.getByRole('button', { name: '계속 매칭할게요' })).toBeVisible();
  }
});

test('하루가 지나면 기본 예약 검색 시간과 예시 매칭 시간이 함께 갱신된다', async ({ page }) => {
  await page.clock.install({ time: startTime });
  await login(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
  const before = await seedIds(page);
  const schedule = await page.getByLabel('검색 시작 시각').inputValue();
  await page.clock.fastForward(DAY);
  await expect(page.getByLabel('검색 시작 시각')).not.toHaveValue(schedule);
  await expect(page.locator('.recruitment-row')).toHaveCount(before.length);
  await expect(page.locator('.recruitment-row.unavailable')).toHaveCount(0);
  expect(await seedIds(page)).toEqual(before);
  await expect(page.getByLabel('검색 시작 시각')).toHaveValue(/^2026-09-15T/);
  const seed = await page.evaluate(async id => {
    const apiPath = '/src/api/recruitment.ts';
    const api = await import(/* @vite-ignore */ apiPath);
    const row = await api.getRecruitment(id);
    return { status: row.status, availableFrom: row.availableFrom, future: Date.parse(row.availableFrom) > Date.now() };
  }, before[0]);
  expect(seed.status).toBe('OPEN');
  expect(seed.future).toBe(true);
});

test('직접 선택한 미래 예약 검색 시간과 플레이 양은 예시 갱신으로 덮어쓰지 않는다', async ({ page }) => {
  await page.clock.install({ time: startTime });
  await login(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  const filter = page.locator('.board-filter-bar');
  await filter.getByLabel('검색 시작 시각').fill('2026-09-20T20:00');
  await filter.getByLabel('검색 종료 시각').fill('2026-09-20T22:00');
  await filter.getByLabel('검색 플레이 양').selectOption('TWO_PLUS');
  const schedule = await page.getByLabel('검색 시작 시각').inputValue();
  await page.clock.fastForward(DAY);
  await expect(page.getByLabel('검색 시작 시각')).toHaveValue(schedule);
  await expect(filter.getByLabel('검색 시작 시각')).toHaveValue('2026-09-20T20:00');
  await expect(filter.getByLabel('검색 종료 시각')).toHaveValue('2026-09-20T22:00');
  await expect(filter.getByLabel('검색 플레이 양')).toHaveValue('TWO_PLUS');
  // 예시를 늘리기 위해 실제 시간/플레이 양 필터를 우회하지 않는다.
  await expect(page.locator('.recruitment-row')).toHaveCount(0);
});

test('작성 중인 예약 입력은 시간이 지나도 보존하고 만료된 시간으로 제출하지 않는다', async ({ page }) => {
  await page.clock.install({ time: startTime });
  await login(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  const dialog = page.locator('.recruitment-composer-shell');
  const from = await dialog.getByLabel('시작 가능 시각').inputValue();
  const to = await dialog.getByLabel('마지막 종료 시각').inputValue();
  await dialog.getByLabel('한마디').fill('작성 중인 예약');
  await page.clock.fastForward(DAY);
  await expect(dialog.getByLabel('시작 가능 시각')).toHaveValue(from);
  await expect(dialog.getByLabel('마지막 종료 시각')).toHaveValue(to);
  await expect(dialog.getByLabel('한마디')).toHaveValue('작성 중인 예약');
  await expect(dialog.getByRole('button', { name: '매칭 시작', exact: true })).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
});

test('직접 등록한 예약은 원래 시간에 종료되고 다음 날 예시 매칭으로 바뀌지 않는다', async ({ page }) => {
  await page.clock.install({ time: startTime });
  await login(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  const own = await readOwn(page);
  await page.clock.fastForward(DAY);
  await expect(page.locator('.my-recruitment')).toHaveCount(0);
  const current = await readOwn(page);
  expect(current).toMatchObject({ id: own.id, status: 'CLOSED', availableFrom: own.availableFrom, availableTo: own.availableTo, createdAt: own.createdAt });
  await expect(page.locator('.recruitment-row').first()).toBeVisible();
});
