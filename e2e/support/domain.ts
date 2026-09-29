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
  host: { userId: number; nickname: string; host: boolean } | null;
  members: { userId: number; nickname: string; host: boolean }[];
}

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

/** LoL 일반 5인 · 방장 미드 · 탑을 찾는 · 음성 안 씀 — 시나리오가 필요한 칸만 덮는다. */
export function lolPost(title: string, over: Partial<PostBody> = {}): PostBody {
  return { game: 'LOL', mode: 'NORMAL_5', title, voice: 'NO_VOICE', conditions: {}, wantedPositions: ['TOP'], hostPosition: 'MID', ...over };
}

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

/** `GET /rooms/{roomId}/members` — 방 안의 사람만 볼 수 있다. */
export async function roomMembers(user: QmUser, roomId: string): Promise<{ status: number; hostId?: string; members?: string[]; code?: string }> {
  const r = await user.get(`/rooms/${encodeURIComponent(roomId)}/members`);
  return r.status === 200 ? { status: 200, hostId: r.body.hostId, members: r.body.members } : { status: r.status, code: r.body?.code };
}
