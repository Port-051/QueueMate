import type { GameKey } from '../api/types';

export interface TierRange { minTier: string | null; maxTier: string | null; }
export const ALL_TIERS: TierRange = { minTier: null, maxTier: null };
export const TIER_ORDER: Record<GameKey, readonly string[]> = {
  LOL: ['IRON', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'EMERALD', 'DIAMOND', 'MASTER', 'GRANDMASTER', 'CHALLENGER'],
  VALORANT: ['IRON', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'ASCENDANT', 'IMMORTAL', 'RADIANT'],
  PUBG: ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER'],
};

/** Old saved rooms have no range; invalid or cross-game values never become new tiers. */
export function normalizeTierRange(game: GameKey, value?: Partial<TierRange> | null): TierRange {
  const order = TIER_ORDER[game];
  let minTier = value?.minTier && order.includes(value.minTier) ? value.minTier : null;
  let maxTier = value?.maxTier && order.includes(value.maxTier) ? value.maxTier : null;
  if (minTier && maxTier && order.indexOf(minTier) > order.indexOf(maxTier)) [minTier, maxTier] = [maxTier, minTier];
  return { minTier, maxTier };
}

/** Inclusive recruitment preference, not Riot's queue eligibility / MMR validation. */
export function tierInRange(game: GameKey, tier: string | null | undefined, value?: TierRange): boolean {
  const { minTier, maxTier } = normalizeTierRange(game, value);
  if (!minTier && !maxTier) return true;
  const order = TIER_ORDER[game];
  const index = tier ? order.indexOf(tier) : -1;
  return index >= 0 && (!minTier || index >= order.indexOf(minTier)) && (!maxTier || index <= order.indexOf(maxTier));
}
