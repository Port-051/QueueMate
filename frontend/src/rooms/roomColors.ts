import { roomColors } from '../domain/avatarColor';
import type { BoardRoom } from './types';

/**
 * 게시판 방 하나의 얼굴 색(사람 번호 → 팔레트 번호) — **한 방의 사람은 모두 다른 색이다**(2026-09-30 소유자 — `domain/avatarColor.ts` `roomColors`).
 * 사람 = 카드에 그리는 사람(모집 중이면 방 안 사람 · 확정이면 확정 순간의 파티원 전원(P-40) · 만료면 방장), 방장 = 글쓴이(`hostId` — 방장이 탈퇴한 확정된 글은 `null` 이라 방장 자리 없이 고른다 · P-48).
 * 게시판 카드(`RoomDeck`) · 좌석을 눌러 여는 프로필 창(`RoomMemberProfile`) · 방 화면의 음성 칸 좌석과 채팅(`PartyRoomPage`)이 같은 표를 써서 한 사람은 세 곳에서 같은 색이다.
 * `lateIds` — 카드에는 아직 없는 사람(방 화면이 아는 지금의 방 안 — 목록보다 먼저 바뀐다)은 카드의 사람이 색을 다 고른 뒤에 남은 색을 받는다(카드의 색을 바꾸지 않게).
 */
export function boardRoomColors(room: BoardRoom, lateIds: readonly string[] = []): Map<string, number> {
  const people = room.members.length ? room.members : room.host ? [room.host] : [];
  return roomColors(people.map(member => member.id), room.hostId, lateIds);
}
