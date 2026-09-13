import type { ReactNode } from 'react';
import type { GameKey } from '../api/types';

type SymbolProps = { size?: number };

const ALL_ROLES = <>
  <circle cx="12" cy="5.2" r="2.7" />
  <circle cx="19.1" cy="10.4" r="2.7" />
  <circle cx="16.4" cy="18.8" r="2.7" />
  <circle cx="7.6" cy="18.8" r="2.7" />
  <circle cx="4.9" cy="10.4" r="2.7" />
</>;

const LOL_ROLES: Record<string, ReactNode> = {
  TOP: <>
    <path d="M3 3h18l-5 5H8v8l-5 5V3Z" />
    <path d="M11 11h5v5h-5zM19 11h2v10H11v-2h8z" opacity=".4" />
  </>,
  JUNGLE: <>
    <path d="M11.3 21C5.2 18.7 2.2 14.8 2 8.3c2 1.3 3.5 3 4.8 5.1C6.1 8.7 6.7 5.3 8.2 2c3.3 4.5 4.7 10.7 3.1 19Z" />
    <path d="M13 20.8c5.5-2.2 8.6-6.1 9-12.5-2.1 1.4-3.5 3-4.9 5.1.7-4.7.1-8-1.4-11.4-1.2 1.7-2.2 3.9-2.7 6.2 1.3 3.7 1.4 7.8 0 12.6Z" />
  </>,
  MID: <>
    <path d="M3 3h12l-3 3H6v6l-3 3V3Zm18 6v12H9l3-3h6v-6l3-3Z" opacity=".4" />
    <path d="m17 3 4 4L7 21l-4-4L17 3Z" />
  </>,
  ADC: <>
    <path d="M21 21H3l5-5h8V8l5-5v18Z" />
    <path d="M8 8h5v5H8zM3 3h10v2H5v8H3z" opacity=".4" />
  </>,
  SUPPORT: <>
    <path d="m12 3 3 3-3 4-3-4 3-3ZM2 6l7 3 2 4-6-2-3-5Zm20 0-7 3-2 4 6-2 3-5Z" />
    <path d="M11 11h2v6l3 4H8l3-4v-6Z" />
  </>,
};

const VALORANT_ROLES: Record<string, ReactNode> = {
  DUELIST: <>
    <path d="m14 2-9 11h6l-1 9 9-12h-6l1-8Z" />
    <path d="M5 3 2 8h3l2-5Zm14 13-2 5h3l2-5Z" opacity=".45" />
  </>,
  INITIATOR: <>
    <path d="m4 3 8 9-8 9v-6l3-3-3-3V3Zm8 0 8 9-8 9v-6l3-3-3-3V3Z" />
  </>,
  CONTROLLER: <g fill="none" stroke="currentColor" strokeWidth="1.8">
    <circle cx="12" cy="12" r="8" />
    <ellipse cx="12" cy="12" rx="3.6" ry="8" transform="rotate(35 12 12)" />
    <path d="M4.2 10.2c4 3.1 9.4 4.6 15.2 3.9" />
  </g>,
  SENTINEL: <>
    <path d="M12 2 3 6v6c0 4.7 5 8.1 9 10 4-1.9 9-5.3 9-10V6l-9-4Zm0 3 6 2.6V12c0 3-3.5 5.8-6 7.1-2.5-1.3-6-4.1-6-7.1V7.6L12 5Z" />
    <path d="M11 8h2v7h-2zm-2 2h6v2H9z" />
  </>,
};

const PUBG_STYLES: Record<string, ReactNode> = {
  AGGRESSIVE: <g fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
    <circle cx="12" cy="12" r="7" />
    <path d="M12 2v5m0 10v5M2 12h5m10 0h5" />
    <circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none" />
  </g>,
  BALANCED: <g fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 3v18m-4 0h8M4 7h16M6 7l-4 8h8L6 7Zm12 0-4 8h8l-4-8Z" />
    <path d="M2 15c.6 4 7.4 4 8 0m4 0c.6 4 7.4 4 8 0" />
  </g>,
  SURVIVAL: <>
    <path d="M12 2 3 6v6c0 4.7 5 8.1 9 10 4-1.9 9-5.3 9-10V6l-9-4Zm0 2.5 7 3.1V12c0 3.4-4 6.4-7 7.8-3-1.4-7-4.4-7-7.8V7.6l7-3.1Z" />
    <path d="m12 7 4 6h-3v4h-2v-4H8l4-6Z" />
  </>,
};

