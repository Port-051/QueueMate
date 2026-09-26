import { expect, test, type Page, type Locator } from '@playwright/test';
import type { GameRoom, RoomMember } from '../src/rooms/types';
import { login } from './helpers';

const STORAGE_KEY = 'qm:room-board:v1:u-me';

function member(id: string, overrides: Partial<RoomMember> = {}): RoomMember {
  return {
    id,
    nickname: id === 'u-me' ? 'QueueMaster' : `테스터 ${id}`,
    avatarUrl: null,
    tier: 'GOLD',
    division: 2,
    winRate: 50,
    kda: 2,
    roles: ['MID'],
    champions: ['Ahri'],
    bio: '함께 즐겁게 게임해요.',
    voice: 'OPTIONAL',
    ...overrides,
  };
}

function room(id: string, members: RoomMember[], overrides: Partial<GameRoom> = {}): GameRoom {
  return {
    id,
    game: 'LOL',
    modeKey: 'NORMAL_DRAFT',
    type: 'REALTIME',
    title: `테스트 방 ${id}`,
    ownerId: members[0].id,
    capacity: 5,
    members,
    desiredRoles: ['TOP', 'JUNGLE'],
    voice: 'OPTIONAL',
    status: 'OPEN',
    createdAt: Date.now(),
    availableFrom: null,
    messages: [],
    ...overrides,
  };
}

async function seedRooms(page: Page, rooms: GameRoom[]) {
  await page.addInitScript(({ key, rooms }) => {
    if (!localStorage.getItem(key)) localStorage.setItem(key, JSON.stringify({ version: 1, rooms }));
  }, { key: STORAGE_KEY, rooms });
}

async function selectFullLineup(composer: Locator) {
  await composer.getByRole('radiogroup', { name: '내 포지션', exact: true }).getByRole('radio', { name: '미드', exact: true }).click();
  const wanted = composer.getByRole('group', { name: '찾는 포지션', exact: true });
  for (const name of ['탑', '정글', '바텀', '서포터']) await wanted.getByRole('button', { name, exact: true }).click();
}

async function expectNoPageOverflow(page: Page) {
  const widths = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    overflowing: Array.from(document.body.querySelectorAll('*')).flatMap(element => {
      const bounds = element.getBoundingClientRect();
      return bounds.right > window.innerWidth + 1 && !element.closest('.room-spread-scroll')
        ? [{ tag: element.tagName, className: element.className, right: Math.round(bounds.right), width: Math.round(bounds.width) }]
        : [];
    }).slice(0, 15),
  }));
  expect(widths.document, JSON.stringify(widths)).toBeLessThanOrEqual(widths.viewport + 1);
}

