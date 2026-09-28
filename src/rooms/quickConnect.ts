import { tierInRange, type TierRange } from '../domain/tierRange';
import type { GameKey, VoicePreference } from '../api/types';
import { roomVoice } from './voice';
import { autoClosePhase } from './autoClose';
import { canonicalRoomRoles } from './summary';
import { remainingRoomRoles } from './positions';
import { reservationTimeError } from './schedule';
import type { GameRoom } from './types';

export interface QuickConnectCriteria {
  game: GameKey;
  modeKey: string;
  capacity?: number;
  availableFrom?: string | null;
  role: string;
  roles?: string[];
  desiredRoles?: string[];
  ownTier?: string | null;
  desiredTierRange?: TierRange;
  voice: VoicePreference;
  userId: string;
}

/** The room prototype shares one pool. Free-text preferences stay visible on the board and seat confirmation. */
export function quickConnectCandidates(rooms: GameRoom[], criteria: QuickConnectCriteria): GameRoom[] {
  const noRoles = criteria.game === 'LOL' && criteria.modeKey === 'ARAM';
  const ownRoles = canonicalRoomRoles(criteria.game, criteria.roles ?? [criteria.role]);
  const desiredRoles = canonicalRoomRoles(criteria.game, criteria.desiredRoles ?? []);
  if (!noRoles && !ownRoles.length) return [];
  const scheduled = criteria.availableFrom !== undefined && criteria.availableFrom !== null;
  if (scheduled && reservationTimeError(criteria.availableFrom!)) return [];
  return rooms.filter(room => {
    if (room.game !== criteria.game || room.modeKey !== criteria.modeKey
      || room.status !== 'OPEN' || room.members.length >= room.capacity || autoClosePhase(room, Date.now()) === 'due'
      || room.members.some(member => member.id === criteria.userId)) return false;
    if (scheduled ? room.type !== 'RESERVATION' || !room.availableFrom || Date.parse(room.availableFrom) !== Date.parse(criteria.availableFrom!) : room.type !== 'REALTIME') return false;
    if (roomVoice(criteria.voice) !== roomVoice(room.voice)) return false;
    if (criteria.capacity !== undefined && room.capacity !== criteria.capacity) return false;
    if (!tierInRange(room.game, criteria.ownTier, room.desiredTierRange)) return false;
    if (!room.members.every(member => tierInRange(room.game, member.tier, criteria.desiredTierRange))) return false;
    if (noRoles) return true;
    if (desiredRoles.length && !desiredRoles.includes('ANY') && !room.members.some(member => member.roles.some(role => role === 'ANY' || desiredRoles.includes(role)))) return false;
    const remaining = remainingRoomRoles(room);
    return ownRoles.some(role => remaining.includes(role));
  }).sort((a, b) => (a.capacity - a.members.length) - (b.capacity - b.members.length) || b.createdAt - a.createdAt || a.id.localeCompare(b.id));
}
