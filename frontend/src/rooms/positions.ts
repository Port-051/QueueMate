import type { GameKey } from '../api/types';
import { canonicalRoomRoles, ROOM_ROLES, roomCapacityLimit } from './summary';
import type { CreateRoomInput, GameRoom } from './types';

type RoomSetup = Pick<CreateRoomInput, 'game' | 'modeKey' | 'capacity' | 'desiredRoles'>;

export function needsFullLineup(game: GameKey, modeKey: string, capacity: number): boolean {
  return game === 'LOL' && capacity === 5 && modeKey !== 'ARAM' && roomCapacityLimit(game, modeKey) === 5;
}

export function roomPositionError(setup: RoomSetup, ownRoles: readonly string[]): string | null {
  if (!needsFullLineup(setup.game, setup.modeKey, setup.capacity)) return null;
  const own = canonicalRoomRoles(setup.game, ownRoles);
  if (own.length !== 1) return '5인 방에서는 내 포지션을 하나 선택해 주세요.';
  const desired = canonicalRoomRoles(setup.game, setup.desiredRoles);
  if (desired.length !== 4 || desired.includes(own[0])) return '내 포지션을 제외한 나머지 4개 포지션을 선택해 주세요.';
  return null;
}

export function remainingRoomRoles(room: GameRoom): string[] {
  const desired = canonicalRoomRoles(room.game, room.desiredRoles);
  const requested = desired.length ? desired : [...ROOM_ROLES[room.game]];
  if (room.game !== 'LOL') return requested;
  if (room.modeKey === 'ARAM') return [];
  // A member's multiple preferences do not mean they occupy multiple seats.
  const occupied = room.members.flatMap(member => {
    if (member.id === room.ownerId && room.capacity < 5) return [];
    const roles = canonicalRoomRoles(room.game, member.roles);
    return roles.length === 1 ? roles : [];
  });
  return requested.filter(role => !occupied.includes(role));
}

export function vacantRoleOptions(room: GameRoom): string[][] {
  const count = Math.max(0, room.capacity - room.members.length);
  const roles = remainingRoomRoles(room);
  // Exact lineups get one role per seat. Alternatives remain together so no
  // eligible role disappears just because there are fewer seats than choices.
  return Array.from({ length: count }, (_, index) => roles.length === count ? [roles[index]] : [...roles]);
}
