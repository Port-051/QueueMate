import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import { IconCalendar } from '../components/icons';
import { localRoomDateTime } from './schedule';
import './room-date-picker.css';

const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
const parseDay = (day: string) => new Date(`${day}T12:00`);
const dayKey = (date: Date) => localRoomDateTime(date.getTime()).slice(0, 10);
const dayLabel = (date: Date) => `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 ${weekdays[date.getDay()]}요일`;

function monthDay(date: Date, offset: number): Date {
  const last = new Date(date.getFullYear(), date.getMonth() + offset + 1, 0).getDate();
  return new Date(date.getFullYear(), date.getMonth() + offset, Math.min(date.getDate(), last), 12);
}

export function RoomDatePicker({ value, min, onChange }: { value: string; min: string; onChange: (value: string) => void }) {
  const [open, setOpen] = useState(false);
  const [focused, setFocused] = useState(value);
  const [position, setPosition] = useState<CSSProperties>({});
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const id = useId();
  const selected = parseDay(value);
  const current = parseDay(focused);
  const today = dayKey(new Date());
  const first = new Date(current.getFullYear(), current.getMonth(), 1, 12);
  const close = (restore = false) => { setOpen(false); if (restore) trigger.current?.focus({ preventScroll: true }); };
  const moveMonth = (offset: number) => setFocused(dayKey(monthDay(current, offset)) < min ? min : dayKey(monthDay(current, offset)));

  useLayoutEffect(() => {
    if (!open || !panel.current || !trigger.current) return;
    const rect = trigger.current.getBoundingClientRect();
    const width = Math.min(320, window.innerWidth - 24);
    const below = window.innerHeight - rect.bottom - 20;
    const above = rect.top - 20;
    const placeBelow = below >= panel.current.scrollHeight || below >= above;
    setPosition({ width, left: Math.max(12, Math.min(rect.left + (rect.width - width) / 2, window.innerWidth - width - 12)),
      maxHeight: Math.max(120, placeBelow ? below : above),
      ...(placeBelow ? { top: rect.bottom + 8 } : { bottom: window.innerHeight - rect.top + 8 }) });
    panel.current.querySelector<HTMLButtonElement>(`[data-date="${focused}"]`)?.focus({ preventScroll: true });
  }, [open, focused]);

  useEffect(() => {
    if (!open) return;
    const initial = trigger.current?.getBoundingClientRect();
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !panel.current?.contains(event.target) && !trigger.current?.contains(event.target)) close();
    };
    const moved = (event: Event) => {
      if (event.target instanceof Node && panel.current?.contains(event.target)) return;
      const rect = trigger.current?.getBoundingClientRect();
      if (event.type === 'resize' || !rect || !initial || Math.abs(rect.top - initial.top) > 1 || Math.abs(rect.left - initial.left) > 1) close();
    };
    document.addEventListener('pointerdown', outside);
    window.addEventListener('resize', moved);
    window.addEventListener('scroll', moved, true);
    return () => { document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', moved); window.removeEventListener('scroll', moved, true); };
  }, [open]);

  return <>
    <button ref={trigger} type="button" className="room-date-trigger" aria-label="시작 날짜" aria-haspopup="dialog" aria-expanded={open} aria-controls={id}
      onClick={() => { if (open) close(); else { setFocused(value < min ? min : value); setOpen(true); } }}>
      <span aria-hidden="true"><IconCalendar size={18} /></span>
      <span>{selected.getMonth() + 1}월 {selected.getDate()}일 ({weekdays[selected.getDay()]})</span>
      <svg className="room-date-chevron" width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><path d="m4 6 4 4 4-4" /></svg>
    </button>
    {open ? createPortal(<div ref={panel} id={id} className="room-calendar" role="dialog" aria-modal="true" aria-label="시작 날짜 선택" style={position}
      onKeyDown={event => {
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(true); }
        if (event.key === 'Tab') {
          const buttons = Array.from(panel.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled):not([tabindex="-1"])') ?? []);
          const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
          if (event.shiftKey && index === 0) { event.preventDefault(); buttons.at(-1)?.focus(); }
          else if (!event.shiftKey && index === buttons.length - 1) { event.preventDefault(); buttons[0]?.focus(); }
        }
      }}>
      <header className="room-calendar-header">
        <strong aria-live="polite">{current.getFullYear()}년 {current.getMonth() + 1}월</strong>
        <div>
          <button type="button" aria-label="이전 달" disabled={dayKey(new Date(current.getFullYear(), current.getMonth(), 0, 12)) < min} onClick={() => moveMonth(-1)}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m14 6-6 6 6 6" /></svg></button>
          <button type="button" aria-label="다음 달" onClick={() => moveMonth(1)}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m10 6 6 6-6 6" /></svg></button>
        </div>
      </header>
      <div className="room-calendar-weekdays" aria-hidden="true">{weekdays.map(day => <span key={day}>{day}</span>)}</div>
      <div className="room-calendar-days" role="group" aria-label="날짜">
        {Array.from({ length: 42 }, (_, index) => {
          const date = new Date(first.getFullYear(), first.getMonth(), index - first.getDay() + 1, 12);
          const key = dayKey(date);
          return <button type="button" key={key} data-date={key} className={date.getMonth() !== current.getMonth() ? 'is-outside-month' : ''}
            aria-label={dayLabel(date)} aria-pressed={key === value} aria-current={key === today ? 'date' : undefined} disabled={key < min} tabIndex={key === focused ? 0 : -1}
            onClick={() => { onChange(key); close(true); }}
            onKeyDown={event => {
              const offset = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7, Home: -date.getDay(), End: 6 - date.getDay() } as Record<string, number>)[event.key];
              if (offset !== undefined) {
                event.preventDefault(); const next = new Date(date); next.setDate(next.getDate() + offset);
                setFocused(dayKey(next) < min ? min : dayKey(next));
              } else if (event.key === 'PageUp' || event.key === 'PageDown') { event.preventDefault(); moveMonth(event.key === 'PageUp' ? -1 : 1); }
            }}>{date.getDate()}</button>;
        })}
      </div>
    </div>, document.body) : null}
  </>;
}
