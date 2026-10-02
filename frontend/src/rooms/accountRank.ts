/**
 * 사다리 이름 하나(gameconfig 티어 이름 — `GOLD_4` · `MASTER` · `UNRANKED` · `null`)를 카드가 그리는 `{tier, division}` 으로.
 * `UNRANKED` 는 사다리 위의 값이 아니라(seed 주석) 티어 없음으로 본다. 게임마다 단의 방향이 다르다(LoL · PUBG 4→1 · VALORANT 1→3) — 숫자만 넘긴다.
 * **어느 사다리의 티어를 넘길지는 부르는 쪽이 `domain/profileTier.ts` 로 고른다**(2026-09-29 — 게임 프로필의 티어가 사다리마다 따로다: `tiers`).
 */
export function accountRank(tierName: string | null | undefined) {
  const match = tierName?.match(/^([A-Z]+)(?:_([1-5]))?$/);
  const tier = match?.[1] ?? null;
  return { tier: tier === 'UNRANKED' ? null : tier, division: match?.[2] ? Number(match[2]) : null };
}
