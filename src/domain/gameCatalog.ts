import type { GameKey, KeyConditionType, PlayPurpose, VoicePreference } from '../api/types';

/**
 * 게임 · 모드 · 핵심 조건 값 · 티어 사다리의 **정적 상수**.
 *
 * **원본은 `matching/seed/gameconfig.redis` 이고 여기는 그 사본이다 — 두 곳이다**(2026-09-28 소유자 결정). 백엔드에 조회 API 가 없어
 * (`GET /games` 는 계약에만 있고 미구현 — `matching/contracts/README.md` #7) 프런트가 값을 들고 있는다. **seed 의 모드 · 티어를 고치면 여기도 같이 고친다** —
 * 어긋나면 서버가 400 을 낸다(`matching` `INVALID_MATCH_CONDITION` · `platform` `VALIDATION_FAILED "mode: … 에 없는 모드입니다"`).
 * 값은 seed 그대로다(모드 24 · `targetPartySize` · `tierRule` · `positionUniqueness` · 사다리 셋 — `UNRANKED` 포함). 한글 라벨만 프런트의 것이다(seed 에 없다).
 *
 * - 모드 키 · 정원 · `tierRule`(`EXIST` = 티어를 본다 · `NONE` = 안 본다) · `positionUniqueness`(파티 안에서 포지션이 겹칠 수 없는가 — PUBG 에는 없다)는
 *   `HSET qm:gameconfig:{GAME}:{MODE}` 그대로. 티어별 허용 범위(`tier-range`)는 옮기지 않았다 — 판정은 서버가 한다(자동 합류 · 매칭).
 * - 핵심 조건(`keyCondition`)의 type 과 값은 `matching/contracts/openapi.yaml` `KeyCondition` · `platform-api.md` "계정" 의 `mainPosition`:
 *   LoL `POSITION`(TOP/JUNGLE/MID/ADC/SUPPORT/NONE) · VALORANT `ROLE`(4역할군) · PUBG `PLATFORM`(STEAM/KAKAO — 원본 프런트의 `PLAY_STYLE` 이 아니다, A-13).
 * - 티어 사다리는 `ZADD qm:gameconfig:{GAME}:tier` 의 순서(오름차순 · 0 이 `UNRANKED`). VALORANT 는 디비전이 1→3 으로 커지고 LoL · PUBG 는 4→1 로 작아진다.
 * - `VoicePreference` 는 `REQUIRED` · `NO_VOICE` 둘이다 — `OPTIONAL` 은 없다(openapi 개정 이력 · docs/11 #31). `PlayPurpose` 는 셋.
 */

export type TierRule = 'NONE' | 'EXIST';

export interface GameModeSeed {
  key: string;
  label: string;
  targetPartySize: number;
  tierRule: TierRule;
  /** 파티 안에서 핵심 조건 값이 겹칠 수 없는가. seed 가 PUBG 에는 두지 않았다(`HDEL`) — 플랫폼은 겹쳐야 한다. */
  positionUniqueness?: boolean;
}

export interface KeyConditionValue { value: string; label: string; }

export interface GameSeed {
  key: GameKey;
  keyConditionType: KeyConditionType;
  keyConditionValues: readonly KeyConditionValue[];
  modes: readonly GameModeSeed[];
  /** `UNRANKED` 부터 위로. 인덱스가 ZSET 의 score 다. */
  tierLadder: readonly string[];
}

const LOL_TIERS = [
  'UNRANKED',
  'IRON_4', 'IRON_3', 'IRON_2', 'IRON_1',
  'BRONZE_4', 'BRONZE_3', 'BRONZE_2', 'BRONZE_1',
  'SILVER_4', 'SILVER_3', 'SILVER_2', 'SILVER_1',
  'GOLD_4', 'GOLD_3', 'GOLD_2', 'GOLD_1',
  'PLATINUM_4', 'PLATINUM_3', 'PLATINUM_2', 'PLATINUM_1',
  'EMERALD_4', 'EMERALD_3', 'EMERALD_2', 'EMERALD_1',
  'DIAMOND_4', 'DIAMOND_3', 'DIAMOND_2', 'DIAMOND_1',
  'MASTER', 'GRANDMASTER', 'CHALLENGER',
] as const;

