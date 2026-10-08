import type { GameKey, MatchPartyMember, MemberCard, PostResponse, PubgPerspective, VoicePreference } from '../api/types';
import { modeSeed } from '../domain/gameCatalog';
import { tierForMode } from '../domain/profileTier';
import { accountRank } from './accountRank';
import { canonicalRoomRoles, ROOM_ROLES } from './summary';
import type { BoardMember, BoardRoom } from './types';

/** 가입하지 않은 번호(닉네임 `null`)의 이름 — 게시판 카드 · 빠른매치 팀원 카드 · 방 화면 좌석이 같이 쓴다. 번호(`#42`)를 이름 자리에 그리지 않는다. */
export const UNKNOWN_NICKNAME = '알 수 없음';

const finite = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;

/** 이 게임의 포지션 이름이면 그대로, 아니면(없음 · 빈 문자열 · 모르는 이름) `null` — `FilterRoleIcon` 은 모르는 이름을 "전체" 그림으로 그린다. */
export const knownPosition = (game: GameKey, value: string | null | undefined): string | null =>
  value && ROOM_ROLES[game].includes(value) ? value : null;

/**
 * 카드 한 장 → 보드의 사람. 가입하지 않은 번호(`nickname: null`)도 인원수와 어긋나지 않게 그린다(platform CLAUDE.md §3.3).
 * **티어는 그 글의 모드의 사다리 티어다** — 모드에 사다리가 없으면(일반 · 칼바람 · 언레이티드) 그 사람 사다리들 가운데 가장 높은 티어(`domain/profileTier.ts` `tierForMode` — Claude 가 정한 세부).
 * PUBG 의 두 칸은 치킨률 · K/D 다(`stats.detail.top1Rate` · `kd` — PUBG 는 `winRate` · `kda` 가 늘 `null` 이다).
 * 포지션은 카드의 `position`(2026-10-01 — platform P-44 ⑨)이다 — 칸이 없는 옛 서버면 `fallbackPosition`(방장 카드에 글의 `hostPosition` — 전처럼 방장 포지션은 보이게)이다.
 */
export function toBoardMember(card: MemberCard, game: GameKey, modeKey: string, fallbackPosition: string | null = null): BoardMember {
  const profile = card.profile;
  const picked = tierForMode(game, modeKey, profile);
  const rank = accountRank(picked.tier);
  const stats = profile?.stats ?? null;
  const champions = stats?.detail?.mostChampions ?? null;
  const pubg = game === 'PUBG';
  return {
    id: String(card.userId),
    nickname: card.nickname ?? UNKNOWN_NICKNAME,
    host: card.host,
    position: knownPosition(game, card.position === undefined ? fallbackPosition : card.position),
    tier: rank.tier,
    division: rank.division,
    tierLadder: picked.ladder,
    winRate: pubg ? finite(stats?.detail?.top1Rate) : stats?.winRate ?? null,
    kda: pubg ? finite(stats?.detail?.kd) : stats?.kda ?? null,
    champions: Array.isArray(champions) ? champions.slice(0, 3).map(c => c.championId) : [],
    profile,
    card,
  };
}

const perspectiveOf = (game: GameKey, post: PostResponse): PubgPerspective | null => {
  if (game !== 'PUBG') return null;
  const value = post.conditions?.perspective;
  return value === 'TPP' || value === 'FPP' ? value : null;
};

/** `PostResponse` → `BoardRoom`. 순수 함수 — 목록 · 단건 · 글 쓰기 응답이 다 이것을 거친다. */
export function toBoardRoom(post: PostResponse): BoardRoom {
  return {
    id: String(post.postId),
    postId: post.postId,
    game: post.game,
    modeKey: post.mode ?? '',
    title: post.title,
    description: post.description ?? '',
    // 방장이 탈퇴한 확정된 글은 `hostId` · `host` 가 `null` 이다(2026-10-02 — platform P-48). 방장 표시 없이 `members`(남은 파티원)만 그린다 — 빈 카드를 지어내지 않는다.
    hostId: post.hostId == null ? null : String(post.hostId),
    capacity: post.capacity,
    memberCount: post.memberCount,
    full: post.full,
    // 옛 서버는 칸을 안 보낸다(`undefined`) — `false`. 확정된 글에서만 뜻이 있다(P-46).
    closed: post.status === 'CONFIRMED' && post.closed === true,
    status: post.status,
    voice: post.voice,
    perspective: perspectiveOf(post.game, post),
    wantedPositions: post.wantedPositions ?? [],
    allowAutoJoin: post.allowAutoJoin ?? true,
    // 옛 서버는 칸을 안 보낸다(`undefined`) — `null` 로. 이 게임의 포지션 이름이 아니면 그리지 않는다(`FilterRoleIcon` 은 모르는 이름을 "전체" 그림으로 그린다).
    hostPosition: knownPosition(post.game, post.hostPosition),
    createdAt: post.createdAt,
    autoConfirmAt: post.autoConfirmAt,
    autoConfirmWarningAt: post.autoConfirmWarningAt,
    host: post.host ? toBoardMember(post.host, post.game, post.mode ?? '', post.hostPosition) : null,
    members: (post.members ?? []).map(card => toBoardMember(card, post.game, post.mode ?? '', card.host ? post.hostPosition : null)),
  };
}

