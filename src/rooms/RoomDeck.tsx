import { useLayoutEffect, useRef } from 'react';
import { tierColor } from '../domain/rankAssets';
import type { GameKey } from '../api/types';
import { Avatar } from '../components/ui';
import { FilterRoleIcon, FilterTierIcon } from '../components/FilterSymbols';
import { PerformanceValue, PreferredChampions } from '../components/IntroductionVisuals';
import { keyConditionOptions } from '../domain/gameConfig';
import { modeLabel } from '../domain/labels';
import { TIER_LABELS } from '../domain/recruitment';
import { relativeTime } from '../domain/time';
import { hasLolRankDivision } from '../domain/lolRank';
import { canonicalRoomRoles, hasPositions, ROOM_ROLES } from './summary';
import { RoomVoice } from './RoomVoice';
import type { BoardMember, BoardRoom } from './types';

function RoomBubbleTail() {
  return <svg className="room-bubble-tail" width="48" height="48" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
    <path d="M0 4C0 19 4 27 12 32C17 36 17 40 13 44Q11 47 15 46C24 43 26 38 35 36H48V0H0Z" fill="var(--room-bubble-bg)" />
  </svg>;
}

/** 포지션 아이콘 줄. 비어 있거나 전부면 "모든 포지션". PUBG 는 포지션이 없다(`ROOM_ROLES.PUBG` 가 빈 배열) — 부르는 쪽이 가린다. */
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

export const POST_STATUS_LABEL: Record<BoardRoom['status'], string> = { RECRUITING: '모집 중', CONFIRMED: '확정', EXPIRED: '만료' };

export function RoomMemberAvatar({ member, size }: { member: BoardMember; size: number }) {
  return <span className="room-member-avatar">
    <Avatar name={member.nickname} size={size} />
    {member.host ? <span className="room-host-crown" role="img" aria-label="방장" title="방장">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="m3 6 5 4 4-7 4 7 5-4-2 12H5L3 6Zm2 14h14v2H5v-2Z" /></svg>
    </span> : null}
  </span>;
}

/** 카드의 사실 넷 — 티어 · 포지션(PUBG 는 서버) · 승률 · KDA. 전부 게임 프로필(`profile`)에서 온다 — VALORANT · PUBG 의 전적은 아직 없어 `—` 다. */
export function RoomMemberFacts({ room, member, iconSize = 22 }: { room: BoardRoom; member: BoardMember; iconSize?: number }) {
  const positions = hasPositions(room.game, room.modeKey);
  const server = member.profile?.server;
  return <dl className="room-member-facts">
    <div><dt className="sr-only">티어</dt><dd><RoomRank game={room.game} tier={member.tier} division={member.division} size={iconSize} /></dd></div>
    <div><dt className="sr-only">{room.game === 'PUBG' ? '서버' : '포지션'}</dt><dd>{room.game === 'PUBG'
      ? <span className="room-random-role">{server === 'STEAM' ? '스팀' : server === 'KAKAO' ? '카카오' : '서버 미정'}</span>
      : positions
        ? member.roles.length ? <RoomRoles game={room.game} roles={member.roles} labels /> : <span className="room-random-role">포지션 미정</span>
        : <span className="room-random-role">무작위</span>}</dd></div>
    <div><dt>승률</dt><dd><Stat kind="winRate" value={member.winRate} /></dd></div>
    <div><dt>KDA</dt><dd><Stat kind="kda" value={member.kda} /></dd></div>
  </dl>;
}

/**
 * 방 카드 한 장 = 모집 글 하나(`BoardRoom`). 빈 자리(`capacity - memberCount`)마다 "참여" 버튼이 있다 — 자리에 포지션은 없다(입장할 때 고르지 않는다 · D-20 · P-18).
 * 찾는 포지션(`wantedPositions`)은 빈 자리에 같이 보여 준다. 끝난 글(`CONFIRMED` · `EXPIRED`)은 흐리게, 빈 자리 없이 방장 카드만 그린다.
 */
