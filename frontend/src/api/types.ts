/** contracts/openapi.yaml 1:1 매핑. 계약에 없는 필드를 임의로 추가하지 않는다. */

export type GameKey = 'LOL' | 'VALORANT' | 'PUBG';
export type VoicePreference = 'REQUIRED' | 'OPTIONAL' | 'NO_VOICE';
export type PlayPurpose = 'RANK_UP' | 'NORMAL' | 'FUN';
export type PlayAmount = 'ONE_GAME' | 'TWO_PLUS';
export type KeyConditionType = 'POSITION' | 'ROLE' | 'PLAY_STYLE';

export interface KeyCondition {
  type: KeyConditionType;
  value: string;
}

export interface MatchCondition {
  game: GameKey;
  modeKey: string;
  keyCondition: KeyCondition;
  voicePreference: VoicePreference;
  playPurpose: PlayPurpose;
}

/* ---------- auth / user ---------- */
export interface SignupRequest { email: string; password: string; nickname: string; }
export interface LoginRequest { email: string; password: string; }
export interface TokenResponse { accessToken: string; refreshToken: string; tokenType: 'Bearer'; expiresIn: number; }
export interface RefreshRequest { refreshToken: string; }
export interface UserProfile { id: string; nickname: string; avatarUrl?: string | null; }
export interface UpdateUserRequest { nickname?: string; avatarUrl?: string | null; }

export interface GameAccountView {
  id: string;
  game: GameKey;
  externalGameId: string;
  region?: string | null;
  rankCode?: string | null;
  verifiedAt?: string | null;
}
export interface CreateGameAccountRequest { game: GameKey; externalGameId: string; region?: string | null; }

/* ---------- realtime matching ---------- */
export type CreateMatchRequest = MatchCondition;
export type MatchRequestStatus = 'QUEUED' | 'PROPOSED' | 'MATCHED' | 'CANCELLED' | 'EXPIRED';

export interface MatchRequestView {
  id: string;
  status: MatchRequestStatus;
  queuedAt: string;
  proposalId?: string | null;
}

export type ProposalStatus = 'PENDING' | 'CONFIRMED' | 'DECLINED' | 'EXPIRED' | 'CANCELLED';
export type Acceptance = 'PENDING' | 'ACCEPTED' | 'DECLINED';

export interface ProposalMember { userId: string; nickname: string; acceptance: Acceptance; }
export interface ProposalView {
  id: string;
  status: ProposalStatus;
  expiresAt: string;
  members: ProposalMember[];
  partyId?: string | null;
}

/* ---------- reservation ---------- */
export type ReservationStatus = 'ACTIVE' | 'PROPOSED' | 'MATCHED' | 'CANCELLED' | 'EXPIRED' | 'COMPLETED';

export interface CreateReservationRequest {
  condition: MatchCondition;
  availableFrom: string;
  availableTo: string;
  playAmount: PlayAmount;
}

export interface ReservationView {
  id: string;
  status: ReservationStatus;
  condition: MatchCondition;
  availableFrom: string;
  availableTo: string;
  playAmount: PlayAmount;
  createdAt?: string;
  scheduledStart?: string | null;
  proposalId?: string | null;
  partyId?: string | null;
}

/* ---------- party ---------- */
export type PartyStatus = 'OPEN' | 'READY' | 'PLAYING' | 'CLOSED';
export interface PartyMemberView { userId: string; nickname: string; ready: boolean; }
export interface PartyView {
  id: string;
  game: GameKey;
  modeKey: string;
  targetSize: number;
  status: PartyStatus;
  members: PartyMemberView[];
}

/* ---------- social ---------- */
export interface FriendView { userId: string; nickname: string; avatarUrl?: string | null; friendedAt: string; }
export type FriendRequestDirection = 'RECEIVED' | 'SENT';
export type FriendRequestStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED';
export interface FriendRequestView {
  id: string;
  direction: FriendRequestDirection;
  counterpartUserId: string;
  counterpartNickname: string;
  status: FriendRequestStatus;
  createdAt: string;
}
export interface CreateFriendRequest { targetUserId: string; }
export interface BlockView { userId: string; nickname: string; blockedAt: string; }
export interface CreateBlockRequest { targetUserId: string; }

export interface RecentPlayerView {
  userId: string;
  nickname: string;
  avatarUrl?: string | null;
  lastPlayedAt: string;
  playCount: number;
  friend: boolean;
}

export type ReportReason = 'ABUSIVE_LANGUAGE' | 'HARASSMENT' | 'CHEATING' | 'TROLLING_OR_AFK' | 'INAPPROPRIATE_PROFILE' | 'OTHER';
export interface CreateReportRequest {
  targetUserId: string;
  reason: ReportReason;
  description?: string | null;
  partyId?: string | null;
}

/* ---------- websocket (contracts/events.md) ---------- */
export type ServerEventType =
  | 'MATCH_QUEUE_UPDATED'
  | 'MATCH_PROPOSAL_CREATED'
  | 'MATCH_PROPOSAL_EXPIRED'
  | 'MATCH_CONFIRMED'
  | 'MATCH_CANCELLED'
  | 'RESERVATION_UPDATED'
  | 'RESERVATION_PROPOSAL_CREATED'
  | 'PARTY_MEMBER_JOINED'
  | 'PARTY_MEMBER_LEFT'
  | 'PARTY_READY_CHANGED'
  | 'PARTY_CLOSED'
  | 'FRIEND_REQUEST_RECEIVED'
  | 'FRIEND_REQUEST_UPDATED'
  | 'PARTY_INVITE_RECEIVED'
  | 'WEBRTC_SIGNAL';

export interface ServerEvent<T = Record<string, unknown>> {
  type: ServerEventType;
  eventId: string;
  occurredAt: string;
  payload: T;
}

export type SignalType = 'OFFER' | 'ANSWER' | 'ICE';

export interface WebRtcSignalMessage {
  type: 'WEBRTC_SIGNAL';
  partyId: string;
  targetUserId: string;
  signalType: SignalType;
  data: Record<string, unknown>;
}

/* payload shapes the client relies on */
export interface QueueUpdatedPayload { requestId: string; candidateCount: number; waitingSeconds: number; }
export interface ProposalCreatedPayload { proposal: ProposalView; requestId?: string; reservationId?: string; }
export interface MatchConfirmedPayload { proposalId: string; partyId: string; }
export interface PartyEventPayload { partyId: string; userId?: string; nickname?: string; ready?: boolean; }
export interface FriendEventPayload { request: FriendRequestView; }
export interface PartyInvitePayload { partyId: string; fromNickname: string; }
export interface ReservationUpdatedPayload { reservation: ReservationView; }
export interface WebRtcSignalPayload { partyId: string; fromUserId: string; signalType: SignalType; data: Record<string, unknown>; }
