import type { GameKey, GameModeView, KeyConditionType, PlayPurpose, VoicePreference } from '../api/types';

/**
 * 프론트엔드 체험용 게임 카탈로그. LoL은 승인된 화면 시안에 맞춰
 * 네 가지 모드에서 2인 파티를 모집한다. 신속 대전과 일반·칼바람 2인 정원은
 * 기존 서버 계약과 다른 미리보기 설정이며 실제 API 지원을 의미하지 않는다.
 * 실제 API 모드의 화면은 서버가 반환하는 카탈로그만 사용한다.
 */
export interface GameSeed {
  keyConditionType: KeyConditionType;
  values: string[];
  modes: GameModeView[];
}

export const GAME_SEED: Record<GameKey, GameSeed> = {
  LOL: {
    keyConditionType: 'POSITION',
    values: ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT', 'ANY'],
    modes: [
      { modeKey: 'SOLO_DUO_RANKED', targetPartySize: 2, roleUniqueness: true },
      { modeKey: 'NORMAL_DRAFT', targetPartySize: 2, roleUniqueness: true },
      { modeKey: 'SWIFTPLAY', targetPartySize: 2, roleUniqueness: true },
      { modeKey: 'ARAM', targetPartySize: 2, roleUniqueness: false },
    ],
  },
  VALORANT: {
    keyConditionType: 'ROLE',
    values: ['DUELIST', 'INITIATOR', 'CONTROLLER', 'SENTINEL'],
    modes: [
      { modeKey: 'COMPETITIVE', targetPartySize: 5, roleUniqueness: false },
      { modeKey: 'UNRATED', targetPartySize: 5, roleUniqueness: false },
    ],
  },
  PUBG: {
    keyConditionType: 'PLAY_STYLE',
    values: ['AGGRESSIVE', 'BALANCED', 'SURVIVAL'],
    modes: [
      { modeKey: 'DUO', targetPartySize: 2, roleUniqueness: false },
      { modeKey: 'SQUAD', targetPartySize: 4, roleUniqueness: false },
    ],
  },
};

export const VOICE_PREFERENCES: VoicePreference[] = ['REQUIRED', 'OPTIONAL', 'NO_VOICE'];
export const PLAY_PURPOSES: PlayPurpose[] = ['RANK_UP', 'NORMAL', 'FUN'];

export const modeOf = (game: GameKey, modeKey: string): GameModeView | undefined =>
  GAME_SEED[game]?.modes.find((m) => m.modeKey === modeKey);
