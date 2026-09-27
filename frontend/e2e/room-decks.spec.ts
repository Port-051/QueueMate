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

test.describe('방 메시지 표시', () => {
  test.use({ timezoneId: 'Asia/Seoul' });
  test('연속 메시지는 묶고 날짜가 바뀌면 분리하며 입력할 때만 전송 버튼이 나타난다', async ({ page }) => {
    await seedRooms(page, [room('messages', [member('u-me'), member('friend')], {
      messages: [
        { id: 'one', authorId: 'u-me', text: '첫 메시지', createdAt: Date.parse('2026-09-27T23:58:00+09:00') },
        { id: 'two', authorId: 'u-me', text: '이어지는 메시지', createdAt: Date.parse('2026-09-27T23:59:00+09:00') },
        { id: 'three', authorId: 'u-me', text: '다음 날 메시지', createdAt: Date.parse('2026-09-28T00:00:00+09:00') },
        { id: 'four', authorId: 'friend', text: '상대방 답장', createdAt: Date.parse('2026-09-28T00:01:00+09:00') },
      ],
    })]);
    await login(page);
    const chat = page.getByRole('region', { name: '방 채팅과 음성' });
    await expect(chat.locator('.room-conversation-day')).toHaveText(['2026년 9월 27일', '2026년 9월 28일']);
    await expect(chat.locator('.room-conversation-message.is-grouped')).toHaveText(/이어지는 메시지/);
    await expect(chat.locator('.room-conversation-roster').getByRole('img', { name: '방장' })).toBeVisible();
    const input = chat.getByPlaceholder('메시지 입력...');
    const send = chat.getByRole('button', { name: '메시지 보내기', exact: true });
    await expect(send).toHaveCount(0);
    await input.fill('   ');
    await expect(send).toHaveCount(0);
    await input.fill('버튼으로 전송');
    await expect(send).toBeEnabled();
    await send.click();
    await expect(chat.getByRole('log')).toContainText('버튼으로 전송');
    await expect(input).toHaveValue('');
    await expect(send).toHaveCount(0);
  });
});

test('방장은 혼자 있어도 마감하며 대화와 초안을 유지한다', async ({ page }) => {
  await seedRooms(page, [room('reopen', [member('u-me')], { capacity: 3 })]);
  await login(page);
  const chat = page.getByRole('region', { name: '방 채팅과 음성' });
  const input = chat.getByRole('textbox', { name: '방에 메시지 보내기' });
  await input.fill('모집 중단 전 메시지');
  await input.press('Enter');
  await input.fill('작성 중인 초안');
  await chat.getByRole('button', { name: '모집 마감', exact: true }).click();
  const deck = page.getByRole('article', { name: '테스트 방 reopen 방 정보', exact: true });
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(chat.getByRole('button', { name: '모집 마감', exact: true })).toBeDisabled();
  await expect(chat.getByRole('button', { name: '모집 다시 열기', exact: true })).toHaveCount(0);
  await expect(input).toHaveValue('작성 중인 초안');
  await expect(chat.getByRole('log')).toContainText('모집 중단 전 메시지');
  await page.reload();
  // Mock authentication is in memory; saved rooms survive signing in again.
  await login(page);
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(chat.getByRole('log')).toContainText('모집 중단 전 메시지');
  await expect(page.getByRole('button', { name: '모집 마감', exact: true })).toBeDisabled();
});

