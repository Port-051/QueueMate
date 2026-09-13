import type { BoardPreferences, BoardRow, BoardSearch, BoardWrite } from '../api/recruitment';
import type { GameKey } from '../api/types';
import { readPreferences } from '../state/preferences';
import { defaultCondition } from './gameConfig';
export const anyPreferences = (): BoardPreferences => ({ ownTier: null, minTier: null, maxTier: null, desiredKeys: [], purposeRequired: false });
export const TIER_LABELS: Record<string, string> = { IRON: '아이언', BRONZE: '브론즈', SILVER: '실버', GOLD: '골드', PLATINUM: '플래티넘', EMERALD: '에메랄드', DIAMOND: '다이아몬드', MASTER: '마스터', GRANDMASTER: '그랜드마스터', CHALLENGER: '챌린저', ASCENDANT: '초월자', IMMORTAL: '불멸', RADIANT: '레디언트' };
export const tiers = (game: GameKey) => game === 'LOL'
  ? ['IRON', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'EMERALD', 'DIAMOND', 'MASTER', 'GRANDMASTER', 'CHALLENGER']
  : game === 'VALORANT' ? ['IRON', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'ASCENDANT', 'IMMORTAL', 'RADIANT']
  : ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER'];
export const BOARD_STATUS: Record<BoardRow['status'], string> = { OPEN: '모집 중', PAUSED: '잠시 멈춤', STALE: '활동 확인 필요', REQUESTED: '신청 응답 대기', JOINED: '팀원 모집 중', PROPOSED: '전원 수락 대기', MATCHED: '매칭 확정', CLOSED: '모집 종료' };
export function reservationWindow() {
  const start = Math.ceil((Date.now() + 30 * 60_000) / (30 * 60_000)) * (30 * 60_000);
  return { availableFrom: new Date(start).toISOString(), availableTo: new Date(start + 60 * 60_000).toISOString(), playAmount: 'ONE_GAME' as const };
}
export const initialSearch = (game: GameKey = 'LOL'): BoardSearch => ({ type: 'REALTIME', condition: { ...defaultCondition(game), voicePreference: readPreferences().defaultVoice, playPurpose: readPreferences().defaultPurpose }, preferences: anyPreferences(), availableFrom: null, availableTo: null, playAmount: null, sort: 'RECENT', page: 0, pageSize: 10 });
export const writeFrom = (value: BoardWrite): BoardWrite => ({ type: value.type, condition: value.condition, preferences: value.preferences, description: value.description, autoMatch: value.autoMatch, availableFrom: value.availableFrom, availableTo: value.availableTo, playAmount: value.playAmount });
export const elapsedMinutes = (at: string) => Math.max(0, Math.floor((Date.now() - new Date(at).getTime()) / 60_000));
export function relativeBoardTime(at: string, now = Date.now()) {
  const minutes = Math.max(0, Math.floor((now - Date.parse(at)) / 60_000));
  if (!Number.isFinite(minutes)) return '시간 미상';
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}시간 전`;
  return `${Math.floor(minutes / 1440)}일 전`;
}
export const confirmedLabel = (row: BoardRow, now = Date.now()) => `${relativeBoardTime(row.confirmedAt, now)} 활동`;
export const timeLabel = (iso: string) => new Date(iso).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
export const localInput = (iso: string | null) => iso ? new Date(new Date(iso).getTime() - new Date(iso).getTimezoneOffset() * 60_000).toISOString().slice(0, 16) : '';
