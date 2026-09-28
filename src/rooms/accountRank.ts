import type { GameAccountView } from '../api/types';

export function accountRank(account: GameAccountView | undefined, mode: string) {
  const code = mode === 'FLEX_RANKED' ? account?.flexRankCode : account?.rankCode;
  const match = code?.match(/^([A-Z]+)(?:[_ ]([1-5]|IV|III|II|I))?$/);
  const roman: Record<string, number> = { I: 1, II: 2, III: 3, IV: 4 };
  return { tier: match?.[1] ?? null, division: match?.[2] ? roman[match[2]] ?? Number(match[2]) : null };
}
