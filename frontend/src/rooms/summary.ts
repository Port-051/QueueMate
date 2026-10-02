import type { GameKey } from '../api/types';
import { GAME_CATALOG } from '../domain/gameCatalog';
import { usesKeyCondition } from '../domain/gameConfig';

/**
 * 카드가 "포지션" 으로 다루는 값 — 글의 `wantedPositions`(찾는 포지션)가 이 목록의 이름이다(원본은 gameconfig — `domain/gameCatalog.ts`). 사람별 포지션은 없다(2026-09-29 — 게임 계정에서 없앴다).
 * **PUBG 는 비어 있다** — PUBG 글의 `wantedPositions` 는 늘 빈 배열이고(서버가 400 으로 막는다) 핵심 조건 `PLATFORM`(STEAM/KAKAO)은 포지션이 아니라 프로필의 `server` 다.
 * 원본 프런트의 `AGGRESSIVE/BALANCED/SURVIVAL`(PLAY_STYLE)은 우리 백엔드에 없다(2단계 ⑤ — 2026-09-29 에 정리).
 */
export const ROOM_ROLES: Record<GameKey, readonly string[]> = {
  LOL: GAME_CATALOG.LOL.keyConditionValues.map(v => v.value),
  VALORANT: GAME_CATALOG.VALORANT.keyConditionValues.map(v => v.value),
  PUBG: [],
};

/** 게임 순서로 세우고 모르는 값은 버린다. `ANY` 는 전부다. */
export function canonicalRoomRoles(game: GameKey, roles: readonly string[]): string[] {
  if (roles.includes('ANY')) return [...ROOM_ROLES[game]];
  return ROOM_ROLES[game].filter(role => roles.includes(role));
}

/** 이 게임 · 모드의 글이 "찾는 포지션" 을 갖는가 — PUBG 는 없고, LoL 칼바람(`positionUniqueness=false`)도 없다. */
export const hasPositions = (game: GameKey, modeKey: string): boolean => ROOM_ROLES[game].length > 0 && usesKeyCondition(game, modeKey);
