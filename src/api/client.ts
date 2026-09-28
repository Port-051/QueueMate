import { request } from './http';
import type {
  BlockView, CreateBlockRequest, CreateFriendRequest, CreateGameAccountRequest, CreateMatchRequest,
  CreateReportRequest, CreateReservationRequest, FriendRequestDirection, FriendRequestView, FriendView,
  GameAccountView, MatchHistoryView, MatchRequestView,
  PartyView, ProposalView, RecentPlayerView, ReservationView, SessionUser, SocialProvider, SocialSignupPending,
  SendRoomSignalRequest, SocialSignupRequest, UpdateUserRequest, UserProfile,
} from './types';

/**
 * 백엔드 엔드포인트. 계정 · 소셜 · 방 · 게시판은 `platform/contracts/platform-api.md`, 매칭 · 제안은 `matching/contracts/openapi.yaml` 이 원본이다.
 * 계약에 없는 경로를 부르지 않고, 계약에 있는 경로를 빠뜨리지 않는다. 아직 원본 프런트의 경로가 남은 절(게임 계정 · 매칭 · 제안 · 파티 · 예약 ·
 * 친구 · 차단 · 신고)은 3 · 5단계에서 바꾼다 — START_HERE.md §3 의 대조표가 그 목록이다.
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
/**
 * 게임 계정 — 아직 원본 프런트의 경로다. 우리 계약은 `PUT /users/me/game-accounts/{game}`(게임마다 본문이 다르다) · `DELETE …/{game}` ·
 * `POST …/{game}/refresh` 이고 목록은 `users/me.gameAccounts` 다 — **3단계에서 바꾼다**(START_HERE.md §2).
 */
export const linkGameAccount = (body: CreateGameAccountRequest) =>
  request<GameAccountView>('/users/me/game-accounts', { method: 'POST', body });
export const unlinkGameAccount = (id: string) =>
  request<void>(`/users/me/game-accounts/${id}`, { method: 'DELETE' });

/* ---------- game config — 없다. 게임 · 모드 · 티어는 정적 상수 `domain/gameCatalog.ts`(seed 의 사본 · 2026-09-28 소유자 결정) ---------- */

/* ---------- realtime matching ---------- */
export const createMatchRequest = (body: CreateMatchRequest) =>
  request<MatchRequestView>('/match-requests', { method: 'POST', body });
export const getMatchRequest = (id: string) => request<MatchRequestView>(`/match-requests/${id}`);
export const listMatchHistory = () => request<MatchHistoryView[]>('/match-requests/history');
export const cancelMatchRequest = (id: string) => request<void>(`/match-requests/${id}`, { method: 'DELETE' });

/* ---------- proposal ---------- */
/** 참가자만 조회할 수 있다. 참가자가 아니면 403이 아니라 404다. */
export const getProposal = async (id: string) => normalizeProposal(await request<ProposalView>(`/proposals/${id}`));
/** 같은 사람이 두 번 수락해도 200이다. 재시도해도 안전하다 (docs/14 §5.2). */
export const acceptProposal = async (id: string) =>
  normalizeProposal(await request<ProposalView>(`/proposals/${id}/accept`, { method: 'POST' }));
export const declineProposal = (id: string) => request<void>(`/proposals/${id}/decline`, { method: 'POST' });

/* ---------- reservation ---------- */
export const createReservation = (body: CreateReservationRequest) =>
  request<ReservationView>('/reservations', { method: 'POST', body });
export const listReservations = () => request<ReservationView[]>('/reservations');
export const getReservation = (id: string) => request<ReservationView>(`/reservations/${id}`);
/** 전체 교체다. 네 필드가 전부 필수이므로 PUT이 정본이다 (docs/14 §11-5). */
export const updateReservation = (id: string, body: CreateReservationRequest) =>
  request<ReservationView>(`/reservations/${id}`, { method: 'PUT', body });
export const cancelReservation = (id: string) => request<void>(`/reservations/${id}`, { method: 'DELETE' });

/* ---------- party ---------- */
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

/**
 * 계약은 party/proposal member의 `nickname`이 `null`일 수 있다고 못박았다 (docs/14 §7.1).
 * 이름이 없다고 화면이 죽으면 안 되므로 여기서 한 번만 메꾼다.
 * 필드를 지어내는 것이 아니라 표시용 빈 값을 채우는 것이다.
 */
const UNKNOWN_NICKNAME = '알 수 없음';

function normalizeParty(party: PartyView): PartyView {
  return { ...party, members: party.members.map((m) => ({ ...m, nickname: m.nickname ?? UNKNOWN_NICKNAME })) };
}

function normalizeProposal(proposal: ProposalView): ProposalView {
  return { ...proposal, members: proposal.members.map((m) => ({ ...m, nickname: m.nickname ?? UNKNOWN_NICKNAME })) };
}
