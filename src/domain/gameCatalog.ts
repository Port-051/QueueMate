import type { GameKey, KeyConditionType, PlayPurpose, PubgPerspective, TierLadder, VoicePreference } from '../api/types';

/**
 * 게임 · 모드 · 핵심 조건 값 · 티어 사다리의 **정적 상수**.
 *
 * **원본은 `matching/seed/gameconfig.redis` 이고 여기는 그 사본이다 — 두 곳이다**(2026-09-28 소유자 결정). 백엔드에 조회 API 가 없어
 * (`GET /games` 는 계약에만 있고 미구현 — `matching/contracts/README.md` #7) 프런트가 값을 들고 있는다. **seed 의 모드 · 티어를 고치면 여기도 같이 고친다** —
 * 어긋나면 서버가 400 을 낸다(`matching` `INVALID_MATCH_CONDITION` · `platform` `VALIDATION_FAILED "mode: … 에 없는 모드입니다"`).
 * 값은 seed 그대로다(모드 24 · `targetPartySize` · `tierRule` · `positionUniqueness` · `tierLadder` · 티어 이름 셋 — `UNRANKED` 포함). 한글 라벨만 프런트의 것이다(seed 에 없다).
 *
 * - 모드 키 · 정원 · `tierRule`(`EXIST` = 티어를 본다 · `NONE` = 안 본다) · `positionUniqueness`(파티 안에서 포지션이 겹칠 수 없는가 — PUBG 에는 없다)는
 *   `HSET qm:gameconfig:{GAME}:{MODE}` 그대로. 티어별 허용 범위(`tier-range`)는 옮기지 않았다 — 판정은 서버가 한다(자동 합류 · 매칭).
 * - 핵심 조건(`keyCondition`)의 type 과 값은 `matching/contracts/openapi.yaml` `KeyCondition` 그대로다(글의 `wantedPositions` 도 이 이름이다 — 게임 계정에는 포지션이 없다, 2026-09-29):
 *   LoL `POSITION`(TOP/JUNGLE/MID/ADC/SUPPORT) · VALORANT `ROLE`(4역할군) · PUBG `PLATFORM`(STEAM/KAKAO — 원본 프런트의 `PLAY_STYLE` 이 아니다, A-13).
 *   **LoL 의 `NONE`(포지션 없음)은 선택지가 아니라 서버에 보내는 값이다** — `positionUniqueness=false` 모드(칼바람)에서만 · 그때는 `NONE` 만 받고, 포지션을 보는 모드에서는
 *   `NONE` 을 거절한다(`matching` `LolConditionValidator`). 화면은 그 자리를 `ANY` 로 두고 보낼 때 옮긴다(`domain/matchRequest.ts`). VALORANT · PUBG 에는 "없음" 이 없다.
 * - **사다리(`tierLadder`)는 모드가 어느 랭크 큐의 티어를 보는가다**(2026-09-29 소유자 결정 "모드별 티어를 무조건 저장한다" — seed 의 모드 HASH 에 새 필드 `tierLadder`, matching 에서 더하는 중).
 *   LoL `RANKED_SOLO`→`SOLO` · `RANKED_FLEX_2/3/5`→`FLEX` / VALORANT `COMPETITIVE_DUO/TRIO`→`COMPETITIVE` / PUBG 랭크 모드 넷(`RANKED_{DUO|SQUAD}_{TPP|FPP}`)→전부 `RANKED`
 *   (시즌 36(2025-06-05)부터 PUBG 티어/RP 는 듀오 · 스쿼드와 FPP · TPP 에 걸쳐 하나다 — `matching/WORKLOG_2026-09-14.md` §1).
 *   `tierRule=NONE` 모드는 사다리가 없다. 게임 프로필의 `tiers` 가 사다리마다 티어를 하나씩 든다(`api/types.ts` `GameProfile`) — 고르는 규칙은 `domain/profileTier.ts`.
 * - 티어 **이름**(`tierNames`)은 `ZADD qm:gameconfig:{GAME}:tier` 의 순서(오름차순 · 0 이 `UNRANKED`)이고 한 게임의 사다리들이 같이 쓴다.
 *   VALORANT 는 디비전이 1→3 으로 커지고 LoL · PUBG 는 4→1 로 작아진다. "더 높은 티어" 는 이 순서의 인덱스다.
 * - `VoicePreference` 는 `REQUIRED` · `NO_VOICE` 둘이다 — `OPTIONAL` 은 없다(openapi 개정 이력 · docs/11 #31). `PlayPurpose` 는 셋.
 * - **모드의 `group` · `perspective` 와 게임의 `modeGroups` 는 seed 에 없는 프런트 전용 UI 메타다**(2026-09-29 소유자 지시 — "모드는 모드끼리 묶고 밑에 인원 수를 따로").
 *   모드 선택기가 첫 줄에 묶음, 그 아래 인원(`targetPartySize`), PUBG 는 시점을 고르고 그 셋으로 모드 키 하나를 찾는다(`domain/modeChoice.ts`).
 *   **묶음의 원본은 이 칸이다 — 모드 키 문자열을 잘라 묶지 않는다.** seed 에 모드를 더하면 `group`(PUBG 는 `perspective` 도)을 같이 채운다.
 */

