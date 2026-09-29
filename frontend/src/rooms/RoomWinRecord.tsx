import { PerformanceValue } from '../components/IntroductionVisuals';
import type { RoomMember } from './types';
import { matchCount } from './stats';

export function RoomWinRecord({ member }: { member: RoomMember }) {
  const wins = matchCount(member.wins);
  const losses = matchCount(member.losses);
  const total = wins !== null && losses !== null ? wins + losses : null;
  const known = total !== null && Number.isSafeInteger(total) && total > 0;
  const rate = known ? Math.round(wins! / total * 100) : total === 0 ? null : member.winRate;
  const validRate = rate !== null && Number.isFinite(rate) && rate >= 0 && rate <= 100;
  return <span className="room-win-record">
    <span className="room-win-summary">
      {validRate ? <PerformanceValue kind="winRate" value={rate} /> : <strong className="room-unknown-stat">—</strong>}
      {known ? <span className="room-win-count"><span>{wins}승</span> <span>{losses}패</span></span> : null}
    </span>
    {known ? <span className="room-win-bar" aria-hidden="true"><span style={{ width: `${wins! / total * 100}%` }} /><span className="room-win-bar-labels"><b>{wins}승</b><b>{losses}패</b></span></span>
      : <span className="room-record-empty">{total === 0 ? '아직 경기 없음' : validRate ? '승패 정보 없음' : '전적 정보 없음'}</span>}
  </span>;
}
