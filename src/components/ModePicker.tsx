import type { ReactNode } from 'react';
import type { GameKey } from '../api/types';
import { PERSPECTIVE_LABEL } from '../domain/gameCatalog';
import { groupPerspectives, groupSizes, modeChoice, modeGroups, pickMode } from '../domain/modeChoice';
import { FilterModeIcon } from './FilterSymbols';
import { SlidingSelector } from './SlidingSelector';

/**
 * 게임 모드 고르기 — 첫 줄 **묶음**(LoL 넷 · VALORANT 둘 · PUBG 둘), 그 아래 **인원**(그 묶음에 있는 것만 · 하나뿐이면 고정), PUBG 는 그 아래 **시점**(3인칭 · 1인칭).
 * 2026-09-29 소유자 지시 — 모드 12개가 한 줄에 서서 글자가 겹쳤다. 규칙은 `domain/modeChoice.ts`, 결과는 **모드 키 하나**로 올린다(부르는 쪽 · 서버 본문은 그대로).
 * 묶음을 바꾸면 인원 · 시점은 그 묶음에 있으면 그대로, 없으면 가장 작은 인원 · 3인칭이다.
 * `compact` 는 방 카드 보드 오른쪽 레일의 모양(미끄러지는 선택 표시)이다.
 */
export function ModePicker({ game, value, onChange, disabled = false, compact = false }: {
  game: GameKey; value: string; onChange: (modeKey: string) => void; disabled?: boolean; compact?: boolean;
}) {
  const groups = modeGroups(game);
  // 모르는 값(legacy 의 `ANY` 등)이면 첫 묶음의 기본 모드를 보여 준다 — 누르면 그 값으로 바뀐다.
  const current = modeChoice(game, value) ?? modeChoice(game, pickMode(game, groups[0].key))!;
  const sizes = groupSizes(game, current.group);
  const perspectives = groupPerspectives(game, current.group);
  const choose = (group: string, size: number, perspective = current.perspective) => {
    const next = pickMode(game, group, size, perspective);
    if (next !== value) onChange(next);
  };
  const fixedSize = sizes.length < 2;
  return <div className={`mode-picker${compact ? ' is-compact' : ''}`}>
    <SlidingSelector enabled={compact} className={`intro-mode-options mode-group-options${compact ? ' room-mode-options' : ''}`} aria-label="게임 모드">
      {groups.map(group => <button type="button" key={group.key} className="filter-mode" aria-pressed={current.group === group.key} disabled={disabled}
        onClick={() => choose(group.key, current.size)}><FilterModeIcon mode={group.key} size={compact ? 22 : 16} /><span>{group.label}</span></button>)}
    </SlidingSelector>
    <ModeSubRow label="인원" compact={compact}>
      {sizes.map(size => <button type="button" key={size} aria-pressed={current.size === size} disabled={disabled || fixedSize}
        title={fixedSize ? `이 모드는 ${size}인만 있어요` : undefined} onClick={() => choose(current.group, size)}>{size}인</button>)}
    </ModeSubRow>
    {perspectives.length ? <ModeSubRow label="시점" compact={compact}>
      {perspectives.map(view => <button type="button" key={view} aria-pressed={current.perspective === view} disabled={disabled}
        onClick={() => choose(current.group, current.size, view)}>{PERSPECTIVE_LABEL[view]}</button>)}
    </ModeSubRow> : null}
  </div>;
}

function ModeSubRow({ label, compact, children }: { label: string; compact: boolean; children: ReactNode }) {
  return <div className="mode-sub-row">
    <span className="mode-sub-label" aria-hidden="true">{label}</span>
    <SlidingSelector enabled={compact} className="room-type-tabs room-capacity-options mode-sub-options" aria-label={label}>{children}</SlidingSelector>
  </div>;
}