export type TierRule = 'NONE' | 'EXIST';

interface GameModeSeedBase {
  key: string;
  label: string;
  targetPartySize: number;
  /** 파티 안에서 핵심 조건 값이 겹칠 수 없는가. seed 가 PUBG 에는 두지 않았다(`HDEL`) — 플랫폼은 겹쳐야 한다. */
  positionUniqueness?: boolean;
  /** 모드 선택기의 묶음 — `GameSeed.modeGroups` 의 키. **프런트 전용 UI 메타(seed 에 없다)** — 머리 주석. */
  group: string;
  /** PUBG 시점 — **프런트 전용 UI 메타**. seed 는 시점을 모드 키에 접어 두었다(`NORMAL_DUO_TPP`). 글의 `conditions.perspective` 도 이 값이다(`rooms/boardRoom.ts`). */
  perspective?: PubgPerspective;
}

/** 모드 선택기 첫 줄의 한 칸 — **프런트 전용 UI 메타**(라벨도 프런트의 것이다). */
export interface ModeGroupSeed { key: string; label: string; }

/** `tierRule=EXIST` 모드는 사다리가 반드시 있고 `NONE` 모드는 없다 — 타입이 그 짝을 지킨다(`mode.tierRule === 'EXIST'` 로 좁히면 `tierLadder` 가 있다). */
export type GameModeSeed = GameModeSeedBase & (
  | { tierRule: 'NONE'; tierLadder?: undefined }
  | { tierRule: 'EXIST'; tierLadder: TierLadder }
);

export interface KeyConditionValue { value: string; label: string; }

