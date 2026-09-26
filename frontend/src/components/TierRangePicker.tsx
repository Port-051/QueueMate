import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import type { GameKey } from '../api/types';
import { ALL_TIERS, normalizeTierRange, TIER_ORDER, tierInRange, type TierRange } from '../domain/tierRange';
import { TIER_LABELS } from '../domain/recruitment';
import { tierColor } from '../domain/rankAssets';
import { FilterTierIcon } from './FilterSymbols';
import '../styles/tier-range.css';

export function TierRangeLabel({ game, value = ALL_TIERS, stacked = false }: { game: GameKey; value?: TierRange; stacked?: boolean }) {
  const { minTier, maxTier } = normalizeTierRange(game, value);
  if (stacked) {
    const endpoint = (tier: string | null, suffix = '') => <span className="room-rank"><FilterTierIcon game={game} tier={tier} size={26} /><strong style={{ color: tierColor(tier) }}>{tier ? `${TIER_LABELS[tier]}${suffix}` : '모든 티어'}</strong></span>;
    return <span className="tier-range-label room-tier-range">
      {minTier && maxTier && minTier !== maxTier ? <>{endpoint(minTier)}<span className="room-tier-separator">~</span>{endpoint(maxTier)}</>
        : endpoint(minTier ?? maxTier, minTier && !maxTier ? ' 이상' : maxTier && !minTier ? ' 이하' : '')}
    </span>;
  }
  const tier = (key: string) => <span className="tier-range-endpoint" style={{ color: tierColor(key) }}><FilterTierIcon game={game} tier={key} size={22} /><span>{TIER_LABELS[key]}</span></span>;
  return <span className="tier-range-label">
    {!minTier && !maxTier ? <><FilterTierIcon game={game} tier={null} size={22} /><span>모든 티어</span></>
      : minTier && maxTier ? <>{tier(minTier)}{minTier !== maxTier ? <><span>~</span>{tier(maxTier)}</> : null}</>
        : minTier ? <>{tier(minTier)}<span>이상</span></> : <>{tier(maxTier!)}<span>이하</span></>}
  </span>;
}

/** A two-click range, with explicit Apply so dismissing never changes a filter. */
export function TierRangePicker({ game, value = ALL_TIERS, onChange, label }: {
  game: GameKey; value?: TierRange; onChange: (range: TierRange) => void; label: string;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<TierRange>(ALL_TIERS);
  const [anchor, setAnchor] = useState<string | null>(null);
  const [hover, setHover] = useState<string | null>(null);
  const [position, setPosition] = useState<CSSProperties>({});
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const id = useId();
  const order = TIER_ORDER[game];
  const preview = anchor && hover ? normalizeTierRange(game, { minTier: anchor, maxTier: hover }) : draft;
  const close = (focus = false) => { setOpen(false); if (focus) trigger.current?.focus({ preventScroll: true }); };
  const context = `${game}:${value.minTier}:${value.maxTier}`;

  useEffect(() => { setOpen(false); }, [context]);
  useLayoutEffect(() => {
    if (!open || !trigger.current || !panel.current) return;
    const rect = trigger.current.getBoundingClientRect();
    const width = Math.min(860, window.innerWidth - 24);
    const below = window.innerHeight - rect.bottom - 16;
    const above = rect.top - 16;
    const placeBelow = below >= panel.current.scrollHeight || below >= above;
    const centeredLeft = rect.left + (rect.width - width) / 2;
    setPosition({ position: 'fixed', width, left: Math.max(12, Math.min(centeredLeft, window.innerWidth - width - 12)),
      maxHeight: Math.max(120, placeBelow ? below : above),
      ...(placeBelow ? { top: rect.bottom + 8 } : { bottom: window.innerHeight - rect.top + 8 }) });
    (panel.current.querySelector<HTMLButtonElement>('[data-endpoint="true"]') ?? panel.current.querySelector<HTMLButtonElement>('.tier-range-option'))?.focus({ preventScroll: true });
  }, [open]);
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
  const choose = (tier: string) => {
    if (anchor) { setDraft(normalizeTierRange(game, { minTier: anchor, maxTier: tier })); setAnchor(null); }
    else { setDraft({ minTier: tier, maxTier: tier }); setAnchor(tier); }
    setHover(null);
  };

  return <div className="tier-range-picker">
    <button ref={trigger} className="tier-range-trigger" type="button" aria-label={label} aria-haspopup="dialog" aria-expanded={open} aria-controls={id}
      onClick={() => { if (open) close(); else { setDraft(normalizeTierRange(game, value)); setAnchor(null); setHover(null); setOpen(true); } }}>
      <TierRangeLabel game={game} value={value} /><svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><path d="m4 6 4 4 4-4" /></svg>
    </button>
    {open ? createPortal(<div ref={panel} id={id} className="tier-range-popover" role="dialog" aria-label={label} style={position}
      onKeyDown={event => {
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(true); }
        if (event.key === 'Tab') {
          const buttons = Array.from(panel.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? []);
          const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
          if (event.shiftKey && index === 0) { event.preventDefault(); buttons.at(-1)?.focus(); }
          else if (!event.shiftKey && index === buttons.length - 1) { event.preventDefault(); buttons[0]?.focus(); }
        }
      }}>
      <header><strong>{label}</strong><button type="button" onClick={() => { setDraft(ALL_TIERS); setAnchor(null); setHover(null); }}>전체</button></header>
      <div className="tier-range-track" role="group" aria-label="티어 범위 선택" onMouseLeave={() => setHover(null)}>
        {order.map((tier, index) => {
          const endpoint = tier === preview.minTier || tier === preview.maxTier;
          const selected = Boolean(preview.minTier || preview.maxTier) && tierInRange(game, tier, preview);
          return <button type="button" className={`tier-range-option${selected ? ' is-in-range' : ''}${endpoint ? ' is-endpoint' : ''}`} key={tier}
            data-endpoint={endpoint} aria-label={TIER_LABELS[tier]} aria-pressed={selected} onClick={() => choose(tier)} onMouseEnter={() => setHover(tier)}
            onKeyDown={event => {
              if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
              event.preventDefault();
              const target = event.key === 'Home' ? 0 : event.key === 'End' ? order.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + order.length) % order.length;
              const button = panel.current?.querySelectorAll<HTMLButtonElement>('.tier-range-option')[target];
              button?.focus({ preventScroll: true }); button?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
              setHover(order[target]);
            }}>
            <FilterTierIcon game={game} tier={tier} size={38} /><span style={{ color: tierColor(tier) }}>{TIER_LABELS[tier]}</span>
          </button>;
        })}
      </div>
      <p className="tier-range-hint">하나를 고르면 해당 티어만, 두 개를 고르면 그 사이 범위를 선택해요.</p>
      <footer><TierRangeLabel game={game} value={draft} /><div><button type="button" onClick={() => close(true)}>취소</button><button className="tier-range-apply" type="button" onClick={() => { onChange(draft); close(true); }}>적용</button></div></footer>
    </div>, document.body) : null}
  </div>;
}
