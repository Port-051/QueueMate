import { useId } from 'react';
import type { GameKey } from '../api/types';
import { keyConditionOptions } from '../domain/gameConfig';
import { FilterRoleIcon } from './FilterSymbols';
import { SlidingSelector } from './SlidingSelector';

/** `only` 를 주면 그 이름만 그린다(게임 순서 그대로) — 참여 창의 "남은 포지션"(2026-10-01). 없으면 그 게임의 선택지 전부다. */
export function SingleRolePicker({ game, value, onChange, label, only }: {
  game: GameKey; value: string | null; onChange: (role: string) => void; label: string; only?: readonly string[];
}) {
  const name = useId();
  const options = keyConditionOptions(game).filter(role => !only || only.includes(role.value));
  const ordered = [...options.filter(role => role.value === 'ANY'), ...options.filter(role => role.value !== 'ANY')];
  return <SlidingSelector className="single-role-picker" role="radiogroup" aria-label={label}>
    {ordered.map(role => <label className="single-role-option" key={role.value}>
      <input type="radio" name={name} value={role.value} aria-label={role.label} checked={value === role.value} onChange={() => onChange(role.value)} />
      <FilterRoleIcon game={game} value={role.value} size={23} /><span>{role.label}</span>
    </label>)}
  </SlidingSelector>;
}
