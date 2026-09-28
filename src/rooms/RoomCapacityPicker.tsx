import type { GameKey } from '../api/types';
import { SlidingSelector } from '../components/SlidingSelector';
import { roomCapacities } from './summary';

export function RoomCapacityPicker({ game, modeKey, value, onChange }: {
  game: GameKey; modeKey: string; value: number; onChange: (capacity: number) => void;
}) {
  const options = roomCapacities(game, modeKey);
  if (options.length < 2) return null;
  return <fieldset className="introduction-choice"><legend>인원</legend>
    <SlidingSelector className="room-capacity-options room-type-tabs" aria-label="모집 인원">
      {options.map(count => <button key={count} type="button" aria-pressed={value === count} onClick={() => onChange(count)}>{count}명</button>)}
    </SlidingSelector>
  </fieldset>;
}