test('다섯 카드가 방 너비에 맞고 빈자리만 선택할 수 있다', async ({ page }) => {
  await login(page);
  const grid = page.locator('.room-deck-grid');
  await expect(grid).toBeVisible();
  await expect.poll(() => grid.evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(1);
  expect(await grid.locator('.room-deck').count()).toBeGreaterThanOrEqual(6);
  const deck = grid.locator('.room-deck[data-status="OPEN"]').first();
  await expect(deck).toHaveCSS('border-bottom-left-radius', '32px');
  await expect(deck).toHaveCSS('border-bottom-right-radius', '32px');
  await expect(deck.locator('.room-bubble-tail')).toHaveCSS('left', '-1px');
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
  await deck.getByRole('heading').click();
  await deck.locator('.room-member-facts').first().click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(deck).not.toHaveAttribute('tabindex');
  await expect(deck).toHaveCSS('cursor', 'default');
  const memberFill = await deck.locator('.compact-member:not(.compact-seat)').first().evaluate(element => getComputedStyle(element).backgroundColor);
  const seat = deck.getByRole('button', { name: '바텀 자리 참여', exact: true });
  expect(await seat.evaluate(element => getComputedStyle(element).backgroundColor)).not.toBe(memberFill);
  await seat.click();
  const dialog = page.getByRole('dialog', { name: '이 자리에 참여할까요?' });
  await expect(dialog).toBeVisible();
  expect(page.url()).toBe(originalUrl);
  await expect(page.locator('.room-member-card, .room-spread-backdrop')).toHaveCount(0);
  await expect(page.locator('.room-home')).not.toHaveClass(/has-active-room/);
  await expect(dialog.getByRole('button', { name: '바텀', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(seat).toBeFocused();
  await expectNoPageOverflow(page);
});

test('멤버 전적 영역은 정보를 표시하며 별도 화면을 열지 않는다', async ({ page }) => {
  await seedRooms(page, [room('averages', [
    member('one', { winRate: 50, kda: 2 }),
    member('two', { winRate: 60, kda: 4, roles: ['SUPPORT'] }),
    member('three', { tier: null, division: null, winRate: null, kda: null, roles: ['JUNGLE'] }),
  ])]);
  await login(page);
  const deck = page.getByRole('article', { name: '테스트 방 averages 방 정보', exact: true });
  await expect(deck).toContainText('50%');
  await expect(deck).toContainText('60%');
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(3);
  const cards = deck.locator('.compact-member:not(.compact-seat)');
  await cards.first().click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(cards.filter({ hasText: '테스터 three' })).not.toContainText('0%');
  await expect(cards.filter({ hasText: '테스터 three' }).locator('.room-unknown-stat')).toHaveCount(2);
});

test('다섯 명 방을 만들면 한 열 목록과 대화를 함께 쓰고 확정해도 방과 채팅이 유지된다', async ({ page }) => {
  await login(page);
  await page.evaluate(() => {
    document.addEventListener('animationstart', event => {
      if (event.animationName === 'room-message-send') document.documentElement.dataset.roomSendAnimated = 'true';
    });
  });
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await composer.getByLabel('한마디', { exact: true }).fill('우리 다섯 명의 방');
  await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '5명', exact: true }).click();
  await selectFullLineup(composer);
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  await expect(page.locator('.room-home')).toHaveClass(/has-active-room/);
  const ownBubble = page.getByRole('article', { name: '우리 다섯 명의 방 방 정보', exact: true });
  await expect(page.locator('html')).toHaveAttribute('data-room-send-animated', 'true');
  await expect(ownBubble).not.toHaveClass(/is-entering/);
  await expect(ownBubble).toHaveCSS('border-bottom-right-radius', '32px');
  await expect(ownBubble).toHaveCSS('border-bottom-left-radius', '32px');
  await expect(ownBubble.locator('.room-bubble-tail')).toHaveCSS('transform', 'matrix(-1, 0, 0, 1, 0, 0)');
  await expect(ownBubble).toHaveCSS('background-color', 'rgb(45, 34, 66)');
  await expect(page.getByRole('region', { name: '방 만들기', exact: true })).toHaveCount(0);
  await expect.poll(() => page.locator('.room-deck-grid').evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(1);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(composer).toBeVisible();
  await page.getByRole('group', { name: '우측 영역 선택' }).getByRole('button', { name: /방 채팅/ }).click();
  await page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true }).fill('확정 전부터 여기서 이야기해요');
  await page.getByRole('button', { name: '메시지 보내기', exact: true }).click();
  await expect(page.getByText('확정 전부터 여기서 이야기해요', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '모집 마감', exact: true })).toBeEnabled();
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
  const deck = page.getByRole('article', { name: '우리 다섯 명의 방 방 정보', exact: true });
  await page.getByRole('checkbox', { name: '모집 중인 방만', exact: true }).uncheck();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(deck).toHaveCSS('filter', 'grayscale(1)');
  await expect(deck).toHaveCSS('border-bottom-right-radius', '32px');
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
  await seedRooms(page, [room('last-seat', ['host', 'two', 'three', 'four'].map((id, i) => member(id, { roles: [['TOP'], ['JUNGLE'], ['MID'], ['ADC']][i] })), { desiredRoles: ['JUNGLE', 'MID', 'ADC', 'SUPPORT'] })]);
  await login(page);
  const deck = page.getByRole('article', { name: '테스트 방 last-seat 방 정보', exact: true });
  await deck.locator('.compact-seat').click();
  await page.getByRole('dialog').getByRole('button', { name: '참여하기', exact: true }).click();
  await expect(page.locator('.room-home')).toHaveClass(/has-active-room/);
  await page.getByRole('checkbox', { name: '모집 중인 방만', exact: true }).uncheck();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(5);
  await expect(page.getByRole('button', { name: /내보내기/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '모집 마감', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '방 나가기', exact: true }).click();
  await page.getByRole('alert').getByRole('button', { name: '나가기', exact: true }).click();
  await page.getByRole('checkbox', { name: '모집 중인 방만', exact: true }).uncheck();
  await expect(deck).toHaveAttribute('data-status', 'OPEN');
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(4);
  await expect(deck.locator('.compact-seat-status')).toHaveText('모집 중');
  await expect(deck.locator('.compact-seat')).toBeEnabled();
  await deck.locator('.compact-seat').click();
  await page.getByRole('dialog').getByRole('button', { name: '참여하기', exact: true }).click();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(5);
  await deck.getByRole('heading').click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('방장은 특정 참가자만 내보낼 수 있고 자신의 방과 남은 참가자는 유지된다', async ({ page }) => {
  await seedRooms(page, [room('owned', [member('u-me'), member('remove'), member('keep')])]);
  await login(page);
  await expect(page.locator('.room-home')).toHaveClass(/has-active-room/);
  await expect(page.getByRole('button', { name: 'QueueMaster 내보내기', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '테스터 remove 내보내기', exact: true }).click();
  await page.getByRole('dialog', { name: '멤버를 내보낼까요?' }).getByRole('button', { name: '내보내기', exact: true }).click();
  await expect(page.getByRole('button', { name: '테스터 remove 내보내기', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '테스터 keep 내보내기', exact: true })).toBeVisible();
  const deck = page.getByRole('article', { name: '테스트 방 owned 방 정보', exact: true });
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(2);
  await expect(deck).toHaveAttribute('data-status', 'OPEN');
});

test('5인 방에서 강퇴를 확인하고 빈자리를 다시 모집하며 모든 멤버가 화면에 들어간다', async ({ page }) => {
  await seedRooms(page, [room('five', ['u-me', 'jungle', 'mid', 'bottom', 'support'].map((id, i) => member(id, { roles: [['TOP'], ['JUNGLE'], ['MID'], ['ADC'], ['SUPPORT']][i] })), {
    status: 'CONFIRMED', desiredRoles: ['JUNGLE', 'MID', 'ADC', 'SUPPORT'],
  })]);
  await login(page);
  const chat = page.getByRole('region', { name: '방 채팅과 음성' });
  const deck = page.getByRole('article', { name: '테스트 방 five 방 정보', exact: true });
  const close = chat.getByRole('button', { name: '모집 마감', exact: true });
  await expect(close).toBeDisabled();
  await expect(chat).not.toContainText(/5\s*\/\s*5|모집 다시 열기/);
  await expect(page.getByRole('group', { name: '우측 영역 선택' })).not.toContainText(/5\s*\/\s*5/);
  await expect(chat.locator('.room-conversation-roster .room-conversation-member')).toHaveCount(5);
  const fit = await chat.locator('.room-conversation-roster').evaluate(e => e.scrollWidth - e.clientWidth);
  expect(fit).toBeLessThanOrEqual(1);
  const leaveBox = (await chat.getByRole('button', { name: '방 나가기', exact: true }).boundingBox())!;
  const closeBox = (await close.boundingBox())!;
  expect(closeBox.x + closeBox.width).toBeLessThan(leaveBox.x);
  expect(Math.abs(closeBox.y + closeBox.height / 2 - leaveBox.y - leaveBox.height / 2)).toBeLessThan(1);
  const kick = chat.getByRole('button', { name: '테스터 support 내보내기', exact: true });
  await kick.click();
  const dialog = page.getByRole('dialog', { name: '멤버를 내보낼까요?' });
  await expect(dialog).toContainText('테스터 support');
  await dialog.getByRole('button', { name: '취소', exact: true }).click();
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(5);
  await expect(kick).toBeFocused();
  await kick.click();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await kick.click();
  await dialog.getByRole('button', { name: '내보내기', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(deck).toHaveAttribute('data-status', 'OPEN');
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(4);
  await expect(deck.locator('.compact-seat-status')).toHaveText('모집 중');
  await expect(close).toBeEnabled();
  await expectNoPageOverflow(page);
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
  await expect(page.getByRole('article', { name: '테스트 방 voice 방 정보', exact: true })).toHaveAttribute('data-status', 'OPEN');
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
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  await expect(page.getByRole('article', { name: '둘이 랭크 방 정보', exact: true }).locator('.compact-seat')).toHaveCount(1);
});

test('빈자리 하나에 두 포지션을 표시하고 마이크는 빈자리 카드에만 표시한다', async ({ page }) => {
  await seedRooms(page, [room('options', [member('host', { voice: 'REQUIRED' })], {
    capacity: 2, desiredRoles: ['SUPPORT', 'TOP'], voice: 'NO_VOICE',
  })]);
  await login(page);
  const deck = page.getByRole('article', { name: '테스트 방 options 방 정보', exact: true });
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
  await vacancy.click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: '서포터', exact: true }).click();
  await dialog.getByRole('button', { name: '참여하기', exact: true }).click();
  const joined = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).rooms[0].members.find((value: RoomMember) => value.id === 'u-me'), STORAGE_KEY);
  expect(joined.roles).toEqual(['SUPPORT']);
  expect(joined.voice).toBe('NO_VOICE');
  await expect(page.getByRole('region', { name: '방 채팅과 음성' })).toBeVisible();
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

test('내 포지션 전체를 저장하고 서로 다른 포지션의 빈자리로 매칭한다', async ({ page }) => {
  await seedRooms(page, [
    room('all-support', [member('support-host')], { capacity: 2, desiredRoles: ['SUPPORT'], createdAt: Date.now() }),
    room('all-top', [member('top-host')], { capacity: 2, desiredRoles: ['TOP'], createdAt: Date.now() - 1000 }),
  ]);
  await login(page);
  const rail = page.getByRole('region', { name: '빠른 연결', exact: true });
  const own = rail.getByRole('radiogroup', { name: '내 포지션', exact: true });
  await own.getByRole('radio', { name: '전체', exact: true }).check();
  await expect(own.getByRole('radio', { checked: true })).toHaveCount(1);
  await expect(rail.getByRole('group', { name: '찾는 포지션', exact: true }).getByRole('button', { disabled: true })).toHaveCount(0);
  await expect(rail.getByRole('button', { name: '방 만들기', exact: true })).toBeDisabled();
  await rail.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '2명', exact: true }).click();
  await rail.getByRole('button', { name: '마이크 미사용', exact: true }).click();
  await login(page);
  await expect(own.getByRole('radio', { name: '전체', exact: true })).toBeChecked();
  await rail.getByRole('button', { name: '매칭 시작', exact: true }).click();
  const offers = page.getByRole('region', { name: '매칭 추천', exact: true });
  await expect(offers.getByRole('heading')).toHaveText('테스트 방 all-support');
  await offers.getByRole('button', { name: '다른 방', exact: true }).click();
  await expect(offers.getByRole('heading')).toHaveText('테스트 방 all-top');
  await offers.getByRole('button', { name: '자리 확인', exact: true }).click();
  const preview = page.getByRole('dialog', { name: '이 자리에 참여할까요?', exact: true });
  await expect(preview.getByRole('button', { name: '탑', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await preview.getByRole('button', { name: '취소', exact: true }).click();
  await own.getByRole('radio', { name: '미드', exact: true }).check();
  await expect(own.getByRole('radio', { name: '전체', exact: true })).not.toBeChecked();
  await expect(own.getByRole('radio', { checked: true })).toHaveCount(1);
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
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  const deck = page.getByRole('article', { name: '다섯 포지션 완성 방 정보', exact: true });
  await expect(deck.locator('.compact-seat .room-role-icons b')).toHaveText(['정글', '미드', '바텀', '서포터']);
});

test('칼바람 5인 방은 포지션을 고르지 않고도 만들 수 있다', async ({ page }) => {
  await login(page);
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await composer.getByRole('group', { name: '원하는 큐 타입', exact: true }).getByRole('button', { name: '칼바람', exact: true }).click();
  await expect(composer.getByRole('radiogroup', { name: '내 포지션', exact: true })).toHaveCount(0);
  await composer.getByLabel('한마디', { exact: true }).fill('포로 다섯');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  await expect(page.getByRole('article', { name: '포로 다섯 방 정보', exact: true }).locator('.compact-seat')).toHaveCount(4);
});

test('5인 방의 방장이 나가도 그 포지션을 다시 빈자리로 표시한다', async ({ page }) => {
  await seedRooms(page, [room('host-leaves', [member('u-me'), member('next-host', { roles: ['TOP'] })], {
    desiredRoles: ['TOP', 'JUNGLE', 'ADC', 'SUPPORT'],
  })]);
  await login(page);
  await page.getByRole('button', { name: '방 나가기', exact: true }).click();
  await page.getByRole('alert').getByRole('button', { name: '나가기', exact: true }).click();
  const deck = page.getByRole('article', { name: '테스트 방 host-leaves 방 정보', exact: true });
  await expect(deck.locator('.compact-seat .room-role-icons b')).toHaveText(['정글', '미드', '바텀', '서포터']);
  await expect(deck).toHaveAttribute('data-status', 'OPEN');
});

test('예약 방은 과거 시간을 거절하고 미래 시간으로 만든 즉시 채팅할 수 있다', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-09-27T03:30:00Z'));
  await login(page);
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await composer.getByRole('button', { name: '시간 선택', exact: true }).click();
  await composer.getByLabel('한마디', { exact: true }).fill('조금 뒤에 다 같이');
  await selectFullLineup(composer);
  await composer.getByRole('button', { name: '시작 날짜', exact: true }).click();
  const calendar = page.getByRole('dialog', { name: '시작 날짜 선택', exact: true });
  await expect(calendar.getByRole('button', { name: '2026년 9월 26일 토요일', exact: true })).toBeDisabled();
  await expect(calendar.getByRole('button', { name: '이전 달', exact: true })).toBeDisabled();
  await page.keyboard.press('Escape');
  // A date can become past while the user keeps the form open.
  await page.clock.setFixedTime(new Date('2026-09-27T10:30:00Z'));
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await expect(composer.getByRole('alert')).toContainText('현재보다 뒤');
  await composer.getByRole('button', { name: '시작 날짜', exact: true }).click();
  await calendar.getByRole('button', { name: '2026년 9월 28일 월요일', exact: true }).click();
  await expect(calendar).toHaveCount(0);
  await composer.getByRole('button', { name: '시작 시각', exact: true }).click();
  const hours = page.getByRole('dialog', { name: '시작 시각 선택', exact: true });
  await expect(hours.locator('[data-hour]')).toHaveCount(24);
  await hours.getByRole('button', { name: '오후 9시', exact: true }).click();
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  const deck = page.getByRole('article', { name: '조금 뒤에 다 같이 방 정보', exact: true });
  await expect(deck.locator('.compact-room-header time')).toBeVisible();
  await page.getByRole('group', { name: '우측 영역 선택' }).getByRole('button', { name: /방 채팅/ }).click();
  await expect(page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true })).toBeEnabled();
  await expect(deck).toHaveAttribute('data-status', 'OPEN');
});

test('시작 날짜 달력은 월 이동과 키보드 선택을 지원하고 좁은 화면에서도 잘리지 않는다', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-09-27T03:30:00Z'));
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  const rail = page.getByRole('region', { name: '빠른 연결', exact: true });
  await rail.getByRole('button', { name: '시간 선택', exact: true }).click();
  const trigger = rail.getByRole('button', { name: '시작 날짜', exact: true });
  await trigger.click();
  const calendar = page.getByRole('dialog', { name: '시작 날짜 선택', exact: true });
  const bounds = (await calendar.boundingBox())!;
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390);
  expect(bounds.y).toBeGreaterThanOrEqual(0);
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(844);
  await calendar.getByRole('button', { name: '다음 달', exact: true }).click();
  await expect(calendar.locator('header strong')).toHaveText('2026년 10월');
  await calendar.getByRole('button', { name: '2026년 10월 27일 화요일', exact: true }).press('ArrowRight');
  await expect(calendar.getByRole('button', { name: '2026년 10월 28일 수요일', exact: true })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(calendar).toHaveCount(0);
  await expect(trigger).toContainText('10월 28일');
  await expect(trigger).toBeFocused();
  await trigger.click();
  await calendar.getByRole('button', { name: '2026년 10월 28일 수요일', exact: true }).press('ArrowLeft');
  await page.keyboard.press('Escape');
  await expect(trigger).toContainText('10월 28일');
  await expect(trigger).toBeFocused();
  await rail.getByRole('button', { name: '지금', exact: true }).click();
  await expect(trigger).toHaveCount(0);
  await expectNoPageOverflow(page);
});

test('시각 선택은 과거 시간을 막고 날짜 변경·키보드 선택·취소를 지원한다', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-09-27T03:30:00Z'));
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  const rail = page.getByRole('region', { name: '빠른 연결', exact: true });
  const capacity = rail.getByRole('group', { name: '모집 인원', exact: true });
  const start = rail.getByRole('group', { name: '시작 시간 선택', exact: true });
  expect(await capacity.evaluate(el => getComputedStyle(el, '::before').backgroundColor))
    .toBe(await start.evaluate(el => getComputedStyle(el, '::before').backgroundColor));
  await expect(capacity.getByRole('button', { pressed: true })).toHaveCSS('color', 'rgb(255, 255, 255)');
  await start.getByRole('button', { name: '시간 선택', exact: true }).click();
  const trigger = rail.getByRole('button', { name: '시작 시각', exact: true });
  await trigger.click();
  const picker = page.getByRole('dialog', { name: '시작 시각 선택', exact: true });
  await expect(picker.locator('[data-hour]')).toHaveCount(24);
  await expect(picker.getByRole('button', { name: '오전 12시', exact: true })).toBeDisabled();
  const bounds = (await picker.boundingBox())!;
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390);
  expect(bounds.y).toBeGreaterThanOrEqual(0);
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(844);
  await picker.locator('[data-hour]:not(:disabled)').first().press('End');
  await expect(picker.getByRole('button', { name: '오후 11시', exact: true })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(picker).toHaveCount(0);
  await expect(trigger).toHaveText('오후 11시');
  await expect(trigger).toBeFocused();
  await trigger.click();
  await picker.getByRole('button', { name: '오후 11시', exact: true }).press('ArrowLeft');
  await page.keyboard.press('Escape');
  await expect(trigger).toHaveText('오후 11시');
  await expect(trigger).toBeFocused();
  await rail.getByRole('button', { name: '시작 날짜', exact: true }).click();
  await page.getByRole('dialog', { name: '시작 날짜 선택', exact: true }).getByRole('button', { name: '2026년 9월 28일 월요일', exact: true }).click();
  await trigger.click();
  await expect(picker.locator('[data-hour]:not(:disabled)')).toHaveCount(24);
  await picker.getByRole('button', { name: '오전 12시', exact: true }).click();
  await expect(trigger).toHaveText('오전 12시');
  await expect(picker).toHaveCount(0);
  await expectNoPageOverflow(page);
});

