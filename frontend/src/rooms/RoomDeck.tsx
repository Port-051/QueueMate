import { useLayoutEffect, useRef } from 'react';
import { TierRangeLabel } from '../components/TierRangePicker';
import { tierColor } from '../domain/rankAssets';
import type { GameKey } from '../api/types';
import { Avatar } from '../components/ui';
import { FilterRoleIcon, FilterTierIcon } from '../components/FilterSymbols';
import { PerformanceValue, PreferredChampions } from '../components/IntroductionVisuals';
import { keyConditionOptions, usesKeyCondition } from '../domain/gameConfig';
import { TIER_LABELS } from '../domain/recruitment';
import { roomStartLabel } from './schedule';
import { hasLolRankDivision } from '../domain/lolRank';
import { canonicalRoomRoles, ROOM_ROLES } from './summary';
import { RoomVoice } from './RoomVoice';
import { vacantRoleOptions } from './positions';
import type { GameRoom, RoomMember } from './types';

function RoomBubbleTail() {
  return <svg className="room-bubble-tail" width="48" height="48" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
    <path d="M0 4C0 19 4 27 12 32C17 36 17 40 13 44Q11 47 15 46C24 43 26 38 35 36H48V0H0Z" fill="var(--room-bubble-bg)" />
  </svg>;
}

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
  return <span className="room-rank"><FilterTierIcon game={game} tier={tier} size={size} /><strong style={{ color: tierColor(tier) }}>{tier ? `${TIER_LABELS[tier] ?? tier}${suffix ? ` ${suffix}` : ''}` : '—'}</strong></span>;
}

function Stat({ kind, value }: { kind: 'winRate' | 'kda'; value: number | null }) {
  return value === null ? <strong className="room-unknown-stat">—</strong> : <PerformanceValue kind={kind} value={value} />;
}

export function RoomMemberAvatar({ room, member, size }: { room: GameRoom; member: RoomMember; size: number }) {
  return <span className="room-member-avatar">
    <Avatar name={member.nickname} avatarUrl={member.avatarUrl} size={size} />
    {member.id === room.ownerId ? <span className="room-host-crown" role="img" aria-label="방장" title="방장">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="m3 6 5 4 4-7 4 7 5-4-2 12H5L3 6Zm2 14h14v2H5v-2Z" /></svg>
    </span> : null}
  </span>;
}

export function RoomMemberFacts({ room, member, iconSize = 22 }: { room: GameRoom; member: RoomMember; iconSize?: number }) {
  return <dl className="room-member-facts">
    <div><dt className="sr-only">티어</dt><dd><RoomRank game={room.game} tier={member.tier} division={member.division} size={iconSize} /></dd></div>
    <div><dt className="sr-only">포지션</dt><dd>{usesKeyCondition(room.game, room.modeKey)
      ? <RoomRoles game={room.game} roles={member.roles} labels={member.roles.length <= 1} />
      : <span className="room-random-role">무작위</span>}</dd></div>
    <div><dt>승률</dt><dd><Stat kind="winRate" value={member.winRate} /></dd></div>
    <div><dt>KDA</dt><dd><Stat kind="kda" value={member.kda} /></dd></div>
  </dl>;
}

export function RoomDeck({ room, selfId, entering = false, onEntered, entryError, onSeat, onMember }: { room: GameRoom; selfId: string; entering?: boolean; onEntered?: () => void; entryError: string | null; onSeat: (room: GameRoom, roles: string[]) => void; onMember: (room: GameRoom, member: RoomMember) => void }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useLayoutEffect(() => {
    if (!entering) return;
    heading.current?.scrollIntoView({ block: 'nearest', behavior: 'instant' });
    heading.current?.focus({ preventScroll: true });
    const timer = window.setTimeout(() => onEntered?.(), 650);
    return () => window.clearTimeout(timer);
  }, [entering, onEntered]);
  const closed = room.status === 'CONFIRMED';
  const hasRoles = usesKeyCondition(room.game, room.modeKey);
  const vacancies = vacantRoleOptions(room);
  return <article className={`room-deck room-compact${room.ownerId === selfId ? ' is-own' : ''}${closed ? ' is-confirmed' : ''}${entering ? ' is-entering' : ''}`} data-status={room.status} aria-label={`${room.title} 방 정보`}>
    <RoomBubbleTail />
    <div className="compact-room-header" aria-label="방 요약">
      <h3 ref={heading} tabIndex={-1} title={room.title}>{room.title}</h3>
      {closed ? <span className="room-status is-confirmed">마감</span> : null}
      <time dateTime={room.availableFrom ?? undefined}>{roomStartLabel(room.availableFrom)}</time>
    </div>
    <div className="compact-members" aria-label="방 구성원 정보">
      {room.members.map(member => <div className="compact-member" key={member.id}>
        <button type="button" className="compact-member-name" aria-label={`${member.nickname} 프로필 보기`} onClick={() => onMember(room, member)}><RoomMemberAvatar room={room} member={member} size={24}/><strong title={member.nickname}>{member.nickname}</strong></button>
        <RoomMemberFacts room={room} member={member} />
        <div className="compact-member-champions" aria-label={`${member.nickname} ${room.game === 'LOL' ? '주 챔피언' : room.game === 'VALORANT' ? '선호 요원' : '선호 무기'}`}><PreferredChampions game={room.game} names={member.champions.slice(0,3)}/>{room.game === 'LOL' ? Array.from({ length: Math.max(0, 3 - member.champions.length) }, (_, index) => <span className="compact-champion-empty" key={index} role="img" aria-label="챔피언 미등록" title="챔피언 미등록">—</span>) : null}</div>
      </div>)}
      {vacancies.map((roles, index) => <button type="button" className="compact-member compact-seat" key={`seat-${index}`}
        disabled={Boolean(entryError)} title={entryError ?? undefined}
        aria-label={`${hasRoles ? roles.map(role => keyConditionOptions(room.game).find(option => option.value === role)?.label ?? role).join(' · ') || '전체 포지션' : '무작위'} 자리 참여${entryError ? ` · ${entryError}` : ''}`}
        onClick={() => onSeat(room, roles)}>
        <span className="compact-seat-status">{closed ? '모집 마감' : '모집 중'}</span>
        <dl className="room-member-facts room-seat-facts">
          <div><dt className="sr-only">포지션</dt><dd>{hasRoles ? <RoomRoles game={room.game} roles={roles} labels /> : <span className="room-random-role">무작위</span>}</dd></div>
          <div><dt className="sr-only">티어</dt><dd><TierRangeLabel game={room.game} value={room.desiredTierRange} stacked explicitBounds iconSize={22} /></dd></div>
        </dl>
        <span className="room-seat-voice"><RoomVoice value={room.voice} /><span>{room.voice === 'REQUIRED' ? '사용' : '미사용'}</span></span>
      </button>)}
    </div>
  </article>;
}
