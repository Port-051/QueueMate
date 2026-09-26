import type { GameKey } from '../api/types';
import { Avatar } from '../components/ui';
import { FilterRoleIcon, FilterTierIcon } from '../components/FilterSymbols';
import { PerformanceValue, PreferredChampions } from '../components/IntroductionVisuals';
import { IconCheck, IconParty } from '../components/icons';
import { keyConditionOptions, visibleModes, usesKeyCondition } from '../domain/gameConfig';
import { relativeBoardTime, TIER_LABELS, timeLabel } from '../domain/recruitment';
import { hasLolRankDivision } from '../domain/lolRank';
import { canonicalRoomRoles, ROOM_ROLES, summarizeRoom } from './summary';
import { RoomVoice } from './RoomVoice';
import { roomVoice } from './voice';
import { remainingRoomRoles, vacantRoleOptions } from './positions';
import type { GameRoom, RoomMember } from './types';

export const roomModeLabel = (room: GameRoom) => visibleModes(room.game).find(mode => mode.key === room.modeKey)?.label ?? room.modeKey;
export const roomVoiceLabel = (room: GameRoom) => roomVoice(room.voice) === 'REQUIRED' ? '마이크 사용' : '마이크 미사용';

export function RoomRoles({ game, roles, labels = false }: { game: GameKey; roles: string[]; labels?: boolean }) {
  const ordered = canonicalRoomRoles(game, roles);
  const shown = !ordered.length || ordered.length === ROOM_ROLES[game].length ? ['ANY'] : ordered;
  return <span className={`room-role-icons${labels ? ' has-labels' : ''}`}>{shown.map(role => {
    const label = role === 'ANY' ? '모든 포지션' : keyConditionOptions(game).find(item => item.value === role)?.label ?? role;
    return <span key={role} title={label} role="img" aria-label={label}><FilterRoleIcon game={game} value={role} size={19} />{labels ? <b>{role === 'ANY' ? '전체' : label}</b> : null}</span>;
  })}</span>;
}

export function RoomRank({ game, tier, division, size = 30 }: { game: GameKey; tier: string | null; division: number | null; size?: number }) {
  const suffix = tier && division ? game === 'LOL' ? hasLolRankDivision(tier) ? ['', 'I', 'II', 'III', 'IV'][division] : '' : String(division) : '';
  return <span className="room-rank"><FilterTierIcon game={game} tier={tier} size={size} /><strong>{tier ? `${TIER_LABELS[tier] ?? tier}${suffix ? ` ${suffix}` : ''}` : '—'}</strong></span>;
}

function Stat({ kind, value }: { kind: 'winRate' | 'kda'; value: number | null }) {
  return value === null ? <strong className="room-unknown-stat">—</strong> : <PerformanceValue kind={kind} value={value} />;
}

function RoomMemberAvatar({ room, member, size }: { room: GameRoom; member: RoomMember; size: number }) {
  return <span className="room-member-avatar">
    <Avatar name={member.nickname} avatarUrl={member.avatarUrl} size={size} />
    {member.id === room.ownerId ? <span className="room-host-crown" role="img" aria-label="방장" title="방장">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="m3 6 5 4 4-7 4 7 5-4-2 12H5L3 6Zm2 14h14v2H5v-2Z" /></svg>
    </span> : null}
  </span>;
}

function RoomMemberFacts({ room, member }: { room: GameRoom; member: RoomMember }) {
  return <dl className="room-member-facts">
    <div><dt className="sr-only">티어</dt><dd><RoomRank game={room.game} tier={member.tier} division={member.division} size={26} /></dd></div>
    <div><dt className="sr-only">포지션</dt><dd>{usesKeyCondition(room.game, room.modeKey)
      ? <RoomRoles game={room.game} roles={member.roles} labels={member.roles.length <= 1} />
      : <span className="room-random-role">무작위</span>}</dd></div>
    <div><dt>승률</dt><dd><Stat kind="winRate" value={member.winRate} /></dd></div>
    <div><dt>KDA</dt><dd><Stat kind="kda" value={member.kda} /></dd></div>
  </dl>;
}

