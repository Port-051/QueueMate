import type { GameKey, KeyConditionType, MatchCondition, PlayPurpose, VoicePreference } from '../api/types';

/**
 * docs/02_MATCH_CONDITION_SCHEMA.md 기준 게임별 조건 카탈로그.
 * 공통 골격(게임/모드/음성/목적) + 게임별 핵심 조건 1개만 둔다. 새 조건 추가 금지.
 */

export interface ModeConfig {
  key: string;
  label: string;
  /** 파티 목표 인원. 시스템이 조건에서 파생한다(docs/02 §6). */
  targetPartySize: number;
  /** 같은 파티 안에서 핵심 조건 값이 유일해야 하는가 (LoL POSITION hard rule). */
  keyConditionUniqueness: boolean;
  /** UI 노출 여부도 config로 결정한다(docs/02 §3). */
  uiVisible: boolean;
}

export interface KeyConditionOption { value: string; label: string; }

export interface GameConfig {
  key: GameKey;
  name: string;
  shortName: string;
  tagline: string;
  modes: ModeConfig[];
  keyCondition: {
    type: KeyConditionType;
    label: string;
    desc: string;
    options: KeyConditionOption[];
  };
}

export const GAMES: GameConfig[] = [
  {
    key: 'LOL',
    name: 'League of Legends',
    shortName: 'LoL',
    tagline: '포지션이 맞는 팀원과',
    modes: [
      { key: 'SOLO_DUO_RANKED', label: '솔로/듀오 랭크', targetPartySize: 2, keyConditionUniqueness: true, uiVisible: true },
      { key: 'NORMAL_DRAFT', label: '일반 게임', targetPartySize: 5, keyConditionUniqueness: true, uiVisible: true },
      { key: 'ARAM', label: '칼바람 나락', targetPartySize: 5, keyConditionUniqueness: false, uiVisible: true },
    ],
    keyCondition: {
      type: 'POSITION',
      label: '희망 포지션',
      desc: '주로 플레이할 포지션을 선택하세요.',
      options: [
        { value: 'TOP', label: '탑' },
        { value: 'JUNGLE', label: '정글' },
        { value: 'MID', label: '미드' },
        { value: 'ADC', label: '원딜' },
        { value: 'SUPPORT', label: '서포터' },
        { value: 'ANY', label: '전체' },
      ],
    },
  },
  {
    key: 'VALORANT',
    name: 'VALORANT',
    shortName: 'VALORANT',
    tagline: '역할군이 맞는 팀원과',
    modes: [
      { key: 'COMPETITIVE', label: '경쟁전', targetPartySize: 5, keyConditionUniqueness: false, uiVisible: true },
      { key: 'UNRATED', label: '일반전', targetPartySize: 5, keyConditionUniqueness: false, uiVisible: true },
    ],
    keyCondition: {
      type: 'ROLE',
      label: '선호 역할군',
      desc: '주로 맡을 역할군을 선택하세요.',
      options: [
        { value: 'DUELIST', label: '타격대' },
        { value: 'INITIATOR', label: '척후대' },
        { value: 'CONTROLLER', label: '전략가' },
        { value: 'SENTINEL', label: '감시자' },
      ],
    },
  },
  {
    key: 'PUBG',
    name: 'PUBG: BATTLEGROUNDS',
    shortName: 'PUBG',
    tagline: '플레이 스타일이 맞는 팀원과',
    modes: [
      { key: 'DUO', label: '듀오', targetPartySize: 2, keyConditionUniqueness: false, uiVisible: true },
      { key: 'SQUAD', label: '스쿼드', targetPartySize: 4, keyConditionUniqueness: false, uiVisible: true },
    ],
    keyCondition: {
      type: 'PLAY_STYLE',
      label: '플레이 스타일',
      desc: '어떤 스타일로 플레이할지 선택하세요.',
      options: [
        { value: 'AGGRESSIVE', label: '공격적' },
        { value: 'BALANCED', label: '균형형' },
        { value: 'SURVIVAL', label: '생존형' },
      ],
    },
  },
];

export const VOICE_OPTIONS: { value: VoicePreference; label: string }[] = [
  { value: 'REQUIRED', label: '사용' },
  { value: 'OPTIONAL', label: '선택' },
  { value: 'NO_VOICE', label: '사용 안 함' },
];

export const PURPOSE_OPTIONS: { value: PlayPurpose; label: string }[] = [
  { value: 'RANK_UP', label: '랭크 상승' },
  { value: 'NORMAL', label: '일반 플레이' },
  { value: 'FUN', label: '즐겜' },
];

export function gameConfig(game: GameKey): GameConfig {
  const found = GAMES.find((g) => g.key === game);
  if (!found) throw new Error(`UNSUPPORTED_GAME:${game}`);
  return found;
}

export function visibleModes(game: GameKey): ModeConfig[] {
  return gameConfig(game).modes.filter((m) => m.uiVisible);
}

export function modeConfig(game: GameKey, modeKey: string): ModeConfig | undefined {
  return gameConfig(game).modes.find((m) => m.key === modeKey);
}

export function targetPartySize(game: GameKey, modeKey: string): number {
  return modeConfig(game, modeKey)?.targetPartySize ?? 2;
}

/** 게임을 고르면 해당 게임의 기본 조건으로 초기화한다. */
export function defaultCondition(game: GameKey): MatchCondition {
  const cfg = gameConfig(game);
  return {
    game,
    modeKey: visibleModes(game)[0].key,
    keyCondition: { type: cfg.keyCondition.type, value: cfg.keyCondition.options[0].value },
    voicePreference: 'OPTIONAL',
    playPurpose: 'NORMAL',
  };
}

/** 게임을 바꾸면 핵심 조건/모드는 새 게임 카탈로그 값으로 갈아끼운다. */
export function switchGame(condition: MatchCondition, game: GameKey): MatchCondition {
  if (condition.game === game) return condition;
  const next = defaultCondition(game);
  return { ...next, voicePreference: condition.voicePreference, playPurpose: condition.playPurpose };
}

/** docs/02 §2 voice compatibility: REQUIRED ↔ NO_VOICE 만 incompatible. */
export function voiceCompatible(a: VoicePreference, b: VoicePreference): boolean {
  return !((a === 'REQUIRED' && b === 'NO_VOICE') || (a === 'NO_VOICE' && b === 'REQUIRED'));
}
