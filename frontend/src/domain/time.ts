/** 예약 매칭의 플레이 가능 시간은 30분 경계만 허용한다(docs/02 §7). */
export const SLOT_MINUTES = 30;

export function isOnSlotBoundary(iso: string): boolean {
  const d = new Date(iso);
  return d.getMinutes() % SLOT_MINUTES === 0 && d.getSeconds() === 0 && d.getMilliseconds() === 0;
}

export function floorToSlot(date: Date): Date {
  const d = new Date(date);
  d.setSeconds(0, 0);
  d.setMinutes(Math.floor(d.getMinutes() / SLOT_MINUTES) * SLOT_MINUTES);
  return d;
}

/** '00:00' ~ '23:30' 30분 단위 목록. */
export function slotTimes(): string[] {
  const out: string[] = [];
  for (let m = 0; m < 24 * 60; m += SLOT_MINUTES) {
    out.push(`${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`);
  }
  return out;
}

export function toDateKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** 로컬 날짜(yyyy-mm-dd) + 'HH:mm' → ISO 문자열. */
export function toIso(dateKey: string, hhmm: string): string {
  const [y, mo, d] = dateKey.split('-').map(Number);
  const [h, mi] = hhmm.split(':').map(Number);
  return new Date(y, mo - 1, d, h, mi, 0, 0).toISOString();
}

export function nextDays(count: number, from = new Date()): { key: string; label: string }[] {
  const week = ['일', '월', '화', '수', '목', '금', '토'];
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(from.getFullYear(), from.getMonth(), from.getDate() + i);
    const prefix = i === 0 ? '오늘' : i === 1 ? '내일' : `${d.getMonth() + 1}.${d.getDate()}`;
    return { key: toDateKey(d), label: `${prefix} (${week[d.getDay()]})` };
  });
}

const pad = (n: number) => String(n).padStart(2, '0');

export function formatTime(iso: string): string {
  const d = new Date(iso);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function formatDay(iso: string): string {
  const d = new Date(iso);
  const today = new Date();
  const diff = Math.round((new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
    - new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()) / 86400000);
  if (diff === 0) return '오늘';
  if (diff === 1) return '내일';
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

export function formatRange(from: string, to: string): string {
  return `${formatDay(from)} ${formatTime(from)} ~ ${formatTime(to)}`;
}

export function formatDuration(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  return `${pad(Math.floor(s / 60))}:${pad(s % 60)}`;
}

export function relativeTime(iso: string, now = Date.now()): string {
  const diff = Math.floor((now - new Date(iso).getTime()) / 1000);
  if (diff < 60) return '방금 전';
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`;
  return `${Math.floor(diff / 86400)}일 전`;
}

/** INV-9: 시간이 겹치는 활성 예약을 만들 수 없다. */
export function overlaps(aFrom: string, aTo: string, bFrom: string, bTo: string): boolean {
  return new Date(aFrom).getTime() < new Date(bTo).getTime()
    && new Date(bFrom).getTime() < new Date(aTo).getTime();
}
