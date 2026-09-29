import type { RecentRoomStats } from './types';

export function matchCount(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

export function recentRoomStats(value: unknown): RecentRoomStats | null {
  if (!value || typeof value !== 'object') return null;
  const stats = value as Record<string, unknown>;
  const games = matchCount(stats.games);
  if (!games || ![stats.kills, stats.deaths, stats.assists].every(n => typeof n === 'number' && Number.isFinite(n) && n >= 0)
    || !Array.isArray(stats.champions)) return null;
  const champions: RecentRoomStats['champions'] = [];
  let counted = 0;
  for (const item of stats.champions) {
    if (!item || typeof item !== 'object' || typeof item.name !== 'string' || !item.name.trim()) continue;
    const played = matchCount(item.games), wins = matchCount(item.wins);
    if (!played || wins === null || wins > played || played > games - counted || champions.some(c => c.name === item.name)) continue;
    champions.push({ name: item.name, games: played, wins });
    counted += played;
  }
  return { games, kills: stats.kills as number, deaths: stats.deaths as number, assists: stats.assists as number,
    champions: champions.sort((a, b) => b.games - a.games).slice(0, 3) };
}
