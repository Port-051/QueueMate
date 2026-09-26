import ironEmblem from '../assets/ranks/Iron.png';
import bronzeEmblem from '../assets/ranks/Bronze.png';
import silverEmblem from '../assets/ranks/Silver.png';
import goldEmblem from '../assets/ranks/Gold.png';
import platinumEmblem from '../assets/ranks/Platinum.png';
import emeraldEmblem from '../assets/ranks/Emerald.png';
import diamondEmblem from '../assets/ranks/Diamond.png';
import masterEmblem from '../assets/ranks/Master.png';
import grandmasterEmblem from '../assets/ranks/Grandmaster.png';
import challengerEmblem from '../assets/ranks/Challenger.png';

// Official Riot ranked emblems, verified 2026-09-14. See assets/ranks/README.md.
const LOL_RANK_EMBLEMS: Readonly<Record<string, string>> = {
  IRON: ironEmblem,
  BRONZE: bronzeEmblem,
  SILVER: silverEmblem,
  GOLD: goldEmblem,
  PLATINUM: platinumEmblem,
  EMERALD: emeraldEmblem,
  DIAMOND: diamondEmblem,
  MASTER: masterEmblem,
  GRANDMASTER: grandmasterEmblem,
  CHALLENGER: challengerEmblem,
};

/** LoL only. Unranked and unknown tiers have no emblem in the official bundle. */
export function rankEmblem(tier: string | null | undefined): string | null {
  return tier ? LOL_RANK_EMBLEMS[tier.trim().toUpperCase()] ?? null : null;
}

// Non-transparent bounds in each unmodified 1000×1000 PNG.
export const RANK_EMBLEM_SOURCE_SIZE = 1000;
export type RankEmblemBounds = Readonly<{ x: number; y: number; width: number; height: number }>;
const LOL_RANK_BOUNDS: Readonly<Record<string, RankEmblemBounds>> = {
  IRON: { x: 192, y: 406, width: 632, height: 380 },
  BRONZE: { x: 106, y: 308, width: 801, height: 520 },
  SILVER: { x: 56, y: 226, width: 897, height: 606 },
  GOLD: { x: 74, y: 96, width: 848, height: 772 },
  PLATINUM: { x: 42, y: 87, width: 932, height: 753 },
  EMERALD: { x: 0, y: 98, width: 1000, height: 730 },
  DIAMOND: { x: 0, y: 196, width: 1000, height: 660 },
  MASTER: { x: 0, y: 73, width: 1000, height: 803 },
  GRANDMASTER: { x: 0, y: 94, width: 1000, height: 862 },
  CHALLENGER: { x: 0, y: 21, width: 1000, height: 810 },
};

export function rankEmblemBounds(tier: string | null | undefined): RankEmblemBounds | null {
  return tier ? LOL_RANK_BOUNDS[tier.trim().toUpperCase()] ?? null : null;
}

export const TIER_COLORS: Record<string, string> = {
  IRON: '#8b8584', BRONZE: '#b2866c', SILVER: '#aab6c6', GOLD: '#d0ad68',
  PLATINUM: '#7bb7b3', EMERALD: '#6eb68e', DIAMOND: '#93abe0', MASTER: '#b889ca',
  GRANDMASTER: '#cf7e85', CHALLENGER: '#d7bf7e', ASCENDANT: '#83b89a',
  IMMORTAL: '#cb8496', RADIANT: '#d4ca96',
};

export const tierColor = (tier: string | null | undefined): string => tier ? TIER_COLORS[tier] ?? "#aaa3ba" : "#aaa3ba";
