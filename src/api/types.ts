/**
 * 백엔드 계약의 타입 — `platform/contracts/platform-api.md` · `matching/contracts/openapi.yaml` · `events.md` 1:1 매핑.
 * 계약에 없는 필드를 임의로 추가하지 않는다. 계약이 정본이고 구현이 따라간다.
 * 아직 원본 프런트의 모양이 남은 절(파티 · 예약 — 대응물이 없다 · 친구 · 차단 · 신고 — 5단계)은 각 절의 주석 참조. 게임 계정 · 매칭 · 제안은 3단계에서 우리 모양이 됐다.
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
  /** 게임 계정(게임마다 하나) — 게임 프로필 그대로. 목록을 따로 받는 요청은 없다(`GET …/game-accounts` 없음). `RequireOnboarding` 은 개수만 본다. */
  gameAccounts: GameProfile[];
}

/** `PATCH /users/me`. 닉네임 하나다(2~16자 · 유일 · 409 `NICKNAME_TAKEN`). */
export interface UpdateUserRequest { nickname: string; }

/**
 * 게임 프로필 — 게임 계정 하나를 밖에 보여 주는 모양(`users/me.gameAccounts[]` · 게시판 카드의 `profile`). 세 게임이 같은 모양이다(platform-api.md "게임 프로필").
 * `tier` 는 gameconfig 사다리의 이름(`GOLD_4` 꼴 · LoL 은 Riot 이 채우고 언랭이면 `null`), `mainPosition` 은 LOL `TOP|JUNGLE|MID|ADC|SUPPORT` · VALORANT 4역할군 · PUBG `null`,
 * `server` 는 PUBG 만(`STEAM` · `KAKAO`). `verified` · `stats` 는 읽기 전용이다. 언제 긁은 것인지는 `stats.syncedAt` 이다.
 */
export interface GameProfile {
  game: GameKey;
  gameNickname: string;
  verified: boolean;
  tier: string | null;
  mainPosition: string | null;
  server: PubgServer | null;
  stats: GameStats | null;
}

export type PubgServer = 'STEAM' | 'KAKAO';

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
  /** 게임마다 다르다 — LOL `{mostChampions}` · VALORANT `{mostAgents, mainWeapon, …}` · PUBG `{seasonMode, avgDamage, kd, top1Rate}`. jsonb 그대로라 `null` 일 수 있다. */
  detail: GameStatsDetail | null;
  syncedAt: string;
}

/** LoL `stats.detail.mostChampions[]` — 판 수 많은 순 셋까지. `championId` 는 Riot 의 `championName`(`"Samira"`). 숙련도 둘은 못 받으면 `null`. */
export interface LolMostChampion {
  championId: string;
  games: number;
  winRate: number | null;
  masteryLevel: number | null;
  masteryPoints: number | null;
}
export interface GameStatsDetail {
  mostChampions?: LolMostChampion[] | null;
  [key: string]: unknown;
}

/**
 * `PUT /api/v1/users/me/game-accounts/{game}` 의 본문 — **게임마다 다르다**(P-26).
 * LOL 은 `gameNickname`(`이름#태그`) + `mainPosition`(선택) — `tier` · `server` 를 보내면 400(티어는 Riot 이 채운다) ·
 * VALORANT 는 `gameNickname` + `tier`(선택) + `mainPosition`(선택) · PUBG 는 `gameNickname` + `tier`(선택) + `server`.
 * `tier` 는 그 게임의 사다리 이름이어야 하고(400 `VALIDATION_FAILED`), 없으면 보내지 않는다(`undefined` — JSON 에서 빠진다).
 */
export interface LolGameAccountRequest { gameNickname: string; mainPosition?: string; }
export interface ValorantGameAccountRequest { gameNickname: string; tier?: string; mainPosition?: string; }
export interface PubgGameAccountRequest { gameNickname: string; tier?: string; server: PubgServer; }
export type GameAccountRequest = LolGameAccountRequest | ValorantGameAccountRequest | PubgGameAccountRequest;

/* game config(GET /games · match-schema)는 없다 — 정적 상수 `domain/gameCatalog.ts`(원본 seed 의 사본) */

/* ---------- realtime matching (matching/contracts/openapi.yaml · platform-api.md "자동 매칭이 게시판 방에 먼저 합류하는 길") ---------- */

