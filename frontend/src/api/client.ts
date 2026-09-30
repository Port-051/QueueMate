import { request } from './http';
import type {
  AutoJoinResponse, BlockListResponse, BlockView, CreateBlockRequest, CreateFriendRequest, CreateMatchRequest, CreatePostRequest,
  CreateReportRequest, CreateReservationRequest, FriendListResponse, FriendRequestDirection, FriendRequestListResponse, FriendRequestView, FriendView,
  GameAccountRequest, GameKey, GameProfile, MatchRequestView, MatchRoomResponse, MyRoomResponse,
  PostListResponse, PostResponse, RecentPlayerListResponse, ReportResponse, ReservationView, RoomMembersResponse, SessionUser, SocialProvider, SocialSignupPending,
  SendRoomSignalRequest, SocialSignupRequest, UpdatePostRequest, UpdateUserRequest, UserProfile,
} from './types';

/**
 * 백엔드 엔드포인트. 계정 · 소셜 · 방 · 게시판은 `platform/contracts/platform-api.md`, 매칭 · 제안은 `matching/contracts/openapi.yaml` 이 원본이다.
 * 계약에 없는 경로를 부르지 않고, 계약에 있는 경로를 빠뜨리지 않는다. **대응물이 없는 절(예약 · 아바타 — 부르면 404 · 화면을 남긴다, START_HERE.md §5)**은
 * 각 절의 주석 참조. 모집 글 · 방은 4단계, 친구 · 차단 · 신고 · 최근 함께한 사람은 5단계(2026-09-29)에서 우리 경로가 됐다.
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
/**
 * TEMP-DEV-LOGIN — 개발용 로그인(2026-09-29 소유자 결정). 소셜 앱 키 없이 로컬에서 로그인 상태를 만든다.
 * `POST /auth/dev-login {nickname}` → 200 `{userId, nickname}` + 로그인 쿠키. 백엔드의 `DEV_LOGIN_ENABLED` 가 꺼져 있으면 404, 닉네임이 틀리면 400 `VALIDATION_FAILED`.
 * 부르는 곳은 `components/DevLoginPanel.tsx` 하나이고 로그인 화면이 `import.meta.env.DEV` 일 때만 그린다 — 운영 빌드에서는 번들에서 빠진다.
 * 걷어낼 때는 `grep -rn TEMP-DEV-LOGIN src` 로 찾아 통째로 지운다.
 */
