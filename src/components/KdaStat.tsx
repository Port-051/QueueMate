import type { GameStats } from '../api/types';
import '../styles/kda-stat.css';

export interface KdaLine { kills: number; deaths: number; assists: number; kda: number; }
export type KdaStatSize = 'sm' | 'lg';

const finite = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null;

/**
 * OP.GG 모양으로 그릴 수 있는 KDA — 판당 평균 킬 · 데스 · 어시스트와 KDA 비율 넷이 다 숫자일 때만(하나라도 `null` 이면 `null` — 부르는 쪽이 지금까지의 KDA 칸을 그대로 그린다).
 * LoL 은 최근 10판 평균이다. PUBG 는 `avgAssists` · `kda` 가 늘 `null` 이라 여기서 걸러지고, 데스가 0 인 사람도 서버가 `kda` 를 `null` 로 준다(platform-api.md "게임 프로필").
 */
export function kdaLine(stats: GameStats | null | undefined): KdaLine | null {
  const kills = finite(stats?.avgKills);
  const deaths = finite(stats?.avgDeaths);
  const assists = finite(stats?.avgAssists);
  const kda = finite(stats?.kda);
  return kills !== null && deaths !== null && assists !== null && kda !== null ? { kills, deaths, assists, kda } : null;
}

/** KDA 비율의 색 — 3 밑은 차분한 회청색, 3 이상 앱 강조색, 5 이상 경고색(OP.GG 가 높을수록 강조하는 것을 앱 토큰으로 줄였다 — Claude 가 정한 구간 · 2026-09-30). */
const kdaTone = (kda: number) => kda >= 5 ? 'top' : kda >= 3 ? 'high' : 'base';

/**
 * OP.GG 처럼 위 줄에 평균 `킬 / 데스 / 어시스트`(소수 첫째 자리), 아래 줄에 KDA 비율을 크게(소수 둘째 자리). 2026-09-30 소유자 지시 — "KDA 도 OP.GG 처럼".
 * 좌석 작은 창(`SeatPopover`)과 프로필 창(`RoomMemberProfile` → `RoomMemberFacts`)이 쓴다. 화면 읽기에는 한 줄로 읽어 준다.
 */
export function KdaStat({ line, size = 'sm' }: { line: KdaLine; size?: KdaStatSize }) {
  const { kills, deaths, assists, kda } = line;
  const k = kills.toFixed(1), d = deaths.toFixed(1), a = assists.toFixed(1), ratio = kda.toFixed(2);
  return <span className="kda-stat" data-size={size} role="img" aria-label={`평균 킬 ${k} · 데스 ${d} · 어시스트 ${a} · KDA ${ratio}`}>
    <span className="kda-stat-avg" aria-hidden="true">{k}<i>/</i>{d}<i>/</i>{a}</span>
    <b className="kda-stat-ratio" data-tone={kdaTone(kda)} aria-hidden="true">{ratio}</b>
  </span>;
}
