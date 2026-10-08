import { errorMessage, isApiError } from '../api/error';

/**
 * 친구 · 차단 · 신고 요청의 에러 코드 → 사용자 문구(platform-api.md "차단" · "친구 · 신고 · 최근 함께한 사람"). 갈래는 `code` 로만 정한다(`api/error.ts`).
 * 여기 없는 코드는 서버의 `message`(없으면 fallback)를 그대로 보여 준다. 400 `VALIDATION_FAILED` 는 `details[0]`("필드: 사유")이 더 낫다.
 * `USER_NOT_FOUND` 는 없는 번호 · 숫자가 아닌 값 · **어느 방향이든 차단 관계**를 한 문구로 덮는다 — 차단당한 사실을 새지 않게 서버가 같은 404 를 주기 때문이다.
 */
const SOCIAL_ERROR_MESSAGES: Record<string, string> = {
  USER_NOT_FOUND: '그 번호의 사용자를 찾을 수 없어요',
  CANNOT_FRIEND_SELF: '자기 자신에게는 친구 요청을 보낼 수 없어요',
  ALREADY_FRIENDS: '이미 친구예요',
  FRIEND_REQUEST_ALREADY_SENT: '이미 친구 요청을 보냈어요. 상대의 답을 기다려 주세요',
  FRIEND_REQUEST_ALREADY_RECEIVED: '상대가 먼저 친구 요청을 보냈어요. 받은 요청에서 수락해 주세요',
  FRIEND_REQUEST_NOT_FOUND: '이미 처리됐거나 없는 친구 요청이에요',
  CANNOT_BLOCK_SELF: '자기 자신은 차단할 수 없어요',
  ALREADY_BLOCKED: '이미 차단한 사용자예요',
  CANNOT_REPORT_SELF: '자기 자신은 신고할 수 없어요',
};

export function socialErrorMessage(error: unknown, fallback = '요청을 처리하지 못했어요'): string {
  if (isApiError(error)) {
    if (error.code === 'VALIDATION_FAILED' && error.details[0]) return error.details[0];
    const known = SOCIAL_ERROR_MESSAGES[error.code];
    if (known) return known;
  }
  return errorMessage(error, fallback);
}

/** 사용자 번호로 쓸 수 있는 입력인가(1~19자리 숫자 — 서버의 `sub` 규칙과 같다). 앞뒤 공백과 `#` 은 떼어 준다. */
export function parseUserNumber(raw: string): string | null {
  const value = raw.trim().replace(/^#/, '');
  return /^[0-9]{1,19}$/.test(value) ? value : null;
}
