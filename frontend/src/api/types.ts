/**
 * contracts/openapi.yaml v2.0.0 + contracts/events.md 1:1 매핑.
 * 계약에 없는 필드를 임의로 추가하지 않는다. 계약이 정본이고 구현이 따라간다.
 */

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

/** 소셜 로그인. DEV는 로컬 개발용 가짜 제공자라 운영에는 뜨지 않는다. */
export type OAuthProviderKey = 'KAKAO' | 'NAVER' | 'DEV';
export interface OAuthProviderView {
  provider: OAuthProviderKey;
  displayName: string;
  /** 브라우저를 이동시킬 경로. 프론트엔드가 직접 조립하지 않는다. */
  authorizeUrl: string;
}
export interface OAuthExchangeRequest { code: string; }
export interface UserProfile { id: string; nickname: string; avatarUrl: string | null; }

/**
 * 부분 수정이다. **키를 생략한 항목은 건드리지 않는다.**
 * `avatarUrl: null`을 명시하면 아바타를 지운다. `nickname: null`은 400이다 (openapi UpdateUserRequest).
 */
export interface UpdateUserRequest { nickname?: string; avatarUrl?: string | null; }

export interface GameAccountView {
  id: string;
  game: GameKey;
  externalGameId: string;
  region: string | null;
  rankCode: string | null;
  verifiedAt: string | null;
}
export interface CreateGameAccountRequest { game: GameKey; externalGameId: string; region?: string | null; }

/* ---------- game config ---------- */
export interface GameView {
  game: GameKey;
  keyConditionType: KeyConditionType;
}

/** targetPartySize는 서버가 정한다. 클라이언트는 파티 정원을 보내지 않는다 (docs/03 §9). */
export interface GameModeView {
  modeKey: string;
  targetPartySize: number;
  /** true면 파티 안에서 keyCondition 값이 겹칠 수 없다 (LoL POSITION hard rule). */
  roleUniqueness: boolean;
}

/** 프론트가 조건 폼을 그리는 근거 (docs/14 §3.3). */
export interface MatchSchemaView {
  game: GameKey;
  modes: GameModeView[];
  keyCondition: { type: KeyConditionType; values: string[] };
  voicePreferences: VoicePreference[];
  playPurposes: PlayPurpose[];
}

/* ---------- realtime matching ---------- */
export type CreateMatchRequest = MatchCondition;
export type MatchRequestStatus = 'QUEUED' | 'PROPOSED' | 'MATCHED' | 'CANCELLED' | 'EXPIRED';

export interface MatchRequestView {
  id: string;
  status: MatchRequestStatus;
  /** 최초 대기 시작 시각. 제안을 거절하고 큐로 돌아와도 유지된다. */
  queuedAt: string;
  proposalId: string | null;
}

export type ProposalStatus = 'PENDING' | 'CONFIRMED' | 'DECLINED' | 'EXPIRED' | 'CANCELLED';
export type Acceptance = 'PENDING' | 'ACCEPTED' | 'DECLINED';

export interface ProposalMember {
  userId: string;
  /** 서버가 null을 줄 수 있다. `client.ts`가 정규화해서 넘긴다. */
  nickname: string;
  acceptance: Acceptance;
}

export interface ProposalView {
  id: string;
  status: ProposalStatus;
  /** 절대 시각이다. 남은 초는 클라이언트가 계산한다. */
  expiresAt: string;
  members: ProposalMember[];
  /** 확정 전에는 null이고 CONFIRMED 이후에만 채워진다. */
  partyId: string | null;
}

/* ---------- reservation ---------- */
export type ReservationStatus = 'ACTIVE' | 'PROPOSED' | 'MATCHED' | 'CANCELLED' | 'EXPIRED' | 'COMPLETED';

export interface CreateReservationRequest {
  condition: MatchCondition;
  availableFrom: string;
  availableTo: string;
  playAmount: PlayAmount;
}

/**
 * v2에서 `partyId`가 제거됐다 (docs/14 §11-13).
 * 파티에 가려면 `proposalId`로 `GET /proposals/{id}`를 불러 `partyId`를 읽는다.
 * 실시간 매칭(MatchRequestView)과 같은 규칙이다.
 */
