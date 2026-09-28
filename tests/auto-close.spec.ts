import { expect, test } from '@playwright/test';
import { AUTO_CLOSE_GRACE_MS, AUTO_CLOSE_IDLE_MS, autoClosePhase, canAutoClose, nextAutoCloseAt } from '../src/rooms/autoClose';
import type { GameRoom, RoomMember } from '../src/rooms/types';

const member: RoomMember = { id: 'host', nickname: '호스트', avatarUrl: null, tier: null, division: null, winRate: null, kda: null, roles: [], champions: [], bio: '', voice: 'OPTIONAL' };
const base: GameRoom = { id: 'room', game: 'LOL', modeKey: 'NORMAL_DRAFT', type: 'REALTIME', title: '함께해요', ownerId: 'host', capacity: 5, members: [member, { ...member, id: 'guest' }], desiredRoles: [], voice: 'OPTIONAL', status: 'OPEN', createdAt: 1000, availableFrom: null, messages: [] };
const started = 1000;
const deadline = started + AUTO_CLOSE_IDLE_MS + AUTO_CLOSE_GRACE_MS;
const scheduled = { ...base, autoCloseAt: deadline };

test('구성원 변경 후 10분 대기와 60초 예고를 거친다', () => {
  expect(nextAutoCloseAt(base, started)).toBe(deadline);
  expect(autoClosePhase(scheduled, started)).toBe('waiting');
  expect(autoClosePhase(scheduled, deadline - AUTO_CLOSE_GRACE_MS - 1)).toBe('waiting');
  expect(autoClosePhase(scheduled, deadline - AUTO_CLOSE_GRACE_MS)).toBe('warning');
  expect(autoClosePhase(scheduled, deadline - 1)).toBe('warning');
  expect(autoClosePhase(scheduled, deadline)).toBe('due');
});
test('혼자 대기·예약·정원 충족·마감 방은 유휴 자동 마감 대상이 아니다', () => {
  for (const change of [{ members: [member] }, { type: 'RESERVATION' as const }, { capacity: 2 }, { status: 'CONFIRMED' as const }]) {
    expect(nextAutoCloseAt({ ...base, ...change }, started)).toBeNull();
    expect(autoClosePhase({ ...scheduled, ...change }, deadline)).toBe('inactive');
  }
});
test('기존 저장본에 시간 필드가 없으면 갑자기 마감하지 않는다', () => {
  for (const autoCloseAt of [undefined, null, NaN, Infinity]) expect(autoClosePhase({ ...base, autoCloseAt }, deadline)).toBe('inactive');
});
test('방장만 만료 시점에 자동 마감할 수 있다', () => {
  expect(canAutoClose(scheduled, 'host', deadline, deadline)).toBe(true);
  expect(canAutoClose(scheduled, 'guest', deadline, deadline)).toBe(false);
  expect(canAutoClose(scheduled, 'host', deadline, deadline - 1)).toBe(false);
});
test('계속 모집·새 멤버·방장 변경 이후의 늦은 마감 이벤트를 막는다', () => {
  const extended = { ...base, autoCloseAt: nextAutoCloseAt(base, deadline - 1) };
  expect(canAutoClose(extended, 'host', deadline, deadline)).toBe(false);
  expect(canAutoClose({ ...scheduled, ownerId: 'guest' }, 'host', deadline, deadline)).toBe(false);
  expect(canAutoClose({ ...scheduled, status: 'CONFIRMED' }, 'host', deadline, deadline)).toBe(false);
});
