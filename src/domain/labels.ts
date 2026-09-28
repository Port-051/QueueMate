import type {
  GameKey, MatchCondition, PartyStatus, PlayAmount, PlayPurpose,
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
  NORMAL: '일반 플레이',
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

export const PARTY_STATUS_LABEL: Record<PartyStatus, string> = {
  OPEN: '매칭 완료',
  READY: '준비 완료',
  PLAYING: '전원 준비 확인됨',
  CLOSED: '종료됨',
};

export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'ABUSIVE_LANGUAGE', label: '욕설/비속어' },
  { value: 'HARASSMENT', label: '괴롭힘' },
  { value: 'CHEATING', label: '핵/불법 프로그램' },
  { value: 'TROLLING_OR_AFK', label: '트롤링/잠수' },
  { value: 'INAPPROPRIATE_PROFILE', label: '부적절한 프로필' },
  { value: 'OTHER', label: '기타' },
];

/** 조건 한 줄 요약. 카드/리스트에서 재사용한다. */
export function conditionSummary(c: MatchCondition): string[] {
  return [modeLabel(c.game, c.modeKey), ...(usesKeyCondition(c.game, c.modeKey) ? [keyConditionLabel(c)] : []), VOICE_LABEL[c.voicePreference], PURPOSE_LABEL[c.playPurpose]];
}

/**
 * 게임 프로필의 `tier` 는 gameconfig 사다리의 이름이다(`matching/seed/gameconfig.redis` — 사본 `domain/gameCatalog.ts`). 형식은 `GOLD_4` 이고
 * 단이 없는 티어(`MASTER` · `RADIANT` · `UNRANKED` …)는 이름 그대로다. LoL 은 Riot 이 채우고 VALORANT · PUBG 는 자기신고다.
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

/** `GOLD_4` → `골드 4`. `null`(LoL 언랭 · 자기신고 안 함)이면 `null`. */
export function rankLabel(tier: string | null | undefined): string | null {
  if (!tier) return null;
  const [name, division] = tier.split('_');
  const label = TIER_LABEL[name] ?? name;
  return division ? `${label} ${division}` : label;
}

/** 게임 프로필의 `mainPosition`(LOL 포지션 · VALORANT 역할군) 한글 라벨. PUBG 는 `null`. */
export function positionLabel(game: GameKey, position: string | null | undefined): string | null {
  if (!position) return null;
  return gameConfig(game).keyCondition.options.find((o) => o.value === position)?.label ?? position;
}
