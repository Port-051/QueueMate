/** Room times use the browser's local calendar, matching the date input. */
export function ceilRoomHour(timestamp: number): number {
  const date = new Date(timestamp);
  date.setMinutes(0, 0, 0);
  if (date.getTime() < timestamp) date.setHours(date.getHours() + 1);
  return date.getTime();
}

export function localRoomDateTime(timestamp: number): string {
  const date = new Date(timestamp);
  return new Date(timestamp - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

export const roomHourLabel = (hour: number): string => `${hour < 12 ? '오전' : '오후'} ${hour % 12 || 12}시`;

export function roomStartLabel(availableFrom: string | null, now = Date.now()): string {
  if (availableFrom === null) return '지금';
  const date = new Date(availableFrom);
  if (!Number.isFinite(date.getTime())) return '시간 미정';
  const today = new Date(now);
  const day = (value: Date) => Date.UTC(value.getFullYear(), value.getMonth(), value.getDate());
  const distance = (day(date) - day(today)) / 86_400_000;
  const prefix = distance === 0 ? '오늘' : distance === 1 ? '내일'
    : date.getFullYear() !== today.getFullYear() ? `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`
      : date.getMonth() !== today.getMonth() ? `${date.getMonth() + 1}월 ${date.getDate()}일` : `${date.getDate()}일`;
  // Older user reservations retain their actual time; new reservations only accept whole hours.
  return `${prefix} ${roomHourLabel(date.getHours())}${date.getMinutes() ? ` ${date.getMinutes()}분` : ''}`;
}

export function reservationTimeError(value: string | null, now = Date.now()): string | null {
  const timestamp = value ? Date.parse(value) : NaN;
  if (!Number.isFinite(timestamp) || timestamp <= now) return '시작 시간을 현재보다 뒤로 선택해 주세요.';
  const date = new Date(timestamp);
  if (date.getMinutes() || date.getSeconds() || date.getMilliseconds()) return '시작 시간은 1시간 단위로 선택해 주세요.';
  return null;
}
