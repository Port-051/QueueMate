import { request } from './http';
import type {
  AutoJoinResponse, BlockView, CreateBlockRequest, CreateFriendRequest, CreateMatchRequest,
  CreateReportRequest, CreateReservationRequest, FriendRequestDirection, FriendRequestView, FriendView,
  GameAccountRequest, GameKey, GameProfile, MatchRequestView, MatchRoomResponse,
  PartyView, RecentPlayerView, ReservationView, SessionUser, SocialProvider, SocialSignupPending,
  SendRoomSignalRequest, SocialSignupRequest, UpdateUserRequest, UserProfile,
} from './types';

/**
 * 백엔드 엔드포인트. 계정 · 소셜 · 방 · 게시판은 `platform/contracts/platform-api.md`, 매칭 · 제안은 `matching/contracts/openapi.yaml` 이 원본이다.
 * 계약에 없는 경로를 부르지 않고, 계약에 있는 경로를 빠뜨리지 않는다. 아직 원본 프런트의 경로가 남은 절(친구 · 차단 · 신고 — 5단계)과
 * **대응물이 없는 절(파티 Ready/PLAYING · 예약 · 아바타 — 부르면 404 · 화면을 남긴다, START_HERE.md §5)**은 각 절의 주석 참조.
 */

/* ---------- auth (인증 불필요 — /api/v1/auth/**) ---------- */
/**
 * 소셜 로그인은 XHR 이 아니라 **브라우저 이동**이다 — `window.location.assign(oauthStartPath(provider))`.
 * 콜백은 백엔드가 받아 `FRONT_BASE_URL` + `/` · `/signup/social` · `/login?error=OAUTH_FAILED` · `/settings?linked=` · `/settings?error=` 로 302 한다.
 * 로그인한 채 부르면 새 사용자를 만들지 않고 **같은 사용자에 잇는다**(P-27).
 */
export const oauthStartPath = (provider: SocialProvider) => `/api/v1/auth/oauth/${provider}/start`;
/** 재발급(`POST /auth/refresh`)은 `http.ts` 의 `refreshSession()` 이다 — 동시 호출을 한 번으로 묶어야 해서 거기 있다. */
/** 로그아웃. 본문 없음 · 204 · 쿠키 둘 제거. 쿠키가 없어도 · Redis 가 죽어 있어도 204 다. */
export const logout = () => request<void>('/auth/logout', { method: 'POST', noRetry: true });
/** 소셜로 처음 온 사람의 가입 대기 정보. 대기 토큰(`qm_social_signup`)이 없으면 401 `NO_PENDING_SOCIAL_SIGNUP`. */
export const getSocialSignupPending = () => request<SocialSignupPending>('/auth/social/pending', { noRetry: true });
/** 닉네임 하나로 가입 — 201 `{userId, nickname}` + 로그인 쿠키. 409 `NICKNAME_TAKEN` · 400 `VALIDATION_FAILED`. */
export const socialSignup = (body: SocialSignupRequest) =>
  request<SessionUser>('/auth/social/signup', { method: 'POST', body, noRetry: true });

/* ---------- user ---------- */
export const getMe = () => request<UserProfile>('/users/me');
export const updateMe = (body: UpdateUserRequest) => request<UserProfile>('/users/me', { method: 'PATCH', body });
/** 소셜 계정 끊기. 내 것이 아니어도 204(멱등). 마지막 하나면 409 `LAST_SOCIAL_IDENTITY`. 잇기는 `oauthStartPath` 로의 이동이다. */
export const unlinkSocial = (provider: SocialProvider) =>
  request<void>(`/users/me/social/${provider}`, { method: 'DELETE' });
/**
 * 아바타 업로드 — **우리 백엔드에 없다**(원본 `POST /users/me/avatar`). 부르면 404 다. 아바타 화면의 처지는 미정(START_HERE.md §5)이라
 * 화면이 컴파일되게만 남겼다.
 */
