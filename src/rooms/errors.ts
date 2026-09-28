import { errorMessage, isApiError } from '../api/error';

/**
 * 글 · 방 요청의 에러 코드 → 사용자 문구(platform-api.md "모집 글 · 목록" · "방"). 갈래는 `code` 로만 정한다(`api/error.ts`).
 * 여기 없는 코드는 서버의 `message`(없으면 fallback)를 그대로 보여 준다. 400 `VALIDATION_FAILED` 는 `details[0]`("필드: 사유")이 더 낫다.
 */
const ROOM_ERROR_MESSAGES: Record<string, string> = {
  ALREADY_RECRUITING: '모집 중인 내 글이 이미 있어요. 그 방을 닫거나 확정한 뒤 새 글을 올릴 수 있어요',
  ALREADY_QUEUED: '자동 매칭을 기다리는 중이에요. 매칭을 취소한 뒤 다시 시도해 주세요',
  IN_OTHER_ROOM: '이미 다른 방에 들어가 있어요. 그 방에서 나온 뒤 다시 시도해 주세요',
  ROOM_ALREADY_EXISTS: '같은 번호의 방이 이미 있어요. 잠시 뒤 다시 시도해 주세요',
  ROOM_STATE_UNAVAILABLE: '방 상태를 잠시 확인할 수 없어요. 몇 초 뒤 다시 시도해 주세요',
  POST_NOT_FOUND: '글을 찾을 수 없어요. 지워졌거나 볼 수 없는 글이에요',
  POST_NOT_RECRUITING: '모집이 끝난 글이에요',
  POST_CONFIRMED: '확정된 글은 지울 수 없어요',
  NOT_POST_HOST: '방장만 할 수 있어요',
  ROOM_HAS_OTHER_MEMBERS: '방에 다른 사람이 있어 글을 고칠 수 없어요',
  ROOM_NOT_FOUND: '방이 없어요. 이미 닫혔을 수 있어요',
  ROOM_FULL: '정원이 가득 찼어요',
  ROOM_CONFIRMED: '이미 확정된 방이라 들어갈 수 없어요',
  NOT_IN_ROOM: '이 방에 들어와 있지 않아요',
  TARGET_NOT_IN_ROOM: '그 사람은 이미 방에 없어요',
  NOT_HOST: '방장만 할 수 있어요',
  NOT_ENOUGH_MEMBERS: '2명 이상일 때 확정할 수 있어요',
  CANNOT_KICK_SELF: '자기 자신은 내보낼 수 없어요',
  MATCH_PARTY_NOT_FOUND: '파티가 사라졌어요. 확정 뒤 10분 안에 들어와야 해요',
  NOT_PARTY_MEMBER: '이 파티의 멤버가 아니에요',
};

export function roomErrorMessage(error: unknown, fallback = '요청을 처리하지 못했어요'): string {
  if (isApiError(error)) {
    if (error.code === 'VALIDATION_FAILED' && error.details[0]) return error.details[0];
    const known = ROOM_ERROR_MESSAGES[error.code];
    if (known) return error.retryAfterSeconds && error.status === 503 ? `${known}(${error.retryAfterSeconds}초 뒤)` : known;
  }
  return errorMessage(error, fallback);
}
