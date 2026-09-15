import { TIER_LABELS } from './recruitment';

/** Riot: 아이언~다이아몬드는 IV → I, 마스터 이상은 세부 단계 없이 표시한다.
 * https://support.riotgames.com/en-us/league-of-legends/gameplay/ranked-tiers-divisions-and-queues/
 * https://support.riotgames.com/en-us/league-of-legends/gameplay/master-grandmaster-and-challenger-the-apex-tiers/
 */
export const LOL_RANK_DIVISIONS = ['IV', 'III', 'II', 'I'] as const;
export type LolRankDivision = typeof LOL_RANK_DIVISIONS[number];
const dividedTiers = new Set(['IRON', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'EMERALD', 'DIAMOND']);
const apexTiers = new Set(['MASTER', 'GRANDMASTER', 'CHALLENGER']);

export const hasLolRankDivision = (tier: string | null | undefined) => Boolean(tier && dividedTiers.has(tier));
export const isLolApexTier = (tier: string | null | undefined) => Boolean(tier && apexTiers.has(tier));
export const isLolRankTier = (tier: string | null | undefined) => hasLolRankDivision(tier) || isLolApexTier(tier);

export function normalizeLolRankDetails(tier: string | null | undefined, division: unknown): { rankDivision: LolRankDivision | null } {
  const hasDivision = hasLolRankDivision(tier);
  return {
    rankDivision: hasDivision && LOL_RANK_DIVISIONS.includes(division as LolRankDivision) ? division as LolRankDivision : null,
  };
}

/** 모르는 단계를 추정하지 않으며, 미입력을 배치 전/언랭크로 바꾸지 않는다. */
export function formatLolRank(tier: string | null | undefined, division?: LolRankDivision | null): string {
  if (!isLolRankTier(tier)) return '티어 미입력';
  const { rankDivision } = normalizeLolRankDetails(tier, division);
  return `${TIER_LABELS[tier!]}${rankDivision ? ` ${rankDivision}` : ''}`;
}
