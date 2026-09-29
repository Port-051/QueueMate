import type { GameKey } from '../api/types';
import { GAME_CATALOG, isKnownTier } from './gameCatalog';

/**
 * 티어 이름(`BRONZE_1` · `RADIANT`)을 **"티어" · "단계" 두 칸**으로 나누고 다시 합치는 규칙 — 이 파일 한 곳이다
 * (2026-09-29 소유자 지시 — "발로란트 경쟁전 티어를 한 번에 고르니 UX 가 안 좋다" → 넓은 칸 "브론즈" + 좁은 칸 "1". `components/GameAccountForm.tsx`).
 *
 * - 목록은 **`gameCatalog` 의 `tierNames`(seed 사본)에서 만든다** — 하드코딩하지 않는다. seed 에 티어가 더해지면 따라간다.
 * - 이름의 꼴은 `이름_숫자`(단계가 있다) 또는 `이름`(단계가 없다 — `RADIANT` · `MASTER` …). 합친 값은 지금까지 보내던 값과 **같다** — 계약 · 백엔드는 그대로다.
 * - 티어의 순서와 단계의 순서는 `tierNames` 의 순서(낮은 것부터)다 — VALORANT 는 1 → 3, LoL · PUBG 는 4 → 1 이다. 그래서 **단계의 첫째가 그 티어의 가장 낮은 단계**다.
 * - **`UNRANKED` 는 선택지에 없다** — 폼이 "없음(배치 전)" 으로 대신하고 `tier` 를 싣지 않는다(매칭에서 `UNRANKED` 는 `SOLO_ONLY` 라 파티를 못 만든다).
 *   저장된 값이 `UNRANKED` 이거나 이 사본에 없는 이름이면 `splitTier` 가 `null`(= 없음)을 준다.
 */

const UNRANKED = 'UNRANKED';
const WITH_DIVISION = /^(.+)_(\d+)$/;

export interface TierGroup {
  /** 단계를 뗀 이름 — `BRONZE` · `RADIANT`. */
  name: string;
  /** 그 티어의 단계 — 낮은 것부터(`tierNames` 순서). 단계가 없는 티어는 빈 배열. */
  divisions: readonly string[];
}

export interface TierParts {
  name: string;
  /** 단계가 없는 티어면 `null`. */
  division: string | null;
}

function parse(tier: string): TierParts {
  const match = tier.match(WITH_DIVISION);
  return match ? { name: match[1], division: match[2] } : { name: tier, division: null };
}

/** 그 게임의 티어(단계를 뗀 이름)와 티어마다의 단계 — `UNRANKED` 는 뺀다. 순서는 `tierNames` 그대로(낮은 티어부터). */
export function tierGroups(game: GameKey): TierGroup[] {
  const groups: { name: string; divisions: string[] }[] = [];
  for (const tier of GAME_CATALOG[game].tierNames) {
    if (tier === UNRANKED) continue;
    const { name, division } = parse(tier);
    let group = groups.find((g) => g.name === name);
    if (!group) { group = { name, divisions: [] }; groups.push(group); }
    if (division !== null) group.divisions.push(division);
  }
  return groups;
}

/** `BRONZE_1` → `{BRONZE, 1}` · `RADIANT` → `{RADIANT, null}`. 값이 없거나 `UNRANKED` · 이 사본에 없는 이름이면 `null`(= 없음). */
export function splitTier(game: GameKey, tier: string | null | undefined): TierParts | null {
  if (!tier || tier === UNRANKED || !isKnownTier(game, tier)) return null;
  return parse(tier);
}

/** 두 칸을 합친다 — `{BRONZE, 1}` → `BRONZE_1` · `{RADIANT, null}` → `RADIANT`. */
export function joinTier(name: string, division: string | null): string {
  return division ? `${name}_${division}` : name;
}
