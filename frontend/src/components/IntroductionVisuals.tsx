import { useState } from 'react';
import type { GameKey } from '../api/types';
import { championName, championPortrait } from '../domain/champions';
import '../styles/introduction-visuals.css';

type StatKind = 'winRate' | 'kda';
const STAT_THRESHOLDS: Record<StatKind, number[]> = { winRate: [45, 49, 53, 60], kda: [1, 2, 3, 4] };
const STAT_TONES = ['red', 'orange', 'yellow', 'green', 'blue'] as const;

/** 전체 모집에 같은 고정 구간을 사용한다. 모집 목록의 구성에 따라 색이 달라지지 않는다. */
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
    : <span className="preferred-champion-portrait is-fallback" aria-hidden="true">{Array.from(name.trim())[0] ?? '?'}</span>;
}

export function PreferredChampions({ game, names }: { game: GameKey; names: string[] }) {
  return <span className="preferred-champions">
    {names.map((name, index) => {
      const label = game === 'LOL' ? championName(name) ?? name : name;
      return <span className="preferred-champion" key={`${name}-${index}`} title={label}>
        {game === 'LOL' ? <ChampionPortrait name={label} /> : null}
        <span className="preferred-champion-name">{label}</span>
      </span>;
    })}
  </span>;
}
