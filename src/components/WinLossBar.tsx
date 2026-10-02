import { PerformanceValue } from './IntroductionVisuals';
import type { GameStats } from '../api/types';
import '../styles/win-loss-bar.css';

export interface WinLossRecord { wins: number; losses: number; }
export type WinLossSize = 'sm' | 'md' | 'lg';

const count = (value: unknown): number | null => typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : null;

/**
 * 막대를 그릴 수 있는 전적 — `wins` · `losses` 가 둘 다 숫자이고 합이 0 보다 클 때만(그 밖은 `null` — 부르는 쪽이 지금까지의 승률 칸을 그대로 그린다).
 * LoL 은 솔로랭크 시즌 누적(`league-v4`)이고 PUBG 는 둘 다 늘 `null` · VALORANT 는 `stats` 가 `null` 이다(platform-api.md "게임 프로필").
 * **`stats.games`(LoL 은 최근 10판) 는 쓰지 않는다** — 막대의 판 수는 `wins + losses` 다.
 */
export function winLossRecord(stats: GameStats | null | undefined): WinLossRecord | null {
  const wins = count(stats?.wins);
  const losses = count(stats?.losses);
  return wins !== null && losses !== null && wins + losses > 0 ? { wins, losses } : null;
}

/** 승률 — 서버가 준 `stats.winRate`(같은 식으로 반올림한 정수 퍼센트 — `GameStatsResponse#winRate`)가 먼저이고 없을 때만 여기서 센다. */
export const winLossRate = ({ wins, losses }: WinLossRecord, given?: number | null) =>
  typeof given === 'number' && Number.isFinite(given) ? given : Math.round(wins / (wins + losses) * 100);

const number = (value: number) => value.toLocaleString('ko-KR');

/**
 * OP.GG 처럼 승 · 패를 한 막대로 — 왼쪽 승(파랑) · 오른쪽 패(빨강), 폭은 승 : 패, 막대 뒤에 승률(기준 UI의 다섯 색상 구간). 2026-09-30 소유자 지시
 * "몇 판 해서 승률이 어떻게 되는지를 한 눈에". 좌석 작은 창(`SeatPopover`)과 프로필 창 · 방 화면 파티원(`RoomMemberFacts`)이 쓴다.
 * - 한쪽이 아주 적어도(3승 40패) 그 칸은 글자가 들어갈 만큼은 남는다(칸의 최소 폭 = 글자 — 폭이 비율에서 조금 어긋나는 대신 읽힌다).
 * - 한쪽이 0 이면 막대는 한 칸이고 0 쪽 글자는 그 칸 위 반대편 끝에 얹는다(0 에 색 칸을 주지 않는다).
 * - 화면 읽기에는 한 줄로 읽어 준다(`201승 216패 · 승률 48%`).
 */
export function WinLossBar({ record, rate: given, size = 'md' }: { record: WinLossRecord; rate?: number | null; size?: WinLossSize }) {
  const { wins, losses } = record;
  const rate = winLossRate(record, given);
  const winText = `${number(wins)}승`;
  const lossText = `${number(losses)}패`;
  return <span className="win-loss" data-size={size} role="img" aria-label={`${winText} ${lossText} · 승률 ${rate}%`}>
    <span className="win-loss-bar" aria-hidden="true">
      {wins > 0 ? <span className="win-loss-seg is-win" style={{ flexGrow: wins }}>{winText}</span> : null}
      {losses > 0 ? <span className="win-loss-seg is-loss" style={{ flexGrow: losses }}>{lossText}</span> : null}
      {wins === 0 ? <span className="win-loss-zero is-win">{winText}</span> : null}
      {losses === 0 ? <span className="win-loss-zero is-loss">{lossText}</span> : null}
    </span>
    <span className="win-loss-rate" aria-hidden="true"><PerformanceValue kind="winRate" value={rate} /></span>
  </span>;
}
