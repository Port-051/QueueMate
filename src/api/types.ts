/**
 * 백엔드 계약의 타입 — `platform/contracts/platform-api.md` · `matching/contracts/openapi.yaml` · `events.md` 1:1 매핑.
 * 계약에 없는 필드를 임의로 추가하지 않는다. 계약이 정본이고 구현이 따라간다.
 * 아직 원본 프런트의 모양이 남은 절(게임 계정 · 매칭 요청 · 제안 · 파티 · 예약 · 친구 · 차단 · 신고)은 3 · 5단계에서 바꾼다 — 각 절의 주석 참조.
 */

export type GameKey = 'LOL' | 'VALORANT' | 'PUBG';
/** `OPTIONAL` 은 없다(openapi `VoicePreference` 개정 이력 · docs/11 #31) — 매칭 전에 답이 정해지지 않는 조건은 조건이 아니다. */
export type VoicePreference = 'REQUIRED' | 'NO_VOICE';
export type PlayPurpose = 'RANK_UP' | 'NORMAL' | 'FUN';
export type PlayAmount = 'ONE_GAME' | 'TWO_PLUS';
/** LoL = POSITION · VALORANT = ROLE · PUBG = PLATFORM(STEAM/KAKAO — 원본의 PLAY_STYLE 이 아니다, A-13). */
export type KeyConditionType = 'POSITION' | 'ROLE' | 'PLATFORM';

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

/* game config(GET /games · match-schema)는 없다 — 정적 상수 `domain/gameCatalog.ts`(원본 seed 의 사본) */

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

/* ---------- SSE (matching/contracts/events.md · platform-api.md "알림" · notification/CLAUDE.md §5) ---------- */

/**
 * Server → Client `type`. **우리 세 백엔드가 실제로 발행하는 14종만 둔다** — matching 5 · platform 9. 봉투는 `ServerEvent`.
 * 알림은 "다시 조회하라"는 신호다 — 순서 · 재전송 보장이 없으니 핸들러는 멱등해야 하고 데이터는 REST 로 다시 받는다(events.md "순서 보장 범위").
 */
export type ServerEventType =
  | 'MATCH_QUEUE_UPDATED' | 'MATCH_PROPOSAL_CREATED' | 'MATCH_PROPOSAL_EXPIRED' | 'MATCH_CONFIRMED' | 'MATCH_CANCELLED'
  | 'FRIEND_REQUEST_RECEIVED' | 'FRIEND_REQUEST_ACCEPTED'
  | 'ROOM_MEMBER_ENTERED' | 'ROOM_MEMBER_LEFT' | 'ROOM_CLOSED' | 'ROOM_MEMBER_KICKED' | 'ROOM_CONFIRMED'
  | 'WEBRTC_SIGNAL'
  | 'BOARD_CHANGED';

/**
 * **원본 백엔드의 이름 — 우리 백엔드는 보내지 않고 `sse.ts` 의 화이트리스트에도 없다.** 그 이름을 기다리는 핸들러(`MatchContext` ·
 * `PartySessionContext` · `rooms/useRoomData`)가 컴파일되게만 남겼다 — 3단계(매칭 · 파티)와 4단계(방 · 게시판)에서 우리 이름으로 바꾸며 지운다.
 * `SESSION_SNAPSHOT` 은 대응물이 없다(연결 직후 `GET /match-requests` · `GET /rooms/me` 로 맞춘다) · `PARTY_*` 는 두지 않기로 했다(D-44 · P-31) ·
 * `ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED` · `RECRUITMENT_UPDATED` 의 자리는 `BOARD_CHANGED` + `ROOM_*` 다 · `RESERVATION_*` 은 예약이 없다.
 */
export type LegacyServerEventType =
  | 'SESSION_SNAPSHOT' | 'ROOMS_UPDATED' | 'ROOM_MESSAGES_UPDATED' | 'RECRUITMENT_UPDATED' | 'RESERVATION_PROPOSAL_CREATED'
  | 'PARTY_MEMBER_LEFT' | 'PARTY_READY_CHANGED' | 'PARTY_PLAYING' | 'PARTY_CLOSED';

export interface ServerEvent<T = Record<string, unknown>> {
  type: ServerEventType | LegacyServerEventType;
  /** SSE `id:` 와 같다. 재연결 직후 같은 이벤트를 다시 받을 수 있다 — 클라이언트가 멱등해야 한다. */
  eventId: string;
  /** ISO-8601 UTC · 밀리초 */
  occurredAt: string;
  payload: T;
}

/* payload — matching 5종 (events.md "구현 상태" 표. 계약이 정한 것이 아니라 구현이 먼저 정한 것이다) */

