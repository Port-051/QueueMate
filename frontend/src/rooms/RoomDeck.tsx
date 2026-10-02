import './mentor-room.css';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { tierColor } from '../domain/rankAssets';
import type { GameKey, GameStats, LolMostChampion } from '../api/types';
import { Avatar } from '../components/ui';
import { FilterModeIcon, FilterRoleIcon, FilterTierIcon, VoiceIcon } from '../components/FilterSymbols';
import { PerformanceValue } from '../components/IntroductionVisuals';
import { KdaStat, kdaLine, KdStat, kdLine } from '../components/KdaStat';
import { RecentResults, recentRecord } from '../components/RecentResults';
import { WinLossBar, winLossRecord } from '../components/WinLossBar';
import { championName, championPortrait } from '../domain/champions';
import { keyConditionOptions } from '../domain/gameConfig';
import { TIER_LADDER_LABEL } from '../domain/gameCatalog';
import { modeChoice, modeChoiceLabel } from '../domain/modeChoice';
import { TIER_LABELS } from '../domain/recruitment';
import { relativeTime } from '../domain/time';
import { hasLolRankDivision } from '../domain/lolRank';
import { canonicalRoomRoles, hasPositions, ROOM_ROLES } from './summary';
import { roomVoice } from './voice';
import { remainingPositions } from './boardRoom';
import { boardRoomColors } from './roomColors';
import type { BoardMember, BoardRoom } from './types';

export function RoomWantedPositions({ room }: { room: BoardRoom }) {
  if (room.status !== 'RECRUITING') return <span>모집 마감</span>;
  const remaining = remainingPositions(room);
  return room.wantedPositions.length && !remaining.length ? <span>빈 포지션 없음</span> : <RoomRoles game={room.game} roles={remaining} labels />;
}

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
/** 확정된 글의 파티가 닫혔을 때(platform P-46 `closed`) "확정" 대신 쓰는 글자(2026-10-01 소유자 결정). 흐림 · 참가 없음은 확정과 같다. */
export const POST_ENDED_LABEL = '끝남';

/** 얼굴 위 방장 왕관 — 게시판 좌석 · 프로필 창 · 방 화면의 음성 칸 좌석(`RoomVoiceSeats` — 글의 카드가 없는 자동 매칭 방도)이 같이 쓴다. */
export function RoomHostCrown() {
  return <span className="room-host-crown" role="img" aria-label="방장" title="방장">
    <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="m3 6 5 4 4-7 4 7 5-4-2 12H5L3 6Zm2 14h14v2H5v-2Z" /></svg>
  </span>;
}

/** 좌석 · 프로필 창의 얼굴(+ 방장 왕관). `color` 는 그 방의 색(`boardRoomColors` — 한 방은 모두 다른 색) · 없으면 그 사람의 집 색이다. */
export function RoomMemberAvatar({ member, size, color }: { member: BoardMember; size: number; color?: number }) {
  return <span className="room-member-avatar">
    <Avatar userId={member.id} name={member.nickname} color={color} size={size} />
    {member.host ? <RoomHostCrown /> : null}
  </span>;
}

/**
 * 사람의 사실 — 티어 · 승률 · KDA, PUBG 는 티어 · 서버(계정이 있을 때) · 치킨률 · K/D. 전부 게임 프로필(`profile`)에서 온다 — VALORANT 의 전적은 아직 없어 `—` 다.
 * **게시판 카드에는 더 쓰지 않는다**(2026-09-30 좌석 줄 — 카드는 `RoomSeat` 의 두 줄이다). 좌석을 눌렀을 때의 프로필 창(`RoomMemberProfile`)만 쓴다
 * (방 화면의 파티원 목록도 쓰다가 같은 날 음성 칸의 좌석 줄이 됐다 — `RoomVoiceSeats`).
 * **티어는 그 글의 모드의 사다리 티어**이고 사다리가 없는 모드면 그 사람의 가장 높은 티어다(2026-09-29 — `rooms/boardRoom.ts` `toBoardMember`). 어느 사다리인지는 풍선말로.
 * **게임 계정의 포지션 칸은 없다**(2026-09-29 소유자 결정 — 게임 계정에서 주 포지션 · 주 역할군을 없앴다). 그래서 포지션이 없으면 LoL · VALORANT 는 티어가 한 줄을 다 쓴다(`is-wide`).
 * **이 방에서의 포지션(`seatPosition` — 방장은 글의 `hostPosition`, 멤버는 참가할 때 고른 것 · 2026-10-01 소유자 결정 — platform P-44 ⑨. 그 전에는 방장만 — 2026-09-30)이 있으면 티어 옆에 그 포지션 아이콘 + 이름**이 붙는다
 * (티어 칸이 반으로 줄고 옆 반에 선다 — PUBG 의 티어 · 서버와 같은 모양). 확정된 방에서는 붙이지 않는다.
 * **`opgg` 를 켜면(프로필 창만 — 2026-09-30 소유자 지시) 전적을 OP.GG 모양으로** — 승 · 패가 있으면(LoL 솔로랭크 시즌 누적) 승률 칸 대신 **승 · 패 막대**(`WinLossBar`)가 한 줄을 다 쓰고
 * (KDA 는 티어 옆 반으로 올라가고 · 포지션이 그 자리를 쓰면 막대 밑 한 줄 · 승률 숫자는 막대 뒤 하나뿐), 평균 킬 · 데스 · 어시스트와 KDA 가 다 있으면 KDA 칸이 **두 줄**(`KdaStat`)이다.
 * 값이 모자라면(VALORANT · 언랭 · 데스 0) 그 칸은 전과 같다. **PUBG 는 K/D 칸이 두 줄**(`KdStat` — 평균 킬 / 데스 위 · K/D 아래 — 같은 날 소유자)이고 막대는 없다(승 · 패가 없다).
 * PUBG 의 서버 칸은 게임 계정이 있을 때만이다(계정이 없는 사람에게 "서버 미정" 을 그리던 것을 걷었다 — 같은 날 소유자). 치킨 칸의 이름은 좌석 · 작은 창과 같은 "치킨률" 이다.
 * **LoL 은 `opgg` 일 때 맨 아래 한 줄에 최근 경기의 승 · 패 칸**(`RecentResults` — duo.gg 의 `7승 3패 (10 게임)` + 칸 줄 · platform P-43 · 같은 날 소유자)이다.
 * 그날 전의 스냅숏(칸이 없다)이면 그리지 않고 프로필 창 한 줄의 `최근 10판` 이 전처럼 남는다(`RoomMemberProfile`).
 */
