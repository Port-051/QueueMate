import { useEffect, useState } from 'react';

/**
 * 게시판을 왼쪽에 두고 방을 오른쪽 패널로 여는 폭(뷰포트 — 2026-09-30 · `pages/HomePage.tsx`). 이보다 좁으면 방이 화면을 덮고 "게시판으로" 로 숨긴다.
 * `rooms/room-panel.css` 의 1100px 과 같은 값이다 — 하나를 바꾸면 둘 다 바꾼다.
 */
export const ROOM_SPLIT_QUERY = '(min-width: 1100px)';

/** 미디어 질의가 맞는가 — 창 크기가 바뀌면 따라간다. */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => typeof window !== 'undefined' && window.matchMedia(query).matches);
  useEffect(() => {
    const list = window.matchMedia(query);
    const change = () => setMatches(list.matches);
    change();
    list.addEventListener('change', change);
    return () => list.removeEventListener('change', change);
  }, [query]);
  return matches;
}
