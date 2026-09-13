import type { BoardRow } from '../api/recruitment';
import { formatElapsed, formatRange } from '../domain/time';

export function RecruitmentClock({ row, now }: { row: BoardRow; now: number }) {
  const start = row.type === 'RESERVATION' && row.availableFrom ? Date.parse(row.availableFrom) : null;
  const beforeStart = start !== null && start > now;
  const seconds = start !== null ? (beforeStart ? Math.ceil((start - now) / 1000) : (now - start) / 1000) : (now - Date.parse(row.createdAt)) / 1000;
  const label = start !== null ? beforeStart ? '예약 시작까지' : '예약 시작 후' : '모집 시작 후';
  return <div className="recruitment-clock"><strong role="timer" aria-label={label} aria-live="off">{formatElapsed(seconds)}</strong>{row.type === 'RESERVATION' && row.availableFrom && row.availableTo ? <small>{formatRange(row.availableFrom, row.availableTo)}</small> : null}</div>;
}