export const devLogin = (nickname: string) =>
  request<SessionUser>('/auth/dev-login', { method: 'POST', body: { nickname }, noRetry: true });

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
 * **LOL · PUBG 는 저장하기 전에 게임사 API 를 동기로 긁는다 — 상한 30초**(PUBG 는 2026-09-29). 응답에 `tiers` · `stats` 가 바로 들어 있다.
 * 못 찾으면 404(LOL `RIOT_ID_NOT_FOUND` — 이름#태그 · PUBG `PUBG_PLAYER_NOT_FOUND` — 그 서버에 그 닉네임, 대소문자까지), API 장애 · 한도 초과 · 시간 초과 · 키 없음은
 * 503 `GAME_STATS_UNAVAILABLE` — 둘 다 저장하지 않는다. 400 `VALIDATION_FAILED` 는 `details[0]` 이 `"필드: 사유"` 다(PUBG 에 `tier` 를 보내도 400).
 * 429 `TOO_MANY_STATS_REFRESHES` + `Retry-After: 60` — 서버가 **지금 그 계정의 전적을 가져오는 중**이다(로그인 직후의 다시 받기와 겹칠 수 있다 · 아무것도 바뀌지 않았다).
 */
export const putGameAccount = (game: GameKey, body: GameAccountRequest) =>
  request<GameProfile>(`/users/me/game-accounts/${game}`, { method: 'PUT', body });
/** 연결 해제. 없어도 204. */
export const deleteGameAccount = (game: GameKey) =>
  request<void>(`/users/me/game-accounts/${game}`, { method: 'DELETE' });
/*
 * 전적 갱신 요청(`POST …/game-accounts/{game}/refresh`)은 **없다**(2026-09-30 소유자 결정 · P-42 — 부르면 404). 서버가 로그인 · 재발급 때
 * 마지막으로 받은 지 1시간이 지난 LoL · PUBG 전적을 뒤에서 다시 받고, 그 값은 다음 `GET /users/me` · 게시판 목록에 실린다(알림은 없다).
 */

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

/* ---------- party(원본의 Ready/PLAYING · `GET /parties/{id}`) — **없다**(P-31). 확정된 파티는 방이다 — 위 `enterMatchPartyRoom` 과 방의 요청(아래) ---------- */

/* ---------- 모집 글 (platform-api.md "모집 글 · 목록" — 글이 곧 방이다: `roomId = String(postId)`) ---------- */
/**
 * 게시판 목록 — `game` 필수(대문자) · `limit` 1~100(없으면 20 · 벗어나면 400) · `cursor` 는 앞 응답의 `nextCursor`. `id` 내림차순이고 끝난 글도 `status` 로 섞여 온다(P-20).
 * 차단 관계인 사람이 방 안에 있는 글은 빠진다(D-20). Redis 를 못 읽으면 방 정보를 비운 채 글만 온다(fail-open). `BOARD_CHANGED` 뒤에는 **커서 없이** 맨 위부터 `limit` 으로 다시 받는다.
 */
export const listPosts = (game: GameKey, limit = 20, cursor?: number) =>
  request<PostListResponse>('/posts', { query: { game, limit, cursor } });
/** 단건. 차단으로 숨겨진 글도 없는 글과 같은 404 `POST_NOT_FOUND`. 방 안 사람의 카드(`members[].profile`)는 이것으로 붙인다 — `GET /rooms/{roomId}/members` 에는 id 뿐이다. */
export const getPost = (postId: number) => request<PostResponse>(`/posts/${postId}`);
/**
 * 글 쓰기 = 방 만들기 — 201 이면 그 번호의 방이 생겼고 내가 방장으로 들어와 있다(`members = [방장]`). 방을 못 만들면 글도 되돌려진다.
 * 409 `ALREADY_RECRUITING`(모집 중인 내 글이 있다) · `ALREADY_QUEUED`(자동 매칭 중) · `IN_OTHER_ROOM`(이미 방에 있다) · 503 `ROOM_STATE_UNAVAILABLE`(+`Retry-After: 5`) · 400 `VALIDATION_FAILED`(`details[0]` = "필드: 사유").
 */
export const createPost = (body: CreatePostRequest) => request<PostResponse>('/posts', { method: 'POST', body });
/** 방장만 · 모집 중일 때만 · **방에 방장 말고 누가 있으면 409 `ROOM_HAS_OTHER_MEMBERS`**(P-19). 403 `NOT_POST_HOST` · 409 `POST_NOT_RECRUITING` · 503(방 안을 못 읽음). */
export const updatePost = (postId: number, body: UpdatePostRequest) => request<PostResponse>(`/posts/${postId}`, { method: 'PATCH', body });
/** 지우지 않고 만료로 바꾸고 **방도 닫는다**(`ROOM_CLOSED` — 있던 전원의 입장 표시 키가 지워진다). 204. 확정된 글은 409 `POST_CONFIRMED` · 403 `NOT_POST_HOST`. */
export const deletePost = (postId: number) => request<void>(`/posts/${postId}`, { method: 'DELETE' });

/* ---------- 방 (platform-api.md "방" — `{roomId}` 는 게시판 방이면 글 번호의 십진 문자열, 자동 매칭 방이면 UUID. 두 방이 같은 요청을 쓴다) ---------- */
const room = (roomId: string) => `/rooms/${encodeURIComponent(roomId)}`;
/**
 * 게시판 방 입장 — 본문 없음 · **201 들어감 / 200 이미 있음**(둘 다 본문 없음). 자동 매칭 방에는 쓰지 않는다(그쪽은 `enterMatchPartyRoom`).
 * 글의 검사가 먼저다 — 404 `POST_NOT_FOUND`(없거나 차단으로 숨김) → 409 `POST_NOT_RECRUITING` → 503 → 방의 Lua(409 `ALREADY_QUEUED` · `ROOM_FULL` · `IN_OTHER_ROOM` · `ROOM_CONFIRMED` · 404 `ROOM_NOT_FOUND`).
 */
export const enterRoom = (roomId: string) => request<void>(`${room(roomId)}/members`, { method: 'POST' });
/** 방 안 사람 — 방 안의 사람만(밖이면 403 `NOT_IN_ROOM` — 방 화면을 닫는다). `hostId` 가 바뀌면 승계다(D-23). */
export const getRoomMembers = (roomId: string) => request<RoomMembersResponse>(`${room(roomId)}/members`);
/** 나가기 — **늘 204**(없는 방 · 안 들어간 방도). 미확정 방의 방장이 나가면 방이 닫히고 글이 만료된다 · 확정한 방은 승계된다. */
export const leaveRoom = (roomId: string) => request<void>(`${room(roomId)}/members/me`, { method: 'DELETE' });
/** 강퇴 — 방장만. 204. 403 `NOT_HOST` · 400 `CANNOT_KICK_SELF` · 404 `TARGET_NOT_IN_ROOM` · 404 `ROOM_NOT_FOUND`. */
export const kickMember = (roomId: string, targetUserId: string) =>
  request<void>(`${room(roomId)}/members/${encodeURIComponent(targetUserId)}`, { method: 'DELETE' });
/**
 * 방장 확정 — 게시판 방만 · 방장만 · 2명 이상 · **되돌릴 수 없다**(REOPEN 없음 — 화면이 한 번 더 묻는다). 204 확정 / 200 이미 확정.
 * 그 순간 방 안의 전원이 파티원이 되고(`ROOM_CONFIRMED {members}`) 글은 `CONFIRMED` 다. 403 `NOT_HOST` · 409 `NOT_ENOUGH_MEMBERS` · 409 `POST_NOT_RECRUITING` · 404.
 */
export const confirmRoom = (roomId: string) => request<void>(`${room(roomId)}/confirm`, { method: 'POST' });
/**
 * 접속 확인 — 방에 있는 동안 **1분마다**. 204. 방의 수명(600초)을 늘린다(미확정 방은 방장의 것만 방을 살린다).
 * 403 `NOT_IN_ROOM` · 404 `ROOM_NOT_FOUND` 는 둘 다 "이 방에 없다" — 방 화면을 닫는다. TTL 로 사라진 방은 알림이 없어 이것으로만 안다.
 */
export const roomHeartbeat = (roomId: string) => request<void>(`${room(roomId)}/heartbeat`, { method: 'POST' });
/** 내 방 — 새로 열었을 때 복구. `{roomId: null}` 이면 없다. */
export const getMyRoom = () => request<MyRoomResponse>('/rooms/me');
/**
 * WebRTC 시그널을 같은 방의 상대에게. 202 는 발행했다는 뜻이지 도착이 아니다 — 답이 없으면 다시 보낸다(WebRtcPartyClient).
 * 403 `NOT_IN_ROOM`(내가 이 방에 없다 — 방 화면을 닫는다) · 404 `TARGET_NOT_IN_ROOM`(상대가 나갔다 — 그 연결을 정리한다).
 */
export const sendSignal = (roomId: string, body: SendRoomSignalRequest) =>
  request<void>(`${room(roomId)}/signals`, { method: 'POST', body });

/* ---------- social (platform-api.md "차단" · "친구 · 신고 · 최근 함께한 사람" — 5단계 · 2026-09-29) — 경로의 `{userId}` · `{requestId}` 는 숫자여야 한다(아니면 400) ---------- */
/** 친구 목록 — 닉네임순. 응답은 `{friends: […]}` 로 감싸여 있다. */
export const listFriends = () => request<FriendListResponse>('/friends');
/** 친구 끊기 — 친구가 아니어도 204(멱등). 상대에게 알리지 않는다. */
export const removeFriend = (userId: string) => request<void>(`/friends/${encodeURIComponent(userId)}`, { method: 'DELETE' });
/** 대기 중인 친구 요청 — `direction` 은 **대문자 그대로**(`RECEIVED` 기본 · `SENT`). 새것이 먼저. `{requests: […]}`. */
export const listFriendRequests = (direction: FriendRequestDirection = 'RECEIVED') =>
  request<FriendRequestListResponse>('/friend-requests', { query: { direction } });
/**
 * 친구 요청 — 본문 `{userId}`(상대의 사용자 번호 · 문자열). 201 친구 요청 한 줄. 상대에게 `FRIEND_REQUEST_RECEIVED` 가 간다.
 * 409 `ALREADY_FRIENDS` · `FRIEND_REQUEST_ALREADY_SENT` · `FRIEND_REQUEST_ALREADY_RECEIVED`(상대가 이미 보냈다 — 받은 요청을 수락하면 된다) · 400 `CANNOT_FRIEND_SELF` ·
 * 404 `USER_NOT_FOUND`(없는 번호 · 숫자가 아님 · **어느 방향이든 차단 관계** — 차단당한 사실이 새지 않게 같은 404 다).
 */
export const sendFriendRequest = (body: CreateFriendRequest) =>
  request<FriendRequestView>('/friend-requests', { method: 'POST', body });
/** 수락 — 200 새 친구 `{userId, nickname, since}`. 보낸 사람에게 `FRIEND_REQUEST_ACCEPTED` 가 간다. 404 `FRIEND_REQUEST_NOT_FOUND`(없음 · 내가 받은 것이 아님 · 이미 처리됨). */
export const acceptFriendRequest = (requestId: number) => request<FriendView>(`/friend-requests/${requestId}/accept`, { method: 'POST' });
/** 거절 — 204. 상대에게 알리지 않는다. 404 `FRIEND_REQUEST_NOT_FOUND`. */
export const declineFriendRequest = (requestId: number) => request<void>(`/friend-requests/${requestId}/decline`, { method: 'POST' });
/** 거두기 — 보낸 사람이. 204. 404 `FRIEND_REQUEST_NOT_FOUND`. */
export const cancelFriendRequest = (requestId: number) => request<void>(`/friend-requests/${requestId}`, { method: 'DELETE' });

/** 내가 차단한 사람 — 새로 차단한 사람이 먼저. `{blocks: […]}`. 나를 차단한 사람은 알 수 없다. */
export const listBlocks = () => request<BlockListResponse>('/blocks');
/**
 * 차단 — 본문 `{userId}`(문자열). 201 `{userId, nickname, createdAt}`. 이미 맺은 친구 관계 · 같은 방에 있는 상태는 건드리지 않는다(미정 그대로).
 * 그 뒤로 그 사람이 있는 글은 목록에서 빠지고 매칭에서도 만나지 않는다. 409 `ALREADY_BLOCKED` · 400 `CANNOT_BLOCK_SELF` · 404 `USER_NOT_FOUND`.
 */
export const blockUser = (body: CreateBlockRequest) => request<BlockView>('/blocks', { method: 'POST', body });
/** 차단 해제 — 차단한 적 없어도 204(멱등). */
export const unblockUser = (userId: string) => request<void>(`/blocks/${encodeURIComponent(userId)}`, { method: 'DELETE' });

/** 최근 함께한 사람 — 최근순 · 50명까지 · 차단 관계 제외. `?limit` 은 없다. `{players: […]}`. 확정된 파티가 닫힐 때 채워진다(P-25 · P-30). */
export const listRecentPlayers = () => request<RecentPlayerListResponse>('/recent-players');
/** 신고 — 201 `{reportId, createdAt}`. 접수만 한다(처리 화면 · 제재 없음). 같은 사람을 여러 번 신고할 수 있다. 400 `CANNOT_REPORT_SELF` · 404 `USER_NOT_FOUND` · 400 `VALIDATION_FAILED`(`details[0]`). */
export const reportUser = (body: CreateReportRequest) => request<ReportResponse>('/reports', { method: 'POST', body });