/** Decorative glyphs; the filter button supplies the accessible name. */
export function FilterRoleIcon({ game, value, size = 20 }: SymbolProps & { game: GameKey; value: string }) {
  const roles = game === 'LOL' ? LOL_ROLES : game === 'VALORANT' ? VALORANT_ROLES : PUBG_STYLES;
  return <svg className="filter-role-symbol" width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    {roles[value] ?? ALL_ROLES}
  </svg>;
}

const TIER_COLORS: Record<string, string> = {
  IRON: '#8b8584', BRONZE: '#b2866c', SILVER: '#aab6c6', GOLD: '#d0ad68',
  PLATINUM: '#7bb7b3', EMERALD: '#6eb68e', DIAMOND: '#93abe0', MASTER: '#b889ca',
  GRANDMASTER: '#cf7e85', CHALLENGER: '#d7bf7e', ASCENDANT: '#83b89a',
  IMMORTAL: '#cb8496', RADIANT: '#d4ca96',
};

export function FilterTierIcon({ tier, size = 20 }: SymbolProps & { tier: string | null }) {
  return <svg className="filter-tier-symbol" width={size} height={size} viewBox="0 0 24 24" fill="none" style={{ color: tier ? TIER_COLORS[tier] ?? '#9c95b0' : '#9c95b0' }} aria-hidden="true" focusable="false">
    <path d="m12 2 7 4v8l-7 8-7-8V6l7-4Z" fill="currentColor" opacity=".18" />
    <path d="m12 3 6 3.5V14l-6 6.5L6 14V6.5L12 3Z" stroke="currentColor" strokeWidth="1.5" />
    <path d="m3 7 3 2v6l-3-3V7Zm18 0-3 2v6l3-3V7Z" fill="currentColor" opacity=".6" />
    {tier ? <>
      <path d="m12 7 4 4-4 5-4-5 4-4Z" fill="currentColor" />
      <path d="m12 7 4 4h-4V7Z" fill="white" opacity=".2" />
    </> : <path d="m9 11 3 3 3-3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />}
  </svg>;
}

export function FilterModeIcon({ mode, size = 16 }: SymbolProps & { mode: string }) {
  let glyph: ReactNode;
  if (mode === 'SOLO_DUO_RANKED' || mode === 'COMPETITIVE') {
    glyph = <><path d="M7 3h10v5c0 4-2 6-5 6s-5-2-5-6V3Zm0 2H3v3c0 3 2 4 5 4m9-7h4v3c0 3-2 4-5 4M12 14v6m-4 1h8" /></>;
  } else if (mode === 'ARAM') {
    glyph = <path d="M12 2v20M3.3 7l17.4 10M3.3 17 20.7 7M9 4l3 3 3-3M9 20l3-3 3 3M4 10l4-1-1-4m10 14-1-4 4-1M4 14l4 1-1 4M17 5l-1 4 4 1" />;
  } else if (mode === 'SWIFTPLAY') {
    glyph = <path d="m14 2-9 12h6l-1 8 9-12h-6l1-8Z" />;
  } else if (mode === 'DUO' || mode === 'SQUAD') {
    glyph = <>
      <circle cx="9" cy="8" r="3" /><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 4v2" />
      {mode === 'SQUAD' ? <path d="M2 5a3 3 0 0 1 2-2m16 0a3 3 0 0 1 2 2" /> : null}
    </>;
  } else if (mode === 'ANY') {
    glyph = <><path d="M4 4h6v6H4zm10 0h6v6h-6zM4 14h6v6H4zm10 0h6v6h-6z" /></>;
  } else {
    glyph = <><path d="M6 21V3m0 1h13l-3 4 3 4H6" /><path d="M3 21h6" /></>;
  }
  return <svg className="filter-mode-symbol" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{glyph}</svg>;
}
