/**
 * contracts/openapi.yaml v2 `ErrorResponse`.
 *
 * 모든 4xx/5xx는 예외 없이 `{code, message}`다. 인증 필터가 막은 401도 같다
 * (docs/14 §0.6). 그래서 클라이언트는 상태코드별로 다른 파싱을 하지 않는다.
 */

/** openapi `ErrorResponse.code` enum 전량. 여기 없는 code가 오면 서버 버그다. */
export type ErrorCode =
  /* 400 */
  | 'VALIDATION_FAILED'
  /* 401 */
  | 'UNAUTHORIZED'
  /* 404 */
  | 'USER_NOT_FOUND'
  | 'UNKNOWN_GAME'
  | 'UNKNOWN_GAME_MODE'
  | 'MATCH_REQUEST_NOT_FOUND'
  | 'PROPOSAL_NOT_FOUND'
  | 'PARTY_NOT_FOUND'
  | 'RESERVATION_NOT_FOUND'
  | 'FRIENDSHIP_NOT_FOUND'
  | 'BLOCK_NOT_FOUND'
  | 'GAME_ACCOUNT_NOT_FOUND'
  | 'FRIEND_REQUEST_NOT_FOUND'
  /* 409 */
  | 'EMAIL_ALREADY_IN_USE'
  | 'NICKNAME_ALREADY_IN_USE'
  | 'EMAIL_OR_NICKNAME_ALREADY_IN_USE'
  | 'GAME_ACCOUNT_ALREADY_LINKED'
  | 'ACTIVE_MATCH_REQUEST_EXISTS'
  | 'MATCH_REQUEST_NOT_CANCELLABLE'
  | 'PROPOSAL_EXPIRED'
  | 'PROPOSAL_NOT_PENDING'
  | 'PROPOSAL_NOT_CONFIRMED'
  | 'PROPOSAL_NOT_FULLY_ACCEPTED'
  | 'PROPOSAL_MEMBER_MISMATCH'
  | 'PARTY_SIZE_MISMATCH'
  | 'PARTY_CLOSED'
  | 'PARTY_PLAYING'
  | 'ALREADY_LEFT'
  | 'BLOCKED_MEMBERS'
  | 'RESERVATION_NOT_EDITABLE'
  | 'RESERVATION_NOT_CANCELLABLE'
  | 'OVERLAPPING_RESERVATION'
  | 'SELF_FRIEND_REQUEST'
  | 'SELF_BLOCK'
  | 'SELF_REPORT'
  | 'ALREADY_FRIENDS'
  | 'REQUEST_ALREADY_PENDING'
  | 'INVERSE_REQUEST_PENDING'
  | 'FRIEND_REQUEST_NOT_PENDING'
  | 'BLOCKED_RELATION'
  | 'ALREADY_BLOCKED'
  /* 415 */
  | 'UNSUPPORTED_MEDIA_TYPE'
  /* 429 */
  | 'SIGNUP_RATE_EXCEEDED'
  | 'LOGIN_ATTEMPTS_EXCEEDED'
  /* 503 */
  | 'MATCHING_UNAVAILABLE';

export interface ErrorResponse {
  code: string;
  message: string;
}

/**
 * 계약이 정한 code라도 서버 버전이 앞서갈 수 있으므로 문자열을 그대로 들고 다닌다.
 * 분기에는 `code`만 쓰고 `message`는 표시·로깅에만 쓴다 (docs/14 §0.6).
 */
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message?: string) {
    super(message ?? code);
    this.name = 'ApiError';
  }

  /** 계약 catalog에 있는 code인가. 아니면 서버 버그이거나 프록시가 만든 응답이다. */
  get isKnownCode(): boolean {
    return KNOWN_CODES.has(this.code);
  }
}

export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError;

/** 특정 code인지 검사한다. `catch`에서 분기할 때 쓴다. */
export const hasErrorCode = (e: unknown, ...codes: ErrorCode[]): boolean =>
  isApiError(e) && (codes as string[]).includes(e.code);

const KNOWN_CODES = new Set<string>([
  'VALIDATION_FAILED', 'UNAUTHORIZED',
  'USER_NOT_FOUND', 'UNKNOWN_GAME', 'UNKNOWN_GAME_MODE', 'MATCH_REQUEST_NOT_FOUND',
  'PROPOSAL_NOT_FOUND', 'PARTY_NOT_FOUND', 'RESERVATION_NOT_FOUND', 'FRIENDSHIP_NOT_FOUND',
  'BLOCK_NOT_FOUND', 'GAME_ACCOUNT_NOT_FOUND', 'FRIEND_REQUEST_NOT_FOUND',
  'EMAIL_ALREADY_IN_USE', 'NICKNAME_ALREADY_IN_USE', 'EMAIL_OR_NICKNAME_ALREADY_IN_USE',
  'GAME_ACCOUNT_ALREADY_LINKED', 'ACTIVE_MATCH_REQUEST_EXISTS', 'MATCH_REQUEST_NOT_CANCELLABLE',
  'PROPOSAL_EXPIRED', 'PROPOSAL_NOT_PENDING', 'PROPOSAL_NOT_CONFIRMED',
  'PROPOSAL_NOT_FULLY_ACCEPTED', 'PROPOSAL_MEMBER_MISMATCH', 'PARTY_SIZE_MISMATCH',
  'PARTY_CLOSED', 'PARTY_PLAYING', 'ALREADY_LEFT', 'BLOCKED_MEMBERS',
  'RESERVATION_NOT_EDITABLE', 'RESERVATION_NOT_CANCELLABLE', 'OVERLAPPING_RESERVATION',
  'SELF_FRIEND_REQUEST', 'SELF_BLOCK', 'SELF_REPORT', 'ALREADY_FRIENDS',
  'REQUEST_ALREADY_PENDING', 'INVERSE_REQUEST_PENDING', 'FRIEND_REQUEST_NOT_PENDING',
  'BLOCKED_RELATION', 'ALREADY_BLOCKED', 'UNSUPPORTED_MEDIA_TYPE',
  'SIGNUP_RATE_EXCEEDED', 'LOGIN_ATTEMPTS_EXCEEDED', 'MATCHING_UNAVAILABLE',
]);

/**
 * 4xx/5xx 본문을 ApiError로 바꾼다.
 *
 * 계약대로면 언제나 `{code, message}`지만, 프록시나 로드밸런서가 끼어들면 HTML이나
 * 빈 본문이 올 수 있다. 그때도 화면이 죽지 않게 status만으로 대체 code를 만든다.
 */
export function toApiError(status: number, rawBody: string, statusText = ''): ApiError {
  if (rawBody) {
    try {
      const parsed = JSON.parse(rawBody) as Partial<ErrorResponse>;
      if (parsed && typeof parsed.code === 'string') {
        return new ApiError(status, parsed.code, parsed.message ?? parsed.code);
      }
    } catch {
      /* 계약 밖 응답이다. 아래 fallback으로 간다 */
    }
  }
  return new ApiError(status, fallbackCode(status), rawBody || statusText || `HTTP ${status}`);
}

/** 계약 밖 응답에도 code가 있어야 화면이 한 갈래로만 처리할 수 있다. */
function fallbackCode(status: number): string {
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 415) return 'UNSUPPORTED_MEDIA_TYPE';
  return `HTTP_${status}`;
}

/** 사용자에게 보여 줄 문구. message가 비어 있으면 code로 대체한다. */
export const errorMessage = (e: unknown, fallback = '요청을 처리하지 못했습니다'): string =>
  (isApiError(e) ? e.message || e.code : e instanceof Error ? e.message : '') || fallback;
