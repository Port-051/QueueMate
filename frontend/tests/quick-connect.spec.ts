import { expect, test } from '@playwright/test';
import { quickConnectCandidates, type QuickConnectCriteria } from '../src/rooms/quickConnect';
import type { GameRoom, RoomMember } from '../src/rooms/types';

const member: RoomMember = { id: 'host', nickname: '호스트', avatarUrl: null, tier: null, division: null, winRate: null, kda: null, roles: ['MID'], champions: [], bio: '서로 존중해요', voice: 'OPTIONAL' };
const base: GameRoom = { id: 'room', game: 'LOL', modeKey: 'NORMAL_DRAFT', type: 'REALTIME', title: '함께해요', ownerId: 'host', capacity: 5, members: [member], desiredRoles: ['SUPPORT'], voice: 'REQUIRED', status: 'OPEN', createdAt: 10, availableFrom: null, messages: [] };
const criteria: QuickConnectCriteria = { game: 'LOL', modeKey: 'NORMAL_DRAFT', role: 'SUPPORT', voice: 'REQUIRED', userId: 'me' };

test('같은 원본 방을 제안하며 탐색이 방이나 멤버를 변경하지 않는다', () => {
  const rooms = [structuredClone(base)];
  const before = structuredClone(rooms);
  expect(quickConnectCandidates(rooms, criteria)[0]).toBe(rooms[0]);
  expect(rooms).toEqual(before);
});

test('다른 게임·모드·예약·확정·정원초과·이미 참여한 방은 제외한다', () => {
  const changes: Partial<GameRoom>[] = [
    { game: 'VALORANT' }, { modeKey: 'ARAM' }, { type: 'RESERVATION' },
    { status: 'CONFIRMED' }, { capacity: 1 }, { members: [{ ...member, id: 'me' }] },
  ];
  for (const change of changes) expect(quickConnectCandidates([{ ...base, ...change }], criteria)).toEqual([]);
});

test('내 역할 미선택·유효하지 않은 역할·방의 모집 역할 불일치를 제외한다', () => {
  for (const role of ['', 'TOP', 'INVALID']) expect(quickConnectCandidates([base], { ...criteria, role })).toEqual([]);
  expect(quickConnectCandidates([{ ...base, desiredRoles: [] }], { ...criteria, role: 'TOP' })).toHaveLength(1);
  expect(quickConnectCandidates([{ ...base, desiredRoles: ['ANY'] }], criteria)).toHaveLength(1);
});

test('마이크 상충은 양방향으로 제외하고 무관은 허용한다', () => {
  expect(quickConnectCandidates([base], { ...criteria, voice: 'NO_VOICE' })).toEqual([]);
  expect(quickConnectCandidates([{ ...base, voice: 'NO_VOICE' }], criteria)).toEqual([]);
  expect(quickConnectCandidates([base], { ...criteria, voice: 'OPTIONAL' })).toHaveLength(1);
  expect(quickConnectCandidates([{ ...base, voice: 'OPTIONAL' }], criteria)).toHaveLength(1);
});

test('칼바람은 역할을 요구하지 않고 발로란트·배그는 해당 게임 역할을 사용한다', () => {
  expect(quickConnectCandidates([{ ...base, modeKey: 'ARAM' }], { ...criteria, modeKey: 'ARAM', role: '' })).toHaveLength(1);
  for (const [game, modeKey, role] of [['VALORANT', 'UNRATED', 'CONTROLLER'], ['PUBG', 'SQUAD', 'SURVIVAL']] as const) {
    expect(quickConnectCandidates([{ ...base, game, modeKey, desiredRoles: [role] }], { ...criteria, game, modeKey, role })).toHaveLength(1);
  }
});

test('적은 빈자리 우선이며 동률이면 최신 방을 제안한다', () => {
  const rooms = [{ ...base, id: 'large' }, { ...base, id: 'old', capacity: 2 }, { ...base, id: 'new', capacity: 2, createdAt: 20 }];
  expect(quickConnectCandidates(rooms, criteria).map(room => room.id)).toEqual(['new', 'old', 'large']);
});

test('제안 후 마감되거나 조건이 변경되면 재검증에서 제외한다', () => {
  expect(quickConnectCandidates([base], criteria)).toHaveLength(1);
  for (const change of [{ status: 'CONFIRMED' as const }, { desiredRoles: ['TOP'] }, { voice: 'NO_VOICE' as const }]) {
    expect(quickConnectCandidates([{ ...base, ...change }], criteria)).toEqual([]);
  }
});

test('호스트 화면이 닫혀 있어도 자동 마감 기한이 지난 방은 제안하지 않는다', () => {
  const room = { ...base, members: [member, { ...member, id: 'guest' }], autoCloseAt: Date.now() - 1 };
  expect(quickConnectCandidates([room], criteria)).toEqual([]);
  expect(quickConnectCandidates([{ ...room, autoCloseAt: Date.now() + 60_000 }], criteria)).toHaveLength(1);
});

test('메인 폼에서 선택한 복수 포지션 중 하나라도 방 모집 조건과 맞으면 제안한다', () => {
  expect(quickConnectCandidates([base], { ...criteria, role: 'TOP', roles: ['TOP', 'SUPPORT'] })).toHaveLength(1);
  expect(quickConnectCandidates([base], { ...criteria, roles: ['TOP', 'JUNGLE'] })).toEqual([]);
});

test('찾는 포지션은 현재 방 멤버에 반영하고 칼바람에서는 무시한다', () => {
  expect(quickConnectCandidates([base], { ...criteria, desiredRoles: ['MID'] })).toHaveLength(1);
  expect(quickConnectCandidates([base], { ...criteria, desiredRoles: ['TOP'] })).toEqual([]);
  expect(quickConnectCandidates([{ ...base, members: [{ ...member, roles: ['ANY'] }] }], { ...criteria, desiredRoles: ['TOP'] })).toHaveLength(1);
  expect(quickConnectCandidates([{ ...base, modeKey: 'ARAM' }], { ...criteria, modeKey: 'ARAM', roles: [], desiredRoles: ['TOP'] })).toHaveLength(1);
});
