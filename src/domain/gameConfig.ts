import type { GameKey, KeyConditionType, MatchCondition, PlayPurpose, VoicePreference } from '../api/types';
import { GAME_CATALOG, GAME_KEYS, modeSeed, PLAY_PURPOSES, VOICE_PREFERENCES } from './gameCatalog';

/**
 * 게임별 조건 카탈로그를 화면이 쓰는 모양으로 내주는 곳.
 *
 * **모드 · 핵심 조건 값 · 티어의 원본은 `matching/seed/gameconfig.redis` 이고 이 앱의 사본은 `gameCatalog.ts` 다**(두 곳 — 2026-09-28 소유자 결정).
 * 서버 조회(`GET /games` · `match-schema`)는 없다 — 그래서 로그인 전에도 · 부팅 직후에도 바로 쓸 수 있고 `RequireGameCatalog` 같은 문이 없다.
 * 여기에는 표시 문구(게임 이름 · 태그라인)와 조건을 다루는 작은 함수만 남는다.
 */

export interface ModeConfig {
  key: string;
  label: string;
  /** 파티 목표 인원. seed 의 `targetPartySize` — 클라이언트는 정원을 보내지 않는다. */
  targetPartySize: number;
  /** 같은 파티 안에서 핵심 조건 값이 유일해야 하는가(seed 의 `positionUniqueness`). PUBG 는 없다(false). */
  keyConditionUniqueness: boolean;
  /** 이 모드가 티어를 보는가(seed 의 `tierRule`). */
  usesTier: boolean;
}

export interface KeyConditionOption { value: string; label: string; }

export interface GameConfig {
  key: GameKey;
  name: string;
  shortName: string;
  tagline: string;
  keyCondition: {
    type: KeyConditionType;
    label: string;
    desc: string;
    options: KeyConditionOption[];
  };
}

/** 표시 문구 카탈로그. 랜딩 · 게임 계정 연결 · 매칭 조건 UI 가 다 같이 쓴다. */
export const GAMES: GameConfig[] = [
  {
    key: 'LOL',
    name: '리그 오브 레전드',
    shortName: '리그 오브 레전드',
    tagline: '포지션이 맞는 팀원과',
    keyCondition: {
      type: GAME_CATALOG.LOL.keyConditionType,
      label: '희망 포지션',
      desc: '이번에 같이 할 때 맡을 포지션을 선택하세요.',
      options: [...GAME_CATALOG.LOL.keyConditionValues],
    },
  },
  {
    key: 'VALORANT',
    name: '발로란트',
    shortName: '발로란트',
    tagline: '역할군이 맞는 팀원과',
    keyCondition: {
      type: GAME_CATALOG.VALORANT.keyConditionType,
      label: '선호 역할군',
      desc: '주로 맡을 역할군을 선택하세요.',
      options: [...GAME_CATALOG.VALORANT.keyConditionValues],
    },
  },
  {
    key: 'PUBG',
    name: '배틀그라운드',
    shortName: '배틀그라운드',
    tagline: '같은 플랫폼의 팀원과',
    keyCondition: {
      type: GAME_CATALOG.PUBG.keyConditionType,
      label: '플랫폼',
      desc: '스팀과 카카오는 서버가 달라 서로 파티를 맺을 수 없습니다.',
      options: [...GAME_CATALOG.PUBG.keyConditionValues],
    },
  },
];

const toModeConfig = (game: GameKey, modeKey: string): ModeConfig | undefined => {
  const m = modeSeed(game, modeKey);
  return m && {
    key: m.key,
    label: m.label,
    targetPartySize: m.targetPartySize,
    keyConditionUniqueness: m.positionUniqueness ?? false,
    usesTier: m.tierRule === 'EXIST',
  };
};

export function gameConfig(game: GameKey): GameConfig {
  const found = GAMES.find((g) => g.key === game);
  if (!found) throw new Error(`UNSUPPORTED_GAME:${game}`);
  return found;
}

