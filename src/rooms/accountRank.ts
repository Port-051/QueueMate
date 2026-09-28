import type { GameProfile } from '../api/types';

/**
 * 게임 프로필의 `tier`(gameconfig 사다리 이름 — `GOLD_4` · `MASTER` · `UNRANKED` · `null`)를 카드가 그리는 `{tier, division}` 으로.
 * `UNRANKED` 는 사다리 위의 값이 아니라(seed 주석) 티어 없음으로 본다. 게임마다 단의 방향이 다르다(LoL · PUBG 4→1 · VALORANT 1→3) — 숫자만 넘긴다.
 * 옛 원본의 `rankCode` · 자유 랭크 `flexRankCode` 는 없다 — 우리 게임 프로필의 티어는 하나다(LoL 은 솔로랭크).
 */
export function accountRank(account: GameProfile | undefined) {
  const match = account?.tier?.match(/^([A-Z]+)(?:_([1-5]))?$/);
  const tier = match?.[1] ?? null;
  return { tier: tier === 'UNRANKED' ? null : tier, division: match?.[2] ? Number(match[2]) : null };
}
