import { expect, test } from '@playwright/test';
import { DEMO, manageRecruitment, login, startRealtimeMatch } from './helpers';

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
  await page.evaluate(() => { history.pushState(null, '', '/app/party/ux-party'); dispatchEvent(new PopStateEvent('popstate')); });
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

test('지난 모집 재사용과 작성 모달은 키보드로 조작할 수 있다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 });
  await login(page); await startRealtimeMatch(page);
  await manageRecruitment(page, '모집 종료');
  await page.locator('.board-history summary').click();
  const reuse = page.getByRole('button', { name: '이 조건으로 다시 모집' });
  await reuse.click();
  const start = page.getByRole('button', { name: '모집 시작', exact: true });
  await expect(start).toBeEnabled();
  for (let i = 0; i < 24; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(reuse).toBeFocused();
});

test('지난 모집 5개는 중복 없이 표시하고 여섯 번째 기록도 다시 열 수 있다', async ({ page }) => {
  await login(page);
  await page.evaluate(async () => {
    const apiPath = '/src/api/recruitment.ts';
    const preferencesPath = '/src/domain/recruitment.ts';
    const api = await import(/* @vite-ignore */ apiPath);
    const { anyPreferences } = await import(/* @vite-ignore */ preferencesPath);
    for (let i = 1; i <= 6; i++) {
      const row = await api.createRecruitment({
        type: 'REALTIME',
        condition: { game: 'LOL', modeKey: 'SOLO_DUO_RANKED', keyCondition: { type: 'POSITION', value: i === 1 ? 'MID' : 'TOP' }, voicePreference: 'OPTIONAL', playPurpose: 'NORMAL' },
        preferences: anyPreferences(), description: `지난 모집 ${i}`, autoMatch: false,
        availableFrom: null, availableTo: null, playAmount: null,
      });
      await api.recruitmentAction(row, 'CLOSE');
    }
  });
  await page.locator('.board-history > summary').click();
  const recent = page.locator('.closed-recruitments > div');
  const earlier = page.locator('.match-history-row');
  await expect(recent).toHaveCount(5);
  await expect(recent).toHaveText([
    /지난 모집 6/, /지난 모집 5/, /지난 모집 4/, /지난 모집 3/, /지난 모집 2/,
  ]);
  // 상위 다섯 건을 다시 표시하지 않으면서 가장 오래된 조건은 남겨둔다.
  await expect(earlier).toHaveCount(1);
  await expect(earlier).toContainText('미드');
  const review = earlier.getByRole('button', { name: '조건 보기' });
  await review.click();
  await expect(page.getByRole('dialog').getByLabel('주 포지션')).toHaveValue('MID');
  await page.keyboard.press('Escape');
  await expect(review).toBeFocused();
});

test('친구 요청 탭, 검색 빈 상태, 신고 모달 키보드 포커스', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav a[href="/app/messages"]').click();
  await page.getByRole('button', { name: '친구 관리', exact: true }).click();
  await expect(page.getByRole('dialog').getByText('HealingYou', { exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.getByLabel('대화 검색').fill('검색되지않는이름');
  await expect(page.getByText('검색 결과가 없습니다', { exact: true })).toBeVisible();
  await page.getByLabel('대화 검색').fill('');
  await page.getByRole('button', { name: /^GankFlow 대화/ }).click();
  const menu = page.locator('.dm-thread-header .action-menu > summary');
  await menu.click();
  await page.getByRole('button', { name: '신고', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  for (let i = 0; i < 10; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(menu).toBeFocused();
});

test('검색 필터는 내 모집을 바꾸지 않고 게임과 필터 선택을 유지한다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await page.getByLabel('찾는 상대 포지션').selectOption('MID');
  await expect(page.locator('.my-recruitment .recruitment-own-summary')).toContainText('무관');
  await manageRecruitment(page, '조건 수정');
  await expect(page.getByRole('dialog').getByLabel('주 포지션')).toHaveValue('ANY');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: '발로란트 매칭', exact: true }).click();
  await expect(page.getByLabel('찾는 상대 포지션')).toHaveValue('ANY');
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
  await expect(page.getByRole('dialog')).toContainText('실시간 모집');
  await openBookmark('/app/reservations/new');
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.getByRole('dialog')).toContainText('예약 모집');
  await openBookmark('/app/reservations');
  await expect(page).toHaveURL(/\/app\/home$/);
  await expect(page.getByRole('dialog')).toHaveCount(0);
});