export interface ReservationView {
  id: string;
  status: ReservationStatus;
  condition: MatchCondition;
  availableFrom: string;
  availableTo: string;
  playAmount: PlayAmount;
  createdAt: string;
  /** 매칭이 붙은 뒤 정해지는 약속 시각. 겹치는 구간 중 가장 이른 30분 슬롯. */
  scheduledStart: string | null;
  proposalId: string | null;
}

/* ---------- party ---------- */
export type PartyStatus = 'OPEN' | 'READY' | 'PLAYING' | 'CLOSED';

export interface PartyMemberView {
  userId: string;
  /** 서버가 null을 줄 수 있다 (docs/14 §7.1). `client.ts`가 정규화해서 넘긴다. */
  nickname: string;
  ready: boolean;
}

export interface PartyView {
  id: string;
  game: GameKey;
  modeKey: string;
  targetSize: number;
  status: PartyStatus;
  members: PartyMemberView[];
}

/* ---------- social ---------- */
export interface FriendView { userId: string; nickname: string; avatarUrl: string | null; friendedAt: string; }
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
  avatarUrl: string | null;
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

/**
 * Server → Client. **백엔드가 실제로 발행하는 것만 둔다.**
 *
 * 계약에는 17종이 적혀 있지만 `MATCH_QUEUE_UPDATED` `RESERVATION_UPDATED`
 * `PARTY_MEMBER_JOINED` `FRIEND_REQUEST_RECEIVED` `FRIEND_REQUEST_UPDATED`
 * `PARTY_INVITE_RECEIVED` 6종은 enum 선언만 있고 발행 지점이 0건이다. 영영 오지 않는
 * 이벤트를 기다리는 화면은 멈춘 것처럼 보이므로 타입에서 지우고 REST 조회로 대체했다.
 *
 * `SESSION_SNAPSHOT`은 연결 직후 한 번, `PARTY_PLAYING`은 게임 시작 판정이다.
 */
export type ServerEventType =
  | 'SESSION_SNAPSHOT'
  | 'MATCH_PROPOSAL_CREATED'
  | 'MATCH_PROPOSAL_EXPIRED'
  | 'MATCH_CONFIRMED'
  | 'MATCH_CANCELLED'
  | 'RESERVATION_PROPOSAL_CREATED'
  | 'PARTY_MEMBER_LEFT'
  | 'PARTY_READY_CHANGED'
  | 'PARTY_PLAYING'
  | 'PARTY_CLOSED'
  | 'WEBRTC_SIGNAL';

export interface ServerEvent<T = Record<string, unknown>> {
  type: ServerEventType;
  /** 재연결 직후 같은 이벤트를 다시 받을 수 있다. 클라이언트가 멱등해야 한다. */
  eventId: string;
  occurredAt: string;
  payload: T;
}

export type SignalType = 'OFFER' | 'ANSWER' | 'ICE';

/** Client → Server는 이것 하나뿐이다. */
export interface WebRtcSignalMessage {
  type: 'WEBRTC_SIGNAL';
  partyId: string;
  targetUserId: string;
  signalType: SignalType;
  data: Record<string, unknown>;
}

/* payload shapes — 서버 발행 지점과 맞춘 것이다 */

/** 연결 직후 한 번. payload는 영역이 늘면 키가 추가되므로 모르는 키는 무시한다. */
export interface SessionSnapshotPayload { parties: PartyView[] }

/** MATCH_PROPOSAL_CREATED / RESERVATION_PROPOSAL_CREATED. proposal 하나만 실린다. */
export interface ProposalCreatedPayload { proposal: ProposalView; }

/** MATCH_PROPOSAL_EXPIRED / MATCH_CANCELLED. proposalId만 실린다. */
export interface ProposalSettledPayload { proposalId: string; }

/** MATCH_CONFIRMED. 클라이언트는 partyId로 파티룸에 들어간다. */
export interface MatchConfirmedPayload { proposalId: string; partyId: string; }

export interface PartyReadyChangedPayload { partyId: string; userId: string; ready: boolean; status: PartyStatus; }
export interface PartyMemberLeftPayload { partyId: string; userId: string; status: PartyStatus; }
export interface PartyPlayingPayload { partyId: string; status: 'PLAYING'; }
export type PartyClosedReason = 'MEMBER_LEFT' | 'PLAY_TIMEOUT';
export interface PartyClosedPayload { partyId: string; reason: PartyClosedReason; }

export interface WebRtcSignalPayload { partyId: string; fromUserId: string; signalType: SignalType; data: Record<string, unknown>; }
