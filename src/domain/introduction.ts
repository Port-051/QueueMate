import { normalizeTierRange, type TierRange } from './tierRange';
import type { BoardRow, BoardWrite } from '../api/recruitment';
import type { GameKey, PlayPurpose, VoicePreference } from '../api/types';
import { conditionForMode, usesKeyCondition, keyConditionOptions } from './gameConfig';
import { storedPlayPurpose } from './gameCatalog';
import { normalizeLolRankDetails, type LolRankDivision } from './lolRank';

export type MatchResult = 'WIN' | 'LOSS' | null;
export type IntroductionRecord = Pick<BoardRow, 'userId' | 'condition' | 'preferences'> & { description?: string };
export interface SelfIntroduction {
  primaryRole: string;
  primaryRoles?: string[];
  desiredRoles: string[];
  desiredTierRange?: TierRange;
  ownTier: string | null;
  rankDivision?: LolRankDivision | null;
  champions: string[];
  winRate: number | null;
  kda: number | null;
  queueType: string;
  roomCapacity?: number;
  recentResults: MatchResult[];
  voice: VoicePreference;
  /**
   * 플레이 목적 — 빠른 연결 폼의 "매칭 시작" 에만 쓴다(2026-09-29 소유자 결정 · 글에는 목적이 없다 — platform P-29).
   * 없으면(옛 저장값 · 고른 적 없음) 부르는 쪽이 프로필 설정의 기본값(`readPreferences().defaultPurpose`)으로 채운다.
   */
  playPurpose?: PlayPurpose;
  bio: string;
}

export const emptyIntroduction = (): SelfIntroduction => ({
  primaryRole: 'ANY', desiredRoles: [], ownTier: null, rankDivision: null, champions: [], winRate: null, kda: null,
  queueType: 'ANY', recentResults: Array<MatchResult>(20).fill(null), voice: 'NO_VOICE', bio: '',
});
const storageKey = (userId: string, game: GameKey) => `queuemate:introduction:v1:${encodeURIComponent(userId)}:${game}`;
const text = (value: unknown, fallback = '') => typeof value === 'string' ? value.slice(0, 120) : fallback;
const strings = (value: unknown) => Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string').slice(0, 50).map(item => item.slice(0, 100)) : [];

export function normalizeDesiredRoles(game: GameKey, selected: string[]): string[] {
  const available = keyConditionOptions(game).filter(role => role.value !== 'ANY').map(role => role.value);
  const valid = available.filter(role => selected.includes(role));
  return valid;
}

function normalize(value: Partial<SelfIntroduction>, game: GameKey): SelfIntroduction {
  const defaults = emptyIntroduction();
  return {
    primaryRole: text(value.primaryRole, defaults.primaryRole) || 'ANY',
    primaryRoles: normalizeDesiredRoles(game, value.primaryRoles ?? (value.primaryRole && value.primaryRole !== 'ANY' ? [value.primaryRole] : [])),
    desiredRoles: normalizeDesiredRoles(game, strings(value.desiredRoles)),
    desiredTierRange: normalizeTierRange(game, value.desiredTierRange),
    ownTier: game === 'LOL' && typeof value.ownTier === 'string' && value.ownTier ? value.ownTier : null,
    ...normalizeLolRankDetails(game === 'LOL' ? value.ownTier : null, value.rankDivision),
    champions: game === 'LOL' ? strings(value.champions).map(name => name.trim()).filter(Boolean) : [],
    winRate: game === 'LOL' && typeof value.winRate === 'number' && Number.isFinite(value.winRate) && value.winRate >= 0 && value.winRate <= 100 ? value.winRate : null,
    kda: game === 'LOL' && typeof value.kda === 'number' && Number.isFinite(value.kda) && value.kda >= 0 ? value.kda : null,
    queueType: text(value.queueType, defaults.queueType) || 'ANY',
    roomCapacity: typeof value.roomCapacity === 'number' && Number.isInteger(value.roomCapacity) && value.roomCapacity >= 2 && value.roomCapacity <= 5 ? value.roomCapacity : undefined,
    recentResults: Array.from({ length: 20 }, (_, i) => game !== 'LOL' ? null : value.recentResults?.[i] === 'WIN' ? 'WIN' : value.recentResults?.[i] === 'LOSS' ? 'LOSS' : null),
    voice: ['REQUIRED', 'NO_VOICE'].includes(value.voice ?? '') ? value.voice! : defaults.voice,
    // 옛 저장값의 `NORMAL` 은 `TRYHARD` 로 옮겨 읽는다(2026-09-29 — matching D-49, 옛 이름은 400).
    playPurpose: storedPlayPurpose(value.playPurpose),
    bio: text(value.bio),
  };
}