const VALORANT_TIERS = [
  'UNRANKED',
  'IRON_1', 'IRON_2', 'IRON_3',
  'BRONZE_1', 'BRONZE_2', 'BRONZE_3',
  'SILVER_1', 'SILVER_2', 'SILVER_3',
  'GOLD_1', 'GOLD_2', 'GOLD_3',
  'PLATINUM_1', 'PLATINUM_2', 'PLATINUM_3',
  'DIAMOND_1', 'DIAMOND_2', 'DIAMOND_3',
  'ASCENDANT_1', 'ASCENDANT_2', 'ASCENDANT_3',
  'IMMORTAL_1', 'IMMORTAL_2', 'IMMORTAL_3',
  'RADIANT',
] as const;

const PUBG_TIERS = [
  'UNRANKED',
  'BRONZE_4', 'BRONZE_3', 'BRONZE_2', 'BRONZE_1',
  'SILVER_4', 'SILVER_3', 'SILVER_2', 'SILVER_1',
  'GOLD_4', 'GOLD_3', 'GOLD_2', 'GOLD_1',
  'PLATINUM_4', 'PLATINUM_3', 'PLATINUM_2', 'PLATINUM_1',
  'CRYSTAL_4', 'CRYSTAL_3', 'CRYSTAL_2', 'CRYSTAL_1',
  'DIAMOND_4', 'DIAMOND_3', 'DIAMOND_2', 'DIAMOND_1',
  'MASTER', 'SURVIVOR',
] as const;

