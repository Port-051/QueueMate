import { useState } from 'react';
import { createPortal } from 'react-dom';
import { IconClock } from '../components/icons';
import { roomHourLabel } from './schedule';
import { useRoomPickerPopover } from './useRoomPickerPopover';
import './room-date-picker.css';

const hours = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'));

export function RoomHourPicker({ date, value, onChange }: { date: string; value: string; onChange: (hour: string) => void }) {
  const [focused, setFocused] = useState<string | null>(value);
  const { open, setOpen, close, position, trigger, panel, id, onKeyDown } = useRoomPickerPopover(`[data-hour="${focused}"]`);
  const available = (hour: string) => Date.parse(`${date}T${hour}:00`) > Date.now();
  const enabled = hours.filter(available);
  return <>
    <button ref={trigger} type="button" className="room-date-trigger room-hour-trigger" aria-label="시작 시각" aria-haspopup="dialog" aria-expanded={open} aria-controls={id}
      onClick={() => { if (open) close(); else { setFocused(available(value) ? value : enabled[0] ?? null); setOpen(true); } }}>
      <span aria-hidden="true"><IconClock size={18}/></span><span>{roomHourLabel(Number(value))}</span>
      <svg className="room-date-chevron" width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><path d="m4 6 4 4 4-4" /></svg>
    </button>
    {open ? createPortal(<div ref={panel} id={id} className="room-calendar room-hour-picker" role="dialog" aria-modal="true" aria-label="시작 시각 선택" tabIndex={-1} style={position} onKeyDown={onKeyDown}>
      <header className="room-calendar-header"><strong>시작 시각</strong><span>{Number(date.slice(5, 7))}월 {Number(date.slice(8, 10))}일</span></header>
      {['오전', '오후'].map((period, index) => <section className="room-hour-section" key={period} aria-label={period}>
        <h4>{period}</h4><div className="room-hour-grid">
          {hours.slice(index * 12, index * 12 + 12).map(hour => <button type="button" key={hour} data-hour={hour} aria-label={roomHourLabel(Number(hour))}
            aria-pressed={value === hour} disabled={!available(hour)} tabIndex={focused === hour ? 0 : -1}
            onClick={() => { if (!available(hour)) { setFocused(null); return; } onChange(hour); close(true); }}
            onKeyDown={event => {
              const offset = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -4, ArrowDown: 4 } as Record<string, number>)[event.key];
              if (event.key === 'Home' || event.key === 'End') {
                event.preventDefault(); setFocused((event.key === 'Home' ? enabled[0] : enabled.at(-1)) ?? null);
              } else if (offset !== undefined) {
                event.preventDefault(); let next = Number(hour) + offset;
                while (next >= 0 && next < 24 && !available(hours[next])) next += Math.sign(offset);
                if (next >= 0 && next < 24) setFocused(hours[next]);
              }
            }}>{Number(hour) % 12 || 12}시</button>)}
        </div>
      </section>)}
      {!enabled.length ? <p className="room-hour-empty" role="status">선택 가능한 시간이 없어요. 날짜를 바꿔 주세요.</p> : null}
    </div>, document.body) : null}
  </>;
}
