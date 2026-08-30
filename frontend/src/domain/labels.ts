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
