import type {
  Acceptance, GameKey, MatchCondition, PartyStatus, PlayAmount, PlayPurpose,
  ReportReason, ReservationStatus, VoicePreference,
} from '../api/types';
import { gameConfig, modeConfig } from './gameConfig';

export const gameLabel = (g: GameKey) => gameConfig(g).shortName;
export const gameFullLabel = (g: GameKey) => gameConfig(g).name;

export function modeLabel(game: GameKey, modeKey: string): string {
  return modeConfig(game, modeKey)?.label ?? modeKey;
}

export function keyConditionLabel(condition: MatchCondition): string {
  const cfg = gameConfig(condition.game);
  return cfg.keyCondition.options.find((o) => o.value === condition.keyCondition.value)?.label ?? condition.keyCondition.value;
}

export const keyConditionTitle = (game: GameKey) => gameConfig(game).keyCondition.label;

export const VOICE_LABEL: Record<VoicePreference, string> = {
  REQUIRED: '음성 사용',
  OPTIONAL: '음성 선택',
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
  OPEN: '모집 완료',
  READY: '준비 완료',
  PLAYING: '게임 중',
  CLOSED: '종료됨',
};

export const ACCEPTANCE_LABEL: Record<Acceptance, string> = {
  PENDING: '응답 대기',
  ACCEPTED: '수락',
  DECLINED: '거절',
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
  return [modeLabel(c.game, c.modeKey), keyConditionLabel(c), VOICE_LABEL[c.voicePreference], PURPOSE_LABEL[c.playPurpose]];
}

/**
 * `rankCode`는 서버가 Riot에서 읽어 채우는 파생 값이다 (contracts GameAccountView).
 * 형식은 `GOLD_2`이고, 마스터 위로는 단계가 없어 티어 이름만 온다.
 *
 * 여기 없는 티어가 와도 화면은 깨지지 않아야 한다. Riot이 티어를 추가한 전례가 있다
 * (2023년 EMERALD). 모르는 값은 받은 그대로 보여준다.
 */
const TIER_LABEL: Record<string, string> = {
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
};

export function rankLabel(rankCode: string | null): string | null {
  if (!rankCode) return null;
  const [tier, division] = rankCode.split('_');
  const label = TIER_LABEL[tier] ?? tier;
  return division ? `${label} ${division}` : label;
}
