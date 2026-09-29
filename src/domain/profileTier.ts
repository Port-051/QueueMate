import type { GameKey, GameProfile, TierLadder } from '../api/types';
import { GAME_CATALOG, modeTierLadder, tierScore } from './gameCatalog';

/**
 * 게임 프로필의 사다리별 티어(`tiers`)에서 **하나를 고르는 규칙** — 이 파일 한 곳이다(2026-09-29 소유자 결정 "모드별 티어를 무조건 저장한다").
 *
 * - **모드가 사다리를 보면 그 사다리의 티어다**(`modeTierLadder` — seed 모드 HASH 의 `tierLadder`). 매칭 요청 · 자동 합류의 `tier`(`domain/matchRequest.ts`)와
 *   게시판 카드의 티어(`rooms/boardRoom.ts`)가 이 규칙이다.
 * - **모드에 사다리가 없으면(일반 · 칼바람 · 언레이티드) 카드는 그 사람 사다리들 가운데 가장 높은 티어를 보여 준다** — 높이는 `gameCatalog` 의 티어 이름 순서(`tierScore`),
 *   같으면 `tierLadders` 의 앞 사다리(LoL 이면 솔로랭크). **이 규칙은 Claude 가 정한 세부다**(소유자 검토 항목 — `START_HERE.md` §1 "사다리별 티어 · PUBG 연동").
 *   매칭 요청은 이 규칙을 쓰지 않는다 — 사다리가 없는 모드에는 `tier` 를 싣지 않는다(실으면 400).
 * - 값이 없으면(`null` · 키 없음 · `tiers` 가 통째로 없는 옛 응답) 티어 없음(`null`)이다. `UNRANKED` 는 이름 그대로 돌려준다 — 그리는 쪽이 티어 없음으로 본다(`rooms/accountRank.ts`).
 */

/** 한 사다리의 티어. 없으면 `null`. */
export function profileTier(profile: GameProfile | null | undefined, ladder: TierLadder): string | null {
  return profile?.tiers?.[ladder] ?? null;
}

export interface PickedTier {
  /** 사다리 이름(`GOLD_4` 꼴) 또는 `null`. */
  tier: string | null;
  /** 그 티어가 온 사다리. 모드가 사다리를 보면 그 사다리(값이 없어도), 아니면 가장 높은 티어의 사다리 — 하나도 없으면 `null`. */
  ladder: TierLadder | null;
}

/** 사다리들 가운데 가장 높은 티어 — 같은 높이면 `tierLadders` 의 앞 사다리. 하나도 없으면 `{null, null}`. */
export function highestTier(game: GameKey, profile: GameProfile | null | undefined): PickedTier {
  let best: PickedTier = { tier: null, ladder: null };
  for (const ladder of GAME_CATALOG[game].tierLadders) {
    const tier = profileTier(profile, ladder);
    if (tier && (best.tier === null || tierScore(game, tier) > tierScore(game, best.tier))) best = { tier, ladder };
  }
  return best;
}

/** 그 모드에서 보여 줄 · 보낼 티어 — 모드의 사다리가 있으면 그 사다리, 없으면 가장 높은 티어(위 규칙). */
export function tierForMode(game: GameKey, modeKey: string, profile: GameProfile | null | undefined): PickedTier {
  const ladder = modeTierLadder(game, modeKey);
  return ladder ? { tier: profileTier(profile, ladder), ladder } : highestTier(game, profile);
}
