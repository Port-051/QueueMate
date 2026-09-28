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

/* ---------- auth / user (platform-api.md "계정" · "소셜 로그인" · "게임 프로필") ---------- */

/** 소셜 제공자 — 대문자 enum. 가입 · 로그인은 이것뿐이다(D-35). */
export type SocialProvider = 'KAKAO' | 'DISCORD';

/**
 * 소셜 가입 · 재발급이 돌려주는 본문. `userId` 는 **사용자 번호**(bigint → JSON 숫자)다 — 로그인 아이디 · 이메일은 없다.
 * `POST /auth/social/signup` 201 · `POST /auth/refresh` 200 이 같은 모양이다.
 */
export interface SessionUser { userId: number; nickname: string; }

/** `GET /auth/social/pending`. `suggestedNickname` 은 `null` 일 수 있다(16자로 자른 값). */
export interface SocialSignupPending { provider: SocialProvider; suggestedNickname: string | null; }
export interface SocialSignupRequest { nickname: string; }

/**
 * `GET /users/me`. 식별자는 `userId`(사용자 번호 · 숫자) 하나, 보여 주는 이름은 `nickname` 하나다(D-25).
 * 방 응답 · 알림 `payload` 의 id 는 **십진 문자열**(`"42"`)이라 비교할 때는 `String(userId)` 다 — `AuthContext` 의 `userId` 가 그것이다.
 * 아바타(`avatarUrl`) · 로그인 아이디 · 이메일은 우리 백엔드에 없다.
 */
export interface UserProfile {
  userId: number;
  nickname: string;
  createdAt: string;
  /** 연결된 소셜 제공자. 화면은 이것으로 제공자마다 "연결됨 / 연결하기" 를 그린다(P-27). */
  socialProviders: SocialProvider[];
  /**
   * 게임 계정(게임마다 하나). **3단계에서 `GameProfile[]` 로 바꾼다** — 지금은 원본 프런트의 `GameAccountView` 모양을 그대로 두어
   * 게임 계정 화면(`OnboardingPage` · `MyInfoPage` · `rooms/accountRank`)이 컴파일만 되게 했다(START_HERE.md §2 "2단계가 남긴 것").
   * 런타임에는 서버가 `GameProfile` 모양을 주므로 이 화면들은 아직 값을 제대로 그리지 못한다. `RequireOnboarding` 은 개수만 본다.
   */
  gameAccounts: GameAccountView[];
}

/** `PATCH /users/me`. 닉네임 하나다(2~16자 · 유일 · 409 `NICKNAME_TAKEN`). */
export interface UpdateUserRequest { nickname: string; }

/**
 * 게임 프로필 — 게임 계정 하나를 밖에 보여 주는 모양(`users/me.gameAccounts[]` · 게시판 카드의 `profile`). 세 게임이 같은 모양이다.
 * `tier` 는 gameconfig 사다리의 이름(`GOLD_4` 꼴), `mainPosition` 은 LOL `TOP|JUNGLE|MID|ADC|SUPPORT` · VALORANT 4역할군 · PUBG `null`,
 * `server` 는 PUBG 만(`STEAM` · `KAKAO`). `verified` · `stats` 는 읽기 전용이다. 3단계(게임 계정 화면)에서 쓴다.
 */
export interface GameProfile {
  game: GameKey;
  gameNickname: string;
  verified: boolean;
  tier: string | null;
  mainPosition: string | null;
  server: 'STEAM' | 'KAKAO' | null;
  stats: GameStats | null;
}

/** 전적 스냅숏. 비는 칸은 빠지지 않고 `null` 이다 — PUBG 는 `wins` · `losses` · `winStreak` · `avgAssists` · `kda` 가 늘 `null`. */
export interface GameStats {
  games: number;
  wins: number | null;
  losses: number | null;
  winRate: number | null;
  winStreak: number | null;
  avgKills: number | null;
  avgDeaths: number | null;
  avgAssists: number | null;
  kda: number | null;
  /** 게임마다 다르다 — LOL `{mostChampions}` · VALORANT `{mostAgents, mainWeapon, …}` · PUBG `{seasonMode, avgDamage, kd, top1Rate}` */
  detail: Record<string, unknown>;
  syncedAt: string;
}

/**
 * 원본 프런트의 게임 계정 모양 — **우리 백엔드에는 없다.** 3단계에서 `GameProfile` 로 바꾸며 지운다.
 * 원본 API `GET /users/me/game-accounts` · `POST …` · `DELETE …/{id}` 는 대응물이 `PUT|DELETE /users/me/game-accounts/{game}` 이다.
 */
export interface GameAccountView {
  id: string;
  game: GameKey;
  externalGameId: string;
  region: string | null;
  rankCode: string | null;
  flexRankCode: string | null;
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

export interface MatchHistoryView extends Omit<MatchRequestView, 'status'> {
  status: 'MATCHED' | 'CANCELLED' | 'EXPIRED';
  condition: MatchCondition;
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
  gameIds?: string[];
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
  | 'ROOMS_UPDATED'
  | 'ROOM_MESSAGES_UPDATED'
  | 'RECRUITMENT_UPDATED'
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
