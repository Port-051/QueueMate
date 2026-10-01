import { errorMessage, isApiError } from '../api/error';

/**
 * 글 · 방 요청의 에러 코드 → 사용자 문구(platform-api.md "모집 글 · 목록" · "방"). 갈래는 `code` 로만 정한다(`api/error.ts`).
 * 여기 없는 코드는 서버의 `message`(없으면 fallback)를 그대로 보여 준다. 400 `VALIDATION_FAILED` 는 `details[0]`("필드: 사유")이 더 낫다.
 */
const ROOM_ERROR_MESSAGES: Record<string, string> = {
  ALREADY_RECRUITING: '모집 중인 내 글이 이미 있어요. 그 방을 닫거나 확정한 뒤 새 글을 올릴 수 있어요',
  ALREADY_QUEUED: '퀵 매칭을 기다리는 중이에요. 매칭을 취소한 뒤 다시 시도해 주세요',
  IN_OTHER_ROOM: '이미 다른 방에 들어가 있어요. 그 방에서 나온 뒤 다시 시도해 주세요',
  ROOM_ALREADY_EXISTS: '같은 번호의 방이 이미 있어요. 잠시 뒤 다시 시도해 주세요',
  ROOM_STATE_UNAVAILABLE: '방 상태를 잠시 확인할 수 없어요. 몇 초 뒤 다시 시도해 주세요',
  POST_NOT_FOUND: '글을 찾을 수 없어요. 지워졌거나 볼 수 없는 글이에요',
  POST_NOT_RECRUITING: '모집이 끝난 글이에요',
  POST_CONFIRMED: '확정된 글은 지울 수 없어요',
  NOT_POST_HOST: '방장만 할 수 있어요',
  ROOM_NOT_FOUND: '방이 없어요. 이미 닫혔을 수 있어요',
  ROOM_FULL: '방이 가득 찼어요. 정원만큼 사람이 모였어요',
  ROOM_CONFIRMED: '이미 확정된 방이라 들어갈 수 없어요',
  NOT_IN_ROOM: '이 방에 들어와 있지 않아요',
  TARGET_NOT_IN_ROOM: '그 사람은 이미 방에 없어요',
  NOT_HOST: '방장만 할 수 있어요',
  NOT_ENOUGH_MEMBERS: '2명 이상일 때 확정할 수 있어요',
  CANNOT_KICK_SELF: '자기 자신은 내보낼 수 없어요',
  MATCH_PARTY_NOT_FOUND: '파티가 사라졌어요. 확정 뒤 10분 안에 들어와야 해요',
  NOT_PARTY_MEMBER: '이 파티의 멤버가 아니에요',
};

/**
 * 400 `VALIDATION_FAILED` 의 `details` 줄("필드: 사유")에서 필드 이름을 화면의 칸 이름으로 바꾼다 — `hostPosition: 필요합니다` → `내 포지션: 필요합니다`.
 * 글 쓰기 팝업의 칸 이름과 같다(2026-09-30 — 내 포지션이 생기며 붙였다). 모르는 필드는 받은 줄 그대로다. 사유는 서버의 글귀 그대로다.
 */
const POST_FIELD_LABELS: Record<string, string> = {
  hostPosition: '내 포지션', wantedPositions: '찾는 포지션', title: '한마디', mode: '게임 모드', voice: '음성', 'conditions.perspective': '시점', position: '포지션',
};

function readableDetail(detail: string): string {
  const match = /^([\w.]+):\s*(.*)$/.exec(detail);
  const label = match ? POST_FIELD_LABELS[match[1]] : undefined;
  return label && match ? `${label}: ${match[2]}` : detail;
}

/**
 * 입장의 포지션 거절인가 — 400 `VALIDATION_FAILED` 의 `details` 가 `"position: …"`(안 골랐다 · 남지 않은 포지션이다 — 남이 먼저 골랐어도 이것이다 · 포지션 없는 방에 줬다 — 2026-10-01 · platform P-44).
 * 참여 창이 목록을 다시 받고 남은 포지션에서 다시 고르게 하는 갈래다(`RoomJoinConfirm` · `useRoomData#join`).
 */
export const isPositionError = (error: unknown): boolean =>
  isApiError(error) && error.code === 'VALIDATION_FAILED' && error.details.some(detail => /^position:/.test(detail));

export function roomErrorMessage(error: unknown, fallback = '요청을 처리하지 못했어요'): string {
  if (isApiError(error)) {
    if (error.code === 'VALIDATION_FAILED' && error.details[0]) return readableDetail(error.details[0]);
    const known = ROOM_ERROR_MESSAGES[error.code];
    if (known) return error.retryAfterSeconds && error.status === 503 ? `${known}(${error.retryAfterSeconds}초 뒤)` : known;
  }
  return errorMessage(error, fallback);
}
