import type { GameKey } from '../api/types';
import { IconParty } from '../components/icons';
import { SlidingSelector } from '../components/SlidingSelector';
import { roomCapacities } from './summary';

export function RoomCapacityPicker({ game, modeKey, value, onChange }: {
  game: GameKey; modeKey: string; value: number; onChange: (capacity: number) => void;
}) {
  const options = roomCapacities(game, modeKey);
  if (options.length < 2) return null;
  return <fieldset className="introduction-choice"><legend>인원</legend>
    <SlidingSelector className="room-capacity-options room-mode-options" aria-label="모집 인원">
      {options.map(count => <button key={count} type="button" className="filter-mode" aria-pressed={value === count} onClick={() => onChange(count)}>
        <IconParty size={22} /><span>{count}명</span>
      </button>)}
    </SlidingSelector>
  </fieldset>;
}