/**
 * 빠른매치 파티 → 좌석이 그리는 방(2026-10-01 소유자 결정 — platform P-47 `GET /match-parties/{partyId}/members`). **글이 아니다** — 좌석 몸통 · 작은 창 · 프로필 창
 * (`RoomSeatBody` · `SeatPopover` · `RoomMemberProfile`)이 `BoardRoom` 을 받아서 그 모양으로 편다(`quickMatch` — 고른 포지션을 늘 붙인다). 제목 · 글 번호 · 상태는 뜻이 없다.
 * 팀원 카드는 게시판 카드와 같은 `toBoardMember` 를 거친다 — 티어는 그 모드의 사다리 티어(모드를 모르면 가장 높은 티어) · 포지션은 고른 것(이 게임의 이름만).
 * 방장은 방 화면이 아는 지금의 방장(`hostId` — 승계 D-23)이고 제안에는 없다(`null` — 그때 `host` 도 `null`). 정원은 모르면 팀원 수다. 팀원이 없으면(올 일이 없다 — 나도 팀원이다) `null`.
 */
export function toMatchPartyRoom({ partyId, game, modeKey, voice, capacity, hostId, members }: {
  partyId: string; game: GameKey; modeKey: string | null; voice: VoicePreference | null; capacity: number | null; hostId: string | null; members: MatchPartyMember[];
}): BoardRoom | null {
  const mode = modeKey ?? '';
  const people = members.map(member => toBoardMember({ ...member, host: hostId !== null && String(member.userId) === hostId }, game, mode));
  if (!people.length) return null;
  return {
    id: partyId,
    postId: 0,
    game,
    modeKey: mode,
    title: '빠른매치 파티',
    description: '',
    hostId,
    capacity: capacity ?? people.length,
    memberCount: people.length,
    full: false,
    closed: false,
    // 처음부터 확정인 파티다(제안 중에도 이 값이다) — 좌석의 포지션은 `quickMatch` 가 정해 이 값을 보지 않는다.
    status: 'CONFIRMED',
    voice: voice ?? 'NO_VOICE',
    perspective: perspectiveFromMode(game, mode),
    wantedPositions: [],
    allowAutoJoin: false,
    hostPosition: null,
    createdAt: '',
    // 방장을 모르면(제안 중) `null` — 남의 카드를 방장 자리에 세우지 않는다(게시판 글의 `host` 와 같은 뜻 · P-48 뒤 `null` 을 허락한다).
    host: people.find(member => member.host) ?? null,
    members: people,
    quickMatch: true,
  };
}

/**
 * 포지션 방인가 — 글의 `wantedPositions` 가 비지 않았다(2026-10-01 소유자 결정 — platform P-44 · 서버의 `PostEntryGate` 가 가르는 기준과 같다 — 모드가 아니라 글의 값이다).
 * 포지션 방은 참가할 때 남은 포지션 하나를 골라야 하고(`?position=`), 포지션이 없는 방(칼바람 · PUBG · 찾는 포지션이 빈 옛 글)은 고르지 않는다(보내면 400).
 */
export const isPositionRoom = (room: BoardRoom): boolean => room.wantedPositions.length > 0;

/**
 * 남은 포지션 — 글의 찾는 포지션에서 방 안 사람(카드)의 포지션을 뺀 것(게임 순서). 포지션 방이 아니면 빈 배열.
 * 목록을 받은 때의 값이라 그 사이 누가 먼저 고를 수 있다 — 서버가 400 으로 막고 참여 창이 목록을 다시 받는다(`RoomJoinConfirm`).
 * 칸을 안 보내는 옛 서버면 멤버의 포지션을 몰라 찾는 포지션 전부가 남은 것으로 보인다(서버가 막는다).
 */
export function remainingPositions(room: BoardRoom): string[] {
  if (!isPositionRoom(room)) return [];
  const taken = new Set(room.members.map(member => member.position).filter((position): position is string => position !== null));
  return canonicalRoomRoles(room.game, room.wantedPositions).filter(role => !taken.has(role));
}

/**
 * PUBG 모드의 시점 — 카탈로그 모드의 `perspective`(프런트 전용 UI 메타 · 2026-09-29)에서 읽는다. 값은 모드 이름에 접힌 시점과 같다(`RANKED_DUO_TPP` → `TPP` —
 * 서버의 auto-join 은 이름에서 같은 규칙으로 읽는다, `AutoJoinService#perspectiveOf`). 글의 `conditions.perspective` 는 이것으로 채운다(글 쓰기).
 */
export function perspectiveFromMode(game: GameKey, modeKey: string): PubgPerspective | null {
  return game === 'PUBG' ? modeSeed(game, modeKey)?.perspective ?? null : null;
}

/** 보드에서 참여 버튼을 누르기 전에 거르는 것 — 서버가 어차피 거절하는 것을 미리 문구로. `null` 이면 눌러도 된다. */
export function roomEntryError(room: BoardRoom, selfId: string, activeRoomId: string | null): string | null {
  if (room.status === 'CONFIRMED') return room.closed ? '끝난 파티예요' : '확정된 방이에요';
  if (room.status === 'EXPIRED') return '모집이 끝났어요';
  if (room.full || room.memberCount >= room.capacity) return `가득 찬 방이에요(정원 ${room.capacity}명)`;
  if (activeRoomId === room.id || room.members.some(member => member.id === selfId)) return '이미 참여 중인 방이에요';
  // 다른 방 참여는 차단 조건이 아니다. 확인창에서 동의를 받은 뒤 기존 방을 나가고 입장한다.
  // 포지션 방인데 남은 포지션이 없다 — 찾는 포지션이 정원보다 적던 옛 글에서 생긴다(2026-10-01 부터 새 글은 정원 − 1 개 이상이라 자리와 함께 찬다).
  if (isPositionRoom(room) && !remainingPositions(room).length) return '남은 포지션이 없어 참여할 수 없어요';
  return null;
}
