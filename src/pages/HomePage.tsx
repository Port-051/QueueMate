import { RoomBoardHome } from '../rooms/RoomBoardHome';

/**
 * 홈은 방 카드 보드다(2026-09-28 소유자 결정 — mock 전용이던 것을 기본 홈으로).
 * 옛 홈 `LegacyRecruitmentHome` 은 파일만 남겼고 라우트에서 뺐다(대응물 없음 — 지우지 않는다, 2026-09-29 소유자 결정).
 * 보드는 4단계부터 우리 게시판이다 — `GET /posts?game=` · `POST /posts` · `POST /rooms/{roomId}/members` · `BOARD_CHANGED`(`rooms/useRoomData.ts`).
 */
export function HomePage() {
  return <RoomBoardHome />;
}
