import type { CSSProperties } from 'react';
import type { GameStats } from '../api/types';
import '../styles/recent-results.css';

export type RecentResult = 'W' | 'L';
export interface RecentRecord { results: RecentResult[]; wins: number; losses: number; }
export type RecentResultsSize = 'sm' | 'lg';

/** 최근 경기 표시는 최대 10판, 한 줄이다. 옛 20판 스냅숏도 최신 10판만 표시한다. */
const TILES_PER_ROW = 10;

/**
 * 최근 경기의 승 · 패(LoL `stats.detail.recentResults` — platform P-43 · 2026-09-30). 배열이면(빈 배열도) 기록을, **칸이 없으면(그날 전의 스냅숏) `null`** 을 준다 —
 * 부르는 쪽이 지금까지의 판 수 칸(`최근 10판`)을 그대로 그린다. 순서는 받은 그대로(새 경기가 먼저)이고 `"W"` · `"L"` 밖의 값은 뺀다.
 * **승 · 패 수와 판 수는 이 배열에서 센다** — `stats.wins` · `losses`(솔로랭크 시즌 누적 — 승 · 패 막대의 숫자)를 쓰지 않는다.
 */
export function recentRecord(stats: GameStats | null | undefined): RecentRecord | null {
  const raw = stats?.detail?.recentResults;
  if (!Array.isArray(raw)) return null;
  const results = raw.filter((value): value is RecentResult => value === 'W' || value === 'L').slice(0, 10);
  const wins = results.filter(result => result === 'W').length;
  return { results, wins, losses: results.length - wins };
}

/**
 * duo.gg 처럼 머리 한 줄 `N승 M패 (K 게임)` + 그 아래 경기마다 작은 칸 하나(`승` 초록 · `패` 빨강) — 2026-09-30 소유자 지시.
 * **왼쪽 칸이 가장 최근 경기다**(배열 순서 그대로). 한 줄에 10칸까지 · 더 많으면 다음 줄로 넘기고(20판 = 10칸 두 줄), 자리가 모자라면 칸이 정사각형 그대로 줄어든다.
 * 경기가 없으면(빈 배열) 칸 없이 `없음` 한 마디다 — 두 자리 모두 앞에 `최근 경기` 라는 이름이 있다. 칸 밑의 점(duo.gg 의 경기 표시)은 없다 — 받는 데이터가 없다.
 * `label` 을 주면 머리 줄 앞에 작은 이름을 붙인다(좌석 작은 창의 `최근 경기` — 프로필 창은 칸의 `dt` 가 이름이다).
 * 브라우저 풍선말(`title`)은 달지 않는다(소유자가 싫어했다). 화면 읽기에는 한 줄로 읽어 준다(`최근 10게임 7승 3패 — 왼쪽이 가장 최근: 승, 패, …`).
 * 좌석 작은 창(`SeatPopover`)과 프로필 창(`RoomMemberFacts` `opgg`)이 쓴다.
 */
export function RecentResults({ record, size = 'sm', label }: { record: RecentRecord; size?: RecentResultsSize; label?: string }) {
  const { results, wins, losses } = record;
  const games = results.length;
  const spoken = games
    ? `최근 ${games}게임 ${wins}승 ${losses}패 — 왼쪽이 가장 최근: ${results.map(result => result === 'W' ? '승' : '패').join(', ')}`
    : '최근 경기 없음';
  return <span className="recent-wl" data-size={size} role="img" aria-label={spoken}>
    <span className="recent-wl-head" aria-hidden="true">
      {label ? <small className="recent-wl-label">{label}</small> : null}
      {games
        ? <span className="recent-wl-sum"><b>{wins}승 {losses}패</b> <span className="recent-wl-count">({games} 게임)</span></span>
        : <span className="recent-wl-none">없음</span>}
    </span>
    {games ? <span className="recent-wl-tiles" aria-hidden="true" style={{ '--rr-cols': Math.min(games, TILES_PER_ROW) } as CSSProperties}>
      {results.map((result, index) => <span key={index} className={`recent-wl-tile ${result === 'W' ? 'is-win' : 'is-loss'}`}>{result === 'W' ? '승' : '패'}</span>)}
    </span> : null}
  </span>;
}
