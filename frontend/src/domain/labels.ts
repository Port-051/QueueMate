import type {
  GameKey, MatchCondition, PlayAmount, PlayPurpose,
  ReportReason, ReservationStatus, VoicePreference,
} from '../api/types';
import { gameConfig, modeConfig, usesKeyCondition } from './gameConfig';

export const gameLabel = (g: GameKey) => gameConfig(g).shortName;
export const gameFullLabel = (g: GameKey) => gameConfig(g).name;

export function modeLabel(game: GameKey, modeKey: string): string {
  return modeKey === 'ANY' ? '큐 무관' : modeConfig(game, modeKey)?.label ?? modeKey;
}

export function keyConditionLabel(condition: MatchCondition): string {
  if (condition.keyCondition.value === 'ANY') return '무관';
  const cfg = gameConfig(condition.game);
  return cfg.keyCondition.options.find((o) => o.value === condition.keyCondition.value)?.label ?? condition.keyCondition.value;
}

export const keyConditionTitle = (game: GameKey) => gameConfig(game).keyCondition.label;

export const VOICE_LABEL: Record<VoicePreference, string> = {
  REQUIRED: '음성 사용',
  NO_VOICE: '음성 사용 안 함',
};

export const PURPOSE_LABEL: Record<PlayPurpose, string> = {
  RANK_UP: '랭크 상승',
  TRYHARD: '빡겜',
  FUN: '즐겜',
};

export const PLAY_AMOUNT_LABEL: Record<PlayAmount, string> = {
  ONE_GAME: '1판',
  TWO_PLUS: '2판 이상',
};

export const RESERVATION_STATUS_LABEL: Record<ReservationStatus, string> = {
  ACTIVE: '대기 중',
  PROPOSED: '제안 확인 중',
  MATCHED: '성사됨',
  CANCELLED: '취소됨',
  EXPIRED: '만료됨',
  COMPLETED: '완료됨',
};

/** 신고 사유 — platform-api.md "친구 · 신고 · 최근 함께한 사람" 의 다섯(원본의 여섯 이름은 우리 백엔드에 없다). `OTHER` 는 `detail` 이 필수다. */
export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'ABUSE', label: '욕설 · 비매너' },
  { value: 'CHEATING', label: '핵 · 대리' },
  { value: 'SPAM', label: '도배 · 광고' },
  { value: 'NO_SHOW', label: '잠수 · 탈주' },
  { value: 'OTHER', label: '기타 (설명 필수)' },
];

/** 조건 한 줄 요약. 카드/리스트에서 재사용한다. */
export function conditionSummary(c: MatchCondition): string[] {
  return [modeLabel(c.game, c.modeKey), ...(usesKeyCondition(c.game, c.modeKey) ? [keyConditionLabel(c)] : []), VOICE_LABEL[c.voicePreference], PURPOSE_LABEL[c.playPurpose]];
}

/**
 * 게임 프로필 `tiers` 의 값은 gameconfig 티어 이름이다(`matching/seed/gameconfig.redis` — 사본 `domain/gameCatalog.ts` `tierNames`). 형식은 `GOLD_4` 이고
 * 단이 없는 티어(`MASTER` · `RADIANT` · `UNRANKED` …)는 이름 그대로다. LoL · PUBG 는 게임사 API 가 채우고 VALORANT 는 자기신고다(2026-09-29 — 사다리마다 하나씩).
 *
 * 여기 없는 티어가 와도 화면은 깨지지 않아야 한다 — seed 에 티어가 더해질 수 있다. 모르는 값은 받은 그대로 보여준다.
 */
const TIER_LABEL: Record<string, string> = {
  UNRANKED: '언랭크',
  IRON: '아이언',
  BRONZE: '브론즈',
  SILVER: '실버',
  GOLD: '골드',
  PLATINUM: '플래티넘',
  EMERALD: '에메랄드',
  DIAMOND: '다이아몬드',
  MASTER: '마스터',
  GRANDMASTER: '그랜드마스터',
  CHALLENGER: '챌린저',
  ASCENDANT: '초월자',
  IMMORTAL: '불멸',
  RADIANT: '레디언트',
  CRYSTAL: '크리스탈',
  SURVIVOR: '서바이버',
};

/** `GOLD_4` → `골드 4`. `null`(언랭 · 모름 · 자기신고 안 함)이면 `null`. */
export function rankLabel(tier: string | null | undefined): string | null {
  if (!tier) return null;
  const [name, division] = tier.split('_');
  const label = TIER_LABEL[name] ?? name;
  return division ? `${label} ${division}` : label;
}
