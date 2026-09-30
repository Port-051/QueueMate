import { useLayoutEffect, useRef, useState } from 'react';
import { tierColor } from '../domain/rankAssets';
import type { GameKey, LolMostChampion } from '../api/types';
import { Avatar } from '../components/ui';
import { FilterModeIcon, FilterRoleIcon, FilterTierIcon, VoiceIcon } from '../components/FilterSymbols';
import { PerformanceValue } from '../components/IntroductionVisuals';
import { championName, championPortrait } from '../domain/champions';
import { keyConditionOptions } from '../domain/gameConfig';
import { TIER_LADDER_LABEL } from '../domain/gameCatalog';
import { modeChoice, modeChoiceLabel } from '../domain/modeChoice';
import { TIER_LABELS } from '../domain/recruitment';
import { relativeTime } from '../domain/time';
import { hasLolRankDivision } from '../domain/lolRank';
import { canonicalRoomRoles, hasPositions, ROOM_ROLES } from './summary';
import { roomVoice } from './voice';
import type { BoardMember, BoardRoom } from './types';

function RoomBubbleTail() {
  return <svg className="room-bubble-tail" width="48" height="48" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
    <path d="M0 4C0 19 4 27 12 32C17 36 17 40 13 44Q11 47 15 46C24 43 26 38 35 36H48V0H0Z" fill="var(--room-bubble-bg)" />
  </svg>;
}

const roleLabel = (game: GameKey, role: string) => keyConditionOptions(game).find(item => item.value === role)?.label ?? role;

/** 포지션 아이콘 줄. 비어 있거나 전부면 "모든 포지션". PUBG 는 포지션이 없다(`ROOM_ROLES.PUBG` 가 빈 배열) — 부르는 쪽이 가린다. */
export function RoomRoles({ game, roles, labels = false }: { game: GameKey; roles: string[]; labels?: boolean }) {
  const ordered = canonicalRoomRoles(game, roles);
  const shown = !ordered.length || ordered.length === ROOM_ROLES[game].length ? ['ANY'] : ordered;
  return <span className={`room-role-icons${labels ? ' has-labels' : ''}`}>{shown.map(role => {
    const label = role === 'ANY' ? '모든 포지션' : roleLabel(game, role);
    return <span key={role} title={label} role="img" aria-label={label}><FilterRoleIcon game={game} value={role} size={19} />{labels ? <b>{role === 'ANY' ? '전체' : label}</b> : null}</span>;
  })}</span>;
}

/**
 * 좌석 두 줄째에 쓰는 짧은 티어 이름 — 게이머들이 흔히 줄여 부르는 말(`플래` · `에메` · `다이아` · `그마`). 나머지는 그대로다. 마우스를 올린 작은 창 · 프로필 창 · 좌석 버튼의 이름은 원래 이름이다.
 * 좌석 폭(228px)에 티어 · 승률 · KDA 가 한 줄에 들게 하려는 것이다(Claude 가 정한 세부 — 2026-09-30).
 */
const SHORT_TIER_LABELS: Record<string, string> = { PLATINUM: '플래', EMERALD: '에메', DIAMOND: '다이아', GRANDMASTER: '그마' };

/** 티어 글자 — `골드 II`(LoL 은 로마 숫자 · 마스터 이상은 단계 없음) · `골드 2`(VALORANT) · 없으면 `—`. `short` 면 좌석용 짧은 이름. */
export function rankText(game: GameKey, tier: string | null, division: number | null, short = false): string {
  if (!tier) return '—';
  const suffix = division ? game === 'LOL' ? hasLolRankDivision(tier) ? ['', 'I', 'II', 'III', 'IV'][division] : '' : String(division) : '';
  return `${(short ? SHORT_TIER_LABELS[tier] : undefined) ?? TIER_LABELS[tier] ?? tier}${suffix ? ` ${suffix}` : ''}`;
}

/** 티어 하나. `ladderLabel` 은 그 티어가 온 사다리(`솔로랭크` · `랭크` …) — 풍선말(`title`)로만 보여 준다. */
export function RoomRank({ game, tier, division, size = 30, ladderLabel }: { game: GameKey; tier: string | null; division: number | null; size?: number; ladderLabel?: string }) {
  const text = rankText(game, tier, division);
  return <span className="room-rank" title={ladderLabel ? `${ladderLabel} · ${tier ? text : '언랭'}` : undefined}><FilterTierIcon game={game} tier={tier} size={size} /><strong style={{ color: tierColor(tier) }}>{text}</strong></span>;
}

