import type { GameKey, GameProfile, MemberCard, PostResponse, PostStatus, PubgPerspective, TierLadder, VoicePreference } from '../api/types';

/**
 * 방 카드 보드가 그리는 모양 — 서버의 `PostResponse`(글이 곧 방)를 카드가 읽기 좋게 편 것이다(`boardRoom.ts` `toBoardRoom`).
 * 원본의 `GameRoom`(방 = 독립 자원 · 메시지 · 예약 시각 · 정원 선택 · 티어 범위 · `REOPEN`)은 우리 계약에 없어 2026-09-29 에 이 모양으로 바꿨다(START_HERE.md §4.3).
 *
 * - `id` 는 `String(postId)` — 방 요청(`/rooms/{roomId}/…`) · 방 화면 경로(`/app/party/{roomId}`)에 그대로 쓴다.
 * - 사람의 id 도 십진 문자열이다(방 응답 · 알림 `payload` 와 같은 글자) — `AuthContext.userId` 와 바로 비교한다.
 * - 티어 · 승률 · KDA · 챔피언은 `profile`(그 글의 게임에 연결한 게임 프로필)에서 온다. **티어는 그 글의 모드의 사다리 티어**이고 사다리가 없는 모드면 가장 높은 티어다(2026-09-29 — `domain/profileTier.ts`).
 *   PUBG 는 승률 · KDA 자리에 치킨률 · K/D 가 온다(2026-09-29 PUBG 연동). VALORANT 의 `stats` 는 아직 늘 `null` 이라 `—` 로 그린다.
 * - **사람별 포지션은 없다**(2026-09-29 소유자 결정 — 게임 계정에서 주 포지션 · 주 역할군을 없앴다). 포지션은 글의 `wantedPositions`(찾는 포지션) 하나다.
 */
export interface BoardMember {
  id: string;
  nickname: string;
  host: boolean;
  /** 그 글의 모드의 사다리 티어(`GOLD_4` → `GOLD` · `4`). 언랭 · 미연결은 `null`. */
  tier: string | null;
  division: number | null;
  /** 그 티어가 온 사다리 — 카드의 풍선말("자유랭크")에 쓴다. 사다리가 없는 모드에서 티어가 하나도 없으면 `null`. */
  tierLadder: TierLadder | null;
  /** LoL · VALORANT 는 `stats.winRate`(정수 퍼센트), PUBG 는 치킨률(`stats.detail.top1Rate`). */
  winRate: number | null;
  /** LoL · VALORANT 는 `stats.kda`, PUBG 는 K/D(`stats.detail.kd`). */
  kda: number | null;
  /** LoL `stats.detail.mostChampions[].championId`(Riot 의 영문 이름). 셋까지. */
  champions: string[];
  profile: GameProfile | null;
  /** 원본 카드 그대로. */
  card: MemberCard;
}

export interface BoardRoom {
  /** `String(postId)` = `roomId`. */
  id: string;
  postId: number;
  game: GameKey;
  /** 옛 글이면 `null` 일 수 있다(P-16 미정) — 화면은 `''` 로 다룬다. */
  modeKey: string;
  title: string;
  description: string;
  hostId: string;
  capacity: number;
  memberCount: number;
  full: boolean;
  status: PostStatus;
  voice: VoicePreference;
  /** PUBG 만. `conditions.perspective`. */
  perspective: PubgPerspective | null;
  wantedPositions: string[];
  /**
   * 방장(글쓴이)의 포지션 — 글을 쓸 때 고른 것이다(2026-09-30 소유자 결정). 방장 카드에 아이콘 하나로 붙는다(`RoomMemberFacts`).
   * 포지션이 없는 모드 · 옛 글 · 이 게임에 없는 이름이면 `null`. 사람별 포지션(게임 계정의 주 포지션)은 여전히 없다 — 이것은 **글의** 값이다.
   */
  hostPosition: string | null;
  /** ISO-8601. */
  createdAt: string;
  host: BoardMember;
  /** 지금 방 안에 있는 사람(방장 먼저). 끝난 글은 비어 있다. */
  members: BoardMember[];
  /** 원본 응답 — 고치기 폼이 기본값으로 쓴다. */
  post: PostResponse;
}