test('다섯 카드가 방 너비에 맞고 상세는 페이지 이동 없이 중앙에 열린다', async ({ page }) => {
  await login(page);
  const grid = page.locator('.room-deck-grid');
  await expect(grid).toBeVisible();
  await expect.poll(() => grid.evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(1);
  expect(await grid.locator('.room-deck').count()).toBeGreaterThanOrEqual(6);
  const deck = grid.locator('.room-deck[data-status="OPEN"]').first();
  await expect(deck).toHaveCSS('border-bottom-left-radius', '30px');
  await expect(deck).toHaveCSS('border-bottom-right-radius', '30px');
  await expect(deck.locator('.room-bubble-tail')).toHaveCSS('left', '-9px');
  const roster = deck.locator('.compact-members');
  await expect(roster.locator('.compact-member')).toHaveCount(5);
  await expect(roster.locator('.compact-seat .room-role-icons b')).toHaveText(['바텀', '서포터']);
  const fit = await roster.evaluate(element => {
    const first = element.firstElementChild!.getBoundingClientRect();
    const last = element.lastElementChild!.getBoundingClientRect();
    return { overflow: element.scrollWidth - element.clientWidth, unused: element.clientWidth - (last.right - first.left) };
  });
  expect(Math.abs(fit.unused)).toBeLessThanOrEqual(1);
  expect(fit.overflow).toBeLessThanOrEqual(1);
  await expect(deck.getByRole('img', { name: '방장', exact: true })).toHaveCount(1);
  await expect(page.locator('.room-quick-rail').getByRole('button', { name: '방 만들기', exact: true })).toBeVisible();
  const originalUrl = page.url();
  await deck.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  expect(page.url()).toBe(originalUrl);
  await expect.poll(() => dialog.locator('.room-member-card').count()).toBeGreaterThan(1);
  await expect.poll(async () => {
    const bounds = await dialog.boundingBox();
    return bounds ? Math.abs(bounds.x + bounds.width / 2 - 800) : Infinity;
  }).toBeLessThanOrEqual(3);
  await expect(dialog.getByRole('button', { name: '카드 접기', exact: true })).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(dialog.getByRole('button', { name: '입장하기', exact: true })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(dialog.getByRole('button', { name: '카드 접기', exact: true })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(deck).toBeFocused();
  await expectNoPageOverflow(page);
});

test('목록에서 개인 전적을 보여주고 상세 요약 평균은 전적이 있는 사람만 계산한다', async ({ page }) => {
  await seedRooms(page, [room('averages', [
    member('one', { winRate: 50, kda: 2 }),
    member('two', { winRate: 60, kda: 4, roles: ['SUPPORT'] }),
    member('three', { tier: null, division: null, winRate: null, kda: null, roles: ['JUNGLE'] }),
  ])]);
  await login(page);
  const deck = page.getByRole('button', { name: '테스트 방 averages 방 정보', exact: true });
  await expect(deck).toContainText('50%');
  await expect(deck).toContainText('60%');
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(3);
  await deck.click();
  await expect(page.getByRole('dialog').locator('.room-summary-stats')).toContainText('55%');
  await expect(page.getByRole('dialog').locator('.room-summary-stats')).toContainText('3.00');
  const cards = page.getByRole('dialog').locator('.room-member-card');
  await expect(cards).toHaveCount(3);
  await expect(cards.filter({ hasText: '테스터 one' })).toContainText('50%');
  await expect(cards.filter({ hasText: '테스터 two' })).toContainText('60%');
  await expect(cards.filter({ hasText: '테스터 three' })).not.toContainText('0%');
});

test('다섯 명 방을 만들면 한 열 목록과 대화를 함께 쓰고 확정해도 방과 채팅이 유지된다', async ({ page }) => {
  await login(page);
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await composer.getByLabel('한마디', { exact: true }).fill('우리 다섯 명의 방');
  await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '5명', exact: true }).click();
  await selectFullLineup(composer);
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await expect(page.locator('.room-home')).toHaveClass(/has-active-room/);
  const ownBubble = page.getByRole('button', { name: '우리 다섯 명의 방 방 정보', exact: true });
  await expect(ownBubble).toHaveCSS('border-bottom-right-radius', '30px');
  await expect(ownBubble).toHaveCSS('border-bottom-left-radius', '30px');
  await expect(ownBubble.locator('.room-bubble-tail')).toHaveCSS('transform', 'matrix(-1, 0, 0, 1, 0, 0)');
  await expect(ownBubble).toHaveCSS('background-color', 'rgb(32, 27, 48)');
  await expect(page.getByRole('region', { name: '방 만들기', exact: true })).toHaveCount(0);
  await expect.poll(() => page.locator('.room-deck-grid').evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(1);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true }).fill('확정 전부터 여기서 이야기해요');
  await page.getByRole('button', { name: '메시지 보내기', exact: true }).click();
  await expect(page.getByText('확정 전부터 여기서 이야기해요', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '모집 마감', exact: true })).toBeDisabled();
  // Simulate an incoming membership update; these local prototype rooms have no remote peer server.
  await page.evaluate(({ key, newcomer }) => {
    const snapshot = JSON.parse(localStorage.getItem(key)!);
    const own = snapshot.rooms.find((room: GameRoom) => room.ownerId === 'u-me');
    own.members.push(newcomer);
    const newValue = JSON.stringify(snapshot);
    localStorage.setItem(key, newValue);
    window.dispatchEvent(new StorageEvent('storage', { key, newValue }));
  }, { key: STORAGE_KEY, newcomer: member('incoming') });
  await page.getByRole('button', { name: '모집 마감', exact: true }).click();
  const deck = page.getByRole('button', { name: '우리 다섯 명의 방 방 정보', exact: true });
  await page.getByRole('checkbox', { name: '모집 중인 방만', exact: true }).uncheck();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(deck).toHaveCSS('filter', 'grayscale(1)');
  await expect(deck).toHaveCSS('border-bottom-right-radius', '30px');
  await expect(page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true })).toBeEnabled();
  await page.locator('.side-nav').getByRole('link', { name: '프로필', exact: true }).press('Enter');
  await page.locator('.side-nav').getByRole('link', { name: '홈', exact: true }).press('Enter');
  await expect(page.getByText('확정 전부터 여기서 이야기해요', { exact: true })).toBeVisible();
  await login(page); // The in-memory mock session resets on navigation; room/preferences stay in localStorage.
  // Mock authentication sessions are in memory; the room snapshot survives a fresh login.
  await login(page);
  await page.getByRole('checkbox', { name: '모집 중인 방만', exact: true }).uncheck();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(page.getByText('확정 전부터 여기서 이야기해요', { exact: true })).toBeVisible();
});

