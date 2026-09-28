import { TIER_ORDER } from '../domain/tierRange';
import type { GameKey } from '../api/types';
import type { GameRoom, RoomMember, RoomSummary } from './types';

export const ROOM_ROLES: Record<GameKey, readonly string[]> = {
  LOL: ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'],
  VALORANT: ['DUELIST', 'INITIATOR', 'CONTROLLER', 'SENTINEL'],
  PUBG: ['AGGRESSIVE', 'BALANCED', 'SURVIVAL'],
};

/** Frontend room demo capacities. The existing duo API is not used by this room prototype. */
export function roomCapacityLimit(game: GameKey, modeKey: string): number {
  if (game === 'LOL') return modeKey === 'SOLO_DUO_RANKED' ? 2 : ['FLEX_RANKED', 'NORMAL_DRAFT', 'SWIFTPLAY', 'ARAM'].includes(modeKey) ? 5 : 0;
  if (game === 'VALORANT') return ['COMPETITIVE', 'UNRATED'].includes(modeKey) ? 5 : 0;
  return modeKey === 'DUO' ? 2 : modeKey === 'SQUAD' ? 4 : 0;
}

export function canonicalRoomRoles(game: GameKey, roles: readonly string[]): string[] {
  if (roles.includes('ANY')) return [...ROOM_ROLES[game]];
  return ROOM_ROLES[game].filter(role => roles.includes(role));
}

function rankLadder(game: GameKey): { tier: string; division: number | null }[] {
  return TIER_ORDER[game].flatMap(tier => {
    const divisions = game === 'LOL' ? (['MASTER', 'GRANDMASTER', 'CHALLENGER'].includes(tier) ? [null] : [4, 3, 2, 1])
      : game === 'VALORANT' ? (tier === 'RADIANT' ? [null] : [1, 2, 3])
        : tier === 'MASTER' ? [null] : [5, 4, 3, 2, 1];
    return divisions.map(division => ({ tier, division }));
  });
}

function average(values: (number | null)[], max = Infinity): number | null {
  const valid = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= max);
  return valid.length ? valid.reduce((sum, value) => sum + value, 0) / valid.length : null;
}

/** An ordinal rank average, not an estimate of MMR; members without ranked data are excluded. */
export function summarizeRoom(room: GameRoom): RoomSummary {
  const ladder = rankLadder(room.game);
  const scores = room.members.flatMap((member: RoomMember) => {
    const tier = member.tier?.toUpperCase();
    const positions = ladder.flatMap((rank, index) => rank.tier === tier ? [index] : []);
    if (!positions.length) return [];
    const exact = ladder.findIndex(rank => rank.tier === tier && rank.division === member.division);
    return [exact >= 0 ? exact : (positions[0] + positions[positions.length - 1]) / 2];
  });
  const rank = scores.length ? ladder[Math.round(scores.reduce((sum, score) => sum + score, 0) / scores.length)] : null;
  const winRate = average(room.members.map(member => member.winRate), 100);
  const kda = average(room.members.map(member => member.kda));
  const noFixedRoles = room.game === 'LOL' && room.modeKey === 'ARAM';
  const anyMemberRole = room.members.some(member => !member.roles.length || member.roles.includes('ANY'));
  return {
    tier: rank?.tier ?? null,
    division: rank?.division ?? null,
    winRate: winRate === null ? null : Math.round(winRate * 10) / 10,
    kda: kda === null ? null : Math.round(kda * 100) / 100,
    roles: noFixedRoles ? [] : canonicalRoomRoles(room.game, anyMemberRole ? ['ANY'] : room.members.flatMap(member => member.roles)),
    ratedCount: scores.length,
  };
}

/** Recruitment excludes solo; Flex cannot queue with four players. */
export function roomCapacities(game: GameKey, mode: string): number[] {
  if (game === "LOL" && mode === "FLEX_RANKED") return [2, 3, 5];
  return Array.from({ length: Math.max(0, roomCapacityLimit(game, mode) - 1) }, (_, index) => index + 2);
}

export function normalizeRoomCapacity(game: GameKey, mode: string, value?: number): number {
  const options = roomCapacities(game, mode);
  return value !== undefined && options.includes(value) ? value : options.at(-1) ?? 2;
}