export function RoomMemberFacts({ room, member, iconSize = 22, opgg = false }: { room: BoardRoom; member: BoardMember; iconSize?: number; opgg?: boolean }) {
  const pubg = room.game === 'PUBG';
  const server = pubg ? member.profile?.server : null;
  const ladderLabel = member.tierLadder ? TIER_LADDER_LABEL[member.tierLadder] : undefined;
  const position = pubg ? null : seatPosition(room, member);
  const record = opgg && !pubg ? winLossRecord(member.profile?.stats) : null;
  const kdaDetail = opgg && !pubg ? kdaLine(member.profile?.stats) : null;
  const kdDetail = opgg && pubg ? kdLine(member.profile?.stats) : null;
  const recent = opgg && room.game === 'LOL' ? recentRecord(member.profile?.stats) : null;
  const kda = <div className={record && position ? 'is-wide' : undefined}><dt>{pubg ? 'K/D' : 'KDA'}</dt><dd>{kdaDetail ? <KdaStat line={kdaDetail} size="lg" /> : kdDetail ? <KdStat line={kdDetail} size="lg" /> : <Stat kind="kda" value={member.kda} />}</dd></div>;
  const bar = record ? <div className="is-wide"><dt>승률</dt><dd><WinLossBar record={record} rate={member.winRate} size="lg" /></dd></div> : null;
  return <dl className="room-member-facts">
    <div className={server || position || record ? undefined : 'is-wide'}><dt className="sr-only">{ladderLabel ? `${ladderLabel} 티어` : '티어'}</dt><dd><RoomRank game={room.game} tier={member.tier} division={member.division} size={iconSize} ladderLabel={ladderLabel} /></dd></div>
    {position ? <div className="room-member-position"><dt className="sr-only">{room.game === 'VALORANT' ? '역할' : '포지션'}</dt><dd><RoomRoles game={room.game} roles={[position]} labels /></dd></div> : null}
    {server ? <div><dt className="sr-only">서버</dt><dd><span className="room-random-role">{SERVER_LABEL[server]}</span></dd></div> : null}
    {record
      ? position ? <>{bar}{kda}</> : <>{kda}{bar}</>
      : <><div><dt>{pubg ? '치킨률' : '승률'}</dt><dd><Stat kind="winRate" value={member.winRate} plain={pubg} /></dd></div>{kda}</>}
    {recent ? <div className="is-wide"><dt>최근 경기</dt><dd><RecentResults record={recent} size="lg" /></dd></div> : null}
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

/**
 * 좌석 · 작은 창 · 프로필 창에 붙이는 그 사람의 포지션 — 이 방에서의 값이다(`BoardMember.position` — 방장은 글의 방장 포지션, 멤버는 참가할 때 고른 것 · 2026-10-01 소유자 결정 — platform P-44 ⑨ · 방 안 목록은 ⑩.
 * 그 전에는 방장 좌석에만 글의 `hostPosition` 을 붙였다 — 2026-09-30). 게임 계정의 값이 아니다. 포지션이 있는 모드에서만 · 안 골랐으면 붙이지 않는다.
 * **확정된 방에서는 붙이지 않는다**(소유자 — "확정 뒤 굳이 보여줄 필요 없다" · 서버도 확정된 글의 카드에는 `null` 을 보낸다).
 * 방 화면의 음성 칸 좌석은 방 안 사람 목록(`GET …/members`)의 포지션을 넣고, 확정이면 방 화면이 `null` 로 넣는다(`PartyRoomPage` — 글의 `status` 가 늦게 바뀔 수 있어서).
 * **빠른매치 파티(`quickMatch` — 제안 화면 · 빠른매치 방)는 늘 붙인다**(2026-10-01 소유자 결정 — platform P-47 "닉네임 · 게임 프로필 · 고른 포지션"). 처음부터 확정인 파티지만
 * 그 포지션은 빠른매치에서 고른 조건이고 파티가 그것으로 짜였다.
 */
export const seatPosition = (room: BoardRoom, member: BoardMember): string | null =>
  hasPositions(room.game, room.modeKey) ? member.position : null;

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

/** PUBG 전적이 무엇을 합산했나(`stats.detail.seasonMode`) — 값 목록이 계약에 없어 아는 둘만 옮긴다(내 정보 `SEASON_MODE_LABEL` 과 같은 둘). */
const PUBG_SEASON_MODE_LABEL: Record<string, string> = { RANKED: '랭크', NORMAL: '일반' };

/**
 * 판 수(`stats.games`)를 뜻에 맞는 이름으로 — 작은 창의 칸(`label` 위 · `value` 아래)과 프로필 창 한 줄(`gamesText`)이 같이 쓴다.
 * - **LoL `최근` · `10판`**(2026-09-30 소유자 결정 — "판 수 10" → "최근 10판"). LoL 의 `games` 는 전적을 긁을 때 읽은 최근 경기 수(기본 10 — platform P-13)라
 *   바로 위 승 · 패 막대(솔로랭크 **시즌 누적**)와 헷갈렸다.
 * - **PUBG `이번 시즌` · `랭크 10판`**(랭크가 0 판이면 서버가 일반 모드를 합산한다 → `일반 10판` — P-12 · P-36). 치킨률 · K/D · 평균 딜이 같은 판에서 나온다. 이름은 Claude 가 정한 세부다.
 * - 그 밖(VALORANT — `stats` 가 늘 `null` 이다) `판 수` · `10판`.
 * **LoL 은 최근 경기의 승 · 패(`recentResults` — P-43)가 있으면 이 칸 대신 승 · 패 칸 줄이다**(`RecentResults`) — 이 칸은 그날 전의 스냅숏에만 남는다.
 */
function gamesFact(game: GameKey, stats: GameStats): { label: string; value: string } {
  if (game === 'LOL') return { label: '최근', value: `${stats.games}판` };
  if (game === 'PUBG') {
    const mode = stats.detail?.seasonMode;
    const source = typeof mode === 'string' ? PUBG_SEASON_MODE_LABEL[mode] : undefined;
    return { label: '이번 시즌', value: `${source ? `${source} ` : ''}${stats.games}판` };
  }
  return { label: '판 수', value: `${stats.games}판` };
}

/** 프로필 창 한 줄의 판 수 — LoL `최근 10판` · PUBG `이번 시즌 랭크 10판` · 그 밖 `10판`(`gamesFact`). */
export function gamesText(game: GameKey, stats: GameStats): string {
  const fact = gamesFact(game, stats);
  return game === 'LOL' || game === 'PUBG' ? `${fact.label} ${fact.value}` : fact.value;
}

/**
 * 좌석에 마우스를 올리면 뜨는 작은 창(마우스가 있는 화면에서만 — CSS `hover: hover`). 좌석 두 줄에 다 못 싣는 것을 싣는다 —
 * 게임 닉네임 · 인증 · 사다리 · 전적 · 판 수(뜻에 맞는 이름 — `gamesFact`) · **LoL 숙련도 높은 챔피언 셋**(P-39 — 좌석 줄로 바꾸며 카드에서 여기로 옮겼다) · PUBG 서버 · 평균 딜.
 * **전적은 OP.GG 모양이다**(2026-09-30 소유자 지시) — LoL 은 승 · 패가 있으면 승률 칸 대신 **승 · 패 막대**(`WinLossBar`)가 한 줄을 다 쓰고(티어 · KDA 한 줄 → 막대 → 최근 10판),
 * 평균 킬 · 데스 · 어시스트와 KDA 가 다 있으면 KDA 칸이 **두 줄**(`KdaStat` — `7.2 / 7.6 / 5.4` 위 · `1.66` 아래)이다. 값이 모자라면(VALORANT · 언랭) 전과 같다.
 * **LoL 의 `최근 10판` 칸은 최근 경기의 승 · 패 칸 줄이 됐다**(같은 날 소유자 — duo.gg 처럼 `최근 경기 7승 3패 (10 게임)` + 경기마다 `승` · `패` 칸 · 왼쪽이 가장 최근 · 막대처럼 한 줄을 다 쓴다 — `RecentResults` · platform P-43).
 * 승 · 패 수는 그 배열에서 세고(막대의 시즌 누적 `wins` · `losses` 가 아니다) 그날 전의 스냅숏(칸이 없다)이면 전처럼 `최근` · `10판` 이다.
 * **PUBG 도 같은 모양이다**(같은 날 소유자) — 사다리 티어 | **K/D 두 줄**(`KdStat` — 평균 킬 / 데스 `1.8 / 1.1` 위 · K/D `1.64` 아래) → 치킨률 | 평균 딜 → 이번 시즌 판 수. 승 · 패가 없어 막대는 없다.
 * 좌석을 누르면 여는 프로필 창(`RoomMemberProfile`)과 같은 사실이라 읽어 주지 않는다(`aria-hidden`) — 좌석 버튼의 이름이 요약을 싣는다.
 * 방 화면의 음성 칸 좌석(`RoomVoiceSeats`)도 같은 창을 쓴다. 게시판만 `seatPopoverShown`을 따르고, 방 패널은 모든 게임에서 상세를 호버로 연다.
 */
export function SeatPopover({ room, member }: { room: BoardRoom; member: BoardMember }) {
  const profile = member.profile;
  const stats = profile?.stats ?? null;
  const pubg = room.game === 'PUBG';
  const ladderLabel = member.tierLadder ? TIER_LADDER_LABEL[member.tierLadder] : '티어';
  const mostChampions = room.game === 'LOL' ? stats?.detail?.mostChampions : null;
  const champions: LolMostChampion[] = Array.isArray(mostChampions) ? mostChampions.slice(0, 3) : [];
  const avgDamage = pubg ? finite(stats?.detail?.avgDamage) : null;
  const position = seatPosition(room, member);
  const role = [member.host ? '방장' : null, position ? roleLabel(room.game, position) : null].filter(Boolean).join(' · ');
  const record = pubg ? null : winLossRecord(stats);
  const kdaDetail = pubg ? null : kdaLine(stats);
  const kdDetail = pubg ? kdLine(stats) : null;
  const recent = room.game === 'LOL' ? recentRecord(stats) : null;
  const games = stats && !recent ? gamesFact(room.game, stats) : null;
  return <span className="room-seat-popover" aria-hidden="true">
    <span className="room-pop-head">
      <b>{member.nickname}</b>
      {role ? <em>{role}</em> : null}
    </span>
    <span className="room-pop-sub">{profile
      ? [profile.gameNickname, profile.verified ? '인증됨' : null, pubg && profile.server ? SERVER_LABEL[profile.server] : null].filter(Boolean).join(' · ')
      : '이 게임의 계정을 아직 연결하지 않았어요'}</span>
    {profile ? pubg ? <span className="room-pop-facts">
      <span><small>{ladderLabel}</small><RoomRank game={room.game} tier={member.tier} division={member.division} size={18} /></span>
      <span><small>K/D</small>{kdDetail ? <KdStat line={kdDetail} /> : <Stat kind="kda" value={member.kda} />}</span>
      <span><small>치킨률</small><Stat kind="winRate" value={member.winRate} plain /></span>
      {avgDamage !== null ? <span><small>평균 딜</small><strong className="performance-value">{Math.round(avgDamage)}</strong></span> : null}
      {games ? <span><small>{games.label}</small><strong className="performance-value">{games.value}</strong></span> : null}
    </span> : <span className="room-pop-facts">
      <span><small>{ladderLabel}</small><RoomRank game={room.game} tier={member.tier} division={member.division} size={18} /></span>
      {record ? null : <span><small>승률</small><Stat kind="winRate" value={member.winRate} /></span>}
      <span><small>KDA</small>{kdaDetail ? <KdaStat line={kdaDetail} /> : <Stat kind="kda" value={member.kda} />}</span>
      {record ? <span style={{ gridColumn: '1 / -1' }}><small>승률</small><WinLossBar record={record} rate={member.winRate} size="sm" /></span> : null}
      {recent ? <span className="room-pop-recent" style={{ gridColumn: '1 / -1' }}><RecentResults record={recent} label="최근 경기" /></span> : null}
      {games ? <span><small>{games.label}</small><strong className="performance-value">{games.value}</strong></span> : null}
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

/** 작은 창이 보이는 화면(뷰포트)의 위 · 아래 끝에서 띄울 여백(px). */
const SEAT_POP_SCREEN_MARGIN = 8;

/**
 * 작은 창(`SeatPopover`)을 좌석 아래로 열지 위로 열지(`data-pop-up`) — 마우스를 올릴 때 · 키보드로 들어올 때 한 번 잰다(열려 있는 동안 스크롤 · 창 크기로 다시 뒤집지 않는다).
 * ① **아래로 열면 페이지 끝을 넘으면 늘 위로**(2026-09-30 — "맨 밑줄 · 두 번째 줄의 프로필을 누르면 화면 전체가 흔들린다"). 아래로 열린 작은 창은 문서의 스크롤 길이를 늘린다(방 패널이 열린 1440×709 에서 맨 아래 줄 1448 → 1699px ·
 * 그 위 줄 1539px). 맨 아래까지 내린 채 좌석에 마우스가 있을 때 휠 한 칸이 그 늘어난 자리로 내려가면 좌석이 커서에서 벗어나 창이 닫히고 → 길이가 줄어 스크롤이 당겨지고 →
 * 좌석이 다시 커서 밑에 와 창이 열리면 → Chrome 의 스크롤 앵커링이 당겨진 만큼을 되돌린다. Windows Chrome 에서 잰 값 — **매 프레임 scrollY 가 739 ↔ 859 로 오갔다**(61프레임에 60번).
 * 문서에 `overflow-anchor: none` 을 걸면 멈췄다(원인 확인용으로만 — 목록이 바뀔 때 화면을 붙잡아 주는 앵커링을 통째로 끌 수는 없다). 위로 열면 창이 문서 끝을 넘지 않아
 * 마우스를 올려도 스크롤 길이가 그대로다. 위로도 모자라면(아주 낮은 창) 위쪽이 잘린다 — 문서 위쪽 너머는 스크롤 길이를 늘리지 않는다.
 * ② **아래로 열면 보이는 화면 아래 끝을 넘고 위로 열면 들어가면 위로**(같은 날 소유자 — 페이지가 아래로 더 이어지는 만료 카드에서 창이 화면 아래로 잘려 숙련도 챔피언 셋째가 안 보였다).
 * 위로도 화면 위 끝을 넘으면 덜 넘는 쪽(공간이 큰 쪽)이다. 아래로 열어도 페이지 끝 안이면 문서 길이가 그대로라 ①의 흔들림은 생기지 않는다.
 * 잴 때 창이 숨어 있으면(`display:none`) 보이지 않게 잠깐 펼친다 — 같은 작업 안이라 그려지지 않는다. 높이 · 간격은 CSS 가 정한 그대로 잰다(값을 여기에 베끼지 않는다).
 * React 상태가 아니라 속성을 바로 건다 — 창이 한 프레임이라도 반대쪽으로 그려지기 전에 방향이 정해져야 해서다(스크롤로 hover 가 옮겨 갈 때도 `mouseenter` 가 온다 — 확인했다).
 */
export function placeSeatPopover(seat: HTMLElement) {
  const popover = seat.querySelector<HTMLElement>('.room-seat-popover');
  if (!popover) return;
  const hidden = !popover.offsetHeight;
  if (hidden) popover.style.cssText = 'display:grid;visibility:hidden';
  // 레이아웃 값(`offsetTop` — 좌석 기준)으로 잰다 — 나타나는 움직임(transform)이 끼면 몇 px 모자라게 읽힌다.
  const seatTop = seat.getBoundingClientRect().top;
  seat.removeAttribute('data-pop-up');
  const downBottom = seatTop + popover.offsetTop + popover.offsetHeight;
  seat.setAttribute('data-pop-up', '');
  const upTop = seatTop + popover.offsetTop;
  if (hidden) popover.removeAttribute('style');
  const page = document.documentElement;
  const pageEnd = Math.max(page.getBoundingClientRect().bottom, page.clientHeight);
  const screenTop = SEAT_POP_SCREEN_MARGIN;
  const screenBottom = window.innerHeight - SEAT_POP_SCREEN_MARGIN;
  const up = downBottom > pageEnd
    || (downBottom > screenBottom && (upTop >= screenTop || screenTop - upTop < downBottom - screenBottom));
  seat.toggleAttribute('data-pop-up', up);
}

/**
 * 좌석에 마우스를 올린 작은 창을 띄우는가 — **VALORANT 글의 좌석에는 띄우지 않는다**(2026-09-30 소유자 결정 — "발로란트 그거는 일단 작은 창 안 뜨게 해"). 운영 키가 없어 VALORANT 의 `stats` 가 늘 `null` 이라
 * 창에 실을 것이 게임 닉네임 · 티어뿐이었다. 창을 그리지 않아 마우스 · 키보드 포커스 어느 쪽으로도 열리지 않는다(좌석 · 닉네임 · 누르면 여는 것은 그대로).
 * 게시판 좌석(`RoomSeat`)에만 적용한다. 방 패널은 2026-10-02 사용자 요청에 따라 모든 게임에서 상세 팝오버를 쓴다.
 */
export const seatPopoverShown = (game: GameKey) => game !== 'VALORANT';

/** 좌석 버튼의 이름(읽어 주는 요약) — 닉네임 · 나 · 방장 · 포지션 · 인증 · 사다리 티어 · 숫자. 작은 창은 읽어 주지 않아 이것이 요약을 싣는다. */
export function seatSummary(room: BoardRoom, member: BoardMember, selfId: string): string {
  const position = seatPosition(room, member);
  const ladderLabel = member.tierLadder ? TIER_LADDER_LABEL[member.tierLadder] : undefined;
  const tier = member.profile ? member.tier ? rankText(room.game, member.tier, member.division) : '언랭' : '게임 계정 미연결';
  return [
    member.nickname,
    member.id === selfId ? '나' : null,
    member.host ? '방장' : null,
    position ? roleLabel(room.game, position) : null,
    member.profile?.verified ? '인증됨' : null,
    ladderLabel && member.tier ? `${ladderLabel} ${tier}` : tier,
    ...seatNumbers(room.game, member).map(item => `${item.label} ${item.text}`),
  ].filter(Boolean).join(' · ');
}

/**
 * 채워진 좌석의 몸통 — 얼굴(방장이면 왕관 · 좁으면 티어 배지) · 닉네임(+ 인증 표시) · 그 사람의 포지션(`seatPosition` — 2026-10-01 부터 멤버도 · 확정된 방은 없음) · 두 줄째에 그 글의 사다리 티어와 승률 · KDA.
 * 게시판 좌석(`RoomSeat`)과 방 화면의 음성 칸 좌석(`RoomVoiceSeats` — `me` 로 닉네임 뒤 "(나)")이 같이 쓴다 — 두 곳의 좌석이 같은 모양이게.
 * 두 줄째 숫자에는 풍선말(`title`)을 달지 않는다 — 마우스를 올리면 뜨는 작은 창 위에 브라우저 풍선말 "KDA" 가 겹쳐 떴다(2026-09-30 소유자 스크린숏). 이름은 작은 창의 칸 · 좌석 버튼의 이름(`seatSummary`)에 있다.
 */
export function RoomSeatBody({ room, member, me = false, color }: { room: BoardRoom; member: BoardMember; me?: boolean; color?: number }) {
  const position = seatPosition(room, member);
  const numbers = seatNumbers(room.game, member);
  return <>
    <span className="room-seat-face">
      <RoomMemberAvatar member={member} size={34} color={color} />
      <span className="room-seat-tier-badge"><FilterTierIcon game={room.game} tier={member.tier} size={16} /></span>
    </span>
    <span className="room-seat-text">
      <span className="room-seat-name">
        {position ? <span className="room-seat-position-icon"><FilterRoleIcon game={room.game} value={position} size={12} /></span> : null}
        <strong>{member.nickname}</strong>
        {me ? <span className="room-seat-me">(나)</span> : null}
        {member.profile?.verified ? <VerifiedMark /> : null}
        {position ? <span className="room-seat-position"><FilterRoleIcon game={room.game} value={position} size={15} /><b>{roleLabel(room.game, position)}</b></span> : null}
      </span>
      <span className="room-seat-line">
        <span className="room-seat-rank"><FilterTierIcon game={room.game} tier={member.tier} size={16} /><span style={member.tier ? { color: tierColor(member.tier) } : undefined}>{member.profile ? rankText(room.game, member.tier, member.division, true) : '—'}</span></span>
        {numbers.length ? <span className="room-seat-numbers">{numbers.map(item => <span key={item.label}><small>{item.label}</small><b>{item.text}</b></span>)}</span> : null}
      </span>
      {room.game === 'LOL' ? <span className="room-seat-champions" aria-label="주 챔피언">
        {member.champions.length ? member.champions.map(id => <span key={id}><img src={championPortrait(id) ?? undefined} alt="" width="24" height="24" loading="lazy" /><span>{championName(id)}</span></span>) : <span className="room-seat-no-stats">주 챔피언 정보 없음</span>}
      </span> : null}
      {!numbers.length ? <span className="room-seat-no-stats">전적 정보 없음</span> : null}
    </span>
  </>;
}

/**
 * 채워진 좌석 하나(게시판 카드) — 몸통은 `RoomSeatBody` 다.
 * 정보를 숨기지 않고 충분한 폭의 카드로 줄바꿈한다(mentor-room.css).
 * 누르면 프로필 창이다(전파를 끊는다 — 참가로 번지지 않게). 마우스를 올린 작은 창의 위 · 아래는 `placeSeatPopover` 가 정하고, VALORANT 글이면 창이 없다(`seatPopoverShown`).
 */
function RoomSeat({ room, member, color, selfId, popEnd, onMember }: { room: BoardRoom; member: BoardMember; color?: number; selfId: string; popEnd: boolean; onMember: (room: BoardRoom, member: BoardMember) => void }) {
  const popover = seatPopoverShown(room.game);
  return <li className={`room-seat is-filled${member.host ? ' is-host' : ''}${member.id === selfId ? ' is-self' : ''}${popEnd ? ' pop-end' : ''}`}
    onMouseEnter={popover ? event => placeSeatPopover(event.currentTarget) : undefined} onFocus={popover ? event => placeSeatPopover(event.currentTarget) : undefined}>
    <button type="button" className="room-seat-button" aria-label={`${seatSummary(room, member, selfId)} — 프로필 보기`} onClick={event => { event.stopPropagation(); onMember(room, member); }}>
      <RoomSeatBody room={room} member={member} color={color} />
    </button>
    {popover ? <SeatPopover room={room} member={member} /> : null}
  </li>;
}

/**
 * 방 카드 한 장 = 모집 글 하나(`BoardRoom`) — **좌석 줄**(2026-09-30 소유자 승인 — 사람마다 큰 카드 · 빈 자리마다 같은 조건을 되풀이하던 큰 점선 카드를 걷었다).
 *
 * - **머리 한 줄** — 제목 · 상태 점과 글자(모집 중 / 확정 · n명 / 만료) · 몇 분 전. **조건은 그 아래 한 줄에 한 번만** — 모드 · 인원 · 마이크 · 찾는 포지션(포지션이 있는 모드만).
 * - **좌석 줄** — 채워진 좌석(`RoomSeat` — 서버 순서 그대로 · 방장 먼저)이 같은 폭으로 서서 카드끼리 줄이 맞는다.
 *   빈 자리는 + 아이콘과 참여하기가 있는 점선 버튼이며 좁은 카드에서는 참여로 줄여 쓴다.
 *   그 뒤 `n/정원` 한 번과 **[참가]** — 누르면 참여 확인 창(`RoomJoinConfirm` — 포지션 방이면 거기서 남은 포지션 하나를 고른다, 2026-10-01)이다. 들어갈 수 없으면(`entryError` — 정원 · 이미 참여 · 다른 방 · 남은 포지션 없음 등) 버튼이 잠기고 이유가 풍선말 · 이름에 붙는다.
 *   채워진 좌석에는 그 사람이 고른 포지션이 붙는다(`seatPosition` — 확정된 글에는 없다).
 *   **좌석 수 = 그 글의 정원**(`capacity` — 그 모드의 인원: 솔로 랭크 2 · 자유 랭크 2/3/5 · 일반 · 칼바람 2~5, 그 전에 쓴 글 5 — P-41, 2026-09-30). 찬 방은 버튼이 **"가득 참"** 이다.
 *   빈자리 버튼도 같은 확인창을 열며 마우스 · 터치 · 키보드로 참여할 수 있다.
 * - **확정된 글**은 확정 순간의 파티원 전원(P-40 — `members` = 파티원 · `memberCount` = 파티 인원)이 좌석이고 빈 원 · [참가] 가 없다(머리는 `확정 · n명` — **그 파티가 닫혔으면 `끝남 · n명`**, platform P-46 `closed` · 2026-10-01 소유자 결정. 흐림 · 참가 없음은 같다).
 *   **만료된 글**은 방장 좌석만(서버가 `members` 를 비워 보낸다). 둘 다 흐리게 그린다.
 * - 카드가 460px 보다 좁으면(폰 폭) 좌석이 원 다섯 칸(얼굴 원 + 티어 배지 + 아래 닉네임)이 되고 `n/정원` · [참가] 는 그 아래 줄로 간다 —
 *   숫자 · 챔피언은 프로필 창(눌러서)에만 있다. 그보다 넓은데 좌석이 좁으면(분할 화면 · 사람이 많은 방) 좌석이 스스로 줄인다(`RoomSeat` · CSS 컨테이너 질의 — `room-board.css` "좌석 줄").
 */
export function RoomDeck({ room, selfId, entering = false, onEntered, reveal = false, onRevealed, entryError, onSeat, onMember }: { room: BoardRoom; selfId: string; entering?: boolean; onEntered?: () => void; reveal?: boolean; onRevealed?: () => void; entryError: string | null; onSeat: (room: BoardRoom) => void; onMember: (room: BoardRoom, member: BoardMember) => void }) {
  const heading = useRef<HTMLHeadingElement>(null);
  const card = useRef<HTMLElement>(null);
  useLayoutEffect(() => {
    if (!entering) return;
    heading.current?.scrollIntoView({ block: 'nearest', behavior: 'instant' });
    heading.current?.focus({ preventScroll: true });
    const timer = window.setTimeout(() => onEntered?.(), 650);
    return () => window.clearTimeout(timer);
  }, [entering, onEntered]);
  // 들어간 방의 카드 — 방 패널이 밀려 들어오며(0.32초) 게시판이 좁아진 뒤에 화면 안으로(`RoomBoardHome` `enter`).
  useEffect(() => {
    if (!reveal) return;
    const timer = window.setTimeout(() => {
      const still = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      card.current?.scrollIntoView({ block: 'nearest', behavior: still ? 'auto' : 'smooth' });
      onRevealed?.();
    }, 380);
    return () => window.clearTimeout(timer);
  }, [reveal, onRevealed]);
  const recruiting = room.status === 'RECRUITING';
  const positions = hasPositions(room.game, room.modeKey);
  // 확정된 글은 파티원 전원(P-40), 만료된 글은 방장만 — 서버가 만료된 글의 `members` 를 비워 보낸다.
  // 방장이 탈퇴한 확정된 글은 `host` 가 `null` 이고 `members` 에서 그 사람이 빠져 온다(P-48) — 남은 파티원만 그리고 방장 표시 · 빈 카드는 없다.
  const members = room.members.length ? room.members : room.host ? [room.host] : [];
  // 얼굴 색 — 한 방(카드 한 장)의 사람은 모두 다른 색이다(2026-09-30 소유자). 방장 먼저 · 나머지는 사용자 번호 순으로 집 색을 잡고 겹치면 다음 빈 색(`roomColors.ts`).
  const colors = boardRoomColors(room);
  const vacancies = recruiting ? Math.max(0, room.capacity - room.memberCount) : 0;
  const voice = roomVoice(room.voice);
  const group = modeChoice(room.game, room.modeKey)?.group ?? room.modeKey;
  const status = room.status === 'CONFIRMED' ? `${room.closed ? POST_ENDED_LABEL : POST_STATUS_LABEL.CONFIRMED} · ${room.memberCount || members.length}명` : POST_STATUS_LABEL[room.status];
  const openSeat = entryError ? undefined : () => onSeat(room);
  // 내가 이미 들어가 있는 방이면 잠긴 버튼의 글자를 "참여 중" 으로, 정원이 찼으면 "가득 참" 으로(이유는 `boardRoom.ts` `roomEntryError`).
  const inside = recruiting && members.some(member => member.id === selfId);
  const full = recruiting && (room.full || room.memberCount >= room.capacity);
  const joinText = inside ? '참여 중' : full ? '가득 참' : '참가';
  return <article ref={card} className={`room-deck room-row${room.hostId === selfId ? ' is-own' : ''}${recruiting ? '' : ' is-closed'}${entering ? ' is-entering' : ''}`} data-status={room.status} aria-label={`${room.title} 방 정보`}>
    <RoomBubbleTail />
    <div className="room-row-head">
      <h3 ref={heading} tabIndex={-1} title={room.title}>{room.title}</h3>
      <p className="room-row-state"><span className="room-row-status" data-status={room.status}><i aria-hidden="true" />{status}</span><time dateTime={room.createdAt}>{relativeTime(room.createdAt)}</time></p>
    </div>
    <p className="room-row-meta" aria-label="방 조건">
      <span className="room-row-mode"><FilterModeIcon mode={group} size={16} />{modeChoiceLabel(room.game, room.modeKey, room.perspective)}</span>
      <span className={`room-row-voice ${voice === 'REQUIRED' ? 'is-on' : 'is-off'}`}><VoiceIcon preference={voice} size={16} />{voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'}</span>
      {positions ? <span className="room-row-wanted"><span className="room-row-wanted-label">찾는 포지션</span><RoomWantedPositions room={room} /></span> : null}
    </p>
    <div className="room-seat-row">
      <ul className="room-seats" aria-label={recruiting ? `자리 ${room.memberCount} / ${room.capacity}` : `파티원 ${members.length}명`}>
        {members.map((member, index) => <RoomSeat key={member.id} room={room} member={member} color={colors.get(member.id)} selfId={selfId} popEnd={index >= 3} onMember={onMember} />)}
        {Array.from({ length: vacancies }, (_, index) => <li className="room-seat is-empty" key={`seat-${index}`}>
          <button type="button" className="room-seat-hole room-seat-join" disabled={Boolean(entryError)}
            aria-label={`빈자리 ${room.memberCount + index + 1}${entryError ? ` · ${entryError}` : ' 참여하기'}`} title={entryError ?? '이 방에 참여하기'} onClick={openSeat}>
            <svg className="room-seat-join-icon" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
            {entryError ? <span>빈자리</span> : <span><span className="room-seat-join-full">참여하기</span><span className="room-seat-join-short">참여</span></span>}
          </button>
        </li>)}
      </ul>
      {recruiting ? <div className="room-seats-tail">
        <span className="room-seat-count" aria-hidden="true">{room.memberCount}/{room.capacity}</span>
        <button type="button" className="room-join-button" disabled={Boolean(entryError)} title={entryError ?? undefined}
          aria-label={inside || full ? joinText : `참가${entryError ? ` · ${entryError}` : ''}`} onClick={() => onSeat(room)}>{joinText}</button>
      </div> : null}
    </div>
  </article>;
}
