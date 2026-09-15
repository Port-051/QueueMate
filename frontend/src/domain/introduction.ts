import type { BoardRow, BoardWrite } from '../api/recruitment';
import type { GameKey, VoicePreference } from '../api/types';
import { USE_MOCK } from '../config';
import { conditionForMode, usesKeyCondition, keyConditionOptions } from './gameConfig';
import { normalizeLolRankDetails, type LolRankDivision } from './lolRank';

export type MatchResult = 'WIN' | 'LOSS' | null;
export type IntroductionRecord = Pick<BoardRow, 'userId' | 'condition' | 'preferences'> & { description?: string };
export interface SelfIntroduction {
  primaryRole: string;
  primaryRoles?: string[];
  desiredRoles: string[];
  ownTier: string | null;
  rankDivision?: LolRankDivision | null;
  champions: string[];
  winRate: number | null;
  kda: number | null;
  queueType: string;
  recentResults: MatchResult[];
  voice: VoicePreference;
  bio: string;
}

export const emptyIntroduction = (): SelfIntroduction => ({
  primaryRole: 'ANY', desiredRoles: [], ownTier: null, rankDivision: null, champions: [], winRate: null, kda: null,
  queueType: 'ANY', recentResults: Array<MatchResult>(20).fill(null), voice: 'OPTIONAL', bio: '',
});
const storageKey = (userId: string, game: GameKey) => `queuemate:introduction:v1:${encodeURIComponent(userId)}:${game}`;
const text = (value: unknown, fallback = '') => typeof value === 'string' ? value.slice(0, 120) : fallback;
const strings = (value: unknown) => Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string').slice(0, 50).map(item => item.slice(0, 100)) : [];

export function normalizeDesiredRoles(game: GameKey, selected: string[]): string[] {
  const available = keyConditionOptions(game).filter(role => role.value !== 'ANY').map(role => role.value);
  const valid = [...new Set(selected)].filter(role => available.includes(role));
  return valid;
}

function normalize(value: Partial<SelfIntroduction>, game: GameKey): SelfIntroduction {
  const defaults = emptyIntroduction();
  return {
    primaryRole: text(value.primaryRole, defaults.primaryRole) || 'ANY',
    primaryRoles: normalizeDesiredRoles(game, value.primaryRoles ?? (value.primaryRole && value.primaryRole !== 'ANY' ? [value.primaryRole] : [])),
    desiredRoles: normalizeDesiredRoles(game, strings(value.desiredRoles)),
    ownTier: typeof value.ownTier === 'string' && value.ownTier ? value.ownTier : null,
    ...normalizeLolRankDetails(game === 'LOL' ? value.ownTier : null, value.rankDivision),
    champions: strings(value.champions).map(name => name.trim()).filter(Boolean),
    winRate: typeof value.winRate === 'number' && Number.isFinite(value.winRate) && value.winRate >= 0 && value.winRate <= 100 ? value.winRate : null,
    kda: typeof value.kda === 'number' && Number.isFinite(value.kda) && value.kda >= 0 ? value.kda : null,
    queueType: text(value.queueType, defaults.queueType) || 'ANY',
    recentResults: Array.from({ length: 20 }, (_, i) => value.recentResults?.[i] === 'WIN' ? 'WIN' : value.recentResults?.[i] === 'LOSS' ? 'LOSS' : null),
    voice: ['REQUIRED', 'OPTIONAL', 'NO_VOICE'].includes(value.voice ?? '') ? value.voice! : defaults.voice,
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

const seedUsers = ['u-gankflow', 'u-playmaker', 'u-supportlife', 'u-lategame', 'u-midtheory', 'u-aimking', 'u-blueocean', 'u-chickendinner', 'u-silentjungle', 'u-healingyou'];
const seedChampions: Record<GameKey, string[][]> = {
  LOL: [['리 신', '비에고'], ['아리', '오리아나'], ['쓰레쉬', '룰루'], ['징크스', '카이사'], ['신드라', '아지르']],
  VALORANT: [['제트', '레이나'], ['소바', '페이드'], ['오멘', '브림스톤'], ['사이퍼', '킬조이'], ['세이지', '스카이']],
  PUBG: [['M416', '미니14'], ['베릴 M762', 'SLR'], ['AUG', 'Mk12'], ['AKM', 'Kar98k'], ['UMP', 'SKS']],
};

export function introductionForRow(row: IntroductionRecord): SelfIntroduction {
  const saved = readIntroduction(row.userId, row.condition.game);
  if (saved) return introductionFromBoard(row, saved);
  const modeExample = /^u-lol-(?:normal_draft|swiftplay|aram)-(\d)$/.exec(row.userId);
  const index = USE_MOCK ? modeExample ? Number(modeExample[1]) : seedUsers.indexOf(row.userId) : -1;
  const example = index >= 0 ? {
    ...emptyIntroduction(), champions: seedChampions[row.condition.game][index % 5],
    ownTier: row.preferences.ownTier,
    ...normalizeLolRankDetails(row.condition.game === 'LOL' ? row.preferences.ownTier : null, ['II', 'III', 'I', 'IV'][index % 4]),
    winRate: 48 + index * 2, kda: Number((2.1 + index * 0.19).toFixed(2)),
    recentResults: Array.from({ length: 20 }, (_, i): MatchResult => (i + index) % 5 < 3 ? 'WIN' : 'LOSS'),
  } : null;
  return introductionFromBoard(row, example);
}

export function introductionInputError(value: SelfIntroduction): string {
  if (value.winRate !== null && (!Number.isFinite(value.winRate) || value.winRate < 0 || value.winRate > 100)) return '승률은 0~100 사이로 입력해 주세요.';
  if (value.kda !== null && (!Number.isFinite(value.kda) || value.kda < 0)) return 'KDA는 0 이상으로 입력해 주세요.';
  return '';
}
