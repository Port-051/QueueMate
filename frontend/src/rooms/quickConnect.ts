import type { GameKey, VoicePreference } from '../api/types';
import { canonicalRoomRoles } from './summary';
import type { GameRoom } from './types';

export interface QuickConnectCriteria {
  game: GameKey;
  modeKey: string;
  role: string;
  voice: VoicePreference;
  userId: string;
}

/** The room prototype shares one pool. Free-text preferences are reviewed in the detail view. */
export function quickConnectCandidates(rooms: GameRoom[], criteria: QuickConnectCriteria): GameRoom[] {
  const noRoles = criteria.game === 'LOL' && criteria.modeKey === 'ARAM';
  if (!noRoles && !canonicalRoomRoles(criteria.game, [criteria.role]).length) return [];
  return rooms.filter(room => {
    if (room.game !== criteria.game || room.modeKey !== criteria.modeKey || room.type !== 'REALTIME'
      || room.status !== 'OPEN' || room.members.length >= room.capacity
      || room.members.some(member => member.id === criteria.userId)) return false;
    if (criteria.voice !== 'OPTIONAL' && room.voice !== 'OPTIONAL' && criteria.voice !== room.voice) return false;
    return noRoles || !room.desiredRoles.length || room.desiredRoles.includes('ANY') || room.desiredRoles.includes(criteria.role);
  }).sort((a, b) => (a.capacity - a.members.length) - (b.capacity - b.members.length) || b.createdAt - a.createdAt || a.id.localeCompare(b.id));
}