export const GAME_CATALOG: Record<GameKey, GameSeed> = {
  LOL: {
    key: 'LOL',
    keyConditionType: 'POSITION',
    keyConditionValues: [
      { value: 'TOP', label: '탑' },
      { value: 'JUNGLE', label: '정글' },
      { value: 'MID', label: '미드' },
      { value: 'ADC', label: '원딜' },
      { value: 'SUPPORT', label: '서포터' },
      { value: 'NONE', label: '무관' },
    ],
    modes: [
      { key: 'RANKED_SOLO', label: '솔로 랭크', targetPartySize: 2, positionUniqueness: true, tierRule: 'EXIST' },
      // 게임 자체가 4인 파티 큐를 금지하므로 RANKED_FLEX_4 는 없다.
      { key: 'RANKED_FLEX_2', label: '자유 랭크 2인', targetPartySize: 2, positionUniqueness: true, tierRule: 'EXIST' },
      { key: 'RANKED_FLEX_3', label: '자유 랭크 3인', targetPartySize: 3, positionUniqueness: true, tierRule: 'EXIST' },
      { key: 'RANKED_FLEX_5', label: '자유 랭크 5인', targetPartySize: 5, positionUniqueness: true, tierRule: 'EXIST' },
      // 칼바람은 포지션 개념이 없다 — positionUniqueness=false 가 "핵심 조건을 안 본다" 의 근거다.
      { key: 'ARAM_2', label: '칼바람 2인', targetPartySize: 2, positionUniqueness: false, tierRule: 'NONE' },
      { key: 'ARAM_3', label: '칼바람 3인', targetPartySize: 3, positionUniqueness: false, tierRule: 'NONE' },
      { key: 'ARAM_4', label: '칼바람 4인', targetPartySize: 4, positionUniqueness: false, tierRule: 'NONE' },
      { key: 'ARAM_5', label: '칼바람 5인', targetPartySize: 5, positionUniqueness: false, tierRule: 'NONE' },
      { key: 'NORMAL_2', label: '일반 2인', targetPartySize: 2, positionUniqueness: true, tierRule: 'NONE' },
      { key: 'NORMAL_3', label: '일반 3인', targetPartySize: 3, positionUniqueness: true, tierRule: 'NONE' },
      { key: 'NORMAL_4', label: '일반 4인', targetPartySize: 4, positionUniqueness: true, tierRule: 'NONE' },
      { key: 'NORMAL_5', label: '일반 5인', targetPartySize: 5, positionUniqueness: true, tierRule: 'NONE' },
    ],
    tierLadder: LOL_TIERS,
  },
  VALORANT: {
    key: 'VALORANT',
    keyConditionType: 'ROLE',
    keyConditionValues: [
      { value: 'DUELIST', label: '타격대' },
      { value: 'INITIATOR', label: '척후대' },
      { value: 'CONTROLLER', label: '전략가' },
      { value: 'SENTINEL', label: '감시자' },
    ],
    modes: [
      { key: 'COMPETITIVE_DUO', label: '경쟁전 듀오', targetPartySize: 2, positionUniqueness: true, tierRule: 'EXIST' },
      { key: 'COMPETITIVE_TRIO', label: '경쟁전 트리오', targetPartySize: 3, positionUniqueness: true, tierRule: 'EXIST' },
      { key: 'UNRATED_DUO', label: '일반전 듀오', targetPartySize: 2, positionUniqueness: true, tierRule: 'NONE' },
      { key: 'UNRATED_TRIO', label: '일반전 트리오', targetPartySize: 3, positionUniqueness: true, tierRule: 'NONE' },
    ],
    tierLadder: VALORANT_TIERS,
  },
  PUBG: {
    key: 'PUBG',
    keyConditionType: 'PLATFORM',
    keyConditionValues: [
      { value: 'STEAM', label: '스팀' },
      { value: 'KAKAO', label: '카카오' },
    ],
    modes: [
      { key: 'NORMAL_DUO_TPP', label: '일반 듀오 TPP', targetPartySize: 2, tierRule: 'NONE' },
      { key: 'NORMAL_DUO_FPP', label: '일반 듀오 FPP', targetPartySize: 2, tierRule: 'NONE' },
      { key: 'NORMAL_SQUAD_TPP', label: '일반 스쿼드 TPP', targetPartySize: 4, tierRule: 'NONE' },
      { key: 'NORMAL_SQUAD_FPP', label: '일반 스쿼드 FPP', targetPartySize: 4, tierRule: 'NONE' },
      { key: 'RANKED_DUO_TPP', label: '경쟁전 듀오 TPP', targetPartySize: 2, tierRule: 'EXIST' },
      { key: 'RANKED_DUO_FPP', label: '경쟁전 듀오 FPP', targetPartySize: 2, tierRule: 'EXIST' },
      { key: 'RANKED_SQUAD_TPP', label: '경쟁전 스쿼드 TPP', targetPartySize: 4, tierRule: 'EXIST' },
      { key: 'RANKED_SQUAD_FPP', label: '경쟁전 스쿼드 FPP', targetPartySize: 4, tierRule: 'EXIST' },
    ],
    tierLadder: PUBG_TIERS,
  },
};

export const GAME_KEYS: readonly GameKey[] = ['LOL', 'VALORANT', 'PUBG'];

/** `OPTIONAL` 은 없다 — 매칭 전에 답이 정해지지 않는 조건은 조건이 아니다(openapi `VoicePreference` 개정 이력). */
export const VOICE_PREFERENCES: readonly VoicePreference[] = ['REQUIRED', 'NO_VOICE'];
export const PLAY_PURPOSES: readonly PlayPurpose[] = ['RANK_UP', 'NORMAL', 'FUN'];

export const modeSeed = (game: GameKey, modeKey: string): GameModeSeed | undefined =>
  GAME_CATALOG[game].modes.find((m) => m.key === modeKey);

/** 그 모드가 티어를 보는가(`tierRule === 'EXIST'`). 보면 매칭 요청의 `tier` 가 사실상 필수다(`GOLD_4` 꼴). 모르는 모드는 안 본다고 답한다. */
export const modeUsesTier = (game: GameKey, modeKey: string): boolean => modeSeed(game, modeKey)?.tierRule === 'EXIST';

/** 사다리에 있는 이름인가(`ZSCORE` 가 `null` 이 아닌가). */
export const isKnownTier = (game: GameKey, tier: string): boolean => GAME_CATALOG[game].tierLadder.includes(tier);
