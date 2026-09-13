import type { BoardWrite } from '../api/recruitment';
import type { GameKey } from '../api/types';

type ReservationDraft = Pick<BoardWrite, 'availableFrom' | 'availableTo' | 'playAmount'>;
const key = (userId: string, game: GameKey) => `queuemate:reservation-draft:v1:${encodeURIComponent(userId)}:${game}`;

export function readReservationDraft(userId: string, game: GameKey): Partial<ReservationDraft> {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(key(userId, game)) ?? 'null');
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
    const draft = value as ReservationDraft;
    const validTime = (time: unknown) => time === null || (typeof time === 'string' && Number.isFinite(Date.parse(time)));
    if (!validTime(draft.availableFrom) || !validTime(draft.availableTo) || !['ONE_GAME', 'TWO_PLUS', null].includes(draft.playAmount)) return {};
    return { availableFrom: draft.availableFrom, availableTo: draft.availableTo, playAmount: draft.playAmount };
  } catch { return {}; }
}

export function saveReservationDraft(userId: string, game: GameKey, value: ReservationDraft) {
  try { localStorage.setItem(key(userId, game), JSON.stringify(value)); } catch { /* 저장 공간이 없더라도 현재 폼의 입력은 유지한다. */ }
}
