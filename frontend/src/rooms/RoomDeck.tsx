import type { GameKey } from '../api/types';
import { Avatar } from '../components/ui';
import { FilterModeIcon, FilterRoleIcon, FilterTierIcon, VoiceIcon } from '../components/FilterSymbols';
import { PerformanceValue, PreferredChampions } from '../components/IntroductionVisuals';
import { IconCheck, IconParty } from '../components/icons';
import { keyConditionOptions, visibleModes, usesKeyCondition } from '../domain/gameConfig';
import { relativeBoardTime, TIER_LABELS, timeLabel } from '../domain/recruitment';
import { hasLolRankDivision } from '../domain/lolRank';
import { canonicalRoomRoles, ROOM_ROLES, summarizeRoom } from './summary';
import type { GameRoom, RoomMember } from './types';

export const roomModeLabel = (room: GameRoom) => visibleModes(room.game).find(mode => mode.key === room.modeKey)?.label ?? room.modeKey;
export const roomVoiceLabel = (room: GameRoom) => room.voice === 'REQUIRED' ? '마이크 사용' : room.voice === 'NO_VOICE' ? '마이크 미사용' : '마이크 무관';

export function RoomRoles({ game, roles }: { game: GameKey; roles: string[] }) {
  const ordered = canonicalRoomRoles(game, roles);
  const shown = !ordered.length || ordered.length === ROOM_ROLES[game].length ? ['ANY'] : ordered;
  return <span className="room-role-icons">{shown.map(role => {
    const label = role === 'ANY' ? '모든 포지션' : keyConditionOptions(game).find(item => item.value === role)?.label ?? role;
    return <span key={role} title={label} role="img" aria-label={label}><FilterRoleIcon game={game} value={role} size={19} /></span>;
  })}</span>;
}

export function RoomRank({ game, tier, division }: { game: GameKey; tier: string | null; division: number | null }) {
  const suffix = tier && division ? game === 'LOL' ? hasLolRankDivision(tier) ? ['', 'I', 'II', 'III', 'IV'][division] : '' : String(division) : '';
  return <span className="room-rank"><FilterTierIcon game={game} tier={tier} size={30} /><strong>{tier ? `${TIER_LABELS[tier] ?? tier}${suffix ? ` ${suffix}` : ''}` : '—'}</strong></span>;
}

function Stat({ kind, value }: { kind: 'winRate' | 'kda'; value: number | null }) {
  return value === null ? <strong className="room-unknown-stat">—</strong> : <PerformanceValue kind={kind} value={value} />;
}

