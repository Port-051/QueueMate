import { request } from './http';
import type {
  BlockView, CreateBlockRequest, CreateFriendRequest, CreateGameAccountRequest, CreateMatchRequest,
  CreateReportRequest, CreateReservationRequest, FriendRequestDirection, FriendRequestView, FriendView,
  GameAccountView, LoginRequest, MatchRequestView, PartyView, ProposalView, RecentPlayerView,
  ReservationView, SignupRequest, TokenResponse, UpdateUserRequest, UserProfile,
} from './types';

/* ---------- auth ---------- */
export const signup = (body: SignupRequest) => request<UserProfile>('/auth/signup', { method: 'POST', body });
export const login = (body: LoginRequest) => request<TokenResponse>('/auth/login', { method: 'POST', body });
export const refresh = (refreshToken: string) => request<TokenResponse>('/auth/refresh', { method: 'POST', body: { refreshToken } });
export const logout = (refreshToken: string) => request<void>('/auth/logout', { method: 'POST', body: { refreshToken } });

/* ---------- user ---------- */
export const getMe = () => request<UserProfile>('/users/me');
export const updateMe = (body: UpdateUserRequest) => request<UserProfile>('/users/me', { method: 'PATCH', body });
export const getGameAccounts = () => request<GameAccountView[]>('/users/me/game-accounts');
export const linkGameAccount = (body: CreateGameAccountRequest) => request<GameAccountView>('/users/me/game-accounts', { method: 'POST', body });
export const unlinkGameAccount = (id: string) => request<void>(`/users/me/game-accounts/${id}`, { method: 'DELETE' });

/* ---------- realtime matching ---------- */
export const createMatchRequest = (body: CreateMatchRequest) => request<MatchRequestView>('/match-requests', { method: 'POST', body });
export const getMatchRequest = (id: string) => request<MatchRequestView>(`/match-requests/${id}`);
export const cancelMatchRequest = (id: string) => request<void>(`/match-requests/${id}`, { method: 'DELETE' });
export const acceptProposal = (id: string) => request<ProposalView>(`/proposals/${id}/accept`, { method: 'POST' });
export const declineProposal = (id: string) => request<void>(`/proposals/${id}/decline`, { method: 'POST' });

/* ---------- reservation ---------- */
export const createReservation = (body: CreateReservationRequest) => request<ReservationView>('/reservations', { method: 'POST', body });
export const listReservations = () => request<ReservationView[]>('/reservations');
export const getReservation = (id: string) => request<ReservationView>(`/reservations/${id}`);
export const updateReservation = (id: string, body: CreateReservationRequest) => request<ReservationView>(`/reservations/${id}`, { method: 'PATCH', body });
export const cancelReservation = (id: string) => request<void>(`/reservations/${id}`, { method: 'DELETE' });

/* ---------- party ---------- */
export const getParty = (id: string) => request<PartyView>(`/parties/${id}`);
export const setPartyReady = (id: string, ready: boolean) => request<PartyView>(`/parties/${id}/ready`, { method: 'POST', body: { ready } });
export const leaveParty = (id: string) => request<void>(`/parties/${id}/leave`, { method: 'POST' });
export const invitePartyMember = (id: string, friendUserId: string) => request<void>(`/parties/${id}/invite/${friendUserId}`, { method: 'POST' });

/* ---------- social ---------- */
export const listFriends = () => request<FriendView[]>('/friends');
export const removeFriend = (userId: string) => request<void>(`/friends/${userId}`, { method: 'DELETE' });
export const listFriendRequests = (direction: FriendRequestDirection = 'RECEIVED') =>
  request<FriendRequestView[]>('/friend-requests', { query: { direction } });
export const sendFriendRequest = (body: CreateFriendRequest) => request<FriendRequestView>('/friend-requests', { method: 'POST', body });
export const acceptFriendRequest = (id: string) => request<FriendView>(`/friend-requests/${id}/accept`, { method: 'POST' });
export const declineFriendRequest = (id: string) => request<void>(`/friend-requests/${id}/decline`, { method: 'POST' });
export const cancelFriendRequest = (id: string) => request<void>(`/friend-requests/${id}`, { method: 'DELETE' });

export const listBlocks = () => request<BlockView[]>('/blocks');
export const blockUser = (body: CreateBlockRequest) => request<BlockView>('/blocks', { method: 'POST', body });
export const unblockUser = (userId: string) => request<void>(`/blocks/${userId}`, { method: 'DELETE' });

export const listRecentPlayers = (limit = 20) => request<RecentPlayerView[]>('/recent-players', { query: { limit } });
export const reportUser = (body: CreateReportRequest) => request<void>('/reports', { method: 'POST', body });
