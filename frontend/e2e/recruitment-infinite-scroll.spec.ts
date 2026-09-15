import { expect, test, type Page } from '@playwright/test';
import type { BoardPage, BoardRow, BoardSearch } from '../src/api/recruitment';
import { DEMO, login, selectBoardFilter } from './helpers';

interface SearchCall { query: BoardSearch; ids: string[]; settled: boolean }
interface SearchControls {
  calls: SearchCall[];
  hold: { page: number; tier?: string | null } | null;
  held: { release: () => void; call: SearchCall }[];
  failPage: number | null;
  duplicateBoundary: boolean;
  previousLast: BoardRow | null;
}
declare global {
  interface Window {
    boardSearchControls: SearchControls;
    controlledBoardSearch: (query: BoardSearch, search: () => Promise<BoardPage>) => Promise<BoardPage>;
  }
}

const rowIds = (page: Page) => page.locator('.recruitment-row').evaluateAll(rows => rows.map(row => row.getAttribute('data-recruitment-id')!));
const calls = (page: Page, index: number) => page.evaluate(index => window.boardSearchControls.calls.filter(call => call.query.page === index).length, index);
const releaseSearch = (page: Page) => page.evaluate(() => {
  window.boardSearchControls.hold = null;
  window.boardSearchControls.held.splice(0).forEach(entry => entry.release());
});

test.beforeEach(async ({ page }) => {
  // 제품 코드에 테스트 전용 API를 추가하지 않고 브라우저 mock 응답의 순서와 실패만 제어한다.
  await page.addInitScript(() => {
    const controls: SearchControls = { calls: [], hold: null, held: [], failPage: null, duplicateBoundary: false, previousLast: null };
    window.boardSearchControls = controls;
    window.controlledBoardSearch = async (query, search) => {
      const call: SearchCall = { query: structuredClone(query), ids: [], settled: false };
      controls.calls.push(call);
      const hold = controls.hold;
      const shouldHold = hold?.page === query.page && (hold.tier === undefined || hold.tier === query.preferences.minTier);
      const result = await search();
      call.ids = result.items.map(row => row.id);
      if (controls.failPage === query.page) {
        call.settled = true;
        throw new Error('추가 목록 조회 실패');
      }
      if (controls.duplicateBoundary && query.page === 1 && controls.previousLast) result.items.unshift(controls.previousLast);
      if (query.page === 0) controls.previousLast = result.items.at(-1) ?? null;
      if (shouldHold) await new Promise<void>(resolve => controls.held.push({ release: resolve, call }));
      call.settled = true;
      return result;
    };
  });
  await page.route(/\/src\/api\/recruitment\.ts(?:\?.*)?$/, async route => {
    const response = await route.fetch();
    const source = await response.text();
    expect(source).toContain('export const searchBoard =');
    const body = source.replace('export const searchBoard =', 'const originalSearchBoard =')
      + '\nexport const searchBoard = query => window.controlledBoardSearch(query, () => originalSearchBoard(query));\n';
    await route.fulfill({ response, body });
  });
});

test('아래로 스크롤하면 순서와 읽던 위치를 유지하며 매칭을 중복 없이 이어 붙인다', async ({ page }) => {
  await login(page);
  const rows = page.locator('.recruitment-row');
  await expect(rows).toHaveCount(10);
  const first = await rowIds(page);
  await expect(page.locator('.board-results-head')).toContainText('40개 매칭 글');
  await expect(page.getByText('최신순', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^(이전|다음)$/ })).toHaveCount(0);
  await page.evaluate(() => {
    window.boardSearchControls.hold = { page: 1 };
    window.boardSearchControls.duplicateBoundary = true;
  });
  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  await expect.poll(() => page.evaluate(() => window.boardSearchControls.held.length)).toBeGreaterThan(0);
  const anchor = rows.nth(9);
  const before = (await anchor.boundingBox())!.y;
  await expect(rows).toHaveCount(10);
  await releaseSearch(page);
  await expect(rows).toHaveCount(20);
  const second = await rowIds(page);
  expect(second.slice(0, 10)).toEqual(first);
  expect(new Set(second).size).toBe(20);
  expect(Math.abs((await anchor.boundingBox())!.y - before)).toBeLessThanOrEqual(2);
  expect(await calls(page, 1)).toBe(1);

  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  await expect(rows).toHaveCount(30);
  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  await expect(rows).toHaveCount(40);
  const complete = await rowIds(page);
  expect(complete.slice(0, 20)).toEqual(second);
  expect(new Set(complete).size).toBe(40);
  await expect(page.locator('.board-load-more')).toHaveCount(0);
  const finalRequests = await calls(page, 3);
  await rows.first().scrollIntoViewIfNeeded();
  await rows.last().scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);
  expect(await calls(page, 3)).toBe(finalRequests);
  expect(await calls(page, 4)).toBe(0);
});

