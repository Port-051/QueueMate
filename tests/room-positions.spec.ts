import { expect, test } from '@playwright/test';
import { roomPositionError, vacantRoleOptions } from '../src/rooms/positions';
import { quickConnectCandidates } from '../src/rooms/quickConnect';
import type { GameRoom, RoomMember } from '../src/rooms/types';

const member = (id: string, roles: string[]): RoomMember => ({ id, roles, nickname: id, avatarUrl: null, tier: null, division: null, winRate: null, kda: null, champions: [], bio: '', voice: 'REQUIRED' });
const room: GameRoom = { id: 'room', game: 'LOL', modeKey: 'NORMAL_DRAFT', type: 'REALTIME', title: '방', ownerId: 'host', capacity: 5, members: [member('host', ['MID'])], desiredRoles: ['TOP', 'JUNGLE', 'ADC', 'SUPPORT'], voice: 'REQUIRED', status: 'OPEN', createdAt: 0, availableFrom: null, messages: [] };

test('한 자리에 허용한 포지션은 순서에 맞춰 모두 표시한다', () => {
  expect(vacantRoleOptions({ ...room, capacity: 2, desiredRoles: ['SUPPORT', 'TOP'] })).toEqual([['TOP', 'SUPPORT']]);
});

test('5인 방은 본인 포지션 하나와 나머지 네 포지션이 모두 필요하다', () => {
  expect(roomPositionError(room, ['MID'])).toBeNull();
  for (const own of [[], ['MID', 'TOP']]) expect(roomPositionError(room, own)).not.toBeNull();
  for (const desiredRoles of [[], ['TOP', 'SUPPORT'], ['TOP', 'JUNGLE', 'MID', 'SUPPORT'], ['ANY']]) {
    expect(roomPositionError({ ...room, desiredRoles }, ['MID'])).not.toBeNull();
  }
  expect(roomPositionError({ ...room, modeKey: 'SWIFTPLAY' }, ['MID'])).toBeNull();
  expect(roomPositionError({ ...room, modeKey: 'SWIFTPLAY', desiredRoles: [] }, ['MID'])).not.toBeNull();
});

test('2~4인·칼바람·다른 게임에는 롤 5인 포지션 조건을 적용하지 않는다', () => {
  for (const setup of [{ ...room, capacity: 2 }, { ...room, capacity: 3 }, { ...room, capacity: 4 }, { ...room, modeKey: 'ARAM' }, { ...room, game: 'VALORANT' as const, modeKey: 'UNRATED' }, { ...room, game: 'PUBG' as const, modeKey: 'SQUAD', capacity: 4 }]) {
    expect(roomPositionError({ ...setup, desiredRoles: [] }, [])).toBeNull();
  }
});

test('빈자리는 이미 채워진 포지션을 빼고 탈퇴하면 다시 표시한다', () => {
  const joined = { ...room, members: [...room.members, member('top', ['TOP']), member('jungle', ['JUNGLE'])] };
  expect(vacantRoleOptions(joined)).toEqual([['ADC'], ['SUPPORT']]);
  expect(vacantRoleOptions({ ...joined, members: joined.members.filter(person => person.id !== 'top') })).toEqual([['TOP'], ['ADC'], ['SUPPORT']]);
  expect(room.desiredRoles).toEqual(['TOP', 'JUNGLE', 'ADC', 'SUPPORT']);
});

test('복수 포지션인 한 사람을 여러 자리를 채운 것으로 처리하지 않는다', () => {
  const joined = { ...room, members: [...room.members, member('flex', ['TOP', 'JUNGLE']), member('adc', ['ADC']), member('support', ['SUPPORT'])] };
  expect(vacantRoleOptions(joined)).toEqual([['TOP', 'JUNGLE']]);
});

test('퀵 연결은 이미 찬 포지션 대신 카드에 남은 포지션을 기준으로 한다', () => {
  const joined = { ...room, members: [...room.members, member('top', ['TOP'])] };
  const criteria = { game: 'LOL' as const, modeKey: room.modeKey, voice: 'REQUIRED' as const, userId: 'me', role: 'TOP' };
  expect(quickConnectCandidates([joined], criteria)).toEqual([]);
  expect(quickConnectCandidates([joined], { ...criteria, role: 'SUPPORT' })).toEqual([joined]);
});
