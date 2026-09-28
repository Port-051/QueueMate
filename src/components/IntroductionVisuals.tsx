import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import type { GameKey } from '../api/types';
import { championName, championPortrait } from '../domain/champions';
import '../styles/introduction-visuals.css';

type StatKind = 'winRate' | 'kda';
const STAT_THRESHOLDS: Record<StatKind, number[]> = { winRate: [45, 49, 53, 60], kda: [1, 2, 3, 4] };
const STAT_TONES = ['red', 'orange', 'yellow', 'green', 'blue'] as const;

/** 전체 매칭에 같은 고정 구간을 사용한다. 매칭 글 목록의 구성에 따라 색이 달라지지 않는다. */
export function PerformanceValue({ kind, value }: { kind: StatKind; value: number | null }) {
  const valid = value !== null && Number.isFinite(value) && value >= 0 && (kind !== 'winRate' || value <= 100);
  const band = valid ? STAT_THRESHOLDS[kind].filter(threshold => value >= threshold).length : undefined;
  return <strong className="performance-value" data-tone={band === undefined ? undefined : STAT_TONES[band]}>
    {valid ? kind === 'winRate' ? `${value}%` : value.toFixed(2) : '미입력'}
  </strong>;
}

function ChampionPortrait({ name }: { name: string }) {
  const src = championPortrait(name);
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  return src && failedSrc !== src
    ? <img className="preferred-champion-portrait" src={src} alt={`${name} 초상화`} width="28" height="28" loading="lazy" decoding="async" onError={() => setFailedSrc(src)} />
    : <span className="preferred-champion-portrait is-fallback" role="img" aria-label={`${name} 초상화`}>{Array.from(name.trim())[0] ?? '?'}</span>;
}

function PreferredChampion({ name }: { name: string }) {
  const [tooltip, setTooltip] = useState<{ left: number; top: number; below: boolean } | null>(null);
  useEffect(() => {
    if (!tooltip) return;
    const dismiss = () => setTooltip(null);
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') dismiss(); };
    window.addEventListener('scroll', dismiss, true);
    window.addEventListener('resize', dismiss);
    window.addEventListener('keydown', escape);
    return () => {
      window.removeEventListener('scroll', dismiss, true);
      window.removeEventListener('resize', dismiss);
      window.removeEventListener('keydown', escape);
    };
  }, [tooltip]);

  return <span className="preferred-champion has-portrait" onMouseEnter={event => {
    if (!window.matchMedia('(hover: hover)').matches) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const below = rect.top < 44;
    setTooltip({ left: Math.min(window.innerWidth - 118, Math.max(118, rect.left + rect.width / 2)), top: below ? rect.bottom + 7 : rect.top - 7, below });
  }} onMouseLeave={() => setTooltip(null)}>
    <ChampionPortrait name={name} />
    <span className="preferred-champion-name">{name}</span>
    {tooltip ? createPortal(<span className={`preferred-champion-tooltip${tooltip.below ? ' is-below' : ''}`} role="tooltip" style={{ left: tooltip.left, top: tooltip.top }}>{name}</span>, document.body) : null}
  </span>;
}

export function PreferredChampions({ game, names }: { game: GameKey; names: string[] }) {
  return <span className="preferred-champions">
    {names.map((name, index) => {
      const label = game === 'LOL' ? championName(name) ?? name : name;
      return game === 'LOL' ? <PreferredChampion key={`${name}-${index}`} name={label} /> : <span className="preferred-champion" key={`${name}-${index}`}>
        <span className="preferred-champion-name">{label}</span>
      </span>;
    })}
  </span>;
}