export function RoomSummaryCard({ room, showMembers = false }: { room: GameRoom; showMembers?: boolean }) {
  const summary = summarizeRoom(room);
  const host = room.members.find(member => member.id === room.ownerId);
  const confirmed = room.status === 'CONFIRMED';
  const wantedRoles = remainingRoomRoles(room);
  return <div className={`room-summary-card${confirmed ? ' is-confirmed' : ''}`}>
    <div className="room-card-eyebrow"><span className={`room-status${confirmed ? ' is-confirmed' : ''}`}>{confirmed ? <><IconCheck size={12} />모집 마감</> : <><i />모집 중</>}</span></div>
    <h3>{room.title}</h3>
    {room.availableFrom ? <div className="room-start-time">{timeLabel(room.availableFrom)} 시작</div> : null}
    {showMembers ? <div className="room-visible-roster" aria-label="방 구성원 정보">
      {room.members.map(member => <div className="room-roster-row" key={member.id}>
        <div className="room-roster-identity"><RoomMemberAvatar room={room} member={member} size={32} /><div><strong>{member.nickname}</strong><span>{usesKeyCondition(room.game, room.modeKey) ? <RoomRoles game={room.game} roles={member.roles} /> : '무작위 포지션'}</span></div></div>
        <div className="room-roster-rank"><RoomRank game={room.game} tier={member.tier} division={member.division} /><span><Stat kind="winRate" value={member.winRate} /> 승률 · <Stat kind="kda" value={member.kda} /> KDA</span></div>
        {room.game === 'LOL' ? <div className="room-roster-champions" aria-label={`${member.nickname} 주 챔피언`}><PreferredChampions game={room.game} names={member.champions.slice(0, 3)} />{!member.champions.length ? <span>챔피언 정보 없음</span> : null}</div> : null}
      </div>)}
    </div> : <div className="room-summary-stats">
      <div><span>평균 티어</span><RoomRank game={room.game} tier={summary.tier} division={summary.division} /></div>
      <div><span>평균 승률</span><Stat kind="winRate" value={summary.winRate} /></div>
      <div><span>평균 KDA</span><Stat kind="kda" value={summary.kda} /></div>
    </div>}
    <div className="room-summary-conditions">
      {usesKeyCondition(room.game, room.modeKey) ? <><div><span>채워진 포지션</span><RoomRoles game={room.game} roles={summary.roles} /></div>{!confirmed && wantedRoles.length ? <div><span>찾는 포지션</span><RoomRoles game={room.game} roles={wantedRoles} /></div> : null}</> : <div className="room-no-roles"><span>포지션</span><strong>무작위 배정</strong></div>}
      <div className="room-voice" title={roomVoiceLabel(room)}><span>마이크</span><span role="img" aria-label={roomVoiceLabel(room)}><RoomVoice value={room.voice} /></span></div>
    </div>
    {showMembers ? <div className={`room-open-seats${confirmed ? ' is-closed' : ''}`}><span>{confirmed ? '추가 입장 마감' : `${room.capacity - room.members.length}명 더 기다려요`}</span><span aria-label={`${room.members.length}명 참여, 정원 ${room.capacity}명`}>{Array.from({ length: room.capacity }, (_, index) => <i key={index} className={index < room.members.length ? 'is-filled' : ''} />)}</span></div> : null}
    <div className="room-card-footer"><span className="room-avatar-stack">{room.members.slice(0, 5).map(member => <Avatar key={member.id} name={member.nickname} avatarUrl={member.avatarUrl} size={26} />)}</span><span className="room-occupancy"><IconParty size={15} /><strong>{room.members.length}</strong><span>/ {room.capacity}명</span></span></div>
    <div className="room-card-meta"><span>{host?.nickname ?? '방장'}의 방</span><time dateTime={new Date(room.createdAt).toISOString()}>{relativeBoardTime(new Date(room.createdAt).toISOString())}</time></div>
  </div>;
}

export function RoomMemberCard({ room, member }: { room: GameRoom; member: RoomMember }) {
  return <article className="room-member-card" aria-label={`${member.nickname} 정보`}>
    <RoomMemberAvatar room={room} member={member} size={58} />
    <h3>{member.nickname}</h3>
    <RoomMemberFacts room={room} member={member} />
    <div className="room-member-champions"><PreferredChampions game={room.game} names={member.champions.slice(0, 3)} /></div>
    {member.bio ? <p>{member.bio}</p> : null}
  </article>;
}

export function RoomDeck({ room, onOpen }: { room: GameRoom; onOpen: (room: GameRoom, button: HTMLButtonElement) => void }) {
  const closed = room.status === 'CONFIRMED';
  const hasRoles = usesKeyCondition(room.game, room.modeKey);
  const vacancies = vacantRoleOptions(room);
  return <button type="button" className={`room-deck room-compact${closed ? ' is-confirmed' : ''}`} data-status={room.status} aria-label={`${room.title} 방 정보`} onClick={event => onOpen(room, event.currentTarget)}>
    <div className="compact-room-header" aria-label="방 요약">
      <h3 title={room.title}>{room.title}</h3>
      {room.availableFrom ? <time>{timeLabel(room.availableFrom)}</time> : null}
      <span className={`room-status${closed ? ' is-confirmed' : ''}`}>{closed ? '마감' : '모집 중'}<b>{room.members.length}/{room.capacity}</b></span>
    </div>
    <div className="compact-members" aria-label="방 구성원 정보">
      {room.members.map(member => <div className="compact-member" key={member.id}>
        <div className="compact-member-name"><RoomMemberAvatar room={room} member={member} size={34}/><strong title={member.nickname}>{member.nickname}</strong></div>
        <RoomMemberFacts room={room} member={member} />
        <div className="compact-member-champions" aria-label={`${member.nickname} ${room.game === 'LOL' ? '주 챔피언' : room.game === 'VALORANT' ? '선호 요원' : '선호 무기'}`}><PreferredChampions game={room.game} names={member.champions.slice(0,3)}/>{room.game === 'LOL' ? Array.from({ length: Math.max(0, 3 - member.champions.length) }, (_, index) => <span className="compact-champion-empty" key={index} role="img" aria-label="챔피언 미등록" title="챔피언 미등록">—</span>) : null}</div>
      </div>)}
      {vacancies.map((roles, index) => <div className="compact-member compact-seat" key={`seat-${index}`}>
        {hasRoles ? <RoomRoles game={room.game} roles={roles} labels /> : <IconParty size={30} />}
        <RoomVoice value={room.voice} />
      </div>)}
    </div>
  </button>;
}
