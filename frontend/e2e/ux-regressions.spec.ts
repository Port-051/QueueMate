import { expect, test } from '@playwright/test';
import { DEMO, login, startRealtimeMatch } from './helpers';

test('파티 준비 변경이 연결을 초기화하지 않고, 종료되면 통신·조작을 막는다', async ({ page }) => {
  await login(page);
  await page.evaluate(async () => {
    const dbPath = '/src/mocks/db.ts';
    const busPath = '/src/mocks/bus.ts';
    const { db } = await import(/* @vite-ignore */ dbPath);
    const { emitMockEvent } = await import(/* @vite-ignore */ busPath);
    db.parties.set('ux-party', { view: {
      id: 'ux-party', game: 'LOL', modeKey: 'SOLO_DUO_RANKED', targetSize: 2, status: 'OPEN', members: [
        { userId: db.me.id, nickname: db.me.nickname, ready: false, gameIds: ['QueueMaster#KR1'] },
        { userId: 'u-peer', nickname: '팀원', ready: false, gameIds: [] },
      ],
    }, condition: { game: 'LOL', modeKey: 'SOLO_DUO_RANKED', keyCondition: { type: 'POSITION', value: 'TOP' }, voicePreference: 'OPTIONAL', playPurpose: 'NORMAL' }, timers: [] });
    emitMockEvent('SESSION_SNAPSHOT', { parties: [{ id: 'ux-party', status: 'OPEN' }] });
  });
  await page.locator('.side-nav a[href="/app/party/ux-party"]').click();
  await expect(page.getByText('QueueMaster#KR1', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '마이크 켜기' }).click();
  await expect(page.getByRole('button', { name: '음소거', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '게임 준비 완료' }).click();
  await expect(page.getByRole('button', { name: '준비 해제' })).toBeVisible();
  await expect(page.getByRole('button', { name: '음소거', exact: true })).toBeEnabled();
  await page.evaluate(async () => {
    const dbPath = '/src/mocks/db.ts'; const busPath = '/src/mocks/bus.ts';
    const { db } = await import(/* @vite-ignore */ dbPath);
    const { emitMockEvent } = await import(/* @vite-ignore */ busPath);
    db.parties.get('ux-party').view.status = 'CLOSED';
    emitMockEvent('PARTY_CLOSED', { partyId: 'ux-party' });
  });
  await expect(page.getByRole('button', { name: '종료된 파티', exact: true })).toBeDisabled();
  await expect(page.getByPlaceholder('메시지를 입력하세요')).toBeDisabled();
  await expect(page.getByRole('button', { name: '보내기' })).toBeDisabled();
  await expect(page.getByRole('button', { name: '나가기', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '연결 다시 시도' })).toHaveCount(0);
});

test('지난 매칭의 조건을 확인하고 조건 입력 바로 아래에서 매칭을 시작할 수 있다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 });
  await login(page);
  const reservationHeading = page.getByRole('heading', { name: '예약 매칭', exact: true });
  expect((await reservationHeading.boundingBox())!.y).toBeLessThan(720);
  await startRealtimeMatch(page);
  await page.getByRole('button', { name: '매칭 취소', exact: true }).click();
  await page.getByRole('button', { name: '조건 보기', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.getByRole('dialog')).toBeVisible();
  const start = page.getByRole('button', { name: '매칭 시작', exact: true });
  await expect(start).toBeEnabled();
  await start.scrollIntoViewIfNeeded();
  const form = (await page.locator('.condition-form').boundingBox())!;
  const action = (await page.locator('.action-bar').boundingBox())!;
  expect(action.y).toBeGreaterThanOrEqual(form.y + form.height);
  expect(action.y - form.y - form.height).toBeLessThan(40);
});

test('친구 요청 탭, 검색 빈 상태, 신고 모달 키보드 포커스', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/friends"]').click();
  await page.getByRole('button', { name: /받은 요청/ }).click();
  await expect(page.getByText('HealingYou', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /친구 목록/ }).click();
  await page.getByPlaceholder('친구 검색').fill('검색되지않는이름');
  await expect(page.getByText('검색 결과가 없습니다', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '검색 지우기' }).click();
  const menu = page.locator('.list-item summary').first();
  await menu.click();
  const report = page.getByRole('button', { name: '신고', exact: true }).first();
  await report.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  for (let i = 0; i < 10; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(menu).toBeFocused();
});

test('홈 팝업에서 매칭 방식을 전환해도 조건과 예약 시간은 유지되고 닫으면 게임 카드로 돌아간다', async ({ page }) => {
  await login(page);
  const tile = page.getByRole('button', { name: 'VALORANT 매칭', exact: true });
  await tile.click();
  const dialog = page.getByRole('dialog', { name: 'VALORANT', exact: true });
  await expect(dialog).toBeVisible();
  await expect(page).toHaveURL(/\/app\/home$/);
  await page.getByRole('button', { name: '사용 안 함', exact: true }).click();
  await page.getByRole('button', { name: '즐겜', exact: true }).click();
  const summary = dialog.getByRole('status', { name: '선택한 매칭 조건' });
  await expect(summary).toContainText('VALORANT · 5인 파티');
  await expect(summary).toContainText('경쟁전 · 타격대 · 음성 사용 안 함 · 즐겜');
  await page.getByRole('button', { name: '전략가', exact: true }).click();
  await expect(summary).toContainText('경쟁전 · 전략가 · 음성 사용 안 함 · 즐겜');
  await expect(summary).not.toContainText('타격대');
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await page.getByRole('button', { name: /내일/ }).click();
  await page.getByRole('combobox', { name: '시작 시간', exact: true }).selectOption('22:00');
  await page.getByRole('combobox', { name: '종료 날짜', exact: true }).selectOption('next');
  await page.getByRole('combobox', { name: '종료 시간', exact: true }).selectOption('00:30');
  await page.getByRole('button', { name: '2판 이상', exact: true }).click();
  await expect(summary).toContainText('22:00');
  await expect(summary).toContainText('00:30');
  await expect(summary).toContainText('2판 이상');
  await page.getByRole('tab', { name: '바로 매칭', exact: true }).click();
  await expect(summary).toContainText('전략가 · 음성 사용 안 함 · 즐겜');
  await expect(summary).not.toContainText('22:00');
  await expect(summary).not.toContainText('2판 이상');
  await expect(page.getByRole('combobox', { name: '시작 시간', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '매칭 시작', exact: true })).toBeEnabled();
  await page.getByRole('tab', { name: '바로 매칭', exact: true }).press('ArrowRight');
  await expect(page.getByRole('tab', { name: '예약 매칭', exact: true })).toBeFocused();
  await expect(summary).toContainText('22:00');
  await expect(summary).toContainText('2판 이상');
  await expect(page.getByRole('combobox', { name: '시작 시간', exact: true })).toHaveValue('22:00');
  await expect(page.getByRole('combobox', { name: '종료 날짜', exact: true })).toHaveValue('next');
  await expect(page.getByRole('combobox', { name: '종료 시간', exact: true })).toHaveValue('00:30');
  await expect(page.getByRole('button', { name: '사용 안 함', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: '즐겜', exact: true })).toHaveAttribute('aria-pressed', 'true');
  for (let i = 0; i < 24; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(tile).toBeFocused();
});

test('기존 매칭과 예약 주소도 홈 위의 팝업으로 연결된다', async ({ page }) => {
  // Mock sessions are in memory, so a full navigation requires signing in again.
  const openBookmark = async (path: string) => {
    await page.goto(path);
    await expect(page).toHaveURL(/\/login$/);
    await page.getByPlaceholder('이메일 주소를 입력하세요').fill(DEMO.email);
    await page.getByPlaceholder('비밀번호를 입력하세요').fill(DEMO.password);
    await page.locator('.auth-form button[type="submit"]').click();
  };
  await openBookmark('/app/match');
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('tab', { name: '바로 매칭', exact: true })).toHaveAttribute('aria-selected', 'true');
  await openBookmark('/app/reservations/new');
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.getByRole('tab', { name: '예약 매칭', exact: true })).toHaveAttribute('aria-selected', 'true');
  await openBookmark('/app/reservations');
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.getByRole('dialog')).toHaveCount(0);
});
