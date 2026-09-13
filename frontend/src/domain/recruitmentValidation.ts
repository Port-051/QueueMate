import type { BoardSearch } from '../api/recruitment';
import { tiers } from './recruitment';
import { isOnSlotBoundary } from './time';

type RecruitmentInput = Pick<BoardSearch, 'type' | 'condition' | 'preferences' | 'availableFrom' | 'availableTo'>;

/** 검색과 작성에서 동일한 입력 오류를 안내한다. 새 예약을 만들 때만 현재 시각을 전달한다. */
export function recruitmentInputError(value: RecruitmentInput, now?: number): string {
  const ranks = tiers(value.condition.game);
  const { minTier, maxTier } = value.preferences;
  if (minTier && maxTier && ranks.indexOf(minTier) > ranks.indexOf(maxTier)) return '최소 티어가 최대 티어보다 높습니다.';
  if (value.type !== 'RESERVATION') return '';
  if (!value.availableFrom || !value.availableTo) return '예약 시간을 선택해 주세요.';
  if (!isOnSlotBoundary(value.availableFrom) || !isOnSlotBoundary(value.availableTo)) return '예약 시간은 30분 단위로 선택해 주세요.';
  if (Date.parse(value.availableTo) <= Date.parse(value.availableFrom)) return '종료 시간이 시작 시간보다 늦어야 합니다.';
  if (now !== undefined && Date.parse(value.availableFrom) <= now) return '시작 시각은 현재 이후여야 합니다.';
  return '';
}