export const uploadAvatar = (file: File) =>
  request<UserProfile>('/users/me/avatar', { method: 'POST', file });
/* ---------- game accounts (platform-api.md "계정" · "게임 프로필" · "전적을 긁는 것") — 목록은 `users/me.gameAccounts` 다(따로 받는 요청이 없다) ---------- */
/**
 * 게임 계정 연결 · 수정 — 없으면 만들고 있으면 바꾼다. 본문은 게임마다 다르다(`GameAccountRequest`). 200 게임 프로필.
 * **LOL 은 저장하기 전에 Riot 을 동기로 긁는다 — 상한 30초.** 응답에 `tier` · `stats` 가 바로 들어 있다. 이름#태그가 Riot 에 없으면 404 `RIOT_ID_NOT_FOUND`,
 * Riot 장애 · 시간 초과 · 키 없음은 503 `GAME_STATS_UNAVAILABLE` — 둘 다 저장하지 않는다. 400 `VALIDATION_FAILED` 는 `details[0]` 이 `"필드: 사유"` 다.
 */
export const putGameAccount = (game: GameKey, body: GameAccountRequest) =>
  request<GameProfile>(`/users/me/game-accounts/${game}`, { method: 'PUT', body });
/** 연결 해제. 없어도 204. */
export const deleteGameAccount = (game: GameKey) =>
  request<void>(`/users/me/game-accounts/${game}`, { method: 'DELETE' });
/**
 * 전적 갱신 — **LOL 만**(동기 · 상한 30초 · 같은 계정은 2분에 한 번). 200 갱신된 게임 프로필(`PUT` 과 같은 모양 — 그대로 갈아 끼운다).
 * 429 `TOO_MANY_STATS_REFRESHES` + `Retry-After`(초) · 404 `GAME_ACCOUNT_NOT_FOUND` · 409 `GAME_STATS_NOT_SUPPORTED`(VALORANT · PUBG) · 503 `GAME_STATS_UNAVAILABLE`.
 */
export const refreshGameStats = (game: GameKey) =>
  request<GameProfile>(`/users/me/game-accounts/${game}/refresh`, { method: 'POST' });

/* ---------- game config — 없다. 게임 · 모드 · 티어는 정적 상수 `domain/gameCatalog.ts`(seed 의 사본 · 2026-09-28 소유자 결정) ---------- */

/* ---------- realtime matching — "매칭 시작" 은 auto-join(platform) → 404 면 match-requests(matching). 흐름은 `state/MatchContext.tsx` ---------- */
/**
 * 게시판 방 먼저 합류(platform-api.md "자동 매칭이 게시판 방에 먼저 합류하는 길" · P-28). 본문은 `POST /match-requests` 와 **같은 객체**다.
 * 200 `{postId, roomId}` 면 들어갔다(방 화면으로) · **404 `NO_MATCHING_POST` 면 그대로 `createMatchRequest` 를 부른다** ·
 * 409 `IN_OTHER_ROOM` · `ALREADY_QUEUED` · 400 `VALIDATION_FAILED` 는 끊는다 · 503 `ROOM_STATE_UNAVAILABLE`(+`Retry-After: 5`).
 */
export const autoJoinPost = (body: CreateMatchRequest) =>
  request<AutoJoinResponse>('/posts/auto-join', { method: 'POST', body });
/** 201 — `MatchRequestView` 의 `QUEUED` 갈래(`requestId` 는 취소에 쓴다). 409 `ALREADY_QUEUED` · `IN_ROOM` · 400 `INVALID_MATCH_CONDITION` 등 · 503 `MATCHING_UNAVAILABLE`. */
export const createMatchRequest = (body: CreateMatchRequest) =>
  request<MatchRequestView>('/match-requests', { method: 'POST', body });
