import type { GameKey, VoicePreference } from '../api/types';
import { roomVoice } from './voice';
import { autoClosePhase } from './autoClose';
import { canonicalRoomRoles } from './summary';
import { remainingRoomRoles } from './positions';
import type { GameRoom } from './types';

export interface QuickConnectCriteria {
  game: GameKey;
  modeKey: string;
  role: string;
  roles?: string[];
  desiredRoles?: string[];
  voice: VoicePreference;
  userId: string;
}

/** The room prototype shares one pool. Free-text preferences are reviewed in the detail view. */
export function quickConnectCandidates(rooms: GameRoom[], criteria: QuickConnectCriteria): GameRoom[] {
  const noRoles = criteria.game === 'LOL' && criteria.modeKey === 'ARAM';
  const ownRoles = canonicalRoomRoles(criteria.game, criteria.roles ?? [criteria.role]);
  const desiredRoles = canonicalRoomRoles(criteria.game, criteria.desiredRoles ?? []);
  if (!noRoles && !ownRoles.length) return [];
  return rooms.filter(room => {
    if (room.game !== criteria.game || room.modeKey !== criteria.modeKey || room.type !== 'REALTIME'
      || room.status !== 'OPEN' || room.members.length >= room.capacity || autoClosePhase(room, Date.now()) === 'due'
      || room.members.some(member => member.id === criteria.userId)) return false;
    if (roomVoice(criteria.voice) !== roomVoice(room.voice)) return false;
    if (noRoles) return true;
    if (desiredRoles.length && !desiredRoles.includes('ANY') && !room.members.some(member => member.roles.some(role => role === 'ANY' || desiredRoles.includes(role)))) return false;
    const remaining = remainingRoomRoles(room);
    return ownRoles.some(role => remaining.includes(role));
  }).sort((a, b) => (a.capacity - a.members.length) - (b.capacity - b.members.length) || b.createdAt - a.createdAt || a.id.localeCompare(b.id));
}