export interface GameSeed {
  key: GameKey;
  keyConditionType: KeyConditionType;
  keyConditionValues: readonly KeyConditionValue[];
  modes: readonly GameModeSeed[];
  /** 모드 선택기의 묶음 — 이 순서로 한 줄에 선다. 모드의 `group` 이 이 키다. **프런트 전용 UI 메타.** */
  modeGroups: readonly ModeGroupSeed[];
  /** 그 게임의 사다리 키 — 게임 프로필 `tiers` 의 키 전부이고, 화면(내 정보의 티어 줄 · 카드의 "가장 높은 티어" 비교)은 이 순서로 돈다. */
  tierLadders: readonly TierLadder[];
  /** 티어 이름 — `UNRANKED` 부터 위로. 인덱스가 ZSET 의 score 다. 그 게임의 사다리들이 같이 쓴다. */
  tierNames: readonly string[];
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
    ],
    modes: [
      { key: 'RANKED_SOLO', label: '솔로 랭크', group: 'SOLO_RANKED', targetPartySize: 2, positionUniqueness: true, tierRule: 'EXIST', tierLadder: 'SOLO' },
      // 게임 자체가 4인 파티 큐를 금지하므로 RANKED_FLEX_4 는 없다.
      { key: 'RANKED_FLEX_2', label: '자유 랭크 2인', group: 'FLEX_RANKED', targetPartySize: 2, positionUniqueness: true, tierRule: 'EXIST', tierLadder: 'FLEX' },
      { key: 'RANKED_FLEX_3', label: '자유 랭크 3인', group: 'FLEX_RANKED', targetPartySize: 3, positionUniqueness: true, tierRule: 'EXIST', tierLadder: 'FLEX' },
      { key: 'RANKED_FLEX_5', label: '자유 랭크 5인', group: 'FLEX_RANKED', targetPartySize: 5, positionUniqueness: true, tierRule: 'EXIST', tierLadder: 'FLEX' },
      // 칼바람은 포지션 개념이 없다 — positionUniqueness=false 가 "핵심 조건을 안 본다" 의 근거다.
      { key: 'ARAM_2', label: '칼바람 2인', group: 'ARAM', targetPartySize: 2, positionUniqueness: false, tierRule: 'NONE' },
      { key: 'ARAM_3', label: '칼바람 3인', group: 'ARAM', targetPartySize: 3, positionUniqueness: false, tierRule: 'NONE' },
      { key: 'ARAM_4', label: '칼바람 4인', group: 'ARAM', targetPartySize: 4, positionUniqueness: false, tierRule: 'NONE' },
      { key: 'ARAM_5', label: '칼바람 5인', group: 'ARAM', targetPartySize: 5, positionUniqueness: false, tierRule: 'NONE' },
      { key: 'NORMAL_2', label: '일반 2인', group: 'NORMAL', targetPartySize: 2, positionUniqueness: true, tierRule: 'NONE' },
      { key: 'NORMAL_3', label: '일반 3인', group: 'NORMAL', targetPartySize: 3, positionUniqueness: true, tierRule: 'NONE' },
      { key: 'NORMAL_4', label: '일반 4인', group: 'NORMAL', targetPartySize: 4, positionUniqueness: true, tierRule: 'NONE' },
      { key: 'NORMAL_5', label: '일반 5인', group: 'NORMAL', targetPartySize: 5, positionUniqueness: true, tierRule: 'NONE' },
    ],
    modeGroups: [
      { key: 'SOLO_RANKED', label: '솔로 랭크' },
      { key: 'FLEX_RANKED', label: '자유 랭크' },
      { key: 'ARAM', label: '칼바람' },
      { key: 'NORMAL', label: '일반' },
    ],
    tierLadders: ['SOLO', 'FLEX'],
    tierNames: LOL_TIERS,
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
      { key: 'COMPETITIVE_DUO', label: '경쟁전 듀오', group: 'COMPETITIVE', targetPartySize: 2, positionUniqueness: true, tierRule: 'EXIST', tierLadder: 'COMPETITIVE' },
      { key: 'COMPETITIVE_TRIO', label: '경쟁전 트리오', group: 'COMPETITIVE', targetPartySize: 3, positionUniqueness: true, tierRule: 'EXIST', tierLadder: 'COMPETITIVE' },
      { key: 'UNRATED_DUO', label: '일반전 듀오', group: 'UNRATED', targetPartySize: 2, positionUniqueness: true, tierRule: 'NONE' },
      { key: 'UNRATED_TRIO', label: '일반전 트리오', group: 'UNRATED', targetPartySize: 3, positionUniqueness: true, tierRule: 'NONE' },
    ],
    modeGroups: [
      { key: 'COMPETITIVE', label: '경쟁전' },
      { key: 'UNRATED', label: '일반전' },
    ],
    tierLadders: ['COMPETITIVE'],
    tierNames: VALORANT_TIERS,
  },
  PUBG: {
    key: 'PUBG',
    keyConditionType: 'PLATFORM',
    keyConditionValues: [
      { value: 'STEAM', label: '스팀' },
      { value: 'KAKAO', label: '카카오' },
    ],
    modes: [
      { key: 'NORMAL_DUO_TPP', label: '일반 듀오 TPP', group: 'NORMAL', perspective: 'TPP', targetPartySize: 2, tierRule: 'NONE' },
      { key: 'NORMAL_DUO_FPP', label: '일반 듀오 FPP', group: 'NORMAL', perspective: 'FPP', targetPartySize: 2, tierRule: 'NONE' },
      { key: 'NORMAL_SQUAD_TPP', label: '일반 스쿼드 TPP', group: 'NORMAL', perspective: 'TPP', targetPartySize: 4, tierRule: 'NONE' },
      { key: 'NORMAL_SQUAD_FPP', label: '일반 스쿼드 FPP', group: 'NORMAL', perspective: 'FPP', targetPartySize: 4, tierRule: 'NONE' },
      { key: 'RANKED_DUO_TPP', label: '경쟁전 듀오 TPP', group: 'RANKED', perspective: 'TPP', targetPartySize: 2, tierRule: 'EXIST', tierLadder: 'RANKED' },
      { key: 'RANKED_DUO_FPP', label: '경쟁전 듀오 FPP', group: 'RANKED', perspective: 'FPP', targetPartySize: 2, tierRule: 'EXIST', tierLadder: 'RANKED' },
      { key: 'RANKED_SQUAD_TPP', label: '경쟁전 스쿼드 TPP', group: 'RANKED', perspective: 'TPP', targetPartySize: 4, tierRule: 'EXIST', tierLadder: 'RANKED' },
      { key: 'RANKED_SQUAD_FPP', label: '경쟁전 스쿼드 FPP', group: 'RANKED', perspective: 'FPP', targetPartySize: 4, tierRule: 'EXIST', tierLadder: 'RANKED' },
    ],
    modeGroups: [
      { key: 'NORMAL', label: '일반' },
      { key: 'RANKED', label: '경쟁전' },
    ],
    tierLadders: ['RANKED'],
    tierNames: PUBG_TIERS,
  },
};