/** 내 매칭 요청의 상태 — 경로 변수 없음 · **늘 200**(없으면 `{status: 'IDLE'}`). SSE 를 놓친 뒤(재연결 직후 · 새로고침) 상태를 맞추는 곳이다. */
export const getMatchRequest = () => request<MatchRequestView>('/match-requests');
/** compare-and-delete — 저장된 `requestId` 와 다르면 404 `MATCH_REQUEST_MISMATCH`, 활성 요청이 없으면 404 `MATCH_REQUEST_NOT_FOUND`. 둘 다 "이미 빠졌다" 다. */
export const cancelMatchRequest = (requestId: string) => request<void>(`/match-requests/${requestId}`, { method: 'DELETE' });
/**
 * 대기 화면(QUEUED · PROPOSED)이 열려 있는 동안 **30초마다**. 본문 없음 · 204. 90초 안에 다음 신호가 없으면 서버가 요청을 취소한다(D-43).
 * 404 `MATCH_REQUEST_NOT_FOUND` = 이미 큐에서 빠졌다 → `getMatchRequest` 로 다시 맞춘다. 확정된 요청(MATCHED)에는 보내지 않아도 된다.
 */
export const heartbeatMatchRequest = () => request<void>('/match-requests/heartbeat', { method: 'POST' });

/* ---------- proposal — `{id}` 는 proposalId = partyId(`MATCH_PROPOSAL_CREATED` 의 `payload.partyId` · 상태 조회의 `partyId`). 조회(`GET /proposals/{id}`)는 없다 ---------- */
/** 204 · 본문 없음. **멱등** — 이미 확정된 제안에 다시 보내도 204. 404 `PROPOSAL_NOT_FOUND`(만료 · 누가 거절) · 409 `PROPOSAL_CONFLICT` · 403 `NOT_PROPOSAL_MEMBER`. 확정은 `MATCH_CONFIRMED` 가 말한다. */
export const acceptProposal = (partyId: string) => request<void>(`/proposals/${partyId}/accept`, { method: 'POST' });
/** 204 · 본문 없음. 제안이 깨지고 본인만 큐에서 빠진다. **멱등이 아니다** — 재시도는 404. 409 `PROPOSAL_CONFLICT`(이미 확정 · 수락해 놓고 거절). */
export const declineProposal = (partyId: string) => request<void>(`/proposals/${partyId}/decline`, { method: 'POST' });

/* ---------- match party room (platform-api.md "자동 매칭 파티의 방" · P-30) ---------- */
/**
 * `MATCH_CONFIRMED {partyId}` 를 받으면 **조작 없이 바로** 부른다 — "없으면 만들고 있으면 들어간다". 201/200 `{roomId}`(= `partyId` · UUID). 재입장 · 새로고침도 이 요청이다.
 * 404 `MATCH_PARTY_NOT_FOUND`(파티 HASH 가 사라졌다 — 확정 뒤 10분 · 아직 안 들어온 사람만) · 403 `NOT_PARTY_MEMBER` · 409 `IN_OTHER_ROOM` · 409 `ROOM_FULL` ·
 * 503 `ROOM_STATE_UNAVAILABLE`(fail-closed · `Retry-After: 5` — 잠시 뒤 다시). 이 방에 `POST /rooms/{roomId}/members` · `/confirm` 은 쓰지 않는다.
 */
export const enterMatchPartyRoom = (partyId: string) =>
  request<MatchRoomResponse>(`/match-parties/${encodeURIComponent(partyId)}/room`, { method: 'POST' });

/* ---------- reservation — **우리 백엔드에 없다**(`app:reservation` Lambda · 미착수). 부르면 404 다. 화면이 컴파일되게 남겼다 ---------- */
export const createReservation = (body: CreateReservationRequest) =>
  request<ReservationView>('/reservations', { method: 'POST', body });
export const listReservations = () => request<ReservationView[]>('/reservations');
export const getReservation = (id: string) => request<ReservationView>(`/reservations/${id}`);
/** 전체 교체다. 네 필드가 전부 필수이므로 PUT이 정본이다 (docs/14 §11-5). */
export const updateReservation = (id: string, body: CreateReservationRequest) =>
  request<ReservationView>(`/reservations/${id}`, { method: 'PUT', body });
