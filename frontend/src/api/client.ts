import { request } from './http';
import type {
  BlockView, CreateBlockRequest, CreateFriendRequest, CreateGameAccountRequest, CreateMatchRequest,
  CreateReportRequest, CreateReservationRequest, FriendRequestDirection, FriendRequestView, FriendView,
  GameAccountView, GameKey, GameView, LoginRequest, MatchRequestView, MatchSchemaView,
  PartyView, ProposalView, RecentPlayerView, ReservationView, SignupRequest, TokenResponse,
  UpdateUserRequest, UserProfile,
} from './types';

/**
 * contracts/openapi.yaml v2.0.0의 엔드포인트 38개.
 * 계약에 없는 경로를 부르지 않고, 계약에 있는 경로를 빠뜨리지 않는다.
 */

/* ---------- auth (인증 불필요, docs/14 §0.4) ---------- */
export const signup = (body: SignupRequest) =>
  request<UserProfile>('/auth/signup', { method: 'POST', body, anonymous: true });
export const login = (body: LoginRequest) =>
  request<TokenResponse>('/auth/login', { method: 'POST', body, anonymous: true });
export const refresh = (refreshToken: string) =>
  request<TokenResponse>('/auth/refresh', { method: 'POST', body: { refreshToken }, anonymous: true });
/** refresh token 자체가 자격 증명이라 access token을 요구하지 않는다 (docs/14 §11-12). */
export const logout = (refreshToken: string) =>
  request<void>('/auth/logout', { method: 'POST', body: { refreshToken }, anonymous: true });

/* ---------- user ---------- */
export const getMe = () => request<UserProfile>('/users/me');
/** 부분 수정이다. `avatarUrl: null`을 보내면 지워지고, 키를 빼면 유지된다. */
export const updateMe = (body: UpdateUserRequest) => request<UserProfile>('/users/me', { method: 'PATCH', body });
export const getGameAccounts = () => request<GameAccountView[]>('/users/me/game-accounts');
export const linkGameAccount = (body: CreateGameAccountRequest) =>
  request<GameAccountView>('/users/me/game-accounts', { method: 'POST', body });
export const unlinkGameAccount = (id: string) =>
  request<void>(`/users/me/game-accounts/${id}`, { method: 'DELETE' });

/* ---------- game config ---------- */
export const listGames = () => request<GameView[]>('/games');
/** 조건 폼의 선택지 전체. 프론트가 폼을 그리는 근거다 (docs/14 §3.3). */
export const getMatchSchema = (gameKey: GameKey) => request<MatchSchemaView>(`/games/${gameKey}/match-schema`);

/* ---------- realtime matching ---------- */
export const createMatchRequest = (body: CreateMatchRequest) =>
  request<MatchRequestView>('/match-requests', { method: 'POST', body });
export const getMatchRequest = (id: string) => request<MatchRequestView>(`/match-requests/${id}`);
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