export function RoomSummaryCard({ room, showMembers = false }: { room: GameRoom; showMembers?: boolean }) {
  const summary = summarizeRoom(room);
  const host = room.members.find(member => member.id === room.ownerId);
  const confirmed = room.status === 'CONFIRMED';
  return <div className={`room-summary-card${confirmed ? ' is-confirmed' : ''}`}>
    <div className="room-card-eyebrow"><span><FilterModeIcon mode={room.modeKey} />{roomModeLabel(room)}</span><span className={`room-status${confirmed ? ' is-confirmed' : ''}`}>{confirmed ? <><IconCheck size={12} />모집 마감</> : <><i />모집 중</>}</span></div>
    <h3>{room.title}</h3>
    {room.availableFrom ? <div className="room-start-time">{timeLabel(room.availableFrom)} 시작</div> : null}
    {showMembers ? <div className="room-visible-roster" aria-label="방 구성원 정보">
      {room.members.map(member => <div className="room-roster-row" key={member.id}>
        <div className="room-roster-identity"><Avatar name={member.nickname} avatarUrl={member.avatarUrl} size={32} /><div><strong>{member.nickname}</strong><span>{member.id === room.ownerId ? '방장' : '팀원'} · {usesKeyCondition(room.game, room.modeKey) ? <RoomRoles game={room.game} roles={member.roles} /> : '무작위 포지션'}</span></div></div>
        <div className="room-roster-rank"><RoomRank game={room.game} tier={member.tier} division={member.division} /><span><Stat kind="winRate" value={member.winRate} /> 승률 · <Stat kind="kda" value={member.kda} /> KDA</span></div>
        {room.game === 'LOL' ? <div className="room-roster-champions" aria-label={`${member.nickname} 주 챔피언`}><PreferredChampions game={room.game} names={member.champions.slice(0, 3)} />{!member.champions.length ? <span>챔피언 정보 없음</span> : null}</div> : null}
      </div>)}
    </div> : <div className="room-summary-stats">
      <div><span>평균 티어</span><RoomRank game={room.game} tier={summary.tier} division={summary.division} /></div>
      <div><span>평균 승률</span><Stat kind="winRate" value={summary.winRate} /></div>
      <div><span>평균 KDA</span><Stat kind="kda" value={summary.kda} /></div>
    </div>}
    <div className="room-summary-conditions">
      {usesKeyCondition(room.game, room.modeKey) ? <><div><span>채워진 포지션</span><RoomRoles game={room.game} roles={summary.roles} /></div><div><span>찾는 포지션</span><RoomRoles game={room.game} roles={room.desiredRoles} /></div></> : <div className="room-no-roles"><span>포지션</span><strong>무작위 배정</strong></div>}
      <div className="room-voice" title={roomVoiceLabel(room)}><span>마이크</span><span role="img" aria-label={roomVoiceLabel(room)}><VoiceIcon preference={room.voice} /></span></div>
    </div>
    {showMembers ? <div className={`room-open-seats${confirmed ? ' is-closed' : ''}`}><span>{confirmed ? '추가 입장 마감' : `${room.capacity - room.members.length}명 더 기다려요`}</span><span aria-label={`${room.members.length}명 참여, 정원 ${room.capacity}명`}>{Array.from({ length: room.capacity }, (_, index) => <i key={index} className={index < room.members.length ? 'is-filled' : ''} />)}</span></div> : null}
    <div className="room-card-footer"><span className="room-avatar-stack">{room.members.slice(0, 5).map(member => <Avatar key={member.id} name={member.nickname} avatarUrl={member.avatarUrl} size={26} />)}</span><span className="room-occupancy"><IconParty size={15} /><strong>{room.members.length}</strong><span>/ {room.capacity}명</span></span></div>
    <div className="room-card-meta"><span>{host?.nickname ?? '방장'}의 방</span><time dateTime={new Date(room.createdAt).toISOString()}>{relativeBoardTime(new Date(room.createdAt).toISOString())}</time></div>
  </div>;
}

export function RoomMemberCard({ room, member }: { room: GameRoom; member: RoomMember }) {
  return <article className="room-member-card" aria-label={`${member.nickname} 정보`}>
    <span className="room-member-badge">{member.id === room.ownerId ? '방장' : '팀원'}</span>
    <Avatar name={member.nickname} avatarUrl={member.avatarUrl} size={58} />
    <h3>{member.nickname}</h3>
    <RoomRank game={room.game} tier={member.tier} division={member.division} />
    <div className="room-member-stats"><div><span>승률</span><Stat kind="winRate" value={member.winRate} /></div><div><span>KDA</span><Stat kind="kda" value={member.kda} /></div></div>
    {usesKeyCondition(room.game, room.modeKey) ? <RoomRoles game={room.game} roles={member.roles} /> : null}
    <div className="room-member-champions"><PreferredChampions game={room.game} names={member.champions.slice(0, 3)} /><span title={roomVoiceLabel({ ...room, voice: member.voice })} role="img" aria-label={roomVoiceLabel({ ...room, voice: member.voice })}><VoiceIcon preference={member.voice} /></span></div>
    {member.bio ? <p>{member.bio}</p> : null}
  </article>;
}

export function RoomDeck({ room, onOpen }: { room: GameRoom; onOpen: (room: GameRoom, button: HTMLButtonElement) => void }) {
  return <button type="button" className={`room-deck${room.status === 'CONFIRMED' ? ' is-confirmed' : ''}`} data-status={room.status} aria-label={`${room.title} 방 펼치기`} onClick={event => onOpen(room, event.currentTarget)}>
    <span className="room-deck-backs" aria-hidden="true">{room.members.slice(0, 5).map((member, index) => <span className="room-deck-back" key={member.id} style={{ '--card-index': index } as React.CSSProperties}><Avatar name={member.nickname} avatarUrl={member.avatarUrl} size={20} /></span>)}</span>
    <RoomSummaryCard room={room} showMembers />
  </button>;
}