export const cancelReservation = (id: string) => request<void>(`/reservations/${id}`, { method: 'DELETE' });

/* ---------- party — **우리 백엔드에 없다**(확정된 파티를 조회하는 경로를 두지 않는다 — P-31. Ready/PLAYING 도 없다). `PartyRoomPage` 가 컴파일되게 남겼다 — 4단계에서 방 요청(`GET /rooms/{roomId}/members` 등)으로 ---------- */
export const getParty = async (id: string) => normalizeParty(await request<PartyView>(`/parties/${id}`));
/** 토글이 아니라 명시적 대입이다. 준비를 푸는 것은 `{ready:false}`다. */
export const setPartyReady = async (id: string, ready: boolean) =>
  normalizeParty(await request<PartyView>(`/parties/${id}/ready`, { method: 'POST', body: { ready } }));
export const leaveParty = (id: string) => request<void>(`/parties/${id}/leave`, { method: 'POST' });

/* ---------- room signals (platform-api.md "시그널 보내기") ---------- */
/**
 * WebRTC 시그널을 같은 방의 상대에게. 202 는 발행했다는 뜻이지 도착이 아니다 — 답이 없으면 다시 보낸다(WebRtcPartyClient).
 * 403 `NOT_IN_ROOM`(내가 이 방에 없다 — 방 화면을 닫는다) · 404 `TARGET_NOT_IN_ROOM`(상대가 나갔다 — 그 연결을 정리한다).
 * 방의 다른 요청(입장 · 나가기 · 강퇴 · 확정 · 접속 확인 · 목록)은 4단계에서 붙인다.
 */
export const sendRoomSignal = (roomId: string, body: SendRoomSignalRequest) =>
  request<void>(`/rooms/${encodeURIComponent(roomId)}/signals`, { method: 'POST', body });

/* ---------- social ---------- */
export const listFriends = () => request<FriendView[]>('/friends');
export const removeFriend = (userId: string) => request<void>(`/friends/${userId}`, { method: 'DELETE' });
export const listFriendRequests = (direction: FriendRequestDirection = 'RECEIVED') =>
  request<FriendRequestView[]>('/friend-requests', { query: { direction } });
export const sendFriendRequest = (body: CreateFriendRequest) =>
  request<FriendRequestView>('/friend-requests', { method: 'POST', body });
export const acceptFriendRequest = (id: string) => request<FriendView>(`/friend-requests/${id}/accept`, { method: 'POST' });
export const declineFriendRequest = (id: string) => request<void>(`/friend-requests/${id}/decline`, { method: 'POST' });
export const cancelFriendRequest = (id: string) => request<void>(`/friend-requests/${id}`, { method: 'DELETE' });

export const listBlocks = () => request<BlockView[]>('/blocks');
export const blockUser = (body: CreateBlockRequest) => request<BlockView>('/blocks', { method: 'POST', body });
export const unblockUser = (userId: string) => request<void>(`/blocks/${userId}`, { method: 'DELETE' });

/** limit 범위는 1~50이다. 벗어나면 400 VALIDATION_FAILED다 (docs/14 §11-7). */
export const listRecentPlayers = (limit = 20) => request<RecentPlayerView[]>('/recent-players', { query: { limit } });
export const reportUser = (body: CreateReportRequest) => request<void>('/reports', { method: 'POST', body });

/* ---------- normalization ---------- */

/** 원본 계약은 party member 의 `nickname` 이 `null` 일 수 있다고 못박았다. 이름이 없다고 화면이 죽으면 안 되므로 여기서 한 번만 메꾼다(파티 조회는 대응물이 없다 — 위). */
const UNKNOWN_NICKNAME = '알 수 없음';

function normalizeParty(party: PartyView): PartyView {
  return { ...party, members: party.members.map((m) => ({ ...m, nickname: m.nickname ?? UNKNOWN_NICKNAME })) };
}