const formatStat = (kind: 'winRate' | 'kda', value: number) => kind === 'winRate' ? `${Number.isInteger(value) ? value : value.toFixed(1)}%` : value.toFixed(2);

/** PUBG 의 치킨률은 승률의 색 구간(45~60%)에 맞지 않아 색 없이 그린다 — 구간은 정하지 않았다. */
function Stat({ kind, value, plain = false }: { kind: 'winRate' | 'kda'; value: number | null; plain?: boolean }) {
  if (value === null) return <strong className="room-unknown-stat">—</strong>;
  if (plain) return <strong className="performance-value">{formatStat(kind, value)}</strong>;
  return <PerformanceValue kind={kind} value={value} />;
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

/**
 * 사람의 사실 — 티어 · 승률 · KDA, PUBG 는 티어 · 서버 · 치킨률 · K/D 넷. 전부 게임 프로필(`profile`)에서 온다 — VALORANT 의 전적은 아직 없어 `—` 다.
 * **게시판 카드에는 더 쓰지 않는다**(2026-09-30 좌석 줄 — 카드는 `RoomSeat` 의 두 줄이다). 좌석을 눌렀을 때의 프로필 창(`RoomMemberProfile`)과 방 화면의 파티원 목록이 쓴다.
 * **티어는 그 글의 모드의 사다리 티어**이고 사다리가 없는 모드면 그 사람의 가장 높은 티어다(2026-09-29 — `rooms/boardRoom.ts` `toBoardMember`). 어느 사다리인지는 풍선말로.
 * **사람별 포지션 칸은 없다**(2026-09-29 소유자 결정 — 게임 계정에서 주 포지션 · 주 역할군을 없앴다). 그래서 LoL · VALORANT 는 티어가 한 줄을 다 쓴다(`is-wide`).
 * **방장만 예외다 — 글의 `hostPosition`(방장이 글을 쓸 때 고른 자기 포지션 · 2026-09-30 소유자 결정)이 있으면 티어 옆에 그 포지션 아이콘 + 이름**이 붙는다
 * (티어 칸이 반으로 줄고 옆 반에 선다 — PUBG 의 티어 · 서버와 같은 모양). 게임 계정의 값이 아니라 글의 값이라 방장 한 사람만이다.
 */
export function RoomMemberFacts({ room, member, iconSize = 22 }: { room: BoardRoom; member: BoardMember; iconSize?: number }) {
  const pubg = room.game === 'PUBG';
  const server = member.profile?.server;
  const ladderLabel = member.tierLadder ? TIER_LADDER_LABEL[member.tierLadder] : undefined;
  const hostPosition = member.host && !pubg && hasPositions(room.game, room.modeKey) ? room.hostPosition : null;
  return <dl className="room-member-facts">
    <div className={pubg || hostPosition ? undefined : 'is-wide'}><dt className="sr-only">{ladderLabel ? `${ladderLabel} 티어` : '티어'}</dt><dd><RoomRank game={room.game} tier={member.tier} division={member.division} size={iconSize} ladderLabel={ladderLabel} /></dd></div>
    {hostPosition ? <div className="room-host-position"><dt className="sr-only">{room.game === 'VALORANT' ? '방장의 역할' : '방장의 포지션'}</dt><dd><RoomRoles game={room.game} roles={[hostPosition]} labels /></dd></div> : null}
    {pubg ? <div><dt className="sr-only">서버</dt><dd><span className="room-random-role">{server === 'STEAM' ? '스팀' : server === 'KAKAO' ? '카카오' : '서버 미정'}</span></dd></div> : null}
    <div><dt>{pubg ? '치킨' : '승률'}</dt><dd><Stat kind="winRate" value={member.winRate} plain={pubg} /></dd></div>
    <div><dt>{pubg ? 'K/D' : 'KDA'}</dt><dd><Stat kind="kda" value={member.kda} /></dd></div>
  </dl>;
}

const SERVER_LABEL = { STEAM: '스팀', KAKAO: '카카오' } as const;
const finite = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;

/** 좌석 두 줄째의 숫자 — LoL · VALORANT 승률 · KDA, PUBG 치킨률 · K/D. 없는 값은 빼고 둘 다 없으면 빈 배열(그 줄은 티어만). */
function seatNumbers(game: GameKey, member: BoardMember): { label: string; text: string }[] {
  const pubg = game === 'PUBG';
  return [
    member.winRate !== null ? { label: pubg ? '치킨률' : '승률', text: formatStat('winRate', member.winRate) } : null,
    member.kda !== null ? { label: pubg ? 'K/D' : 'KDA', text: formatStat('kda', member.kda) } : null,
  ].filter((item): item is { label: string; text: string } => item !== null);
}

/** 글의 방장 포지션 — 방장 좌석에만, 포지션이 있는 모드에서만(2026-09-30 소유자 결정 — 게임 계정이 아니라 글의 값이다). */
const seatHostPosition = (room: BoardRoom, member: BoardMember) => member.host && hasPositions(room.game, room.modeKey) ? room.hostPosition : null;

function VerifiedMark() {
  return <span className="room-seat-verified" role="img" aria-label="인증됨" title="인증됨">
    <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m12 1.5 2.6 1.9 3.2-.1 1 3 2.6 1.9-1 3.1 1 3.1-2.6 1.9-1 3-3.2-.1L12 22.5l-2.6-1.9-3.2.1-1-3-2.6-1.9 1-3.1-1-3.1 2.6-1.9 1-3 3.2.1L12 1.5Z" fill="currentColor" /><path d="m8 12.2 2.8 2.8L16.3 9.4" fill="none" stroke="#10121c" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" /></svg>
  </span>;
}

function ChampionFace({ id }: { id: string }) {
  const name = championName(id) ?? id;
  const src = championPortrait(id);
  const [failed, setFailed] = useState(false);
  return src && !failed
    ? <img className="room-pop-portrait" src={src} alt="" width="26" height="26" loading="lazy" decoding="async" onError={() => setFailed(true)} />
    : <span className="room-pop-portrait is-fallback">{Array.from(name.trim())[0] ?? '?'}</span>;
}

/**
 * 좌석에 마우스를 올리면 뜨는 작은 창(마우스가 있는 화면에서만 — CSS `hover: hover`). 좌석 두 줄에 다 못 싣는 것을 싣는다 —
 * 게임 닉네임 · 인증 · 사다리 · 전적 · 판 수 · **LoL 숙련도 높은 챔피언 셋**(P-39 — 좌석 줄로 바꾸며 카드에서 여기로 옮겼다) · PUBG 서버 · 평균 딜.
 * 좌석을 누르면 여는 프로필 창(`RoomMemberProfile`)과 같은 사실이라 읽어 주지 않는다(`aria-hidden`) — 좌석 버튼의 이름이 요약을 싣는다.
 */
function SeatPopover({ room, member }: { room: BoardRoom; member: BoardMember }) {
  const profile = member.profile;
  const stats = profile?.stats ?? null;
  const pubg = room.game === 'PUBG';
  const ladderLabel = member.tierLadder ? TIER_LADDER_LABEL[member.tierLadder] : '티어';
  const mostChampions = room.game === 'LOL' ? stats?.detail?.mostChampions : null;
  const champions: LolMostChampion[] = Array.isArray(mostChampions) ? mostChampions.slice(0, 3) : [];
  const avgDamage = pubg ? finite(stats?.detail?.avgDamage) : null;
  const hostPosition = seatHostPosition(room, member);
  return <span className="room-seat-popover" aria-hidden="true">
    <span className="room-pop-head">
      <b>{member.nickname}</b>
      {member.host ? <em>방장{hostPosition ? ` · ${roleLabel(room.game, hostPosition)}` : ''}</em> : null}
    </span>
    <span className="room-pop-sub">{profile
      ? [profile.gameNickname, profile.verified ? '인증됨' : null, pubg && profile.server ? SERVER_LABEL[profile.server] : null].filter(Boolean).join(' · ')
      : '이 게임의 계정을 아직 연결하지 않았어요'}</span>
    {profile ? <span className="room-pop-facts">
      <span><small>{ladderLabel}</small><RoomRank game={room.game} tier={member.tier} division={member.division} size={18} /></span>
      <span><small>{pubg ? '치킨률' : '승률'}</small><Stat kind="winRate" value={member.winRate} plain={pubg} /></span>
      <span><small>{pubg ? 'K/D' : 'KDA'}</small><Stat kind="kda" value={member.kda} /></span>
      {stats ? <span><small>판 수</small><strong className="performance-value">{stats.games}</strong></span> : null}
      {avgDamage !== null ? <span><small>평균 딜</small><strong className="performance-value">{Math.round(avgDamage)}</strong></span> : null}
    </span> : null}
    {champions.length ? <span className="room-pop-champions">
      <small>숙련도 높은 챔피언</small>
      {champions.map(champion => {
        const level = finite(champion.masteryLevel);
        return <span className="room-pop-champion" key={champion.championId}>
          <ChampionFace id={champion.championId} />
          <span>{championName(champion.championId) ?? champion.championId}</span>
          {level !== null ? <small>숙련도 {level}</small> : null}
        </span>;
      })}
    </span> : null}
    {profile && !stats ? <span className="room-pop-note">전적 정보 없음</span> : null}
  </span>;
}

/**
 * 채워진 좌석 하나 — 얼굴(방장이면 왕관) · 닉네임(+ 인증 표시) · 방장이면 글의 방장 포지션 · 두 줄째에 그 글의 사다리 티어와 승률 · KDA(좌석이 좁으면 티어만 — 좌석이 제 폭을 보고 고른다, CSS).
 * 누르면 프로필 창이다(전파를 끊는다 — 참가로 번지지 않게). 카드가 좁으면(폰 폭) 원 + 티어 배지 + 아래 닉네임 한 줄로 바뀐다(CSS 컨테이너 질의).
 */
function RoomSeat({ room, member, selfId, popEnd, onMember }: { room: BoardRoom; member: BoardMember; selfId: string; popEnd: boolean; onMember: (room: BoardRoom, member: BoardMember) => void }) {
  const hostPosition = seatHostPosition(room, member);
  const numbers = seatNumbers(room.game, member);
  const ladderLabel = member.tierLadder ? TIER_LADDER_LABEL[member.tierLadder] : undefined;
  const tier = member.profile ? member.tier ? rankText(room.game, member.tier, member.division) : '언랭' : '게임 계정 미연결';
  const label = [
    member.nickname,
    member.id === selfId ? '나' : null,
    member.host ? '방장' : null,
    hostPosition ? roleLabel(room.game, hostPosition) : null,
    member.profile?.verified ? '인증됨' : null,
    ladderLabel && member.tier ? `${ladderLabel} ${tier}` : tier,
    ...numbers.map(item => `${item.label} ${item.text}`),
  ].filter(Boolean).join(' · ');
  return <li className={`room-seat is-filled${member.host ? ' is-host' : ''}${member.id === selfId ? ' is-self' : ''}${popEnd ? ' pop-end' : ''}`}>
    <button type="button" className="room-seat-button" aria-label={`${label} — 프로필 보기`} onClick={event => { event.stopPropagation(); onMember(room, member); }}>
      <span className="room-seat-face">
        <RoomMemberAvatar member={member} size={34} />
        <span className="room-seat-tier-badge"><FilterTierIcon game={room.game} tier={member.tier} size={16} /></span>
      </span>
      <span className="room-seat-text">
        <span className="room-seat-name">
          {hostPosition ? <span className="room-seat-position-icon"><FilterRoleIcon game={room.game} value={hostPosition} size={12} /></span> : null}
          <strong>{member.nickname}</strong>
          {member.profile?.verified ? <VerifiedMark /> : null}
          {hostPosition ? <span className="room-seat-position"><FilterRoleIcon game={room.game} value={hostPosition} size={15} /><b>{roleLabel(room.game, hostPosition)}</b></span> : null}
        </span>
        <span className="room-seat-line">
          <span className="room-seat-rank"><FilterTierIcon game={room.game} tier={member.tier} size={16} /><span style={member.tier ? { color: tierColor(member.tier) } : undefined}>{member.profile ? rankText(room.game, member.tier, member.division, true) : '—'}</span></span>
          {numbers.length ? <span className="room-seat-numbers">{numbers.map(item => <span key={item.label} title={item.label}>{item.text}</span>)}</span> : null}
        </span>
      </span>
    </button>
    <SeatPopover room={room} member={member} />
  </li>;
}

/**
 * 방 카드 한 장 = 모집 글 하나(`BoardRoom`) — **좌석 줄**(2026-09-30 소유자 승인 — 사람마다 큰 카드 · 빈 자리마다 같은 조건을 되풀이하던 큰 점선 카드를 걷었다).
 *
 * - **머리 한 줄** — 제목 · 상태 점과 글자(모집 중 / 확정 · n명 / 만료) · 몇 분 전. **조건은 그 아래 한 줄에 한 번만** — 모드 · 인원 · 마이크 · 찾는 포지션(포지션이 있는 모드만).
 * - **좌석 줄** — 채워진 좌석(`RoomSeat` — 서버 순서 그대로 · 방장 먼저)이 같은 폭으로 서서 카드끼리 줄이 맞고, 빈 자리는 글자 없는 작은 점선 원(`빈자리` 풍선말)이다.
 *   그 뒤 `n/정원` 한 번과 **[참가]** — 누르면 참여 확인 창(`RoomJoinConfirm`)이다. 들어갈 수 없으면(`entryError` — 정원 · 이미 참여 · 다른 방 등) 버튼이 잠기고 이유가 풍선말 · 이름에 붙는다.
 *   빈 원을 눌러도 같은 창이 뜬다(마우스 지름길 — 키보드 · 화면 읽기는 [참가] 하나다).
 * - **확정된 글**은 확정 순간의 파티원 전원(P-40 — `members` = 파티원 · `memberCount` = 파티 인원)이 좌석이고 빈 원 · [참가] 가 없다(머리는 `확정 · n명`).
 *   **만료된 글**은 방장 좌석만(서버가 `members` 를 비워 보낸다). 둘 다 흐리게 그린다.
 * - 카드가 600px 보다 좁으면(폰 폭 · 좁은 분할 화면) 좌석이 원 다섯 칸(얼굴 원 + 티어 배지 + 아래 닉네임)이 되고 `n/정원` · [참가] 는 그 아래 줄로 간다 —
 *   숫자 · 챔피언은 프로필 창(눌러서)에만 있다(CSS 컨테이너 질의 — `room-board.css` "좌석 줄").
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
  const recruiting = room.status === 'RECRUITING';
  const positions = hasPositions(room.game, room.modeKey);
  // 확정된 글은 파티원 전원(P-40), 만료된 글은 방장만 — 서버가 만료된 글의 `members` 를 비워 보낸다.
  const members = room.members.length ? room.members : [room.host];
  const vacancies = recruiting ? Math.max(0, room.capacity - room.memberCount) : 0;
  const voice = roomVoice(room.voice);
  const group = modeChoice(room.game, room.modeKey)?.group ?? room.modeKey;
  const status = room.status === 'CONFIRMED' ? `${POST_STATUS_LABEL.CONFIRMED} · ${room.memberCount || members.length}명` : POST_STATUS_LABEL[room.status];
  const openSeat = entryError ? undefined : () => onSeat(room);
  // 내가 이미 들어가 있는 방이면 잠긴 버튼의 글자를 "참여 중" 으로(이유는 `boardRoom.ts` `roomEntryError`).
  const inside = recruiting && members.some(member => member.id === selfId);
  return <article className={`room-deck room-row${room.hostId === selfId ? ' is-own' : ''}${recruiting ? '' : ' is-closed'}${entering ? ' is-entering' : ''}`} data-status={room.status} aria-label={`${room.title} 방 정보`}>
    <RoomBubbleTail />
    <div className="room-row-head">
      <h3 ref={heading} tabIndex={-1} title={room.title}>{room.title}</h3>
      <p className="room-row-state"><span className="room-row-status" data-status={room.status}><i aria-hidden="true" />{status}</span><time dateTime={room.createdAt}>{relativeTime(room.createdAt)}</time></p>
    </div>
    <p className="room-row-meta" aria-label="방 조건">
      <span className="room-row-mode"><FilterModeIcon mode={group} size={16} />{modeChoiceLabel(room.game, room.modeKey, room.perspective)}</span>
      <span className={`room-row-voice ${voice === 'REQUIRED' ? 'is-on' : 'is-off'}`}><VoiceIcon preference={voice} size={16} />{voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'}</span>
      {positions ? <span className="room-row-wanted"><span className="room-row-wanted-label">찾는 포지션</span><RoomRoles game={room.game} roles={room.wantedPositions} labels /></span> : null}
    </p>
    <div className="room-seat-row">
      <ul className="room-seats" aria-label={recruiting ? `자리 ${room.memberCount} / ${room.capacity}` : `파티원 ${members.length}명`}>
        {members.map((member, index) => <RoomSeat key={member.id} room={room} member={member} selfId={selfId} popEnd={index >= 3} onMember={onMember} />)}
        {Array.from({ length: vacancies }, (_, index) => <li className="room-seat is-empty" key={`seat-${index}`}>
          <span className={`room-seat-hole${openSeat ? ' is-open' : ''}`} title="빈자리" aria-hidden="true" onClick={openSeat} />
          <span className="sr-only">빈자리</span>
        </li>)}
      </ul>
      {recruiting ? <div className="room-seats-tail">
        <span className="room-seat-count" aria-hidden="true">{room.memberCount}/{room.capacity}</span>
        <button type="button" className="room-join-button" disabled={Boolean(entryError)} title={entryError ?? undefined}
          aria-label={inside ? '참여 중' : `참가${entryError ? ` · ${entryError}` : ''}`} onClick={() => onSeat(room)}>{inside ? '참여 중' : '참가'}</button>
      </div> : null}
    </div>
  </article>;
}