test.describe('통합 방 시간', () => {
  test.use({ timezoneId: 'Asia/Seoul' });
  test('지금과 예약 방이 한 목록에 보이고 오늘·내일·이후 날짜를 정각으로 표시한다', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-09-27T10:00:00+09:00'));
    await seedRooms(page, [room('now', [member('now-host')]),
      ...[
        ['today', '2026-09-27T15:00:00+09:00'],
        ['tomorrow', '2026-09-28T23:00:00+09:00'],
        ['later', '2026-09-29T21:00:00+09:00'],
      ].map(([id, availableFrom]) => room(id, [member(`${id}-host`)], { type: 'RESERVATION', availableFrom })),
    ]);
    await login(page);
    await expect(page.getByRole('tablist', { name: '매칭 시간', exact: true })).toHaveCount(0);
    await expect(page.locator('.room-deck')).toHaveCount(4);
    for (const [id, label] of [['now', '지금'], ['today', '오늘 오후 3시'], ['tomorrow', '내일 오후 11시'], ['later', '29일 오후 9시']]) {
      await expect(page.getByRole('article', { name: `테스트 방 ${id} 방 정보`, exact: true }).locator('.compact-room-header time')).toHaveText(label);
    }
    await page.getByRole('article', { name: '테스트 방 today 방 정보', exact: true }).locator('.compact-seat').first().click();
    await expect(page.getByRole('dialog').locator('time')).toHaveText('오늘 오후 3시');
  });
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

test('모션 감소 설정과 좁은 화면에서도 빈자리 참여 창을 열고 닫을 수 있다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await login(page);
  const deck = page.locator('.room-deck[data-status="OPEN"]').first();
  const seat = deck.locator('.compact-seat').first();
  await seat.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  const bounds = await dialog.boundingBox();
  expect(bounds).not.toBeNull();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(391);
  await expectNoPageOverflow(page);
  await dialog.getByRole('button', { name: '참여 창 닫기', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(seat).toBeFocused();
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
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  await expect(page.getByRole('article', { name: '자유 랭크 셋이서 방 정보', exact: true }).locator('.compact-member')).toHaveCount(3);
  await login(page); // The in-memory mock session resets on navigation; room/preferences stay in localStorage.
  await expect(page.getByRole('article', { name: '자유 랭크 셋이서 방 정보', exact: true })).toBeVisible();
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
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  await expect(page.getByRole('article', { name: '자유 랭크 세 명 방 정보', exact: true }).locator('.compact-member')).toHaveCount(3);
});

test('티어 범위는 두 번째 선택에 바로 적용하고 한 번만 선택하고 닫으면 기존 조건을 유지한다', async ({ page }) => {
  await seedRooms(page, [...['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND'].map((tier, i) => room(tier, [member(`host-${i}`, { tier: 'GOLD' })], { desiredTierRange: { minTier: tier, maxTier: tier } })), room('unrestricted', [member('any-host', { tier: 'DIAMOND' })])]);
  await login(page);
  const trigger = page.getByRole('button', { name: '모집 티어 범위', exact: true });
  const dialog = page.getByRole('dialog', { name: '모집 티어 범위', exact: true });
  await trigger.click();
  await expect(dialog.locator('.tier-range-option')).toHaveCount(10);
  await expect(dialog.getByRole('button', { name: '선택 취소', exact: true })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: /^(취소|적용)$/ })).toHaveCount(0);
  expect((await dialog.boundingBox())!.width).toBeLessThan((await page.locator('.room-deck').first().boundingBox())!.width);
  await dialog.getByRole('button', { name: '골드', exact: true }).click();
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: '선택 취소', exact: true })).toBeVisible();
  await expect(trigger).toContainText('모든 티어');
  await expect(page.locator('.room-deck')).toHaveCount(6);
  await dialog.getByRole('button', { name: '실버', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await expect(trigger).toContainText('실버~골드');
  await expect(page.locator('.room-deck')).toHaveCount(3);
  await trigger.click();
  const silver = dialog.getByRole('button', { name: '실버', exact: true });
  const gold = dialog.getByRole('button', { name: '골드', exact: true });
  const silverBounds = (await silver.boundingBox())!;
  const goldBounds = (await gold.boundingBox())!;
  expect(goldBounds.x - silverBounds.x - silverBounds.width).toBeGreaterThan(0);
  await expect(silver).toHaveCSS('border-top-right-radius', '9px');
  await expect(silver).toHaveCSS('border-bottom-right-radius', '9px');
  await expect(gold).toHaveCSS('border-top-left-radius', '9px');
  await expect(gold).toHaveCSS('border-bottom-left-radius', '9px');
  await dialog.getByRole('button', { name: '다이아몬드', exact: true }).click();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await expect(trigger).toContainText('실버~골드');
  await trigger.click();
  await dialog.getByRole('button', { name: '골드', exact: true }).click();
  await page.getByRole('group', { name: '찾는 큐 타입', exact: true }).getByRole('button', { name: '일반', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toContainText('실버~골드');
  await trigger.click();
  await dialog.getByRole('button', { name: '골드', exact: true }).click();
  await expect(dialog).toBeVisible();
  await expect(trigger).toContainText('실버~골드');
  await dialog.getByRole('button', { name: '골드', exact: true }).press('Enter');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toHaveText('골드');
  await expect(page.locator('.room-deck')).toHaveCount(2);
  await expect(page.getByRole('article', { name: '테스트 방 GOLD 방 정보', exact: true })).toBeVisible();
  await expect(page.getByRole('article', { name: '테스트 방 unrestricted 방 정보', exact: true })).toBeVisible();
  await expect(page.getByRole('article', { name: '테스트 방 PLATINUM 방 정보', exact: true })).toHaveCount(0);
  await trigger.click();
  await dialog.getByRole('button', { name: '골드', exact: true }).dblclick();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator('.room-deck')).toHaveCount(2);
  await trigger.click();
  const all = dialog.getByRole('button', { name: '선택 취소', exact: true });
  await expect(all.locator('svg')).toBeVisible();
  await expect(all).toHaveText('선택 취소');
  await all.click();
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toHaveText('모든 티어');
  await expect(page.locator('.room-deck')).toHaveCount(6);
  await trigger.click();
  await expect(all).toHaveCount(0);
  await dialog.getByRole('button', { name: '골드', exact: true }).click();
  await all.click();
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toHaveText('모든 티어');
});

test('방 필터는 아이콘과 이름을 한 줄에 배치하고 높이 44px로 정렬한다', async ({ page }) => {
  await login(page);
  const filters = page.locator('.room-filters');
  const modes = page.getByRole('group', { name: '찾는 큐 타입', exact: true });
  const modeBounds = (await modes.boundingBox())!;
  const filterBounds = (await filters.locator('.board-filter-line').boundingBox())!;
  expect(Math.abs(modeBounds.x - filterBounds.x)).toBeLessThan(1);
  expect(modeBounds.y + modeBounds.height).toBeLessThan(filterBounds.y);
  for (const board of [
    modes,
    filters.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '탑', exact: true }),
    filters.getByRole('group', { name: '마이크 필터', exact: true }),
    filters.getByRole('button', { name: '모집 티어 범위', exact: true }),
  ]) {
    const a = (await board.boundingBox())!;
    expect(a.height).toBe(44);
  }
  const primary = filters.locator('.room-filter-primary');
  await expect(primary.getByRole('group', { name: '방 시작 시간', exact: true })).toBeVisible();
  await expect(primary.getByRole('checkbox', { name: '모집 중인 방만', exact: true })).toBeVisible();
  for (const control of [primary.getByRole('group', { name: '방 시작 시간', exact: true }), primary.locator('.room-open-filter')]) {
    const bounds = (await control.boundingBox())!;
    expect(bounds.height).toBe(44);
    expect(Math.abs(bounds.y - modeBounds.y)).toBeLessThan(1);
  }
  const primaryFit = await primary.evaluate(element => ({ width: element.clientWidth, content: element.scrollWidth, gap: getComputedStyle(element).gap }));
  expect(primaryFit.content).toBeLessThanOrEqual(primaryFit.width);
  expect(primaryFit.gap).toBe('8px');
  await expect(page.locator('.room-board-count')).toHaveCount(0);
  const trigger = filters.getByRole('button', { name: '모집 티어 범위', exact: true });
  const originalWidth = (await trigger.boundingBox())!.width;
  await trigger.click();
  const picker = page.getByRole('dialog', { name: '모집 티어 범위', exact: true });
  await picker.getByRole('button', { name: '다이아몬드', exact: true }).click();
  await picker.getByRole('button', { name: '그랜드마스터', exact: true }).click();
  await expect(trigger).toHaveText('다이아몬드~그랜드마스터');
  expect((await trigger.boundingBox())!.width).toBe(originalWidth);
  const layout = await filters.evaluate(element => {
    const line = element.querySelector('.board-filter-line')!;
    const bounds = line.getBoundingClientRect();
    const controls = Array.from(line.children).map(control => {
      const rect = control.getBoundingClientRect();
      return { centerY: rect.y + rect.height / 2, right: rect.right };
    });
    const choices = Array.from(element.querySelectorAll('.board-mode-options .filter-mode,.tier-range-endpoint')).map(choice => {
      const icon = choice.querySelector('.rank-emblem,svg,img')!.getBoundingClientRect();
      const label = choice.querySelector(':scope > strong,:scope > span:last-child')!;
      const rect = label.getBoundingClientRect();
      return { inline: icon.right <= rect.left, centered: Math.abs(icon.y + icon.height / 2 - rect.y - rect.height / 2) < 1, clipped: label.scrollWidth > label.clientWidth };
    });
    return { right: bounds.right, controls, choices };
  });
  expect(layout.controls).toHaveLength(4);
  for (const control of layout.controls) {
    expect(Math.abs(control.centerY - layout.controls[0].centerY)).toBeLessThan(1);
    expect(control.right).toBeLessThanOrEqual(layout.right + 1);
  }
  for (const choice of layout.choices) {
    expect(choice.inline).toBe(true);
    expect(choice.centered).toBe(true);
    expect(choice.clipped).toBe(false);
  }
  const mic = filters.getByRole('group', { name: '마이크 필터', exact: true });
  await mic.getByRole('button', { name: '마이크 사용', exact: true }).click();
  await expect(mic.getByRole('button', { name: '마이크 사용', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await filters.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(trigger).toHaveText('모든 티어');
  await expect(mic.getByRole('button', { name: '마이크 사용', exact: true })).toHaveAttribute('aria-pressed', 'false');
});

test('시작 시간과 모집 상태를 함께 필터링하고 초기화하면 전체 방으로 돌아온다', async ({ page }) => {
  const later = new Date(Date.now() + 86_400_000).toISOString();
  await seedRooms(page, [
    room('now-open', [member('now-host')], { voice: 'REQUIRED' }),
    room('now-closed', [member('closed-host')], { status: 'CONFIRMED' }),
    room('later-open', [member('later-host')], { type: 'RESERVATION', availableFrom: later, voice: 'REQUIRED' }),
    room('later-full', [member('full-host'), member('full-guest', { roles: ['TOP'] })], { type: 'RESERVATION', availableFrom: later, capacity: 2 }),
    room('later-no-voice', [member('quiet-host')], { type: 'RESERVATION', availableFrom: later, voice: 'NO_VOICE' }),
    room('past', [member('past-host')], { type: 'RESERVATION', availableFrom: new Date(Date.now() - 86_400_000).toISOString(), status: 'CONFIRMED' }),
  ].map(value => ({ ...value, createdAt: 1 })));
  await login(page);
  const filters = page.getByRole('group', { name: '방 필터', exact: true });
  const time = filters.getByRole('group', { name: '방 시작 시간', exact: true });
  const openOnly = filters.getByRole('checkbox', { name: '모집 중인 방만', exact: true });
  const mic = filters.getByRole('group', { name: '마이크 필터', exact: true });
  const titles = page.locator('.room-deck h3');
  await expect(titles).toHaveCount(6);
  await time.getByRole('button', { name: '지금', exact: true }).click();
  await expect(titles).toHaveText(['테스트 방 now-open', '테스트 방 now-closed']);
  await openOnly.check();
  await expect(titles).toHaveText(['테스트 방 now-open']);
  await time.getByRole('button', { name: '나중', exact: true }).click();
  await expect(titles).toHaveText(['테스트 방 later-open', '테스트 방 later-no-voice']);
  await mic.getByRole('button', { name: '마이크 사용', exact: true }).click();
  await expect(titles).toHaveText(['테스트 방 later-open']);
  await expect(page.getByRole('group', { name: '시작 시간 선택', exact: true }).getByRole('button', { name: '지금', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await filters.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(titles).toHaveCount(6);
  await expect(time.getByRole('button', { name: '전체', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(openOnly).not.toBeChecked();
  await expect(mic.getByRole('button', { name: '마이크 사용', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await time.getByRole('button', { name: '지금', exact: true }).click();
  await openOnly.check();
  await mic.getByRole('button', { name: '마이크 미사용', exact: true }).click();
  await expect(titles).toHaveCount(0);
  await page.getByRole('button', { name: '필터 초기화', exact: true }).click();
  await expect(titles).toHaveCount(6);
  await expect(openOnly).not.toBeChecked();
  await page.setViewportSize({ width: 390, height: 844 });
  await time.getByRole('button', { name: '나중', exact: true }).click();
  await expect(titles).toHaveCount(3);
  await expect(openOnly).toBeVisible();
  await expectNoPageOverflow(page);
});

test('우측 티어 범위를 보관하고 방 생성 시 빈자리 조건으로 이어진다', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: '찾는 티어 범위', exact: true }).click();
  const picker = page.getByRole('dialog', { name: '찾는 티어 범위', exact: true });
  await picker.getByRole('button', { name: '실버', exact: true }).click();
  await picker.getByRole('button', { name: '골드', exact: true }).click();
  await expect(picker).toHaveCount(0);
  await login(page); // The in-memory mock session resets on navigation; room/preferences stay in localStorage.
  await expect(page.getByRole('button', { name: '찾는 티어 범위', exact: true })).toContainText('실버~골드');
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await expect(composer.getByRole('button', { name: '찾는 티어 범위', exact: true })).toContainText('실버~골드');
  await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '2명', exact: true }).click();
  await composer.getByLabel('한마디', { exact: true }).fill('티어 범위 확인');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  const seat = page.getByRole('article', { name: '티어 범위 확인 방 정보', exact: true }).locator('.compact-seat');
  await expect(seat).toContainText('실버~골드');
  await expect(seat).toHaveCSS('animation-name', 'vacant-seat-breathe');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(seat).toHaveCSS('animation-name', 'none');
  await login(page); // The in-memory mock session resets on navigation; room/preferences stay in localStorage.
  await expect(seat).toContainText('실버~골드');
});

test('빈자리는 열린 티어 조건도 양끝을 표시하고 물결표와 같은 너비의 티어를 중앙에 배치한다', async ({ page }) => {
  await seedRooms(page, [
    room('lower', [member('lower-host')], { desiredTierRange: { minTier: 'GOLD', maxTier: null } }),
    room('upper', [member('upper-host')], { desiredTierRange: { minTier: null, maxTier: 'GOLD' } }),
    room('single', [member('single-host')], { desiredTierRange: { minTier: 'GOLD', maxTier: 'GOLD' } }),
    room('long', [member('long-host')], { desiredTierRange: { minTier: 'SILVER', maxTier: 'GRANDMASTER' } }),
    room('all', [member('all-host')]),
  ]);
  await login(page);
  for (const [id, text] of [['lower', '골드~챌린저'], ['upper', '아이언~골드'], ['single', '골드'], ['long', '실버~그랜드마스터'], ['all', '아이언~챌린저']]) {
    const label = page.getByRole('article', { name: `테스트 방 ${id} 방 정보`, exact: true }).locator('.compact-seat .tier-range-label').first();
    await expect(label).toHaveText(text);
    const alignment = await label.evaluate(element => {
      const rect = element.getBoundingClientRect();
      const ranks = Array.from(element.querySelectorAll('.room-rank')).map(rank => rank.getBoundingClientRect());
      const middle = element.querySelector('.room-tier-separator')?.getBoundingClientRect();
      const seat = element.closest('.compact-seat')!.getBoundingClientRect();
      return { widthDifference: ranks.length === 2 ? Math.abs(ranks[0].width - ranks[1].width) : 0,
        centerOffset: middle ? Math.abs(middle.x + middle.width / 2 - seat.x - seat.width / 2) : 0,
        outsideCard: rect.left < seat.left || rect.right > seat.right };
    });
    expect(alignment.widthDifference).toBeLessThan(1);
    expect(alignment.centerOffset).toBeLessThan(1);
    expect(alignment.outsideCard).toBe(false);
  }
});

test('티어 범위를 벗어난 방의 빈자리 카드는 입장이 비활성화된다', async ({ page }) => {
  await seedRooms(page, [room('restricted', [member('host')], { desiredTierRange: { minTier: 'CHALLENGER', maxTier: 'CHALLENGER' } })]);
  await login(page);
  const deck = page.getByRole('article', { name: '테스트 방 restricted 방 정보', exact: true });
  for (const seat of await deck.locator('.compact-seat').all()) {
    await expect(seat).toBeDisabled();
    await expect(seat).toHaveAttribute('title', '방에서 찾는 티어 범위와 맞지 않아요');
  }
  await deck.getByRole('heading').click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('양쪽 티어 선택창은 한 줄을 유지하고 사이드바 밖에서 가로 스크롤된다', async ({ page }) => {
  await login(page);
  await page.getByRole('group', { name: '찾는 큐 타입', exact: true }).getByRole('button', { name: '2인 랭크', exact: true }).click();
  for (const width of [1600, 1200, 768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    for (const name of ['모집 티어 범위', '찾는 티어 범위']) {
      await page.getByRole('button', { name, exact: true }).click();
      const picker = page.getByRole('dialog', { name, exact: true });
      await expect(picker.locator('.tier-range-option')).toHaveCount(10);
      const layout = await picker.evaluate(element => {
        const panel = element.getBoundingClientRect();
        const track = element.querySelector('.tier-range-track')!;
        const choices = Array.from(track.children).map(choice => choice.getBoundingClientRect());
        const sidebar = document.querySelector<HTMLElement>('.app-shell>.sidebar')!;
        const sidebarRight = getComputedStyle(sidebar).visibility !== 'hidden' ? sidebar.getBoundingClientRect().right : 0;
        return {
          overflow: track.scrollWidth - track.clientWidth,
          allInside: choices.every(rect => rect.left >= panel.left && rect.right <= panel.right && rect.top >= panel.top && rect.bottom <= panel.bottom),
          rows: new Set(choices.map(rect => Math.round(rect.top))).size,
          outsideSidebar: panel.left >= sidebarRight + 11,
          onScreen: panel.left >= 0 && panel.right <= window.innerWidth && panel.top >= 0 && panel.bottom <= window.innerHeight,
        };
      });
      if (width === 1600) {
        expect(layout.overflow).toBeLessThanOrEqual(1);
        expect(layout.allInside).toBe(true);
      } else expect(layout.overflow).toBeGreaterThan(0);
      expect(layout.onScreen).toBe(true);
      expect(layout.outsideSidebar).toBe(true);
      expect(layout.rows).toBe(1);
      if (width === 1200) {
        await page.locator('.sidebar').hover();
        await expect(page.locator('.sidebar')).toHaveCSS('width', '232px');
        await expect.poll(() => picker.evaluate(element => element.getBoundingClientRect().left - document.querySelector('.sidebar')!.getBoundingClientRect().right)).toBeGreaterThanOrEqual(11);
        await picker.locator('header').hover();
      }
      await picker.getByRole('button', { name: '아이언', exact: true }).press('End');
      await expect(picker.getByRole('button', { name: '챌린저', exact: true })).toBeFocused();
      expect(await picker.locator('.tier-range-track').evaluate(element => {
        const bounds = element.getBoundingClientRect();
        const last = element.lastElementChild!.getBoundingClientRect();
        return last.left >= bounds.left && last.right <= bounds.right;
      })).toBe(true);
      await page.keyboard.press('Escape');
    }
    await expectNoPageOverflow(page);
  }
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
  expect(bounds!.width).toBeLessThan((await page.locator('.room-deck').first().boundingBox())!.width);
  await picker.getByRole('button', { name: '챌린저', exact: true }).click();
  await picker.getByRole('button', { name: '챌린저', exact: true }).click();
  await expect(picker).toHaveCount(0);
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
  await expect(page.getByRole('article', { name: '테스트 방 seeking-top 방 정보', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '찾는 티어 범위', exact: true }).click();
  const picker = page.getByRole('dialog', { name: '찾는 티어 범위', exact: true });
  await picker.getByRole('button', { name: '골드', exact: true }).click();
  await picker.getByRole('button', { name: '골드', exact: true }).click();
  await expect(page.getByRole('button', { name: '찾는 티어 범위', exact: true })).toHaveText('골드');
  await expect(page.getByRole('button', { name: '모집 티어 범위', exact: true })).toHaveText('모든 티어');
  await expect(page.locator('.room-deck')).toHaveCount(1);
  const composer = page.getByRole('region', { name: '빠른 연결', exact: true });
  await expect(composer.getByRole('button', { name: '찾는 티어 범위', exact: true })).toHaveText('골드');
  await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '2명', exact: true }).click();
  await composer.getByLabel('한마디', { exact: true }).fill('골드만 함께해요');
  await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
  await page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true }).getByRole('button', { name: '방 올리기', exact: true }).click();
  await expect(page.getByRole('article', { name: '골드만 함께해요 방 정보', exact: true }).locator('.compact-seat .tier-range-label')).toHaveText('골드');
});

test('요약 확인 뒤에만 방을 만들며 저장 실패 후에도 초안과 요약을 유지한다', async ({ page }) => {
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
  const preview = page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true });
  await expect(preview).toContainText(title);
  await expect(page.locator('.room-home')).not.toHaveClass(/has-active-room/);
  await preview.getByRole('button', { name: '방 올리기', exact: true }).click();
  await expect(preview.getByRole('alert')).toContainText('방 정보를 저장할 수 없어요');
  await expect(rail.getByLabel('한마디', { exact: true })).toHaveValue(title);
  await expect(page.locator('.room-home')).not.toHaveClass(/has-active-room/);
  await page.evaluate(() => {
    const prototype = Storage.prototype as Storage & { originalRoomSetItem?: Storage['setItem'] };
    prototype.setItem = prototype.originalRoomSetItem!;
    delete prototype.originalRoomSetItem;
  });
  await preview.getByRole('button', { name: '방 올리기', exact: true }).click();
  const deck = page.getByRole('article', { name: `${title} 방 정보`, exact: true });
  await expect(deck).toBeVisible();
  await expect(deck.locator('.compact-member:not(.compact-seat) .room-role-icons b')).toHaveText(['바텀']);
  await expect(deck.locator('.compact-seat .room-role-icons b')).toHaveText(['서포터']);
  await expect(deck.locator('.compact-seat').getByRole('img', { name: '마이크 미사용', exact: true })).toBeVisible();
  await page.getByRole('group', { name: '우측 영역 선택' }).getByRole('button', { name: /방 채팅/ }).click();
  await expect(page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true })).toBeEnabled();
  await login(page);
  await expect(deck).toBeVisible();
  await expect(deck.locator('h3')).toHaveText(title);
});

test('방 요약에서 조건을 확인하고 취소하면 게시하지 않으며 모션 감소 설정을 지킨다', async ({ page }) => {
  await login(page);
  const rail = page.getByRole('region', { name: '빠른 연결', exact: true });
  await rail.getByRole('button', { name: '2인 랭크', exact: true }).click();
  await rail.getByRole('radiogroup', { name: '내 포지션', exact: true }).getByRole('radio', { name: '바텀', exact: true }).check();
  await rail.locator('.intro-role-options[aria-label="찾는 포지션"]').getByRole('button', { name: '서포터', exact: true }).click();
  await rail.getByRole('button', { name: '마이크 사용', exact: true }).click();
  await rail.getByRole('button', { name: '찾는 티어 범위', exact: true }).click();
  const tiers = page.getByRole('dialog', { name: '찾는 티어 범위', exact: true });
  await tiers.getByRole('button', { name: '골드', exact: true }).click();
  await tiers.getByRole('button', { name: '골드', exact: true }).click();
  await rail.getByLabel('한마디', { exact: true }).fill('함께 바텀 듀오해요');
  const create = rail.getByRole('button', { name: '방 만들기', exact: true });
  const preview = page.getByRole('dialog', { name: '이대로 방을 만들까요?', exact: true });
  const roomCount = await page.locator('.room-deck').count();
  await create.click();
  await expect(preview).toContainText('함께 바텀 듀오해요');
  await expect(preview.locator('.room-preview-conditions>div').filter({ hasText: '게임 모드' })).toContainText('2인 랭크2명');
  await expect(preview.locator('.room-preview-conditions>div').filter({ hasText: '내 포지션' })).toContainText('바텀');
  await expect(preview.locator('.room-preview-conditions>div').filter({ hasText: '찾는 포지션' })).toContainText('서포터');
  await expect(preview.locator('.room-preview-conditions>div').filter({ hasText: '찾는 티어' })).toContainText('골드');
  await expect(preview.getByRole('img', { name: '마이크 사용', exact: true })).toBeVisible();
  await expect(page.locator('.room-home')).not.toHaveClass(/has-active-room/);
  await expect(page.locator('.room-deck')).toHaveCount(roomCount);
  await expect(preview.getByRole('button', { name: '요약 닫기', exact: true })).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(preview.getByRole('button', { name: '방 올리기', exact: true })).toBeFocused();
  await preview.getByRole('button', { name: '취소', exact: true }).click();
  await expect(preview).toHaveCount(0);
  await expect(create).toBeFocused();
  await expect(rail.getByLabel('한마디', { exact: true })).toHaveValue('함께 바텀 듀오해요');
  await expect(page.locator('.room-deck')).toHaveCount(roomCount);
  await create.click();
  await page.keyboard.press('Escape');
  await expect(preview).toHaveCount(0);
  await expect(create).toBeFocused();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await create.click();
  const bounds = await preview.boundingBox();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390);
  await preview.getByRole('button', { name: '방 올리기', exact: true }).click();
  const own = page.getByRole('article', { name: '함께 바텀 듀오해요 방 정보', exact: true });
  await expect(own).toBeVisible();
  await expect(own).toHaveCSS('animation-name', 'none');
  await expect(page.locator('.room-deck.is-own')).toHaveCount(1);
  await expectNoPageOverflow(page);
});

test('참여 확인 중 포지션이 차면 입장을 막고 다른 빈자리를 선택할 수 있다', async ({ page }) => {
  await seedRooms(page, [room('race', [member('host', { roles: ['MID'] })], { capacity: 3, desiredRoles: ['TOP', 'SUPPORT'] })]);
  await login(page);
  const deck = page.getByRole('article', { name: '테스트 방 race 방 정보', exact: true });
  await deck.getByRole('button', { name: '탑 자리 참여', exact: true }).click();
  await page.evaluate(({ key, entrant }) => {
    const snapshot = JSON.parse(localStorage.getItem(key)!);
    snapshot.rooms[0].members.push(entrant);
    localStorage.setItem(key, JSON.stringify(snapshot));
    window.dispatchEvent(new StorageEvent('storage', { key }));
  }, { key: STORAGE_KEY, entrant: member('new-top', { roles: ['TOP'] }) });
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: '참여하기', exact: true })).toBeDisabled();
  await expect(dialog.getByRole('alert')).toContainText('더 이상 모집하지 않아요');
  await dialog.getByRole('button', { name: '취소', exact: true }).click();
  await deck.getByRole('button', { name: '서포터 자리 참여', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: '참여하기', exact: true }).click();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  const roles = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).rooms[0].members.map((value: RoomMember) => value.roles), STORAGE_KEY);
  expect(roles).toEqual([['MID'], ['TOP'], ['SUPPORT']]);
});

