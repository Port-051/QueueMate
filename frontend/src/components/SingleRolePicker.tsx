import { useId } from 'react';
import type { GameKey } from '../api/types';
import { keyConditionOptions } from '../domain/gameConfig';
import { FilterRoleIcon } from './FilterSymbols';
import { SlidingSelector } from './SlidingSelector';

export function SingleRolePicker({ game, value, onChange, label }: {
  game: GameKey; value: string | null; onChange: (role: string) => void; label: string;
}) {
  const name = useId();
  const options = keyConditionOptions(game);
  const ordered = [...options.filter(role => role.value === 'ANY'), ...options.filter(role => role.value !== 'ANY')];
  return <SlidingSelector className="single-role-picker" role="radiogroup" aria-label={label}>
    {ordered.map(role => <label className="single-role-option" key={role.value}>
      <input type="radio" name={name} value={role.value} aria-label={role.label} checked={value === role.value} onChange={() => onChange(role.value)} />
      <FilterRoleIcon game={game} value={role.value} size={23} /><span>{role.label}</span>
    </label>)}
  </SlidingSelector>;
}
