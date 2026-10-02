import './mentor-room.css';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { tierColor } from '../domain/rankAssets';
import type { GameKey, GameStats, LolMostChampion } from '../api/types';
import { Avatar } from '../components/ui';
import { FilterModeIcon, FilterRoleIcon, FilterTierIcon, VoiceIcon } from '../components/FilterSymbols';
import { PerformanceValue, PreferredChampions } from '../components/IntroductionVisuals';
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
export function RoomMemberAvatar({ member, size, color, showHost = true }: { member: BoardMember; size: number; color?: number; showHost?: boolean }) {
  return <span className="room-member-avatar">
    <Avatar userId={member.id} name={member.nickname} color={color} size={size} />
    {member.host && showHost ? <RoomHostCrown /> : null}
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
 * 방 화면의 음성 좌석도 클릭 상세에서 같은 내용을 쓴다.
 */
export function SeatPopover({ room, member, embedded = false }: { room: BoardRoom; member: BoardMember; embedded?: boolean }) {
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
  return <span className="room-seat-popover" aria-hidden={embedded ? undefined : true}>
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

/** 게시판은 고정 열 표(table), 음성/제안 좌석은 작은 요약을 사용한다. 상세 정보는 공통 팝오버에서 읽는다. */
export function RoomSeatBody({ room, member, me = false, color, table = false, showDetails = false }: { room: BoardRoom; member: BoardMember; me?: boolean; color?: number; table?: boolean; showDetails?: boolean }) {
  const position = seatPosition(room, member);
  const record = room.game === 'LOL' ? winLossRecord(member.profile?.stats) : null;
  const positionLabel = position ? <span className="room-seat-position" title={roleLabel(room.game, position)}><FilterRoleIcon game={room.game} value={position} size={15} /><b>{roleLabel(room.game, position)}</b></span> : null;
  return <>
    {showDetails && position ? <span className="room-voice-position" role="img" aria-label={roleLabel(room.game, position)} title={roleLabel(room.game, position)}><FilterRoleIcon game={room.game} value={position} size={16} /></span> : null}
    <span className="room-seat-face">
      <RoomMemberAvatar member={member} size={showDetails ? 36 : 34} color={color} showHost={!showDetails} />
      {!showDetails ? <span className="room-seat-tier-badge"><FilterTierIcon game={room.game} tier={member.tier} size={16} /></span> : null}
    </span>
    <span className="room-seat-text">
      <span className="room-seat-name">
        {position && !showDetails ? <span className="room-seat-position-icon"><FilterRoleIcon game={room.game} value={position} size={12} /></span> : null}
        <strong>{member.nickname}</strong>
        {me ? <span className="room-seat-me">(나)</span> : null}
        {member.profile?.verified ? <VerifiedMark /> : null}
        {!table && !showDetails ? positionLabel : null}
      </span>
      {table ? positionLabel ?? <span className="room-seat-position">—</span> : null}
      <span className="room-seat-line">
        <span className="room-seat-rank"><FilterTierIcon game={room.game} tier={member.tier} size={16} /><span style={member.tier ? { color: tierColor(member.tier) } : undefined}>{member.profile ? rankText(room.game, member.tier, member.division, !showDetails) : '—'}</span></span>
        {table ? <><span className="room-seat-record" aria-label={room.game === 'PUBG' ? '치킨율' : '승패와 승률'}>
          {record ? <WinLossBar record={record} rate={member.winRate} size="sm" /> : member.winRate !== null ? <PerformanceValue kind="winRate" value={member.winRate} /> : <span className="room-seat-no-stats">—</span>}
        </span>
        <span className="room-seat-kda" aria-label={room.game === 'PUBG' ? 'K/D' : 'KDA'}>{member.kda !== null ? <PerformanceValue kind="kda" value={member.kda} /> : <span className="room-seat-no-stats">—</span>}</span></> : <span className="room-seat-numbers">{member.winRate !== null ? <span><PerformanceValue kind="winRate" value={member.winRate} /></span> : null}{member.kda !== null ? <span><PerformanceValue kind="kda" value={member.kda} /></span> : null}</span>}
      </span>
      {table ? <span className="room-seat-champions" aria-label="선호 챔피언">
        {room.game === 'LOL' && member.champions.length ? <PreferredChampions game={room.game} names={member.champions.slice(0, 3)} /> : <span className="room-seat-no-stats">—</span>}
      </span> : null}
    </span>
  </>;
}

/**
 * 채워진 좌석 하나(게시판 카드) — 몸통은 `RoomSeatBody` 다.
 * 정보를 숨기지 않고 충분한 폭의 카드로 줄바꿈한다(mentor-room.css).
 * 누르면 상세 창을 연다(전파를 끊어 참가로 번지지 않게 한다).
 */
function RoomSeat({ room, member, color, selfId, onMember }: { room: BoardRoom; member: BoardMember; color?: number; selfId: string; onMember: (room: BoardRoom, member: BoardMember) => void }) {
  return <li className={`room-seat is-filled${member.host ? ' is-host' : ''}${member.id === selfId ? ' is-self' : ''}`}>
    <button type="button" className="room-seat-button" aria-haspopup="dialog" aria-label={`${seatSummary(room, member, selfId)} — 상세 정보`} onClick={event => { event.stopPropagation(); event.currentTarget.focus(); onMember(room, member); }}>
      <RoomSeatBody room={room} member={member} color={color} table />
    </button>
  </li>;
}

/** 방 목록과 참여한 방 패널이 같은 모드·마이크 표시를 쓴다. */
export function RoomConditions({ room }: { room: BoardRoom }) {
  const voice = roomVoice(room.voice);
  const group = modeChoice(room.game, room.modeKey)?.group ?? room.modeKey;
  return <>
    <span className="room-row-mode"><FilterModeIcon mode={group} size={16} />{modeChoiceLabel(room.game, room.modeKey, room.perspective)}</span>
    <span className="room-row-voice"><VoiceIcon preference={voice} size={16} />{voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'}</span>
  </>;
}

/** 방 목록은 상태 문구 대신 입장 가능 여부로 구분한다. 실제 빈자리와 참가 버튼은 모든 카드에서 같은 위치에 둔다. */
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
  const vacancies = room.status !== 'EXPIRED' && !room.closed ? Math.max(0, room.capacity - room.memberCount) : 0;
  const canJoin = recruiting && !entryError && vacancies > 0;
  return <article ref={card} className={`room-deck room-row${room.hostId === selfId ? ' is-own' : ''}${canJoin ? ' is-joinable' : ' is-unavailable'}${entering ? ' is-entering' : ''}`} data-status={room.status} aria-label={`${room.title} 방 정보`}>
    <div className="room-row-head">
      <h3 ref={heading} tabIndex={-1} title={room.title}>{room.title}</h3>
      <p className="room-row-state"><time dateTime={room.createdAt}>{relativeTime(room.createdAt)}</time></p>
        <button type="button" className="room-join-button" disabled={!canJoin} title={entryError ?? undefined}
          aria-label={`참가${entryError ? ` · ${entryError}` : ''}`} onClick={() => { if (canJoin) onSeat(room); }}>참가</button>
    </div>
    <p className="room-row-meta" aria-label="방 조건">
      <RoomConditions room={room} />
      {positions && recruiting && remainingPositions(room).length ? <span className="room-row-wanted"><span className="room-row-wanted-label">찾는 포지션</span><RoomWantedPositions room={room} /></span> : null}
    </p>

    <div className="room-seat-row">
      <ul className="room-seats" aria-label={recruiting ? `자리 ${room.memberCount} / ${room.capacity}` : `파티원 ${members.length}명`}>
        {members.map(member => <RoomSeat key={member.id} room={room} member={member} color={colors.get(member.id)} selfId={selfId} onMember={onMember} />)}
        {Array.from({ length: vacancies }, (_, index) => <li className="room-seat is-empty" key={`seat-${index}`}>
          <span className="room-seat-hole room-seat-vacancy" aria-label={`빈자리 ${index + 1}`} />
        </li>)}
      </ul>

    </div>
  </article>;
}