test('마지막 자리에 들어가면 자동 확정되며 참가자는 방장 조작을 할 수 없다', async ({ page }) => {
  await seedRooms(page, [room('last-seat', ['host', 'two', 'three', 'four'].map(id => member(id)))]);
  await login(page);
  const deck = page.getByRole('button', { name: '테스트 방 last-seat 방 정보', exact: true });
  await deck.click();
  await page.getByRole('dialog').getByRole('button', { name: '입장하기', exact: true }).click();
  await expect(page.locator('.room-home')).toHaveClass(/has-active-room/);
  await page.getByRole('checkbox', { name: '모집 중인 방만', exact: true }).uncheck();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(5);
  await expect(page.getByRole('button', { name: /내보내기/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '모집 마감', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '방 나가기', exact: true }).click();
  await page.getByRole('alert').getByRole('button', { name: '나가기', exact: true }).click();
  await page.getByRole('checkbox', { name: '모집 중인 방만', exact: true }).uncheck();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(4);
  await expect(deck.locator('.compact-seat-status')).toHaveText('모집 마감');
  await expect(deck).not.toContainText('모집 중');
  await deck.click();
  await expect(page.getByRole('dialog').getByRole('button', { name: '입장하기', exact: true })).toHaveCount(0);
  await expect(page.getByRole('dialog').getByRole('button', { name: '매칭 확정', exact: true })).toBeDisabled();
});

test('방장은 특정 참가자만 내보낼 수 있고 자신의 방과 남은 참가자는 유지된다', async ({ page }) => {
  await seedRooms(page, [room('owned', [member('u-me'), member('remove'), member('keep')])]);
  await login(page);
  await expect(page.locator('.room-home')).toHaveClass(/has-active-room/);
  await expect(page.getByRole('button', { name: 'QueueMaster 내보내기', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '테스터 remove 내보내기', exact: true }).click();
  await page.getByRole('alert').getByRole('button', { name: '내보내기', exact: true }).click();
  await expect(page.getByRole('button', { name: '테스터 remove 내보내기', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '테스터 keep 내보내기', exact: true })).toBeVisible();
  const deck = page.getByRole('button', { name: '테스트 방 owned 방 정보', exact: true });
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(2);
  await expect(deck).toHaveAttribute('data-status', 'OPEN');
});

test('방을 확정하기 전에도 음성 미리보기에 참여하고 음소거와 나가기를 조작한다', async ({ page }) => {
  await seedRooms(page, [room('voice', [member('u-me'), member('friend')])]);
  await login(page);
  const voice = page.getByRole('region', { name: '방 음성 채널', exact: true });
  await voice.getByRole('button', { name: '참여', exact: true }).click();
  await expect(voice.getByRole('heading', { name: '음성 미리보기', exact: true })).toBeVisible();
  await expect(voice).toContainText('실제 음성은 전송되지 않아요');
  await voice.getByRole('button', { name: '마이크 음소거', exact: true }).click();
  await expect(voice.getByRole('button', { name: '마이크 음소거 해제', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await voice.getByRole('button', { name: '소리 끄기', exact: true }).click();
  await expect(voice.getByRole('button', { name: '마이크 음소거 해제', exact: true })).toBeDisabled();
  await voice.getByRole('button', { name: '소리 켜기', exact: true }).click();
  await expect(voice.getByRole('button', { name: '마이크 음소거 해제', exact: true })).toBeEnabled();
  await voice.getByRole('button', { name: '음성 나가기', exact: true }).click();
  await expect(voice.getByRole('button', { name: '참여', exact: true })).toBeEnabled();
  await voice.getByRole('button', { name: '참여', exact: true }).click();
  await expect(voice.getByRole('heading', { name: '음성 미리보기', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '테스트 방 voice 방 정보', exact: true })).toHaveAttribute('data-status', 'OPEN');
});

test('랭크 방은 두 명까지만 선택되며 선택한 모드는 다시 눌러도 해제되지 않는다', async ({ page }) => {
  await login(page);
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  const modes = composer.getByRole('group', { name: '원하는 큐 타입', exact: true });
  await expect(modes.getByRole('button', { name: '일반', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await modes.getByRole('button', { name: '2인 랭크', exact: true }).click();
  await modes.getByRole('button', { name: '2인 랭크', exact: true }).click();
  await expect(modes.getByRole('button', { pressed: true })).toHaveCount(1);
  await expect(composer.getByRole('group', { name: '모집 인원', exact: true })).toHaveCount(0);
  await composer.getByLabel('한마디', { exact: true }).fill('둘이 랭크');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await expect(page.getByRole('button', { name: '둘이 랭크 방 정보', exact: true }).locator('.compact-seat')).toHaveCount(1);
});

test('빈자리 하나에 두 포지션을 표시하고 마이크는 빈자리 카드에만 표시한다', async ({ page }) => {
  await seedRooms(page, [room('options', [member('host', { voice: 'REQUIRED' })], {
    capacity: 2, desiredRoles: ['SUPPORT', 'TOP'], voice: 'NO_VOICE',
  })]);
  await login(page);
  const deck = page.getByRole('button', { name: '테스트 방 options 방 정보', exact: true });
  const header = deck.locator('.compact-room-header');
  await expect(header).not.toContainText('찾는 포지션');
  await expect(header).not.toContainText('모집 중');
  await expect(header.locator('.room-role-icons, .room-mic-icon')).toHaveCount(0);
  const vacancy = deck.locator('.compact-seat');
  await expect(vacancy).toHaveCount(1);
  await expect(vacancy.locator('.compact-seat-status')).toHaveText('모집 중');
  await expect(vacancy.locator('.room-role-icons b')).toHaveText(['탑', '서포터']);
  await expect(vacancy.getByRole('img', { name: '마이크 미사용', exact: true })).toBeVisible();
  await expect(deck.locator('.compact-member:not(.compact-seat) .room-mic-icon')).toHaveCount(0);
  await deck.click();
  await expect(page.getByRole('dialog').locator('.room-member-card .room-mic-icon')).toHaveCount(0);
});

test('내 포지션은 하나만 선택하고 찾는 포지션은 여러 개 선택하며 재접속해도 유지한다', async ({ page }) => {
  await login(page);
  const rail = page.getByRole('region', { name: '빠른 연결', exact: true });
  const own = rail.getByRole('radiogroup', { name: '내 포지션', exact: true });
  const wanted = rail.locator('.intro-role-options[aria-label="찾는 포지션"]');
  await own.getByRole('radio', { name: '탑', exact: true }).check();
  await own.getByRole('radio', { name: '미드', exact: true }).check();
  await own.getByRole('radio', { name: '미드', exact: true }).press('ArrowRight');
  await expect(own.getByRole('radio', { name: '바텀', exact: true })).toBeChecked();
  await own.getByRole('radio', { name: '바텀', exact: true }).click();
  await expect(own.getByRole('radio', { checked: true })).toHaveCount(1);
  for (const name of ['탑', '서포터']) await wanted.getByRole('button', { name, exact: true }).click();
  await expect(wanted.getByRole('button', { pressed: true })).toHaveCount(2);
  await login(page);
  await expect(own.getByRole('radio', { name: '바텀', exact: true })).toBeChecked();
  await expect(wanted.getByRole('button', { pressed: true })).toHaveCount(2);
  await own.getByRole('radio', { name: '정글', exact: true }).check();
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await composer.getByRole('button', { name: '2인 랭크', exact: true }).click();
  const roomOwn = composer.getByRole('radiogroup', { name: '내 포지션', exact: true });
  await expect(roomOwn.getByRole('radio', { name: '정글', exact: true })).toBeChecked();
  await roomOwn.getByRole('radio', { name: '미드', exact: true }).check();
  await expect(roomOwn.getByRole('radio', { checked: true })).toHaveCount(1);
  await expect(roomOwn.getByRole('radio', { name: '정글', exact: true })).not.toBeChecked();
});

test('5인 방은 내 포지션과 나머지 네 포지션을 모두 골라야 만들 수 있다', async ({ page }) => {
  await login(page);
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  const create = composer.getByRole('button', { name: '방 만들기', exact: true });
  const own = composer.getByRole('radiogroup', { name: '내 포지션', exact: true });
  const wanted = composer.getByRole('group', { name: '찾는 포지션', exact: true });
  await expect(create).toBeDisabled();
  await own.getByRole('radio', { name: '미드', exact: true }).click();
  await expect(wanted.getByRole('button', { name: '미드', exact: true })).toBeDisabled();
  for (const name of ['탑', '정글', '바텀']) await wanted.getByRole('button', { name, exact: true }).click();
  await expect(create).toBeDisabled();
  await composer.getByLabel('한마디', { exact: true }).fill('다섯 포지션 완성');
  await composer.getByLabel('한마디', { exact: true }).press('Enter');
  await expect(page.locator('.room-home')).not.toHaveClass(/has-active-room/);
  await wanted.getByRole('button', { name: '서포터', exact: true }).click();
  await expect(create).toBeEnabled();
  await own.getByRole('radio', { name: '탑', exact: true }).click();
  await expect(own.getByRole('radio', { checked: true })).toHaveCount(1);
  await expect(create).toBeDisabled();
  await wanted.getByRole('button', { name: '미드', exact: true }).click();
  await expect(create).toBeEnabled();
  await create.click();
  const deck = page.getByRole('button', { name: '다섯 포지션 완성 방 정보', exact: true });
  await expect(deck.locator('.compact-seat .room-role-icons b')).toHaveText(['정글', '미드', '바텀', '서포터']);
});

test('칼바람 5인 방은 포지션을 고르지 않고도 만들 수 있다', async ({ page }) => {
  await login(page);
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await composer.getByRole('group', { name: '원하는 큐 타입', exact: true }).getByRole('button', { name: '칼바람', exact: true }).click();
  await expect(composer.getByRole('radiogroup', { name: '내 포지션', exact: true })).toHaveCount(0);
  await composer.getByLabel('한마디', { exact: true }).fill('포로 다섯');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await expect(page.getByRole('button', { name: '포로 다섯 방 정보', exact: true }).locator('.compact-seat')).toHaveCount(4);
});

test('5인 방의 방장이 나가도 그 포지션을 다시 빈자리로 표시한다', async ({ page }) => {
  await seedRooms(page, [room('host-leaves', [member('u-me'), member('next-host', { roles: ['TOP'] })], {
    desiredRoles: ['TOP', 'JUNGLE', 'ADC', 'SUPPORT'],
  })]);
  await login(page);
  await page.getByRole('button', { name: '방 나가기', exact: true }).click();
  await page.getByRole('alert').getByRole('button', { name: '나가기', exact: true }).click();
  const deck = page.getByRole('button', { name: '테스트 방 host-leaves 방 정보', exact: true });
  await expect(deck.locator('.compact-seat .room-role-icons b')).toHaveText(['정글', '미드', '바텀', '서포터']);
  await expect(deck).toHaveAttribute('data-status', 'OPEN');
});

test('예약 방은 과거 시간을 거절하고 미래 시간으로 만든 즉시 채팅할 수 있다', async ({ page }) => {
  await login(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await composer.getByLabel('한마디', { exact: true }).fill('조금 뒤에 다 같이');
  await selectFullLineup(composer);
  await composer.getByLabel('시작 시간', { exact: true }).fill('2000-01-01T12:00');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await expect(composer.getByRole('alert')).toContainText('현재보다 뒤');
  const next = await page.evaluate(() => {
    const date = new Date(Date.now() + 2 * 60 * 60_000);
    date.setMinutes(0, 0, 0);
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
  });
  await composer.getByLabel('시작 시간', { exact: true }).fill(next);
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  const deck = page.getByRole('button', { name: '조금 뒤에 다 같이 방 정보', exact: true });
  await expect(deck.locator('.compact-room-header time')).toBeVisible();
  await expect(page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true })).toBeEnabled();
  await expect(deck).toHaveAttribute('data-status', 'OPEN');
});

test('채팅 저장이 실패하면 메시지와 초안을 잃지 않고 다시 전송할 수 있다', async ({ page }) => {
  await seedRooms(page, [room('storage', [member('u-me'), member('friend')])]);
  await login(page);
  await page.evaluate(() => {
    const prototype = Storage.prototype as Storage & { originalRoomSetItem?: Storage['setItem'] };
    prototype.originalRoomSetItem = prototype.setItem;
    prototype.setItem = function (key, value) {
      if (key.startsWith('qm:room-board:v1:')) throw new DOMException('Storage is full', 'QuotaExceededError');
      return prototype.originalRoomSetItem!.call(this, key, value);
    };
  });
  const input = page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true });
  await input.fill('잃어버리면 안 되는 메시지');
  await page.getByRole('button', { name: '메시지 보내기', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '방 정보를 저장할 수 없어요' })).toBeVisible();
  await expect(input).toHaveValue('잃어버리면 안 되는 메시지');
  await expect(page.getByRole('log', { name: '방 메시지', exact: true })).not.toContainText('잃어버리면 안 되는 메시지');
  await page.evaluate(() => {
    const prototype = Storage.prototype as Storage & { originalRoomSetItem?: Storage['setItem'] };
    prototype.setItem = prototype.originalRoomSetItem!;
    delete prototype.originalRoomSetItem;
  });
  await page.getByRole('button', { name: '메시지 보내기', exact: true }).click();
  await expect(page.getByRole('log', { name: '방 메시지', exact: true }).getByText('잃어버리면 안 되는 메시지', { exact: true })).toHaveCount(1);
  await expect(input).toHaveValue('');
});

test('모션 감소 설정과 좁은 화면에서도 덱을 열고 닫을 수 있고 본문은 넘치지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await login(page);
  const deck = page.locator('.room-deck[data-status="OPEN"]').first();
  await deck.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  const bounds = await dialog.boundingBox();
  expect(bounds).not.toBeNull();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(391);
  await expectNoPageOverflow(page);
  await dialog.getByRole('button', { name: '카드 접기', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(deck).toBeFocused();
});

test('2인 랭크는 두 방씩 배치하고 자유 랭크는 2·3·5명만 모집한다', async ({ page }) => {
  await login(page);
  const modes = page.getByRole('group', { name: '찾는 큐 타입', exact: true });
  await modes.getByRole('button', { name: '2인 랭크', exact: true }).click();
  const grid = page.locator('.room-deck-grid');
  await expect.poll(() => grid.evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(2);
  for (const deck of await grid.locator('.room-deck').all()) {
    await expect(deck.locator('.compact-member')).toHaveCount(2);
    await expect(deck.locator('.compact-room-header')).not.toContainText(/\d\/2/);
  }
  await expectNoPageOverflow(page);
  await modes.getByRole('button', { name: '자유 랭크', exact: true }).click();
  await expect.poll(() => grid.locator('.room-deck').count()).toBeGreaterThan(0);
  await page.getByRole('group', { name: '원하는 큐 타입', exact: true }).getByRole('button', { name: '자유 랭크', exact: true }).click();
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await expect(composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button')).toHaveText(['2명', '3명', '5명']);
  await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '3명', exact: true }).click();
  await composer.getByLabel('한마디', { exact: true }).fill('자유 랭크 셋이서');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await expect(page.getByRole('button', { name: '자유 랭크 셋이서 방 정보', exact: true }).locator('.compact-member')).toHaveCount(3);
  await login(page); // The in-memory mock session resets on navigation; room/preferences stay in localStorage.
  await expect(page.getByRole('button', { name: '자유 랭크 셋이서 방 정보', exact: true })).toBeVisible();
});

test('모드별 인원을 선택하고 재접속과 방 만들기에서도 유지한다', async ({ page }) => {
  await login(page);
  const rail = page.getByRole('region', { name: '빠른 연결', exact: true });
  const modes = rail.getByRole('group', { name: '원하는 큐 타입', exact: true });
  const sizes = rail.getByRole('group', { name: '모집 인원', exact: true });
  await modes.getByRole('button', { name: '2인 랭크', exact: true }).click();
  await expect(sizes).toHaveCount(0);
  for (const mode of ['일반', '신속', '칼바람']) {
    await modes.getByRole('button', { name: mode, exact: true }).click();
    await expect(sizes.getByRole('button')).toHaveText(['2명', '3명', '4명', '5명']);
  }
  await sizes.getByRole('button', { name: '4명', exact: true }).click();
  await modes.getByRole('button', { name: '자유 랭크', exact: true }).click();
  await expect(sizes.getByRole('button')).toHaveText(['2명', '3명', '5명']);
  await expect(sizes.getByRole('button', { name: '5명', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await sizes.getByRole('button', { name: '3명', exact: true }).click();
  await sizes.getByRole('button', { name: '3명', exact: true }).click();
  await expect(sizes.getByRole('button', { pressed: true })).toHaveCount(1);
  await rail.getByRole('radiogroup', { name: '내 포지션', exact: true }).getByRole('radio', { name: '미드', exact: true }).check();
  await login(page);
  await expect(modes.getByRole('button', { name: '자유 랭크', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(sizes.getByRole('button', { name: '3명', exact: true })).toHaveAttribute('aria-pressed', 'true');
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await expect(composer.getByRole('button', { name: '자유 랭크', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '3명', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await composer.getByRole('textbox', { name: '한마디', exact: true }).fill('자유 랭크 세 명');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await expect(page.getByRole('button', { name: '자유 랭크 세 명 방 정보', exact: true }).locator('.compact-member')).toHaveCount(3);
});

test('모집 티어 범위는 방 구성원 평균 대신 모집 조건을 찾고 단일 선택과 취소를 지원한다', async ({ page }) => {
  await seedRooms(page, [...['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND'].map((tier, i) => room(tier, [member(`host-${i}`, { tier: 'GOLD' })], { desiredTierRange: { minTier: tier, maxTier: tier } })), room('unrestricted', [member('any-host', { tier: 'DIAMOND' })])]);
  await login(page);
  const trigger = page.getByRole('button', { name: '모집 티어 범위', exact: true });
  const dialog = page.getByRole('dialog', { name: '모집 티어 범위', exact: true });
  await trigger.click();
  await expect(dialog.locator('.tier-range-option')).toHaveCount(10);
  await dialog.getByRole('button', { name: '골드', exact: true }).click();
  await dialog.getByRole('button', { name: '실버', exact: true }).click();
  await expect(page.locator('.room-deck')).toHaveCount(6);
  await dialog.getByRole('button', { name: '적용', exact: true }).click();
  await expect(trigger).toContainText('실버~골드');
  await expect(page.locator('.room-deck')).toHaveCount(3);
  await trigger.click();
  await dialog.getByRole('button', { name: '다이아몬드', exact: true }).click();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await expect(trigger).toContainText('실버~골드');
  await trigger.click();
  await dialog.getByRole('button', { name: '골드', exact: true }).click();
  await dialog.getByRole('button', { name: '적용', exact: true }).click();
  await expect(trigger).toHaveText('골드');
  await expect(page.locator('.room-deck')).toHaveCount(2);
  await expect(page.getByRole('button', { name: '테스트 방 GOLD 방 정보', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '테스트 방 unrestricted 방 정보', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '테스트 방 PLATINUM 방 정보', exact: true })).toHaveCount(0);
  await trigger.click();
  await dialog.getByRole('button', { name: '골드', exact: true }).click();
  await dialog.getByRole('button', { name: '골드', exact: true }).click();
  await dialog.getByRole('button', { name: '적용', exact: true }).click();
  await expect(page.locator('.room-deck')).toHaveCount(2);
  await page.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(page.locator('.room-deck')).toHaveCount(6);
});

test('우측 티어 범위를 보관하고 방 생성 시 빈자리 조건으로 이어진다', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: '찾는 티어 범위', exact: true }).click();
  const picker = page.getByRole('dialog', { name: '찾는 티어 범위', exact: true });
  await picker.getByRole('button', { name: '실버', exact: true }).click();
  await picker.getByRole('button', { name: '골드', exact: true }).click();
  await picker.getByRole('button', { name: '적용', exact: true }).click();
  await login(page); // The in-memory mock session resets on navigation; room/preferences stay in localStorage.
  await expect(page.getByRole('button', { name: '찾는 티어 범위', exact: true })).toContainText('실버~골드');
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await expect(composer.getByRole('button', { name: '찾는 티어 범위', exact: true })).toContainText('실버~골드');
  await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '2명', exact: true }).click();
  await composer.getByLabel('한마디', { exact: true }).fill('티어 범위 확인');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  const seat = page.getByRole('button', { name: '티어 범위 확인 방 정보', exact: true }).locator('.compact-seat');
  await expect(seat).toContainText('실버~골드');
  await expect(seat).toHaveCSS('animation-name', 'vacant-seat-breathe');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(seat).toHaveCSS('animation-name', 'none');
  await login(page); // The in-memory mock session resets on navigation; room/preferences stay in localStorage.
  await expect(seat).toContainText('실버~골드');
});

test('티어 범위를 벗어난 방은 상세에서 입장을 막는다', async ({ page }) => {
  await seedRooms(page, [room('restricted', [member('host')], { desiredTierRange: { minTier: 'CHALLENGER', maxTier: 'CHALLENGER' } })]);
  await login(page);
  await page.getByRole('button', { name: '테스트 방 restricted 방 정보', exact: true }).click();
  await expect(page.getByRole('dialog').getByRole('button', { name: '입장하기', exact: true })).toBeDisabled();
  await expect(page.getByRole('dialog')).toContainText('방에서 찾는 티어 범위와 맞지 않아요');
});

test('좁은 화면에서는 듀오가 한 열이며 범위 선택창이 화면 안에 들어온다', async ({ page }) => {
  await page.setViewportSize({ width: 540, height: 900 });
  await login(page);
  await page.getByRole('group', { name: '찾는 큐 타입', exact: true }).getByRole('button', { name: '2인 랭크', exact: true }).click();
  await expect.poll(() => page.locator('.room-deck-grid').evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(1);
  await page.getByRole('button', { name: '모집 티어 범위', exact: true }).click();
  const picker = page.getByRole('dialog', { name: '모집 티어 범위', exact: true });
  const bounds = await picker.boundingBox();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(540);
  await picker.getByRole('button', { name: '챌린저', exact: true }).click();
  await picker.getByRole('button', { name: '적용', exact: true }).click();
  await expectNoPageOverflow(page);
});

test('게시판 포지션은 빈자리 모집 조건을 찾고 우측 선호 조건과 독립적이다', async ({ page }) => {
  await seedRooms(page, [
    room('seeking-top', [member('mid-host')], { capacity: 2, desiredRoles: ['TOP'] }),
    room('seeking-support', [member('top-host', { roles: ['TOP'] })], { capacity: 2, desiredRoles: ['SUPPORT'] }),
    room('top-filled', [member('host'), member('guest', { roles: ['TOP'] })], { desiredRoles: ['TOP', 'JUNGLE', 'ADC', 'SUPPORT'] }),
  ]);
  await login(page);
  const board = page.getByRole('region', { name: '방 목록', exact: true });
  await board.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '탑', exact: true }).click();
  await expect(page.locator('.room-deck')).toHaveCount(1);
  await expect(page.getByRole('button', { name: '테스트 방 seeking-top 방 정보', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '찾는 티어 범위', exact: true }).click();
  const picker = page.getByRole('dialog', { name: '찾는 티어 범위', exact: true });
  await picker.getByRole('button', { name: '골드', exact: true }).click();
  await picker.getByRole('button', { name: '적용', exact: true }).click();
  await expect(page.getByRole('button', { name: '찾는 티어 범위', exact: true })).toHaveText('골드');
  await expect(page.getByRole('button', { name: '모집 티어 범위', exact: true })).toHaveText('모든 티어');
  await expect(page.locator('.room-deck')).toHaveCount(1);
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await expect(composer.getByRole('button', { name: '찾는 티어 범위', exact: true })).toHaveText('골드');
  await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '2명', exact: true }).click();
  await composer.getByLabel('한마디', { exact: true }).fill('골드만 함께해요');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await expect(page.getByRole('button', { name: '골드만 함께해요 방 정보', exact: true }).locator('.compact-seat .tier-range-label')).toHaveText('골드');
});

test('현재 조건과 한마디로 바로 만들며 저장 실패 후에도 초안을 유지한다', async ({ page }) => {
  await login(page);
  const rail = page.getByRole('region', { name: '빠른 연결', exact: true });
  await rail.getByRole('button', { name: '2인 랭크', exact: true }).click();
  await rail.getByRole('radiogroup', { name: '내 포지션', exact: true }).getByRole('radio', { name: '바텀', exact: true }).check();
  await rail.locator('.intro-role-options[aria-label="찾는 포지션"]').getByRole('button', { name: '서포터', exact: true }).click();
  await rail.getByRole('button', { name: '마이크 미사용', exact: true }).click();
  const create = rail.getByRole('button', { name: '방 만들기', exact: true });
  await create.click();
  await expect(rail.getByRole('alert')).toContainText('한마디를 입력해 주세요');
  await expect(page.locator('.room-home')).not.toHaveClass(/has-active-room/);
  // A full introduction must survive both creation and snapshot parsing, without a 50-character title cut-off.
  const title = '편하게 두 판 같이 하실 서포터를 찾아요. 서로 실수해도 괜찮고 게임을 즐기면서 천천히 호흡을 맞춰 가실 분이면 좋아요.';
  await rail.getByLabel('한마디', { exact: true }).fill(title);
  await page.evaluate(() => {
    const prototype = Storage.prototype as Storage & { originalRoomSetItem?: Storage['setItem'] };
    prototype.originalRoomSetItem = prototype.setItem;
    prototype.setItem = function (key, value) {
      if (key.startsWith('qm:room-board:v1:')) throw new DOMException('Storage is full', 'QuotaExceededError');
      return prototype.originalRoomSetItem!.call(this, key, value);
    };
  });
  await create.click();
  await expect(page.getByRole('status').filter({ hasText: '방 정보를 저장할 수 없어요' })).toBeVisible();
  await expect(rail.getByLabel('한마디', { exact: true })).toHaveValue(title);
  await expect(page.locator('.room-home')).not.toHaveClass(/has-active-room/);
  await page.evaluate(() => {
    const prototype = Storage.prototype as Storage & { originalRoomSetItem?: Storage['setItem'] };
    prototype.setItem = prototype.originalRoomSetItem!;
    delete prototype.originalRoomSetItem;
  });
  await create.click();
  const deck = page.getByRole('button', { name: `${title} 방 정보`, exact: true });
  await expect(deck).toBeVisible();
  await expect(deck.locator('.compact-member:not(.compact-seat) .room-role-icons b')).toHaveText(['바텀']);
  await expect(deck.locator('.compact-seat .room-role-icons b')).toHaveText(['서포터']);
  await expect(deck.locator('.compact-seat').getByRole('img', { name: '마이크 미사용', exact: true })).toBeVisible();
  await expect(page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true })).toBeEnabled();
  await login(page);
  await expect(deck).toBeVisible();
  await expect(deck.locator('h3')).toHaveText(title);
});
