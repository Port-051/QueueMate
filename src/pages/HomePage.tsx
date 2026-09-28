import { RoomBoardHome } from '../rooms/RoomBoardHome';

/**
 * 홈은 방 카드 보드다(2026-09-28 소유자 결정 — mock 전용이던 것을 기본 홈으로).
 * 옛 홈 `LegacyRecruitmentHome` 은 파일만 남겼고 라우트에서 뺐다 — 처지는 4단계에서 정한다(START_HERE.md §5).
 * 보드가 부르는 API 는 아직 원본의 `/rooms` 다 — 우리 `posts` · `rooms/{roomId}/members` 로 바꾸는 것도 4단계다.
 */
export function HomePage() {
  return <RoomBoardHome />;
}