/**
 * `POST /api/v1/match-requests`(matching) 와 `POST /api/v1/posts/auto-join`(platform) 의 **같은 본문** — `CreateMatchRequestCommand` 와 필드 이름이 글자까지 같다.
 * `MatchCondition` 은 화면의 값이고 이것은 서버에 보내는 값이다 — 둘을 잇는 것은 `domain/matchRequest.ts` `buildMatchRequest` 다:
 * `keyCondition.value` 의 화면 사본 `ANY` 는 `NONE` 으로 · `tier` 는 내 게임 계정의 티어를 **`tierRule=EXIST` 모드에서만** 싣는다(`NONE` 모드에 실으면 400).
 * `userId` 는 없다 — 쿠키의 사용자다.
 */
export interface CreateMatchRequest extends MatchCondition { tier?: string; }

/** `POST /posts/auto-join` 200 — 들어간 글과 방. 둘은 같은 숫자다(게시판 방의 `roomId` 는 글의 번호). 방 화면 경로에는 `String(roomId)`. */
export interface AutoJoinResponse { postId: number; roomId: number; }

/** `POST /match-parties/{partyId}/room` 201/200 — `roomId` 는 `partyId` 와 같은 UUID 문자열이다(P-30). */
export interface MatchRoomResponse { roomId: string; }

export type MatchRequestStatus = 'IDLE' | 'QUEUED' | 'PROPOSED' | 'MATCHED';

/**
 * `MatchRequestView` — 접수(201)와 상태 조회(`GET /match-requests` · 늘 200)가 같은 모양이다. **`status` 만 항상 있고 나머지는 그 갈래에서만 온다**(`@JsonInclude(NON_NULL)`).
 * `IDLE` 은 `{status}` 뿐(취소 · 만료로 빠진 경우도 여기) · `QUEUED` 는 `requestId` `queuedAt`(+ 파티가 잡혔으면 `partyId` `target` `memberCount`) ·
 * `PROPOSED` 는 `requestId` `queuedAt` `partyId` `expiresAt` `isAccepted` · `MATCHED` 는 `requestId` `queuedAt` `partyId`. 시각은 전부 **epoch ms**.
 * 이름 · 타입은 원본 계약과 열려 있다(`matching/contracts/README.md` #4 · #5) — 코드(`MatchRequestResponse.java`)가 답하는 모양을 따랐다.
 */
export interface MatchRequestView {
  status: MatchRequestStatus;
  requestId?: string;
  queuedAt?: number;
  /** 배정된 파티 = `proposalId`. `POST /proposals/{id}/accept|decline` 의 `{id}` 이고 확정 뒤 `POST /match-parties/{id}/room` 의 `{partyId}` 다. */
  partyId?: string;
  target?: number;
  memberCount?: number;
  expiresAt?: number;
  isAccepted?: boolean;
}

/* ---------- reservation ---------- */
export type ReservationStatus = 'ACTIVE' | 'PROPOSED' | 'MATCHED' | 'CANCELLED' | 'EXPIRED' | 'COMPLETED';

export interface CreateReservationRequest {
  condition: MatchCondition;
  availableFrom: string;
  availableTo: string;
  playAmount: PlayAmount;
}

/** 원본 프런트의 예약 — **우리 백엔드에 없다**(`app:reservation` Lambda · 미착수). 화면이 컴파일되게만 남겼다(START_HERE.md §5). */
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

/* ---------- party — 원본 프런트의 Ready/PLAYING 파티. **우리 백엔드에 없다**(파티 조회 경로를 두지 않는다 — P-31). `PartyRoomPage` 가 컴파일되게 남겼다 — 4단계에서 방 요청으로 ---------- */
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
 * **원본 백엔드의 이름 — 우리 백엔드는 보내지 않고 `sse.ts` 의 화이트리스트에도 없다.** 그 이름을 기다리는 핸들러(`rooms/useRoomData` · `state/notifications`)가
 * 컴파일되게만 남겼다 — 4단계(방 · 게시판)에서 우리 이름으로 바꾸며 지운다(`MatchContext` 의 옛 핸들러는 3단계에서 걷어냈다).
 * `SESSION_SNAPSHOT` 은 대응물이 없다(연결 직후 `GET /match-requests` · `GET /rooms/me` 로 맞춘다) · `ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED` · `RECRUITMENT_UPDATED` 의 자리는
 * `BOARD_CHANGED` + `ROOM_*` 다. `PARTY_*` 는 두지 않기로 했다(D-44 · P-31) — `PartyRoomPage` 의 `startsWith('PARTY_')` 는 오지 않는 이벤트를 기다린다(4단계).
 */
export type LegacyServerEventType = 'SESSION_SNAPSHOT' | 'ROOMS_UPDATED' | 'ROOM_MESSAGES_UPDATED' | 'RECRUITMENT_UPDATED';

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
