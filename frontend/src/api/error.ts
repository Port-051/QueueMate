/**
 * 에러 본문 — `platform-api.md` "공통" · `matching/contracts/openapi.yaml` `ErrorResponse`.
 *
 * 모든 4xx/5xx는 예외 없이 `{code, message, details}` 다(`details` 는 문자열 배열 · 없으면 `[]`). 인증 필터가 막은 401도 같다.
 * 그래서 클라이언트는 상태코드별로 다른 파싱을 하지 않고 **`code` 로 갈래를 정한다.** `message` 는 표시 · 로깅에만 쓴다.
 */

/**
 * 세 백엔드가 실제로 내는 `code`. platform 은 `platform-api.md` 각 절, matching 은 `openapi.yaml` `ErrorResponse.code` 에서 옮겼다(2026-09-28).
 * 여기 없는 code가 오면 계약이 앞서갔거나 프록시가 만든 응답이다 — 문자열은 그대로 들고 다닌다.
 */
export type ErrorCode =
  /* 공통 */
  | 'VALIDATION_FAILED' | 'UNAUTHENTICATED' | 'ORIGIN_NOT_ALLOWED' | 'NOT_FOUND' | 'INTERNAL_ERROR'
  /* platform — 인증 · 소셜 */
  | 'INVALID_REFRESH_TOKEN' | 'NO_PENDING_SOCIAL_SIGNUP' | 'NICKNAME_TAKEN' | 'SOCIAL_ALREADY_LINKED'
  | 'LAST_SOCIAL_IDENTITY' | 'OAUTH_PROVIDER_NOT_CONFIGURED'
  /* platform — 게임 계정 · 전적 (`PUBG_PLAYER_NOT_FOUND` 는 2026-09-29 — PUBG 연동. `GAME_ACCOUNT_NOT_FOUND` · `GAME_STATS_NOT_SUPPORTED` 는 2026-09-30 에 전적 갱신 요청과 함께 없어졌다 — P-42) */
  | 'RIOT_ID_NOT_FOUND' | 'PUBG_PLAYER_NOT_FOUND' | 'GAME_STATS_UNAVAILABLE' | 'TOO_MANY_STATS_REFRESHES'
  /* platform — 사람 */
  | 'USER_NOT_FOUND'
  /* platform — 모집 글 · 자동 합류 (`ROOM_HAS_OTHER_MEMBERS` 는 2026-10-01 에 글 고치기(`PATCH`)와 함께 없어졌다 — 소유자 결정 · platform P-45) */
  | 'POST_NOT_FOUND' | 'POST_NOT_RECRUITING' | 'POST_CONFIRMED' | 'ALREADY_RECRUITING' | 'NOT_POST_HOST'
  | 'NO_MATCHING_POST'
  /* platform — 방 · 자동 매칭 파티의 방 */
  | 'ROOM_NOT_FOUND' | 'ROOM_FULL' | 'ROOM_CONFIRMED' | 'ROOM_NOT_CONFIRMED' | 'ROOM_ALREADY_EXISTS' | 'IN_OTHER_ROOM'
  | 'NOT_IN_ROOM' | 'TARGET_NOT_IN_ROOM' | 'ROOM_STATE_UNAVAILABLE' | 'MATCH_PARTY_NOT_FOUND' | 'NOT_PARTY_MEMBER'
  | 'NOT_HOST' | 'NOT_ENOUGH_MEMBERS' | 'CANNOT_KICK_SELF'
  /* platform — 친구 · 차단 · 신고 (문구는 `domain/socialErrors.ts`) */
  | 'CANNOT_FRIEND_SELF' | 'CANNOT_BLOCK_SELF' | 'CANNOT_REPORT_SELF' | 'ALREADY_FRIENDS' | 'ALREADY_BLOCKED'
  | 'FRIEND_REQUEST_ALREADY_SENT' | 'FRIEND_REQUEST_ALREADY_RECEIVED' | 'FRIEND_REQUEST_NOT_FOUND'
  /* matching */
  | 'INVALID_REQUEST' | 'BAD_REQUEST' | 'INVALID_MATCH_CONDITION' | 'ALREADY_QUEUED' | 'IN_ROOM'
  | 'MATCH_REQUEST_NOT_FOUND' | 'MATCH_REQUEST_MISMATCH' | 'PROPOSAL_NOT_FOUND' | 'PROPOSAL_CONFLICT' | 'NOT_PROPOSAL_MEMBER'
  | 'MATCHING_UNAVAILABLE';

export interface ErrorResponse {
  code: string;
  message: string;
  details: string[];
}

