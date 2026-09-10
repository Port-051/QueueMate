import type { GameKey, GameModeView, KeyConditionType, PlayPurpose, VoicePreference } from '../api/types';

/**
 * v2 계약이 정한 gameconfig seed 그대로다 (docs/14 §3.2·§3.3).
 *
 * `domain/gameConfig.ts`는 화면이 쓰는 한글 카탈로그이고, 이쪽은 **서버가 아는 값**이다.
 * 둘이 어긋나면 mock 모드에서는 통하고 실서버에서는 404가 나므로 여기는 계약만 따른다.
 * mock adapter가 계약을 대신 강제한다.
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
    modes: [{ modeKey: 'SOLO_DUO_RANKED', targetPartySize: 2, roleUniqueness: true }],
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
