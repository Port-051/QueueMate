/**
 * 백엔드 계약의 타입 — `platform/contracts/platform-api.md` · `matching/contracts/openapi.yaml` · `events.md` 1:1 매핑.
 * 계약에 없는 필드를 임의로 추가하지 않는다. 계약이 정본이고 구현이 따라간다.
 * 원본 프런트의 모양이 남은 절은 예약 하나다(대응물이 없다 — 그 절의 주석 참조). 게임 계정 · 매칭 · 제안은 3단계, 모집 글 · 방은 4단계, 친구 · 차단 · 신고 · 최근 함께한 사람은 5단계에서 우리 모양이 됐다.
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

/**
 * 소셜 제공자 — 대문자 enum. 가입 · 로그인은 이것뿐이다(D-35).
 * `GOOGLE` 은 2026-09-29 소유자 결정으로 더했다 — 백엔드가 같은 경로(`/auth/oauth/GOOGLE/start`) · 같은 콜백 갈래로 받는다(platform 에서 구현 중).
 */
export type SocialProvider = 'KAKAO' | 'DISCORD' | 'GOOGLE';

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
 * 티어 사다리의 키 — 한 게임 안에서 랭크 큐마다 티어가 따로다(2026-09-29 소유자 결정 "모드별 티어를 무조건 저장한다").
 * LoL `SOLO`(솔로랭크) · `FLEX`(자유랭크) / VALORANT `COMPETITIVE`(경쟁전) / PUBG `RANKED`(랭크 — 하나다. 시즌 36(2025-06-05)부터 티어/RP 가 듀오 · 스쿼드와 FPP · TPP 에 걸쳐
 * 통합됐다 — `matching/WORKLOG_2026-09-14.md` §1. 처음에 넷(`DUO_TPP` …)으로 두었던 것은 틀린 전제였다).
 * 어느 모드가 어느 사다리를 보는지는 gameconfig 모드 HASH 의 `tierLadder` 다 — 사본은 `domain/gameCatalog.ts` 의 모드. 사다리 **안의** 티어 이름(`GOLD_4` …)은 게임마다 하나(`qm:gameconfig:{GAME}:tier`)를 같이 쓴다.
 */
export type LolTierLadder = 'SOLO' | 'FLEX';
export type ValorantTierLadder = 'COMPETITIVE';
export type PubgTierLadder = 'RANKED';
export type TierLadder = LolTierLadder | ValorantTierLadder | PubgTierLadder;

/**
 * 게임 프로필 — 게임 계정 하나를 밖에 보여 주는 모양(`users/me.gameAccounts[]` · 게시판 카드의 `host.profile` · `members[].profile`). 세 게임이 같은 모양이다(platform-api.md "게임 프로필").
 * **`tier` 칸은 없다 — `tiers` 가 사다리마다의 티어다**(2026-09-29 소유자 결정). 그 게임의 사다리 키가 **전부** 들어 있고 값은 사다리 이름(`GOLD_4` 꼴) 또는 `null`(언랭 · 모름) —
 * 예 LoL `{"SOLO":"GOLD_4","FLEX":null}` · VALORANT `{"COMPETITIVE":"GOLD_2"}` · PUBG `{"RANKED":"DIAMOND_3"}`. LoL · PUBG 는 게임사 API 가 채우고 VALORANT 는 자기신고다.
 * 타입이 `Partial` 인 것은 게임마다 키가 다르기 때문이다 — 읽는 것은 `domain/profileTier.ts` 한 곳에서(없는 키 · 옛 응답도 `null` 로 읽는다).
 * `server` 는 PUBG 만(`STEAM` · `KAKAO`). `verified` · `stats` 는 읽기 전용이다. 언제 긁은 것인지는 `stats.syncedAt` 이다.
 * **주 포지션 · 주 역할군(`mainPosition`)은 없다**(2026-09-29 소유자 결정 — 포지션은 글을 쓸 때(`wantedPositions`) · 매칭을 시작할 때(`keyCondition`) 고르는 것이다).
 */