export function RoomDeck({ room, selfId, entering = false, onEntered, entryError, onSeat, onMember }: { room: BoardRoom; selfId: string; entering?: boolean; onEntered?: () => void; entryError: string | null; onSeat: (room: BoardRoom) => void; onMember: (room: BoardRoom, member: BoardMember) => void }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useLayoutEffect(() => {
    if (!entering) return;
    heading.current?.scrollIntoView({ block: 'nearest', behavior: 'instant' });
    heading.current?.focus({ preventScroll: true });
    const timer = window.setTimeout(() => onEntered?.(), 650);
    return () => window.clearTimeout(timer);
  }, [entering, onEntered]);
  const closed = room.status !== 'RECRUITING';
  const positions = hasPositions(room.game, room.modeKey);
  const members = room.members.length ? room.members : [room.host];
  const vacancies = closed ? 0 : Math.max(0, room.capacity - room.memberCount);
  return <article className={`room-deck room-compact${room.hostId === selfId ? ' is-own' : ''}${closed ? ' is-confirmed' : ''}${entering ? ' is-entering' : ''}`} data-status={room.status} aria-label={`${room.title} 방 정보`}>
    <RoomBubbleTail />
    <div className="compact-room-header" aria-label="방 요약">
      <h3 ref={heading} tabIndex={-1} title={room.title}>{room.title}</h3>
      <span className="room-status">{modeLabel(room.game, room.modeKey)}{room.perspective ? ` · ${room.perspective}` : ''}</span>
      {closed ? <span className="room-status is-confirmed">{POST_STATUS_LABEL[room.status]}</span> : <time dateTime={room.createdAt}>{relativeTime(room.createdAt)}</time>}
    </div>
    <div className="compact-members" aria-label="방 구성원 정보">
      {members.map(member => <div className="compact-member" key={member.id}>
        <button type="button" className="compact-member-name" aria-label={`${member.nickname} 프로필 보기`} onClick={() => onMember(room, member)}><RoomMemberAvatar member={member} size={24}/><strong title={member.nickname}>{member.nickname}</strong></button>
        <RoomMemberFacts room={room} member={member} />
        <div className="compact-member-champions" aria-label={`${member.nickname} ${room.game === 'LOL' ? '주 챔피언' : room.game === 'VALORANT' ? '선호 요원' : '선호 무기'}`}><PreferredChampions game={room.game} names={member.champions.slice(0, 3)}/>{room.game === 'LOL' ? Array.from({ length: Math.max(0, 3 - member.champions.length) }, (_, index) => <span className="compact-champion-empty" key={index} role="img" aria-label="챔피언 정보 없음" title="챔피언 정보 없음">—</span>) : null}</div>
      </div>)}
      {Array.from({ length: vacancies }, (_, index) => <button type="button" className="compact-member compact-seat" key={`seat-${index}`}
        disabled={Boolean(entryError)} title={entryError ?? undefined}
        aria-label={`빈 자리 참여${entryError ? ` · ${entryError}` : ''}`}
        onClick={() => onSeat(room)}>
        <span className="compact-seat-status">모집 중</span>
        <dl className="room-member-facts room-seat-facts">
          <div><dt className="sr-only">찾는 포지션</dt><dd>{positions ? <RoomRoles game={room.game} roles={room.wantedPositions} labels /> : <span className="room-random-role">{room.game === 'PUBG' ? (room.perspective ?? '무작위') : '무작위'}</span>}</dd></div>
          <div><dt className="sr-only">인원</dt><dd><span className="room-random-role">{room.memberCount} / {room.capacity}명</span></dd></div>
        </dl>
        <span className="room-seat-voice"><RoomVoice value={room.voice} /><span>{room.voice === 'REQUIRED' ? '사용' : '미사용'}</span></span>
      </button>)}
    </div>
  </article>;
}
