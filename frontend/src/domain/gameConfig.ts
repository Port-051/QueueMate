import * as api from '../api/client';
import type {
  GameKey, GameModeView, KeyConditionType, MatchCondition, MatchSchemaView, PlayPurpose, VoicePreference,
} from '../api/types';

/**
 * 게임별 조건 카탈로그.
 *
 * **모드/핵심 조건 값의 정본은 서버다.** `GET /games/{gameKey}/match-schema`가 주는 것만
 * 화면에 그린다. 예전에는 이 파일이 모드 목록을 하드코딩했는데, 서버가 모르는 모드를
 * 노출해서 매칭 시작이 404 `UNKNOWN_GAME_MODE`로 죽었다. 목록을 서버에서 받으면
 * 백엔드가 모드를 늘리거나 줄여도 이 파일을 고칠 일이 없다.
 *
 * 여기 남는 것은 서버가 주지 않는 **표시 문구**뿐이다. 서버는 `modeKey`와 조건 값만 주고
 * 한글 라벨은 주지 않는다.
 */

export interface ModeConfig {
  key: string;
  label: string;
  /** 파티 목표 인원. 서버가 정한다 (docs/03 §9). */
  targetPartySize: number;
  /** 같은 파티 안에서 핵심 조건 값이 유일해야 하는가 (LoL POSITION hard rule). */
  keyConditionUniqueness: boolean;
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

/**
 * 표시 문구 카탈로그. 매칭 가능 여부와는 무관하다.
 *
 * 로그인 전 화면(랜딩)과 게임 계정 연결처럼 match-schema를 부를 수 없거나 부를 필요가
 * 없는 자리가 이 목록을 쓴다. 매칭 조건 UI는 `availableGames()`를 써야 한다.
 */
export const GAMES: GameConfig[] = [
  {
    key: 'LOL',
    name: 'League of Legends',
    shortName: 'LoL',
    tagline: '포지션이 맞는 팀원과',
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

/**
 * `modeKey` → 한글 라벨. 서버가 모르는 키를 주면 키를 그대로 보여준다.
 * 라벨이 없다고 선택지를 감추지는 않는다. 감추면 서버가 지원하는 모드를 못 고르게 된다.
 */
const MODE_LABELS: Record<string, string> = {
  SOLO_DUO_RANKED: '솔로/듀오 랭크',
  NORMAL_DRAFT: '일반 게임',
  ARAM: '칼바람 나락',
  COMPETITIVE: '경쟁전',
  UNRATED: '일반전',
  DUO: '듀오',
  SQUAD: '스쿼드',
};

/* ---------- 서버 카탈로그 ---------- */

const catalog = new Map<GameKey, MatchSchemaView>();
let loaded = false;

/**
 * 서버가 지원한다고 말한 게임과 모드를 받아 온다.
 * 인증이 필요한 엔드포인트라 로그인 이후에 한 번 부른다.
 */
export async function loadGameCatalog(): Promise<void> {
  const games = await api.listGames();
  const schemas = await Promise.all(games.map((g) => api.getMatchSchema(g.game)));
  catalog.clear();
  schemas.forEach((schema) => catalog.set(schema.game, schema));
  loaded = true;
}

export const isGameCatalogLoaded = (): boolean => loaded;

const toModeConfig = (m: GameModeView): ModeConfig => ({
  key: m.modeKey,
  label: MODE_LABELS[m.modeKey] ?? m.modeKey,
  targetPartySize: m.targetPartySize,
  keyConditionUniqueness: m.roleUniqueness,
});

export function gameConfig(game: GameKey): GameConfig {
  const found = GAMES.find((g) => g.key === game);
  if (!found) throw new Error(`UNSUPPORTED_GAME:${game}`);
  return found;
}

/** 매칭 조건 UI가 쓰는 게임 목록. 서버가 모드를 하나도 주지 않은 게임은 고를 수 없다. */
export function availableGames(): GameConfig[] {
  return GAMES.filter((g) => (catalog.get(g.key)?.modes.length ?? 0) > 0);
}

export function visibleModes(game: GameKey): ModeConfig[] {
  return (catalog.get(game)?.modes ?? []).map(toModeConfig);
}

/** 서버가 허용한 핵심 조건 값만 남긴다. 라벨은 정적 카탈로그에서 찾고 없으면 값을 그대로 쓴다. */
export function keyConditionOptions(game: GameKey): KeyConditionOption[] {
  const cfg = gameConfig(game);
  const values = catalog.get(game)?.keyCondition.values;
  if (!values) return cfg.keyCondition.options;
  return values.map((value) => ({
    value,
    label: cfg.keyCondition.options.find((o) => o.value === value)?.label ?? value,
  }));
}

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

export function modeConfig(game: GameKey, modeKey: string): ModeConfig | undefined {
  return visibleModes(game).find((m) => m.key === modeKey);
}

export function targetPartySize(game: GameKey, modeKey: string): number {
  return modeConfig(game, modeKey)?.targetPartySize ?? 2;
}

/**
 * 게임을 고르면 해당 게임의 기본 조건으로 초기화한다.
 * 카탈로그가 비어 있으면 서버가 모르는 값을 만들어내지 않고 빈 문자열을 둔다.
 * 폼이 빈 선택으로 보이고 매칭 시작은 서버가 막는다.
 */
export function defaultCondition(game: GameKey): MatchCondition {
  const cfg = gameConfig(game);
  return {
    game,
    modeKey: visibleModes(game)[0]?.key ?? '',
    keyCondition: { type: cfg.keyCondition.type, value: keyConditionOptions(game)[0]?.value ?? '' },
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
