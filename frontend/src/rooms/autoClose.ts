import type { GameRoom } from './types';

// Product trial values, not timings specified in the mentoring transcript.
export const AUTO_CLOSE_IDLE_MS = 10 * 60_000;
export const AUTO_CLOSE_GRACE_MS = 60_000;
export const nextAutoCloseAt = (room: GameRoom, now: number): number | null =>
  room.status === 'OPEN' && room.type === 'REALTIME' && room.members.length >= 2 && room.members.length < room.capacity
    ? now + AUTO_CLOSE_IDLE_MS + AUTO_CLOSE_GRACE_MS : null;

export function autoClosePhase(room: GameRoom, now: number): 'inactive' | 'waiting' | 'warning' | 'due' {
  if (room.status !== 'OPEN' || room.type !== 'REALTIME' || room.members.length < 2 || room.members.length >= room.capacity
    || !room.autoCloseAt || !Number.isFinite(room.autoCloseAt)) return 'inactive';
  if (now >= room.autoCloseAt) return 'due';
  return now >= room.autoCloseAt - AUTO_CLOSE_GRACE_MS ? 'warning' : 'waiting';
}

export function canAutoClose(room: GameRoom, userId: string, expectedDeadline: number, now: number): boolean {
  return room.ownerId === userId && room.autoCloseAt === expectedDeadline && autoClosePhase(room, now) === 'due';
}