/**
 * 계약이 정한 code라도 서버 버전이 앞서갈 수 있으므로 문자열을 그대로 들고 다닌다.
 * 분기에는 `code`만 쓰고 `message`는 표시·로깅에만 쓴다.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message?: string,
    /** 400 `VALIDATION_FAILED` 의 `"필드: 사유"` 줄들. 없으면 빈 배열 */
    readonly details: string[] = [],
    /** 503 · 429 가 주는 `Retry-After`(초). 없으면 null */
    readonly retryAfterSeconds: number | null = null,
  ) {
    super(message ?? code);
    this.name = 'ApiError';
  }

  /** 계약 목록에 있는 code인가. 아니면 계약이 앞서갔거나 프록시가 만든 응답이다. */
  get isKnownCode(): boolean {
    return KNOWN_CODES.has(this.code);
  }
}

export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError;

/** 특정 code인지 검사한다. `catch`에서 분기할 때 쓴다. */
export const hasErrorCode = (e: unknown, ...codes: ErrorCode[]): boolean =>
  isApiError(e) && (codes as string[]).includes(e.code);

const KNOWN_CODES = new Set<string>([
  'VALIDATION_FAILED', 'UNAUTHENTICATED', 'ORIGIN_NOT_ALLOWED', 'NOT_FOUND', 'INTERNAL_ERROR',
  'INVALID_REFRESH_TOKEN', 'NO_PENDING_SOCIAL_SIGNUP', 'NICKNAME_TAKEN', 'SOCIAL_ALREADY_LINKED',
  'LAST_SOCIAL_IDENTITY', 'OAUTH_PROVIDER_NOT_CONFIGURED',
  'RIOT_ID_NOT_FOUND', 'PUBG_PLAYER_NOT_FOUND', 'GAME_STATS_UNAVAILABLE', 'TOO_MANY_STATS_REFRESHES',
  'USER_NOT_FOUND',
  'POST_NOT_FOUND', 'POST_NOT_RECRUITING', 'POST_CONFIRMED', 'ALREADY_RECRUITING', 'NOT_POST_HOST',
  'NO_MATCHING_POST',
  'ROOM_NOT_FOUND', 'ROOM_FULL', 'ROOM_CONFIRMED', 'ROOM_NOT_CONFIRMED', 'ROOM_ALREADY_EXISTS', 'IN_OTHER_ROOM',
  'NOT_IN_ROOM', 'TARGET_NOT_IN_ROOM', 'ROOM_STATE_UNAVAILABLE', 'MATCH_PARTY_NOT_FOUND', 'NOT_PARTY_MEMBER',
  'NOT_HOST', 'NOT_ENOUGH_MEMBERS', 'CANNOT_KICK_SELF',
  'CANNOT_FRIEND_SELF', 'CANNOT_BLOCK_SELF', 'CANNOT_REPORT_SELF', 'ALREADY_FRIENDS', 'ALREADY_BLOCKED',
  'FRIEND_REQUEST_ALREADY_SENT', 'FRIEND_REQUEST_ALREADY_RECEIVED', 'FRIEND_REQUEST_NOT_FOUND',
  'INVALID_REQUEST', 'BAD_REQUEST', 'INVALID_MATCH_CONDITION', 'ALREADY_QUEUED', 'IN_ROOM',
  'MATCH_REQUEST_NOT_FOUND', 'MATCH_REQUEST_MISMATCH', 'PROPOSAL_NOT_FOUND', 'PROPOSAL_CONFLICT', 'NOT_PROPOSAL_MEMBER',
  'MATCHING_UNAVAILABLE',
]);

/**
 * 4xx/5xx 본문을 ApiError로 바꾼다.
 *
 * 계약대로면 언제나 `{code, message, details}`지만, 프록시나 로드밸런서가 끼어들면 HTML이나
 * 빈 본문이 올 수 있다. 그때도 화면이 죽지 않게 status만으로 대체 code를 만든다.
 */
export function toApiError(status: number, rawBody: string, statusText = '', retryAfter: string | null = null): ApiError {
  const retry = retryAfter !== null && /^\d+$/.test(retryAfter) ? Number(retryAfter) : null;
  if (rawBody) {
    try {
      const parsed = JSON.parse(rawBody) as Partial<ErrorResponse>;
      if (parsed && typeof parsed.code === 'string') {
        const details = Array.isArray(parsed.details) ? parsed.details.filter((d): d is string => typeof d === 'string') : [];
        return new ApiError(status, parsed.code, parsed.message ?? parsed.code, details, retry);
      }
    } catch {
      /* 계약 밖 응답이다. 아래 fallback으로 간다 */
    }
  }
  return new ApiError(status, fallbackCode(status), rawBody || statusText || `HTTP ${status}`, [], retry);
}

/** 계약 밖 응답에도 code가 있어야 화면이 한 갈래로만 처리할 수 있다. */
function fallbackCode(status: number): string {
  if (status === 401) return 'UNAUTHENTICATED';
  return `HTTP_${status}`;
}

/** 사용자에게 보여 줄 문구. message가 비어 있으면 code로 대체한다. */
export const errorMessage = (e: unknown, fallback = '요청을 처리하지 못했습니다'): string =>
  (isApiError(e) ? e.message || e.code : e instanceof Error ? e.message : '') || fallback;