/** 대기 상태가 바뀌었다(새 파티 · 정원 미달 합류). 상태는 `GET /match-requests` 로 다시 받는다. */
export interface MatchQueueUpdatedPayload { memberNumber: number; }
/** 정원이 차서 제안이 떴다. `partyId` 가 `POST /proposals/{id}/accept|decline` 의 `{id}` 다. 남은 시간 · 내 수락 여부는 `GET /match-requests`. */
export interface MatchProposalCreatedPayload { memberNumber: number; target: number; partyId: string; }
/** 제안 시한 만료 — 그 제안에 있던 전원이 받는다(수락한 사람 포함). */
export interface MatchProposalExpiredPayload { partyId: string; }
/** 전원 수락으로 확정. **받으면 조작 없이 바로 `POST /match-parties/{partyId}/room`** 을 부른다(D-42 · P-30) — `roomId = partyId`. */
export interface MatchConfirmedPayload { partyId: string; }
/** 파티원 누가 취소했다 — 남은 파티원에게만. */
export interface MatchCancelledPayload { memberNumber: number; }

/* payload — platform 9종 (platform-api.md "방" 의 "알림" · "이 앱이 내는 알림" · "게시판 채널 신호") */

/** `FRIEND_*` 의 id 는 JSON **숫자**다(방 알림의 문자열 id 와 다르다). 친구 목록 · 요청 목록을 다시 받는다. */
export interface FriendRequestReceivedPayload { requestId: number; fromUserId: number; }
export interface FriendRequestAcceptedPayload { requestId: number; userId: number; }
/** 방 알림의 id 는 **십진 문자열**(방 키에 적힌 글자 그대로). `ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_MEMBER_KICKED` 가 같은 모양이다. */
export interface RoomMemberPayload { roomId: string; userId: string; }
export interface RoomClosedPayload { roomId: string; }
/** 방장이 확정했다 — `members` 가 파티원이다(방장 포함). */
export interface RoomConfirmedPayload { roomId: string; members: string[]; }
/** 게시판이 바뀌었다 — 데이터가 없다(`{}`). 게시판 페이지를 보고 있을 때만 · 묶어서 · 커서 없이 맨 위부터 `limit` 으로 다시 받는다. */
export type BoardChangedPayload = Record<string, never>;

/**
 * WebRTC 시그널 — 받기는 SSE `WEBRTC_SIGNAL`, 보내기는 `POST /rooms/{roomId}/signals {toUserId, signal}`(202). 서버는 `signal` 을 열어 보지 않는다.
 * 모양은 클라이언트끼리의 약속이고 platform-api.md "`signal` 의 권장 모양" 을 따른다 — 브라우저의 WebRTC API 가 내주는 객체 그대로에 `kind` 만 씌운다.
 * 자동 매칭 파티의 방은 `roomId = partyId`(UUID), 게시판 방은 글 번호의 십진 문자열이다.
 */
export type RoomSignal =
  | { kind: 'description'; description: RTCSessionDescriptionInit }
  | { kind: 'candidate'; candidate: RTCIceCandidateInit };
export interface SendRoomSignalRequest { toUserId: string; signal: RoomSignal; }
export interface WebRtcSignalPayload { roomId: string; fromUserId: string; signal: RoomSignal; }

/* payload — 원본 프런트의 모양. `LegacyServerEventType` 과 같은 처지다 — 3 · 4단계에서 지운다 */

/** @deprecated 원본 `SESSION_SNAPSHOT`. 대응물 없음. */
export interface SessionSnapshotPayload { parties: PartyView[] }
/** @deprecated 원본 `MATCH_PROPOSAL_CREATED` / `RESERVATION_PROPOSAL_CREATED` 의 모양. 우리 것은 `MatchProposalCreatedPayload`. */
export interface ProposalCreatedPayload { proposal: ProposalView; }
/** @deprecated 원본 `MATCH_PROPOSAL_EXPIRED` / `MATCH_CANCELLED` 의 모양. 우리 것은 `MatchProposalExpiredPayload` · `MatchCancelledPayload`. */
export interface ProposalSettledPayload { proposalId: string; }
/** @deprecated 원본 `PARTY_*`. 두지 않기로 했다(D-44). */
export interface PartyReadyChangedPayload { partyId: string; userId: string; ready: boolean; status: PartyStatus; }
export interface PartyMemberLeftPayload { partyId: string; userId: string; status: PartyStatus; }
export interface PartyPlayingPayload { partyId: string; status: 'PLAYING'; }
export type PartyClosedReason = 'MEMBER_LEFT' | 'PLAY_TIMEOUT';
export interface PartyClosedPayload { partyId: string; reason: PartyClosedReason; }