export const GAME_KEYS: readonly GameKey[] = ['LOL', 'VALORANT', 'PUBG'];

/** PUBG 시점 — 선택기에 이 순서로 선다(3인칭 먼저 · 모르면 3인칭). 라벨은 프런트의 것이다(2026-09-29 소유자 지시 — "TPP · FPP" 가 아니라 "3인칭 · 1인칭"). */
export const PERSPECTIVES: readonly PubgPerspective[] = ['TPP', 'FPP'];
export const PERSPECTIVE_LABEL: Record<PubgPerspective, string> = { TPP: '3인칭', FPP: '1인칭' };

/** `OPTIONAL` 은 없다 — 매칭 전에 답이 정해지지 않는 조건은 조건이 아니다(openapi `VoicePreference` 개정 이력). */
export const VOICE_PREFERENCES: readonly VoicePreference[] = ['REQUIRED', 'NO_VOICE'];
export const PLAY_PURPOSES: readonly PlayPurpose[] = ['RANK_UP', 'NORMAL', 'FUN'];

export const modeSeed = (game: GameKey, modeKey: string): GameModeSeed | undefined =>
  GAME_CATALOG[game].modes.find((m) => m.key === modeKey);

/** 그 모드가 티어를 보는가(`tierRule === 'EXIST'`). 보면 매칭 요청의 `tier` 가 사실상 필수다(`GOLD_4` 꼴). 모르는 모드는 안 본다고 답한다. */
export const modeUsesTier = (game: GameKey, modeKey: string): boolean => modeSeed(game, modeKey)?.tierRule === 'EXIST';

/** 사다리에 있는 이름인가(`ZSCORE` 가 `null` 이 아닌가). */
export const isKnownTier = (game: GameKey, tier: string): boolean => GAME_CATALOG[game].tierNames.includes(tier);

/** 티어 이름의 높이 — `tierNames` 의 인덱스(`UNRANKED` 가 0). 사다리에 없는 이름은 `-1`(어느 티어보다 낮게 본다). */
export const tierScore = (game: GameKey, tier: string): number => GAME_CATALOG[game].tierNames.indexOf(tier);

/** 그 모드가 보는 사다리(seed 모드 HASH 의 `tierLadder`). `tierRule=NONE` 모드 · 모르는 모드는 `null`. */
export const modeTierLadder = (game: GameKey, modeKey: string): TierLadder | null => {
  const mode = modeSeed(game, modeKey);
  return mode?.tierRule === 'EXIST' ? mode.tierLadder : null;
};

/** 사다리의 한글 이름 — 프런트의 것이다(seed 에 없다). 내 정보의 티어 줄 · 매칭을 막는 문구 · 카드의 티어 풍선말이 쓴다. */
export const TIER_LADDER_LABEL: Record<TierLadder, string> = {
  SOLO: '솔로랭크',
  FLEX: '자유랭크',
  COMPETITIVE: '경쟁전',
  RANKED: '랭크',
};
