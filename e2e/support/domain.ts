import type { QmUser } from './api';

/** 글 한 줄(`GET /posts` · `POST /posts` 응답) 가운데 시나리오가 보는 칸 — `platform-api.md` "글 한 줄". */
export interface Post {
  postId: number;
  hostId: number;
  game: string;
  mode: string;
  title: string;
  voice: string;
  wantedPositions: string[];
  hostPosition: string | null;
  status: 'RECRUITING' | 'CONFIRMED' | 'EXPIRED';
  memberCount: number;
  capacity: number;
  full: boolean;
  /** 확정된 파티가 끝났는가(platform P-46 — 확정된 글이고 그 파티가 닫혔을 때만 `true` · 모집 중 · 만료 · 진행 중인 확정은 `false`). */
  closed: boolean;
  host: Card | null;
  members: Card[];
}

/**
 * 글 한 줄의 사람 카드. `position`(platform P-44 ⑨ — 2026-10-01) — 모집 중인 글이면 그 사람의 포지션(방장은 글의 `hostPosition` · 멤버는 참가할 때 고른 것 · 안 골랐으면 `null`),
 * **확정 · 만료된 글은 `null`**(만료된 글은 `members` 가 빈 배열이고 `host` 카드의 `position` 도 `null`).
 */
export interface Card { userId: number; nickname: string; host: boolean; position: string | null }

export interface PostBody {
  game: string;
  mode: string;
  title: string;
  voice: string;
  conditions: Record<string, unknown>;
  wantedPositions: string[];
  hostPosition?: string;
  description?: string;
}

/** 겹치지 않는 글 제목(60자 안) — 게시판 화면에서 카드를 찾는 열쇠도 된다. */
export function uniqueTitle(scenario: string): string {
  return `e2e ${scenario} ${Date.now().toString(36)}`;
}

/**
 * LoL 일반 5인 · 방장 미드 · 탑 · 정글 · 원딜 · 서포터를 찾는 · 음성 안 씀 — 시나리오가 필요한 칸만 덮는다.
 * 찾는 포지션은 **정원 − 1 개 이상**이어야 한다(platform P-44 "찾는 포지션 수" · `PostValidation#enoughWantedPositions`) —
 * 5인이면 넷이라 방장 포지션(미드)을 뺀 전부다. 모드를 덮을 때는 찾는 포지션도 그 정원에 맞춰 덮는다(3인이면 둘 이상).
 */
export function lolPost(title: string, over: Partial<PostBody> = {}): PostBody {
  return { game: 'LOL', mode: 'NORMAL_5', title, voice: 'NO_VOICE', conditions: {}, wantedPositions: ['TOP', 'JUNGLE', 'ADC', 'SUPPORT'], hostPosition: 'MID', ...over };
}

/** LoL 칼바람 5인 — 포지션이 없는 모드(`positionUniqueness=false`)라 방장 포지션 · 찾는 포지션이 없다. 이 글의 방은 **포지션 없는 방**이다(입장에 `position` 을 주면 400). */
export function aramPost(title: string, over: Partial<PostBody> = {}): PostBody {
  return { game: 'LOL', mode: 'ARAM_5', title, voice: 'NO_VOICE', conditions: {}, wantedPositions: [], ...over };
}

/**
 * 게시판 방 입장 `POST /rooms/{roomId}/members` — 포지션 방(글의 `wantedPositions` 가 비지 않았다)이면 남은 포지션 하나를 `?position=` 으로 준다(platform P-44).
 * 포지션 없는 방에는 주지 않는다(주면 400). 결과를 그대로 돌려준다 — 거절도 시나리오가 본다.
 */
export function enter(user: QmUser, roomId: string, position?: string) {
  return user.post(`/rooms/${encodeURIComponent(roomId)}/members${position ? `?position=${encodeURIComponent(position)}` : ''}`);
}

/** 400 포지션 거절의 두 글귀(platform `RoomMemberController#enter` — 스크립트 -6: 골랐으면 앞, 안 골랐으면 뒤). */
export const POSITION_NOT_AVAILABLE = 'position: 이 파티방에서 고를 수 없는 포지션입니다';
export const POSITION_REQUIRED = 'position: 필요합니다';

/** `POST /match-requests`(matching) · `POST /posts/auto-join`(platform) 의 같은 본문 — `CreateMatchRequestCommand`. */
export interface MatchBody {
  game: string;
  modeKey: string;
  tier?: string;
  keyCondition?: { type: string; value: string };
  voicePreference: string;
  playPurpose: string;
}