test('첫 조회에는 빈 숫자를 표시하지 않고 필터 계산 중에는 기존 숫자와 목록을 유지한다', async ({ page }) => {
  await page.goto('/login');
  await page.evaluate(() => { window.boardSearchControls.hold = { page: 0 }; });
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
  await page.getByPlaceholder('비밀번호를 입력하세요').fill(DEMO.password);
  await page.locator('.auth-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/home/);
  await expect.poll(() => page.evaluate(() => window.boardSearchControls.held.length)).toBeGreaterThan(0);
  await expect(page.locator('.board-results-head')).not.toContainText(/[—-]개\s*매칭/);
  await releaseSearch(page);
  await expect(page.locator('.recruitment-row')).toHaveCount(10);
  await expect(page.locator('.board-results-head')).toContainText('40개 매칭 글');
  const original = await rowIds(page);
  await page.evaluate(() => { window.boardSearchControls.hold = { page: 0, tier: 'GOLD' }; });
  await selectBoardFilter(page, '찾는 상대 티어', '골드');
  await expect.poll(() => page.evaluate(() => window.boardSearchControls.held.length)).toBeGreaterThan(0);
  await expect(page.locator('.board-results-head')).toContainText('40개 매칭 글');
  expect(await rowIds(page)).toEqual(original);
  await releaseSearch(page);
  await expect(page.locator('.board-results-head')).toContainText('12개 매칭 글');
  await expect(page.locator('.recruitment-row')).toHaveCount(10);
  await expect(page.locator('.recruitment-row .row-tier')).toHaveText(Array(10).fill(/골드/));
});

test('새 필터 조회가 실패하면 이전 행 선택을 막고 재시도나 초기화 성공 후 다시 선택할 수 있다', async ({ page }) => {
  await login(page);
  const rows = page.locator('.recruitment-row');
  const count = page.locator('.board-results-head');
  const error = page.getByRole('alert').filter({ hasText: '매칭 정보를 불러오지 못했습니다' });
  const detail = page.getByRole('region', { name: '매칭 글 상세', exact: true });
  await expect(rows).toHaveCount(10);
  const original = await rowIds(page);

  await page.evaluate(() => { window.boardSearchControls.failPage = 0; });
  await selectBoardFilter(page, '찾는 상대 티어', '골드');
  await expect(error).toBeVisible();
  await expect(count).toContainText('40개 매칭 글');
  expect(await rowIds(page)).toEqual(original);
  await rows.first().focus();
  await page.keyboard.press('Enter');
  await expect(detail).toHaveCount(0);

  await page.evaluate(() => { window.boardSearchControls.failPage = null; });
  await error.getByRole('button', { name: '다시 불러오기', exact: true }).click();
  await expect(error).toHaveCount(0);
  await expect(rows).toHaveCount(10);
  await expect(count).toContainText('12개 매칭 글');
  await expect(rows.locator('.row-tier')).toHaveText(Array(10).fill(/골드/));
  await rows.first().focus();
  await page.keyboard.press('Enter');
  await expect(detail).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(detail).toHaveCount(0);

  const gold = await rowIds(page);
  await page.evaluate(() => { window.boardSearchControls.failPage = 0; });
  await selectBoardFilter(page, '찾는 상대 티어', '실버');
  await expect(error).toBeVisible();
  await expect(count).toContainText('12개 매칭 글');
  expect(await rowIds(page)).toEqual(gold);
  await rows.first().focus();
  await page.keyboard.press('Enter');
  await expect(detail).toHaveCount(0);

  await page.evaluate(() => { window.boardSearchControls.failPage = null; });
  await page.locator('.board-filter-bar').getByRole('button', { name: '초기화', exact: true }).click();
  await expect(error).toHaveCount(0);
  await expect(rows).toHaveCount(10);
  await expect(count).toContainText('40개 매칭 글');
  expect(await rowIds(page)).toEqual(original);
  await rows.first().focus();
  await page.keyboard.press('Enter');
  await expect(detail).toBeVisible();
});

test('여러 번 내려 읽은 뒤 필터를 바꾸면 첫 묶음부터 다시 조회한다', async ({ page }) => {
  await login(page);
  const rows = page.locator('.recruitment-row');
  await expect(rows).toHaveCount(10);
  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  await expect(rows).toHaveCount(20);
  await selectBoardFilter(page, '찾는 상대 티어', '실버');
  await expect(page.locator('.board-results-head')).toContainText('16개 매칭 글');
  await expect(rows).toHaveCount(10);
  expect((await page.evaluate(() => window.boardSearchControls.calls.filter(call => call.query.preferences.minTier === 'SILVER').map(call => call.query.page)))).toEqual([0]);
  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  await expect(rows).toHaveCount(16);
  await expect(rows.locator('.row-tier')).toHaveText(Array(16).fill(/실버/));
  await expect(page.locator('.board-load-more')).toHaveCount(0);
  await page.locator('.board-filter-bar').getByRole('button', { name: '초기화', exact: true }).click();
  await expect(rows).toHaveCount(10);
  await expect(page.locator('.board-results-head')).toContainText('40개 매칭 글');
});

test('이전 필터의 늦은 추가 응답은 새 필터 목록에 섞이지 않는다', async ({ page }) => {
  await login(page);
  const rows = page.locator('.recruitment-row');
  await expect(rows).toHaveCount(10);
  await page.evaluate(() => { window.boardSearchControls.hold = { page: 1, tier: null }; });
  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  await expect.poll(() => page.evaluate(() => window.boardSearchControls.held.length)).toBe(1);
  await selectBoardFilter(page, '찾는 상대 티어', '골드');
  await expect(page.locator('.board-results-head')).toContainText('12개 매칭 글');
  await expect(rows).toHaveCount(10);
  const filtered = await rowIds(page);
  await releaseSearch(page);
  await expect.poll(() => page.evaluate(() => window.boardSearchControls.calls.every(call => call.settled))).toBe(true);
  await expect(rows).toHaveCount(10);
  expect(await rowIds(page)).toEqual(filtered);
  await expect(page.locator('.board-results-head')).toContainText('12개 매칭 글');
  await expect(page.locator('.board-load-more')).toHaveCount(1);
});

test('추가 조회가 실패해도 읽던 목록을 유지하고 사용자가 다시 불러올 때 재시도한다', async ({ page }) => {
  await login(page);
  const rows = page.locator('.recruitment-row');
  await expect(rows).toHaveCount(10);
  const original = await rowIds(page);
  await page.evaluate(() => { window.boardSearchControls.failPage = 1; });
  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  const retry = page.getByRole('button', { name: '다시 불러오기', exact: true });
  await expect(retry).toBeVisible();
  await expect(rows).toHaveCount(10);
  expect(await rowIds(page)).toEqual(original);
  await expect(page.locator('.board-results-head')).toContainText('40개 매칭 글');
  await rows.first().scrollIntoViewIfNeeded();
  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  await page.waitForTimeout(800);
  expect(await calls(page, 1)).toBe(1);
  await page.evaluate(() => { window.boardSearchControls.failPage = null; });
  await retry.click();
  await expect(rows).toHaveCount(20);
  expect((await rowIds(page)).slice(0, 10)).toEqual(original);
  expect(await calls(page, 1)).toBe(2);
  await expect(retry).toHaveCount(0);
});

test('실시간 갱신과 새 목록 반영은 이미 읽은 목록의 길이를 첫 묶음으로 줄이지 않는다', async ({ page }) => {
  await login(page);
  const rows = page.locator('.recruitment-row');
  await expect(rows).toHaveCount(10);
  await page.locator('.board-load-more').scrollIntoViewIfNeeded();
  await expect(rows).toHaveCount(20);
  const previous = await rowIds(page);
  const newId = await page.evaluate(async () => {
    // Vite의 ?t 버전까지 일치시켜 앱과 같은 mock rows Map을 사용한다.
    // 버전 없는 경로를 다시 import하면 별도 모듈에만 매칭이 생성될 수 있다.
    const loadedModule = (path: string) => {
      const resource = performance.getEntriesByType('resource').find(entry => new URL(entry.name).pathname === path);
      if (!resource) throw new Error(`앱이 로드한 모듈을 찾을 수 없습니다: ${path}`);
      return resource.name;
    };
    const { handleBoardMock } = await import(/* @vite-ignore */ loadedModule('/src/mocks/recruitment.ts'));
    const { db } = await import(/* @vite-ignore */ loadedModule('/src/mocks/db.ts'));
    const { anyPreferences } = await import(/* @vite-ignore */ loadedModule('/src/domain/recruitment.ts'));
    const viewer = db.me;
    db.me = { id: 'infinite-scroll-host', nickname: '새 매칭 방장', avatarUrl: null };
    try {
      const row = handleBoardMock('POST', '/recruitments', {
        type: 'REALTIME', condition: { game: 'LOL', modeKey: 'SOLO_DUO_RANKED', keyCondition: { type: 'POSITION', value: 'TOP' }, voicePreference: 'OPTIONAL', playPurpose: 'FUN' },
        preferences: anyPreferences(), description: '새로 등록한 매칭', autoMatch: false, availableFrom: null, availableTo: null, playAmount: null,
      }, () => ({ id: crypto.randomUUID() }), () => {}) as BoardRow;
      return row.id;
    } finally { db.me = viewer; }
  });
  await expect(rows.first()).toHaveAttribute('data-recruitment-id', newId);
  await expect(page.locator('.board-new-results')).toHaveCount(0);
  await expect(rows).toHaveCount(20);
  expect(await rowIds(page)).toEqual([newId, ...previous.slice(0, 19)]);
  await expect(page.locator('.board-results-head')).toContainText('41개 매칭 글');
});
