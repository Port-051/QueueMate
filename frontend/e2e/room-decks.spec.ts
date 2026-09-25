import { expect, test, type Page } from '@playwright/test';
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

test('방은 세 열 덱으로 보이고 클릭하면 페이지 이동 없이 전체 카드를 중앙에 펼친다', async ({ page }) => {
  await login(page);
  const grid = page.locator('.room-deck-grid');
  await expect(grid).toBeVisible();
  await expect.poll(() => grid.evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(3);
  expect(await grid.locator('.room-deck').count()).toBeGreaterThanOrEqual(6);
  const deck = grid.locator('.room-deck[data-status="OPEN"]').first();
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

test('방 요약 평균은 전적이 있는 사람만 계산하고 펼치면 각 사람의 전적을 보여준다', async ({ page }) => {
  await seedRooms(page, [room('averages', [
    member('one', { winRate: 50, kda: 2 }),
    member('two', { winRate: 60, kda: 4, roles: ['SUPPORT'] }),
    member('three', { tier: null, division: null, winRate: null, kda: null, roles: ['JUNGLE'] }),
  ])]);
  await login(page);
  const deck = page.getByRole('button', { name: '테스트 방 averages 방 정보', exact: true });
  await expect(deck).toContainText('55%');
  await expect(deck).toContainText('3.0');
  await expect(deck).toContainText(/3\s*\/\s*5/);
  await deck.click();
  const cards = page.getByRole('dialog').locator('.room-member-card');
  await expect(cards).toHaveCount(3);
  await expect(cards.filter({ hasText: '테스터 one' })).toContainText('50%');
  await expect(cards.filter({ hasText: '테스터 two' })).toContainText('60%');
  await expect(cards.filter({ hasText: '테스터 three' })).not.toContainText('0%');
});

test('다섯 명 방을 만들면 한 열 목록과 대화를 함께 쓰고 확정해도 방과 채팅이 유지된다', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: '방 만들기', exact: true }).click();
  const composer = page.getByRole('region', { name: '방 만들기', exact: true });
  await composer.getByLabel('방 제목', { exact: true }).fill('우리 다섯 명의 방');
  await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '5명', exact: true }).click();
  await composer.getByRole('button', { name: '방 열기', exact: true }).click();
  await expect(page.locator('.room-home')).toHaveClass(/has-active-room/);
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
  await expect(page.getByRole('textbox', { name: '방에 메시지 보내기', exact: true })).toBeEnabled();
  await page.locator('.side-nav').getByRole('link', { name: '프로필', exact: true }).press('Enter');
  await page.locator('.side-nav').getByRole('link', { name: '홈', exact: true }).press('Enter');
  await expect(page.getByText('확정 전부터 여기서 이야기해요', { exact: true })).toBeVisible();
  await page.reload();
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
  await expect(deck).toContainText(/5\s*\/\s*5/);
  await expect(page.getByRole('button', { name: /내보내기/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '모집 마감', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '방 나가기', exact: true }).click();
  await page.getByRole('alert').getByRole('button', { name: '나가기', exact: true }).click();
  await page.getByRole('checkbox', { name: '모집 중인 방만', exact: true }).uncheck();
  await expect(deck).toHaveAttribute('data-status', 'CONFIRMED');
  await expect(deck).toContainText(/4\s*\/\s*5/);
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
  await expect(deck).toContainText(/2\s*\/\s*5/);
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
  await page.getByRole('button', { name: '방 만들기', exact: true }).click();
  const composer = page.getByRole('region', { name: '방 만들기', exact: true });
  const modes = composer.getByRole('group', { name: '게임 모드', exact: true });
  await expect(modes.getByRole('button', { name: '일반', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await modes.getByRole('button', { name: '랭크', exact: true }).click();
  await modes.getByRole('button', { name: '랭크', exact: true }).click();
  await expect(modes.getByRole('button', { pressed: true })).toHaveCount(1);
  await expect(composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button')).toHaveText(['2명']);
  await composer.getByLabel('방 제목', { exact: true }).fill('둘이 랭크');
  await composer.getByRole('button', { name: '방 열기', exact: true }).click();
  await expect(page.getByRole('button', { name: '둘이 랭크 방 정보', exact: true })).toContainText(/1\s*\/\s*2/);
});

test('예약 방은 과거 시간을 거절하고 미래 시간으로 만든 즉시 채팅할 수 있다', async ({ page }) => {
  await login(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await page.getByRole('button', { name: '방 만들기', exact: true }).click();
  const composer = page.getByRole('region', { name: '방 만들기', exact: true });
  await composer.getByLabel('방 제목', { exact: true }).fill('조금 뒤에 다 같이');
  await composer.getByLabel('시작 시간', { exact: true }).fill('2000-01-01T12:00');
  await composer.getByRole('button', { name: '방 열기', exact: true }).click();
  await expect(composer.getByRole('alert')).toContainText('현재보다 뒤');
  const next = await page.evaluate(() => {
    const date = new Date(Date.now() + 2 * 60 * 60_000);
    date.setMinutes(0, 0, 0);
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
  });
  await composer.getByLabel('시작 시간', { exact: true }).fill(next);
  await composer.getByRole('button', { name: '방 열기', exact: true }).click();
  const deck = page.getByRole('button', { name: '조금 뒤에 다 같이 방 정보', exact: true });
  await expect(deck.locator('.room-start-time')).toBeVisible();
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
