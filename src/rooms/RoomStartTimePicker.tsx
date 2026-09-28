import { useState } from 'react';
import { SlidingSelector } from '../components/SlidingSelector';
import { RoomDatePicker } from './RoomDatePicker';
import { RoomHourPicker } from './RoomHourPicker';
import { ceilRoomHour, localRoomDateTime } from './schedule';

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
      <RoomHourPicker date={date} value={hour} onChange={next => change(`${date}T${next}:00`)} />
    </div> : null}
  </fieldset>;
}
