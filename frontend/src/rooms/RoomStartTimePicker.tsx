import { useState } from 'react';
import { SlidingSelector } from '../components/SlidingSelector';
import { IconClock } from '../components/icons';
import { RoomDatePicker } from './RoomDatePicker';
import { ceilRoomHour, localRoomDateTime, roomHourLabel } from './schedule';

export function RoomStartTimePicker({ value, onChange }: { value: string | null; onChange: (value: string | null) => void }) {
  const [lastScheduled, setLastScheduled] = useState(() => value ?? localRoomDateTime(ceilRoomHour(Date.now() + 1)));
  const scheduled = value !== null;
  const date = (value ?? lastScheduled).slice(0, 10);
  const hour = (value ?? lastScheduled).slice(11, 13);
  const change = (next: string) => { setLastScheduled(next); onChange(next); };
  return <fieldset className="introduction-choice room-start-picker"><legend>시작 시간</legend>
    <SlidingSelector className="room-start-options room-type-tabs" aria-label="시작 시간 선택">
      <button type="button" aria-pressed={!scheduled} onClick={() => onChange(null)}>지금</button>
      <button type="button" aria-pressed={scheduled} onClick={() => change(Date.parse(lastScheduled) > Date.now() ? lastScheduled : localRoomDateTime(ceilRoomHour(Date.now() + 1)))}>시간 선택</button>
    </SlidingSelector>
    {scheduled ? <div className="room-start-controls">
      <RoomDatePicker value={date} min={localRoomDateTime(ceilRoomHour(Date.now() + 1)).slice(0, 10)} onChange={next => {
        const selected = `${next}T${hour}:00`;
        change(Date.parse(selected) > Date.now() ? selected : localRoomDateTime(ceilRoomHour(Date.now() + 1)));
      }} />
      <div className="room-hour-control"><span aria-hidden="true"><IconClock size={18}/></span><select aria-label="시작 시각" value={hour} onChange={event => change(`${date}T${event.target.value}:00`)}>
        {Array.from({ length: 24 }, (_, index) => {
          const key = String(index).padStart(2, '0');
          return <option key={key} value={key} disabled={Date.parse(`${date}T${key}:00`) <= Date.now()}>{roomHourLabel(index)}</option>;
        })}
      </select><svg className="room-date-chevron" width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><path d="m4 6 4 4 4-4" /></svg></div>
    </div> : null}
  </fieldset>;
}