/** LoL 일반 2인 · 음성 안 씀 · 빡겜(`TRYHARD` — 옛 `NORMAL`, matching D-49). */
export function lolMatch(position: string, over: Partial<MatchBody> = {}): MatchBody {
  return { game: 'LOL', modeKey: 'NORMAL_2', keyCondition: { type: 'POSITION', value: position }, voicePreference: 'NO_VOICE', playPurpose: 'TRYHARD', ...over };
}

/** 글 쓰기 — 201 이 아니면 던진다(검증 대상이 아닌 준비 단계). */
export async function createPost(user: QmUser, body: PostBody): Promise<Post> {
  const r = await user.post<Post>('/posts', body);
  if (r.status !== 201) throw new Error(`${user.nickname}: 글 쓰기 실패 ${r.status} ${JSON.stringify(r.body)}`);
  return r.body;
}

/** 게시판 목록 첫 쪽(최신순)에서 그 글을 찾는다. */
export async function findOnBoard(user: QmUser, game: string, postId: number): Promise<Post | undefined> {
  const r = await user.get<{ posts: Post[] }>(`/posts?game=${game}&limit=100`);
  if (r.status !== 200) throw new Error(`${user.nickname}: 목록 조회 실패 ${r.status} ${JSON.stringify(r.body)}`);
  return r.body.posts.find((post) => post.postId === postId);
}

/**
 * 게시판 방 먼저 합류(P-28)가 **다른 사람의 방**을 고를 수 있는가 — 남(소유자의 `dev-tester` 등)의 모집 중인 방이 같은 조건이면
 * 시나리오가 "404 여야 한다" 를 말할 수 없고, 남의 방에 e2e 사용자를 들여보내면 안 된다. 그런 후보의 글 번호를 돌려준다.
 * (서버 조건 중 티어 범위는 여기서 보지 않는다 — 티어를 안 보는 일반 모드만 쓴다.)
 */
export async function foreignCandidates(viewer: QmUser, match: MatchBody, ownHostIds: number[]): Promise<number[]> {
  const r = await viewer.get<{ posts: Post[] }>(`/posts?game=${match.game}&limit=100`);
  const position = match.keyCondition?.value;
  return (r.body?.posts ?? []).filter((post) => post.status === 'RECRUITING' && !post.full && post.mode === match.modeKey
    && post.voice === match.voicePreference && post.hostId !== viewer.id && !ownHostIds.includes(post.hostId)
    && (!position || !post.wantedPositions.length || post.wantedPositions.includes(position)))
    .map((post) => post.postId);
}

/** `GET /rooms/me` 의 방 번호(없으면 `null`). */
export async function myRoom(user: QmUser): Promise<string | null> {
  const r = await user.get<{ roomId: string | null }>('/rooms/me');
  if (r.status !== 200) throw new Error(`${user.nickname}: 내 방 조회 실패 ${r.status} ${JSON.stringify(r.body)}`);
  return r.body.roomId;
}

/**
 * `GET /rooms/{roomId}/members` — 방 안의 사람만 볼 수 있다. 2026-10-01 부터 서버의 `members` 는 `{userId, position}` 이다(platform P-44 ⑩) —
 * 시나리오는 사람(id)만 보므로 `members` 는 id 로 펴서 돌려준다(옛 서버의 id 문자열도 받는다). 포지션은 `positions`(id → 포지션 · 고른 사람만)를 따로 읽는다.
 */
export async function roomMembers(user: QmUser, roomId: string): Promise<{ status: number; hostId?: string; members?: string[]; code?: string }> {
  const r = await user.get(`/rooms/${encodeURIComponent(roomId)}/members`);
  if (r.status !== 200) return { status: r.status, code: r.body?.code };
  return { status: 200, hostId: r.body.hostId, members: memberEntries(r.body.members).map((entry) => entry.userId) };
}

/** 방 안 사람마다 고른 포지션(id → 포지션 · 고른 사람만 — platform P-44 ⑩). 방장은 글의 방장 포지션이다. 방 안이 아니면 `null`. */
export async function roomPositions(user: QmUser, roomId: string): Promise<Record<string, string> | null> {
  const r = await user.get(`/rooms/${encodeURIComponent(roomId)}/members`);
  if (r.status !== 200) return null;
  return Object.fromEntries(memberEntries(r.body.members).flatMap((entry) => entry.position ? [[entry.userId, entry.position]] : []));
}

const memberEntries = (members: unknown): { userId: string; position: string | null }[] =>
  (Array.isArray(members) ? members : []).map((entry: unknown) => typeof entry === 'string'
    ? { userId: entry, position: null }
    : { userId: String((entry as { userId: unknown }).userId), position: ((entry as { position?: string | null }).position) || null });