/** 전적은 계정·게임별로 이 브라우저에 보관하며 서버 인증 전적으로 취급하지 않는다. */
export function readIntroduction(userId: string, game: GameKey): SelfIntroduction | null {
  try {
    const raw = localStorage.getItem(storageKey(userId, game));
    if (!raw) return null;
    const value: unknown = JSON.parse(raw);
    return value && typeof value === 'object' && !Array.isArray(value) ? normalize(value as Partial<SelfIntroduction>, game) : null;
  } catch { return null; }
}

export function saveIntroduction(userId: string, game: GameKey, value: SelfIntroduction): boolean {
  try { localStorage.setItem(storageKey(userId, game), JSON.stringify(normalize(value, game))); return true; }
  catch { return false; }
}

/** 수정 중인 매칭의 조건이 저장된 프로필보다 우선한다. */
export function introductionFromBoard(value: Pick<BoardWrite, 'condition' | 'preferences'> & { description?: string }, saved: SelfIntroduction | null = null): SelfIntroduction {
  const hasRoles = usesKeyCondition(value.condition.game, value.condition.modeKey);
  const rankTier = value.condition.game === 'LOL' && saved?.ownTier === value.preferences.ownTier ? value.preferences.ownTier : null;
  return {
    ...(saved ?? emptyIntroduction()), primaryRole: hasRoles ? value.condition.keyCondition.value || 'ANY' : saved?.primaryRole ?? 'ANY',
    primaryRoles: hasRoles ? [...(value.preferences.ownKeys ?? (value.condition.keyCondition.value !== 'ANY' ? [value.condition.keyCondition.value] : []))] : saved?.primaryRoles ?? [],
    desiredRoles: hasRoles ? [...value.preferences.desiredKeys] : [...(saved?.desiredRoles ?? [])], ownTier: value.preferences.ownTier,
    ...normalizeLolRankDetails(rankTier, saved?.rankDivision),
    queueType: value.condition.modeKey || 'ANY', voice: value.condition.voicePreference, bio: value.description ?? saved?.bio ?? '',
  };
}

export function applyIntroduction(value: BoardWrite, introduction: SelfIntroduction): BoardWrite {
  const modeKey = introduction.queueType || 'ANY';
  return {
    ...value,
    condition: conditionForMode({ ...value.condition, keyCondition: { ...value.condition.keyCondition, value: introduction.primaryRole || 'ANY' }, voicePreference: introduction.voice }, modeKey),
    preferences: { ...value.preferences, ownKeys: usesKeyCondition(value.condition.game, modeKey) ? normalizeDesiredRoles(value.condition.game, introduction.primaryRoles ?? (introduction.primaryRole !== 'ANY' ? [introduction.primaryRole] : [])) : [], ownTier: introduction.ownTier, desiredKeys: usesKeyCondition(value.condition.game, modeKey) ? normalizeDesiredRoles(value.condition.game, introduction.desiredRoles) : [] },
    description: introduction.bio,
  };
}

export function introductionForRow(row: IntroductionRecord): SelfIntroduction {
  // 예시 전적(mock 의 seed 사용자)은 2026-09-28 에 mock 과 함께 지웠다 — 저장된 자기소개가 없으면 빈 값이다.
  return introductionFromBoard(row, readIntroduction(row.userId, row.condition.game));
}

export function introductionInputError(value: SelfIntroduction): string {
  if (value.winRate !== null && (!Number.isFinite(value.winRate) || value.winRate < 0 || value.winRate > 100)) return '승률은 0~100 사이로 입력해 주세요.';
  if (value.kda !== null && (!Number.isFinite(value.kda) || value.kda < 0)) return 'KDA는 0 이상으로 입력해 주세요.';
  return '';
}
