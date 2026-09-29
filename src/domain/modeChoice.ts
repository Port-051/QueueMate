import type { GameKey, PubgPerspective } from '../api/types';
import { GAME_CATALOG, PERSPECTIVES, type GameModeSeed, type ModeGroupSeed } from './gameCatalog';

/**
 * 모드를 **묶음 · 인원 · (PUBG) 시점** 셋으로 나눠 고르는 규칙(2026-09-29 소유자 지시 — "모드는 모드끼리 묶고 밑에 인원 수를 따로").
 *
 * 셋의 원본은 `gameCatalog.ts` 모드의 `group` · `targetPartySize` · `perspective` 다(프런트 전용 UI 메타 — seed 에 없다). 모드 키 문자열을 잘라 읽지 않는다.
 * **고른 결과는 늘 모드 키 하나다**(`RANKED_FLEX_3` 등) — 부르는 쪽과 서버 본문(`mode` · `modeKey`)은 바뀌지 않는다.
 */
export interface ModeChoice { group: string; size: number; perspective: PubgPerspective | null; }

export const modeGroups = (game: GameKey): readonly ModeGroupSeed[] => GAME_CATALOG[game].modeGroups;

export const groupModes = (game: GameKey, group: string): GameModeSeed[] => GAME_CATALOG[game].modes.filter(mode => mode.group === group);

/** 그 묶음의 인원(작은 것부터). 솔로 랭크처럼 하나뿐이면 선택기는 고정으로 그린다. */
export const groupSizes = (game: GameKey, group: string): number[] =>
  [...new Set(groupModes(game, group).map(mode => mode.targetPartySize))].sort((a, b) => a - b);

/** 그 묶음의 시점(3인칭 먼저). PUBG 밖은 빈 배열이다. */
export const groupPerspectives = (game: GameKey, group: string): PubgPerspective[] =>
  PERSPECTIVES.filter(view => groupModes(game, group).some(mode => mode.perspective === view));

/** 모드 키 → 묶음 · 인원 · 시점. 모르는 키(`''` · 옛 글의 `null` · legacy 의 `ANY`)는 `null`. */
export function modeChoice(game: GameKey, modeKey: string): ModeChoice | null {
  const mode = GAME_CATALOG[game].modes.find(item => item.key === modeKey);
  return mode ? { group: mode.group, size: mode.targetPartySize, perspective: mode.perspective ?? null } : null;
}

/**
 * 묶음 · 인원 · 시점 → 모드 키. 묶음을 바꿀 때 쓰는 규칙이 여기 있다 — **지금 인원 · 시점이 그 묶음에 있으면 그대로, 없으면 가장 작은 인원 · 3인칭.**
 * 그 게임에 없는 묶음이면 그 게임의 첫 모드다(게임을 바꾼 직후 옛 게임의 묶음이 들어와도 늘 있는 모드 키를 돌려준다).
 */
export function pickMode(game: GameKey, group: string, size?: number | null, perspective?: PubgPerspective | null): string {
  const modes = groupModes(game, group);
  if (!modes.length) return GAME_CATALOG[game].modes[0].key;
  const sizes = groupSizes(game, group);
  const pickedSize = size != null && sizes.includes(size) ? size : sizes[0];
  const sized = modes.filter(mode => mode.targetPartySize === pickedSize);
  const found = sized.find(mode => (mode.perspective ?? null) === (perspective ?? null))
    ?? PERSPECTIVES.map(view => sized.find(mode => mode.perspective === view)).find(Boolean)
    ?? sized[0];
  return found.key;
}