export interface GameProfile {
  game: GameKey;
  gameNickname: string;
  verified: boolean;
  tiers: Partial<Record<TierLadder, string | null>>;
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
  /**
   * 게임마다 다르다 — LOL `{mostChampions}` · VALORANT `{mostAgents, mainWeapon, …}` · PUBG `{seasonMode, avgDamage, kd, top1Rate}`(이번 시즌 랭크 전 모드 합산 · 랭크 판이 없으면 일반 시즌 합산 — 2026-09-29).
   * jsonb 그대로라 `null` 일 수 있다.
   */
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
/**
 * `stats.detail`. PUBG 의 넷은 platform-api.md "게임 프로필" 의 PUBG 칸 그대로다 — 단위는 계약에 없어 프런트가 이렇게 읽는다(Claude 가 정한 세부):
 * `top1Rate` 는 퍼센트 숫자(`winRate` 처럼 — `5.2` = 5.2%) · `kd` 는 킬 / 데스 · `avgDamage` 는 판당 평균 딜량 · `seasonMode` 는 합산의 출처(값 목록은 계약에 없다 — 받은 그대로 보여 준다).
 */
export interface GameStatsDetail {
  mostChampions?: LolMostChampion[] | null;
  seasonMode?: string | null;
  avgDamage?: number | null;
  kd?: number | null;
  top1Rate?: number | null;
  [key: string]: unknown;
}

/**
 * `PUT /api/v1/users/me/game-accounts/{game}` 의 본문 — **게임마다 다르다**(P-26 · 2026-09-29 소유자 결정).
 * LOL 은 `gameNickname`(`이름#태그`) 하나 — 티어(솔로 · 자유)는 Riot 이 채운다 ·
 * VALORANT 는 `gameNickname` + `tier`(선택 · `COMPETITIVE` 사다리로 저장된다 — 자기신고) ·
 * **PUBG 는 `gameNickname` + `server` — `tier` 를 보내면 400 이다**(`RANKED` 사다리를 PUBG API 가 채운다). **`mainPosition` 을 보내면 400 이다**(2026-09-29 소유자 결정).
 * LOL · PUBG 는 저장하기 전에 서버가 게임사 API 를 **동기로** 긁는다(상한 30초) — 응답에 `tiers` · `stats` 가 바로 있다.
 * `tier` 는 그 게임의 사다리 이름이어야 하고(400 `VALIDATION_FAILED`), 없으면 보내지 않는다(`undefined` — JSON 에서 빠진다).
 */
export interface LolGameAccountRequest { gameNickname: string; }
export interface ValorantGameAccountRequest { gameNickname: string; tier?: string; }
export interface PubgGameAccountRequest { gameNickname: string; server: PubgServer; }
export type GameAccountRequest = LolGameAccountRequest | ValorantGameAccountRequest | PubgGameAccountRequest;

/* game config(GET /games · match-schema)는 없다 — 정적 상수 `domain/gameCatalog.ts`(원본 seed 의 사본) */

/* ---------- realtime matching (matching/contracts/openapi.yaml · platform-api.md "자동 매칭이 게시판 방에 먼저 합류하는 길") ---------- */

/**
 * `POST /api/v1/match-requests`(matching) 와 `POST /api/v1/posts/auto-join`(platform) 의 **같은 본문** — `CreateMatchRequestCommand` 와 필드 이름이 글자까지 같다.
 * `MatchCondition` 은 화면의 값이고 이것은 서버에 보내는 값이다 — 둘을 잇는 것은 `domain/matchRequest.ts` `buildMatchRequest` 다:
 * `keyCondition.value` 의 화면 사본 `ANY` 는 `NONE` 으로 · `tier` 는 내 게임 계정의 **그 모드의 사다리(`tierLadder`) 티어**를 **`tierRule=EXIST` 모드에서만** 싣는다(`NONE` 모드에 실으면 400).
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

/* ---------- 모집 글 · 방 (platform-api.md "모집 글 · 목록" · "방" · "자동 매칭 파티의 방") ---------- */

/** 글의 상태. 목록은 셋을 `id` 내림차순으로 섞어 내려 준다(끝난 글도 남는다 — P-20). 화면은 `RECRUITING` 이 아닌 글을 흐리게 그린다. */
export type PostStatus = 'RECRUITING' | 'CONFIRMED' | 'EXPIRED';
export type PubgPerspective = 'TPP' | 'FPP';
/** 게임별 조건 — PUBG 는 `{perspective}` 가 필수이고 LoL · VALORANT 는 `{}` 다(모르는 키는 400). `@JsonRawValue` 라 JSON **객체**로 온다. */
export interface PostConditions { perspective?: PubgPerspective; [key: string]: unknown }

/**
 * 목록 · 단건의 사람 카드(`host` · `members[]`). `userId` 는 JSON **숫자**(방 응답의 문자열 id 와 비교할 때는 `String()`).
 * `nickname` · `profile` 은 가입하지 않은 번호면 `null` 이다(방 키에 손으로 넣은 값). `profile` 은 그 글의 게임에 연결한 게임 프로필 전체다 — 없으면 `null`.
 */
export interface MemberCard { userId: number; nickname: string | null; host: boolean; profile: GameProfile | null }

/**
 * 모집 글 한 줄 — `GET /posts?game=` 의 `posts[]` · `GET /posts/{postId}` · `POST /posts` 201 · `PATCH` 200 이 같은 모양이다.
 * **`postId` 가 곧 `roomId` 다**(방 키에는 십진 문자열로 — 방 요청의 경로에는 `String(postId)`). `capacity` 는 늘 5, `memberCount` 는 끝난 글이면 0, `members` 는 방장 먼저다.
 * 시각은 ISO-8601 문자열. `mode` 는 옛 글이면 `null` 일 수 있다(P-16 미정).
 */
export interface PostResponse {
  postId: number;
  hostId: number;
  game: GameKey;
  mode: string | null;
  title: string;
  description: string | null;
  voice: VoicePreference;
  conditions: PostConditions;
  wantedPositions: string[];
  status: PostStatus;
  createdAt: string;
  memberCount: number;
  capacity: number;
  full: boolean;
  host: MemberCard;
  members: MemberCard[];
}
/** `nextCursor` 는 마지막으로 **읽은** 글의 번호(숫자) — 더 볼 것이 없으면 `null`. 다음 페이지는 `?cursor=` 에 그대로 넣는다(P-14). */
export interface PostListResponse { posts: PostResponse[]; nextCursor: number | null }
/**
 * `POST /posts`. `mode` 는 그 게임의 gameconfig 모드(필수 · ≤30) · `title` 1~60 · `description` ≤300(없으면 보내지 않는다) ·
 * `conditions` 는 PUBG 만 `{perspective}` · `wantedPositions` 는 그 게임의 포지션 이름(PUBG 는 빈 배열). 방이 같이 생기고 응답의 `members` 에 방장이 있다.
 */
export interface CreatePostRequest {
  game: GameKey;
  mode: string;
  title: string;
  description?: string;
  voice: VoicePreference;
  conditions: PostConditions;
  wantedPositions: string[];
}
/** `PATCH /posts/{postId}` — 준 것만 바꾼다(`null` · 없음 = 그대로). `description` 은 빈 문자열이면 비운다 · `title` · `mode` 의 빈 문자열은 400. */
export interface UpdatePostRequest {
  mode?: string;
  title?: string;
  description?: string;
  voice?: VoicePreference;
  conditions?: PostConditions;
  wantedPositions?: string[];
}

/**
 * `GET /rooms/{roomId}/members` — 방 안의 사람만 볼 수 있다(밖이면 403 `NOT_IN_ROOM`). id 는 전부 **십진 문자열**(방 키의 글자 그대로) · `members` 에 방장이 들어 있고 순서는 없다.
 * 확정한 방은 `hostId` 가 바뀔 수 있다(승계 — D-23). 닉네임 · 프로필은 여기 없다 — 게시판 방이면 `GET /posts/{postId}` 의 카드로 붙인다.
 */
export interface RoomMembersResponse { roomId: string; hostId: string; members: string[] }
/** `GET /rooms/me` — 내 입장 표시 키. 없으면 `{roomId: null}`(404 가 아니다). 게시판 방은 글 번호 문자열 · 자동 매칭 방은 UUID. */
export interface MyRoomResponse { roomId: string | null }

/* ---------- social (platform-api.md "차단" · "친구 · 신고 · 최근 함께한 사람") — 5단계(2026-09-29)에 우리 모양이 됐다 ---------- */

/**
 * **id 표기** — 응답의 `userId` · `requestId` · `lastPartyId` · `reportId` 는 JSON **숫자**(사용자 번호 · bigint)다. 요청 본문의 `userId` · `targetUserId` · `contextId` 는
 * **문자열**로 보낸다(서버가 `String` 으로 받아 `Long` 으로 판다 — 숫자가 아니면 없는 사용자와 같은 404 `USER_NOT_FOUND`). `AuthContext.userId` · 방 응답 · `ROOM_*` 의 id 는
 * 십진 문자열이라 비교할 때는 `String(userId)` 다. **사람 검색 API 는 없다** — 상대의 번호는 방 안 카드 · 최근 함께한 사람 · 요청 목록에서 오거나 사용자가 직접 넣는다.
 */
export interface SocialUser { userId: number; nickname: string; }
/** `GET /friends` 의 `friends[]` · `POST /friend-requests/{id}/accept` 200. `since` 는 친구가 된 시각(ISO). */
export interface FriendView { userId: number; nickname: string; since: string; }
export interface FriendListResponse { friends: FriendView[]; }
/** `GET /friend-requests?direction=` — **대문자 그대로**(소문자는 400). 기본 `RECEIVED`. */
export type FriendRequestDirection = 'RECEIVED' | 'SENT';
/** 친구 요청 한 줄 — 대기 중인 것만 온다(`status` 칸이 없다). 상대는 `RECEIVED` 면 `requester`, `SENT` 면 `receiver` 다. */
export interface FriendRequestView { requestId: number; requester: SocialUser; receiver: SocialUser; createdAt: string; }
export interface FriendRequestListResponse { requests: FriendRequestView[]; }
/** `POST /friend-requests` — 상대의 사용자 번호(문자열). 409 `ALREADY_FRIENDS` · `FRIEND_REQUEST_ALREADY_SENT` · `FRIEND_REQUEST_ALREADY_RECEIVED` · 400 `CANNOT_FRIEND_SELF` · 404 `USER_NOT_FOUND`(차단 관계도). */
export interface CreateFriendRequest { userId: string; }
/** `GET /blocks` 의 `blocks[]` · `POST /blocks` 201 — 내가 차단한 사람만. */
export interface BlockView { userId: number; nickname: string; createdAt: string; }
export interface BlockListResponse { blocks: BlockView[]; }
/** `POST /blocks` — 차단할 사람의 사용자 번호(문자열). 409 `ALREADY_BLOCKED` · 400 `CANNOT_BLOCK_SELF` · 404 `USER_NOT_FOUND`. */
export interface CreateBlockRequest { userId: string; }
/**
 * `GET /recent-players` 의 `players[]` — 확정된 파티가 닫힐 때 채워진다(P-25 · P-30). 최근순 · 50명 · 차단 관계는 뺀다. `?limit` 은 없다.
 * `lastPartyId` 는 `parties.id`(조회 경로가 없다 — P-31). 원본의 `avatarUrl` · `playCount` · `friend` 는 없다 — "친구" 표시는 친구 목록과 대조해 프런트가 만든다.
 */
export interface RecentPlayerView { userId: number; nickname: string; lastPartyId: number | null; lastPlayedAt: string; }
export interface RecentPlayerListResponse { players: RecentPlayerView[]; }

/** 신고 사유 — `ABUSE`(욕설 · 비매너) · `CHEATING`(핵 · 대리) · `SPAM`(도배 · 광고) · `NO_SHOW`(잠수 · 탈주) · `OTHER`(`detail` 필수). 대문자 그대로. */
export type ReportReason = 'ABUSE' | 'CHEATING' | 'SPAM' | 'NO_SHOW' | 'OTHER';
/**
 * `POST /reports`. `detail` 은 1000자까지(없으면 보내지 않는다 · `OTHER` 면 필수 — 400 `VALIDATION_FAILED`). `contextId` 는 **글의 id 를 문자열로**(게시판 방의 `roomId` 가 그것이다 ·
 * 자동 매칭 방은 글이 없어 보내지 않는다 · 숫자가 아니면 400). 접수만 받는다 — 차단 관계도 신고할 수 있다. 400 `CANNOT_REPORT_SELF` · 404 `USER_NOT_FOUND`.
 */
export interface CreateReportRequest { targetUserId: string; reason: ReportReason; detail?: string; contextId?: string; }
export interface ReportResponse { reportId: number; createdAt: string; }

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


export interface ServerEvent<T = Record<string, unknown>> {
  type: ServerEventType;
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
