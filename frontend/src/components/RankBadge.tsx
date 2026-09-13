import type { GameKey } from '../api/types';
import { formatLolRank, type LolRankDivision } from '../domain/lolRank';
import { TIER_LABELS } from '../domain/recruitment';
import { FilterTierIcon } from './FilterSymbols';

export function RankBadge({ game, tier, division, lp }: { game: GameKey; tier: string | null; division?: LolRankDivision | null; lp?: number | null }) {
  const label = game === 'LOL' ? formatLolRank(tier, division, lp) : tier ? TIER_LABELS[tier] ?? tier : '티어 미입력';
  return <span className="row-tier"><FilterTierIcon game={game} tier={tier} size={28} /><span className="rank-badge-label">{label}</span></span>;
}
