import { PerformanceValue, PreferredChampions } from '../components/IntroductionVisuals';
import { championName } from '../domain/champions';
import type { GameKey } from '../api/types';
import type { RoomMember } from './types';
import { recentRoomStats } from './stats';

export function RoomKda({ member }: { member: RoomMember }) {
  const recent = recentRoomStats(member.recentStats);
  const ratio = recent && recent.deaths > 0 ? (recent.kills + recent.assists) / recent.deaths : member.kda;
  return <span className="room-kda-detail">
    {recent && recent.deaths === 0 ? <strong className="room-perfect-kda">{recent.kills + recent.assists > 0 ? 'Perfect' : '—'}</strong>
      : ratio === null ? <strong className="room-unknown-stat">—</strong> : <PerformanceValue kind="kda" value={ratio} />}
    {recent ? <>
      <span className="room-kda-split" aria-label={`평균 ${recent.kills}킬 ${recent.deaths}데스 ${recent.assists}어시스트`}>
        <span>{recent.kills.toFixed(1)}</span><i>/</i><span className="room-kda-deaths">{recent.deaths.toFixed(1)}</span><i>/</i><span>{recent.assists.toFixed(1)}</span>
      </span>
      <span className="room-stat-caption">최근 {recent.games}경기 평균</span>
    </> : <span className="room-stat-caption">상세 기록 없음</span>}
  </span>;
}

export function RoomChampionStats({ game, member }: { game: GameKey; member: RoomMember }) {
  const recent = game === 'LOL' ? recentRoomStats(member.recentStats) : null;
  const records = recent?.champions ?? [];
  const names = records.length ? records.map(record => record.name) : member.champions.slice(0, 3);
  const label = game === 'LOL' ? records.length ? `최근 ${recent!.games}경기 챔피언` : '주 챔피언' : game === 'VALORANT' ? '선호 요원' : '선호 무기';
  return <div className="compact-member-champions" aria-label={`${member.nickname} ${label}`}>
    <span className="compact-champions-label">{label}</span>
    <span className="compact-champions-list">{names.map((name, index) => {
      const record = records[index];
      return <span className="room-champion-stat" key={`${name}-${index}`} aria-label={`${championName(name) ?? name}${record ? ` ${record.wins}승 ${record.games - record.wins}패` : ''}`}>
        <PreferredChampions game={game} names={[name]} />
        {record ? <><strong className={record.wins / record.games >= .6 ? 'is-high' : ''}>{Math.round(record.wins / record.games * 100)}%</strong><small>{record.games}전</small></> : null}
      </span>;
    })}{game === 'LOL' ? Array.from({ length: Math.max(0, 3 - names.length) }, (_, index) => <span className="compact-champion-empty" key={index} role="img" aria-label="챔피언 미등록" title="챔피언 미등록">—</span>) : null}</span>
    {game === 'LOL' && !records.length ? <span className="room-stat-caption">챔피언별 전적 없음</span> : null}
  </div>;
}