/** 매칭 조건 UI가 쓰는 게임 목록 — 셋 다 모드가 있다. */
export function availableGames(): GameConfig[] {
  return GAME_KEYS.map(gameConfig);
}

export function visibleModes(game: GameKey): ModeConfig[] {
  return GAME_CATALOG[game].modes.map((m) => toModeConfig(game, m.key)!);
}

/** 핵심 조건의 선택지 — seed 의 값과 프런트의 라벨. */
export function keyConditionOptions(game: GameKey): KeyConditionOption[] {
  return gameConfig(game).keyCondition.options;
}

export const VOICE_OPTIONS: { value: VoicePreference; label: string }[] = VOICE_PREFERENCES.map((value) => ({
  value, label: value === 'REQUIRED' ? '사용' : '사용 안 함',
}));

export const PURPOSE_OPTIONS: { value: PlayPurpose; label: string }[] = PLAY_PURPOSES.map((value) => ({
  value, label: value === 'RANK_UP' ? '랭크 상승' : value === 'TRYHARD' ? '친목' : '즐겜',
}));

/**
 * 폼이 처음 여는 플레이 목적 — 친목. 조건의 기본값(`defaultCondition`)이고, 저장해 둔 조건에 목적이 없거나 모르는 값일 때도 이것으로 채운다.
 * 화면의 빡겜 선택지를 친목으로 바꾸되, 현재 matching 계약과 호환되도록 전송 값은 `TRYHARD`를 유지한다.
 */
export const DEFAULT_PLAY_PURPOSE: PlayPurpose = 'TRYHARD';

export function modeConfig(game: GameKey, modeKey: string): ModeConfig | undefined {
  return toModeConfig(game, modeKey);
}

export function targetPartySize(game: GameKey, modeKey: string): number {
  return modeConfig(game, modeKey)?.targetPartySize ?? 2;
}

/**
 * 이 모드에서 핵심 조건을 고르는가. seed 의 `positionUniqueness=false`(LoL 칼바람 — 포지션 개념이 없다)만 아니다.
 * PUBG 는 seed 에 그 필드가 없지만 플랫폼은 늘 고른다(hard 조건).
 */
export function usesKeyCondition(game: GameKey, modeKey: string): boolean {
  return modeSeed(game, modeKey)?.positionUniqueness !== false;
}

/**
 * 모드를 바꾼다. 핵심 조건을 안 보는 모드(칼바람)에서는 값을 `ANY` 로 둔다 — **화면의 값이다.**
 * 서버에 보낼 때(`POST /match-requests` · `POST /posts/auto-join`)는 `domain/matchRequest.ts` `buildMatchRequest` 가 LoL `NONE` 으로 옮긴다.
 */
export function conditionForMode(condition: MatchCondition, modeKey: string): MatchCondition {
  return {
    ...condition, modeKey,
    keyCondition: usesKeyCondition(condition.game, modeKey) ? condition.keyCondition : { ...condition.keyCondition, value: 'ANY' },
  };
}

/** 게임을 고르면 해당 게임의 기본 조건으로 초기화한다 — 첫 모드 · 첫 핵심 조건 값. */
export function defaultCondition(game: GameKey): MatchCondition {
  const cfg = gameConfig(game);
  return {
    game,
    modeKey: visibleModes(game)[0]?.key ?? '',
    keyCondition: { type: cfg.keyCondition.type, value: keyConditionOptions(game)[0]?.value ?? '' },
    voicePreference: 'NO_VOICE',
    playPurpose: DEFAULT_PLAY_PURPOSE,
  };
}

/** 게임을 바꾸면 핵심 조건/모드는 새 게임 카탈로그 값으로 갈아끼운다. */
export function switchGame(condition: MatchCondition, game: GameKey): MatchCondition {
  if (condition.game === game) return condition;
  const next = defaultCondition(game);
  return { ...next, voicePreference: condition.voicePreference, playPurpose: condition.playPurpose };
}