test('빈자리 참여 저장이 실패해도 선택을 유지하고 재시도하면 한 번만 입장한다', async ({ page }) => {
  await seedRooms(page, [room('join-retry', [member('host')], { capacity: 2, desiredRoles: ['TOP', 'SUPPORT'] })]);
  await login(page);
  const deck = page.getByRole('article', { name: '테스트 방 join-retry 방 정보', exact: true });
  await deck.locator('.compact-seat').click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: '서포터', exact: true }).click();
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.startsWith('qm:room-board:v1:')) {
        Storage.prototype.setItem = original;
        throw new DOMException('Storage is full', 'QuotaExceededError');
      }
      return original.call(this, key, value);
    };
  });
  await dialog.getByRole('button', { name: '참여하기', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('방 정보를 저장할 수 없어요');
  await expect(dialog.getByRole('button', { name: '서포터', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.room-home')).not.toHaveClass(/has-active-room/);
  await dialog.getByRole('button', { name: '참여하기', exact: true }).dblclick();
  await expect(dialog).toHaveCount(0);
  await expect(deck.locator('.compact-member:not(.compact-seat)')).toHaveCount(2);
});

test('빠른 연결 추천도 카드 펼침 없이 해당 빈자리로 참여한다', async ({ page }) => {
  await seedRooms(page, [room('recommended', [member('host', { roles: ['MID'] })], {
    modeKey: 'SOLO_DUO_RANKED', capacity: 2, desiredRoles: ['SUPPORT'], voice: 'REQUIRED',
  })]);
  await login(page);
  const form = page.getByRole('region', { name: '빠른 연결', exact: true });
  await form.getByRole('group', { name: '원하는 큐 타입', exact: true }).getByRole('button', { name: '2인 랭크', exact: true }).click();
  await form.getByRole('radiogroup', { name: '내 포지션', exact: true }).getByRole('radio', { name: '서포터', exact: true }).check();
  await form.getByRole('group', { name: '음성', exact: true }).getByRole('button', { name: '마이크 사용', exact: true }).click();
  const start = form.getByRole('button', { name: '매칭 시작', exact: true });
  await start.hover();
  await expect(start).toHaveCSS('border-color', 'rgb(195, 160, 255)');
  expect(await start.evaluate(element => getComputedStyle(element, '::before').animationName)).toBe('room-match-sheen');
  await start.click();
  await page.getByRole('region', { name: '매칭 추천', exact: true }).getByRole('button', { name: '자리 확인', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: '서포터', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.room-spread-backdrop')).toHaveCount(0);
  await dialog.getByRole('button', { name: '참여하기', exact: true }).click();
  await expect(page.getByRole('region', { name: '방 채팅과 음성' })).toBeVisible();
});

test('사진과 이름에서 상세 프로필을 열고 닫으면 원래 위치로 돌아온다', async ({ page }) => {
  await seedRooms(page, [room('profile', [member('host', { nickname: '밤하늘 서포터', roles: ['SUPPORT'], champions: ['Lulu', 'Thresh'], bio: '차분하게 함께해요.\n브리핑 좋아해요.' }), member('unknown', { tier: null, winRate: null, kda: null })])]);
  await login(page);
  const deck = page.getByRole('article', { name: '테스트 방 profile 방 정보' });
  const trigger = deck.getByRole('button', { name: '밤하늘 서포터 프로필 보기' });
  const nameColor = await trigger.evaluate(element => getComputedStyle(element).color);
  await trigger.hover();
  await expect(trigger).toHaveCSS('color', nameColor);
  await expect.poll(() => trigger.evaluate(element => getComputedStyle(element, '::before').backgroundColor)).toBe('rgba(255, 255, 255, 0.043)');
  await trigger.locator('.room-member-avatar').click();
  const dialog = page.getByRole('dialog', { name: '밤하늘 서포터 프로필' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('img', { name: '방장', exact: true })).toBeVisible();
  await expect(dialog).toContainText('골드 II');
  await expect(dialog).toContainText('서포터');
  await expect(dialog).toContainText('50%');
  await expect(dialog).toContainText('2.00');
  await expect(dialog).toContainText('차분하게 함께해요.');
  await expect(dialog.getByRole('img', { name: '룰루 초상화' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
  await trigger.getByText('밤하늘 서포터', { exact: true }).click();
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: '프로필 닫기' }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await deck.getByRole('button', { name: '테스터 unknown 프로필 보기' }).click();
  const unknown = page.getByRole('dialog', { name: '테스터 unknown 프로필' });
  await expect(unknown.locator('.room-unknown-stat')).toHaveCount(2);
  await expectNoPageOverflow(page);
  await unknown.getByRole('button', { name: '프로필 닫기' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('내 방을 유지한 채 추천을 찾고 채팅 초안과 음성을 유지한다', async ({ page }) => {
  await seedRooms(page, [
    room('my-room', [member('u-me', { roles: ['MID'] }), member('friend', { roles: ['JUNGLE'] })]),
    room('next-room', [member('new-host', { roles: ['MID'] })], { capacity: 2, desiredRoles: ['SUPPORT'], voice: 'REQUIRED' }),
  ]);
  await login(page);
  const chat = page.getByRole('region', { name: '방 채팅과 음성' });
  const input = chat.getByRole('textbox', { name: '방에 메시지 보내기' });
  await input.fill('아직 작성 중인 메시지');
  await chat.getByRole('region', { name: '방 음성 채널' }).getByRole('button', { name: '참여', exact: true }).click();
  await expect(chat.getByRole('heading', { name: '음성 미리보기' })).toBeVisible();
  const views = page.getByRole('group', { name: '우측 영역 선택' });
  const railWidth = (await views.boundingBox())!.width;
  const tabWidths = await views.getByRole('button').evaluateAll(buttons => buttons.map(button => button.getBoundingClientRect().width));
  expect(Math.abs(tabWidths[0] - tabWidths[1])).toBeLessThan(1);
  await views.getByRole('button', { name: '탐색 · 매칭' }).click();
  const form = page.getByRole('region', { name: '빠른 연결' });
  await expect(form).toBeVisible();
  await expect(chat).toBeHidden();
  expect((await views.boundingBox())!.width).toBe(railWidth);
  await form.getByRole('group', { name: '모집 인원' }).getByRole('button', { name: '2명', exact: true }).click();
  await form.getByRole('radiogroup', { name: '내 포지션' }).getByRole('radio', { name: '서포터', exact: true }).check();
  await form.getByRole('group', { name: '음성', exact: true }).getByRole('button', { name: '마이크 사용' }).click();
  await form.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await page.getByRole('region', { name: '매칭 추천' }).getByRole('button', { name: '자리 확인' }).click();
  const preview = page.getByRole('dialog');
  await expect(preview).toContainText('테스트 방 my-room');
  await expect(preview.getByRole('button', { name: '이 방으로 이동' })).toBeEnabled();
  await preview.getByRole('button', { name: '취소', exact: true }).click();
  const own = page.getByRole('article', { name: '테스트 방 my-room 방 정보' });
  await expect(own.getByRole('button', { name: 'QueueMaster 프로필 보기' })).toBeVisible();
  await page.evaluate(key => {
    const data = JSON.parse(localStorage.getItem(key)!);
    data.rooms.find((room: GameRoom) => room.id === 'my-room').messages.push({ id: 'new-message', authorId: 'friend', text: '천천히 찾고 오세요', createdAt: Date.now() });
    localStorage.setItem(key, JSON.stringify(data));
    window.dispatchEvent(new StorageEvent('storage', { key }));
  }, STORAGE_KEY);
  await expect(views.getByRole('img', { name: '내 방 새 소식' })).toBeVisible();
  await views.getByRole('button', { name: /방 채팅/ }).click();
  await expect(input).toHaveValue('아직 작성 중인 메시지');
  await expect(chat.getByRole('heading', { name: '음성 미리보기' })).toBeVisible();
  await expect(chat.getByRole('log')).toContainText('천천히 찾고 오세요');
  await expect(views.getByRole('img', { name: '내 방 새 소식' })).toHaveCount(0);
  await chat.getByRole('button', { name: '테스터 friend 프로필 보기' }).first().click();
  await expect(page.getByRole('dialog', { name: '테스터 friend 프로필' })).toBeVisible();
});

test('다른 방 이동은 저장에 성공한 뒤에만 기존 방에서 나가고 방장을 넘긴다', async ({ page }) => {
  await seedRooms(page, [
    room('origin', [member('u-me', { roles: ['MID'] }), member('friend', { roles: ['JUNGLE'] })]),
    room('destination', [member('host')], { capacity: 2, desiredRoles: ['SUPPORT'] }),
  ]);
  await login(page);
  await page.getByRole('article', { name: '테스트 방 destination 방 정보' }).getByRole('button', { name: '서포터 자리 참여' }).click();
  const preview = page.getByRole('dialog');
  await expect(preview).toContainText('방장은 남은 멤버에게 넘어가요');
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.startsWith('qm:room-board:v1:')) { Storage.prototype.setItem = original; throw new DOMException('Full', 'QuotaExceededError'); }
      return original.call(this, key, value);
    };
  });
  await preview.getByRole('button', { name: '이 방으로 이동' }).click();
  await expect(preview.getByRole('alert')).toContainText('방 정보를 저장할 수 없어요');
  let rooms = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).rooms as GameRoom[], STORAGE_KEY);
  expect(rooms.find(room => room.id === 'origin')!.ownerId).toBe('u-me');
  expect(rooms.find(room => room.id === 'destination')!.members).toHaveLength(1);
  await preview.getByRole('button', { name: '이 방으로 이동' }).click();
  await expect(preview).toHaveCount(0);
  rooms = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).rooms as GameRoom[], STORAGE_KEY);
  const origin = rooms.find(room => room.id === 'origin')!;
  const destination = rooms.find(room => room.id === 'destination')!;
  expect(origin.ownerId).toBe('friend');
  expect(origin.members.map(member => member.id)).toEqual(['friend']);
  expect(origin.desiredRoles).toContain('MID');
  expect(destination.members.find(member => member.id === 'u-me')!.roles).toEqual(['SUPPORT']);
  expect(destination.status).toBe('CONFIRMED');
  await expect(page.getByRole('region', { name: '방 채팅과 음성' }).getByRole('heading', { name: '테스트 방 destination' })).toBeVisible();
});

test('확인 중 대상 방이 마감되어도 기존 방 참여는 유지된다', async ({ page }) => {
  await seedRooms(page, [room('stay', [member('u-me')]), room('closed-next', [member('host')], { capacity: 2, desiredRoles: ['TOP'] })]);
  await login(page);
  await page.getByRole('article', { name: '테스트 방 closed-next 방 정보' }).getByRole('button', { name: '탑 자리 참여' }).click();
  await page.evaluate(key => {
    const snapshot = JSON.parse(localStorage.getItem(key)!);
    snapshot.rooms.find((room: GameRoom) => room.id === 'closed-next').status = 'CONFIRMED';
    localStorage.setItem(key, JSON.stringify(snapshot));
    window.dispatchEvent(new StorageEvent('storage', { key }));
  }, STORAGE_KEY);
  const preview = page.getByRole('dialog');
  await expect(preview.getByRole('button', { name: '이 방으로 이동' })).toBeDisabled();
  await preview.getByRole('button', { name: '취소', exact: true }).click();
  await expect(page.getByRole('region', { name: '방 채팅과 음성' }).getByRole('heading', { name: '테스트 방 stay' })).toBeVisible();
});


test('단일 선택 배경이 끊기지 않고 이동하며 키보드와 화면 크기 변경을 따라간다', async ({ page }) => {
  await login(page);
  const modes = page.getByRole('group', { name: '찾는 큐 타입', exact: true });
  const before = await modes.evaluate(element => new DOMMatrixReadOnly(getComputedStyle(element, '::before').transform).m41);
  await modes.getByRole('button', { name: '칼바람', exact: true }).click();
  const motion = await modes.evaluate(element => {
    const animation = element.getAnimations({ subtree: true }).find(item => item instanceof CSSTransition && item.transitionProperty === 'transform');
    if (!animation) return null;
    animation.pause();
    animation.currentTime = 100;
    const middle = new DOMMatrixReadOnly(getComputedStyle(element, '::before').transform).m41;
    const opacity = getComputedStyle(element, '::before').opacity;
    animation.finish();
    const end = new DOMMatrixReadOnly(getComputedStyle(element, '::before').transform).m41;
    return { middle, end, opacity };
  });
  expect(motion).not.toBeNull();
  expect(motion!.middle).toBeGreaterThan(before);
  expect(motion!.middle).toBeLessThan(motion!.end);
  expect(motion!.opacity).toBe('1');

  const own = page.getByRole('radiogroup', { name: '내 포지션', exact: true });
  await own.getByRole('radio', { name: '탑', exact: true }).check();
  await own.getByRole('radio', { name: '탑', exact: true }).press('ArrowRight');
  await expect(own.getByRole('radio', { name: '정글', exact: true })).toBeChecked();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => own.evaluate(element => {
    const selected = element.querySelector<HTMLInputElement>('input:checked')!.parentElement!;
    const thumb = getComputedStyle(element, '::before');
    return Math.abs(new DOMMatrixReadOnly(thumb.transform).m41 - selected.offsetLeft);
  })).toBeLessThanOrEqual(1);
  await expectNoPageOverflow(page);

  await page.emulateMedia({ reducedMotion: 'reduce' });
  await own.getByRole('radio', { name: '미드', exact: true }).check();
  const reduced = await own.evaluate(element => ({ duration: getComputedStyle(element, '::before').transitionDuration, opacity: getComputedStyle(element, '::before').opacity }));
  expect(parseFloat(reduced.duration)).toBeLessThanOrEqual(0.001);
  expect(reduced.opacity).toBe('1');
});
