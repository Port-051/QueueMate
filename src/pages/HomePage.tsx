import { useCallback, useRef, useState } from 'react';
import { Outlet, useMatch, useNavigate, useOutletContext } from 'react-router-dom';
import type { GameKey } from '../api/types';
import type { AppShellOutletContext } from '../components/AppShell';
import { RoomBoardHome } from '../rooms/RoomBoardHome';
import { ROOM_SPLIT_QUERY, useMediaQuery } from '../rooms/roomPanel';
import '../rooms/room-panel.css';

/** 방 화면(`PartyRoomPage`)이 받는 맥락 — 게임 고르기(`AppShell`) + 방의 게임으로 게시판을 한 번 맞추기. */
export interface BoardRoomOutletContext extends AppShellOutletContext {
  /** 게시판 방의 글을 처음 읽었을 때 — 그 방을 처음 여는 것이면 게시판의 게임을 방의 게임으로 맞춘다(딥 링크 · 새로 고침). 같은 방은 한 번만. */
  syncRoomGame(roomId: string, game: GameKey): void;
}

/**
 * 홈 = 게시판(방 카드 보드 — 2026-09-28 소유자 결정) + 오른쪽 방 패널(2026-09-30 소유자 지시 — "방으로 입장 누르면 방을 옮기지 말고 아예 왼쪽으로 게시판을 밀어버려").
 * 옛 홈 `LegacyRecruitmentHome` 은 파일만 남겼고 라우트에서 뺐다(대응물 없음 — 지우지 않는다, 2026-09-29 소유자 결정).
 *
 * - **경로가 원본이다** — `/app/home` 은 게시판만, `/app/party/{roomId}` 는 게시판 + 그 방. 두 경로가 이 레이아웃 라우트(`App.tsx`) 아래라 오가도 게시판은 다시 그려지지 않는다
 *   (필터 · 펼친 목록 · 스크롤이 남는다). 방 안의 일(heartbeat · `ROOM_*` · 음성 · 채팅 · 나가기 · 강퇴 · 확정)은 전부 그대로 `PartyRoomPage` 이고 그것이 패널의 `<Outlet/>` 이다.
 *   입장 · 글 쓰기 · 자동 합류 · 제안 화면의 "수락하고 파티룸 입장" · 자동 매칭 파티의 방이 다 `/app/party/{roomId}` 로 가므로 전부 이 모양으로 연다.
 * - **넓은 화면(1100px 이상)** — 게시판이 왼쪽으로 좁아지고(55%) 방이 오른쪽(45%)에서 밀려 들어온다. 패널은 화면 높이에 붙어(sticky) 게시판만 스크롤된다.
 *   방에 있는 동안은 자동 매칭을 시작할 수 없어 게시판 맨 위의 자동 매칭 판은 접는다(`RoomBoardHome` `roomPanelOpen`).
 * - **좁은 화면** — 방이 오른쪽에서 밀려 들어와 화면을 덮고 맨 위 "게시판으로" 가 `/app/home` 으로 간다 — **방에서 나오는 것이 아니라 패널만 숨긴다**(음성 · 채팅은
 *   `PartySessionContext` 가 앱 전체에 걸려 있어 이어진다). 게시판의 "방으로 돌아가기" 가 다시 연다.
 * - 방을 나가면(나가기 · 닫힘 · 강퇴 — `PartyRoomPage` 가 `/app/home` 으로 보낸다) 패널이 닫히고 게시판이 다시 넓어진다 — 닫힐 때 빈 패널이 줄어드는 움직임을 한 번 그린다.
 * - 딥 링크 · 새로 고침으로 `/app/party/{roomId}` 에 오면 게시판은 그 방의 게임이다(게시판 방 — 글의 `game`, `syncRoomGame`). 자동 매칭 방은 글이 없어 지금 고른 게임 그대로다.
 */
export function HomePage() {
  const shell = useOutletContext<AppShellOutletContext>();
  const { selectedGame, setSelectedGame } = shell;
  const party = useMatch('/app/party/:roomId');
  const roomId = party?.params.roomId ?? null;
  const navigate = useNavigate();
  const split = useMediaQuery(ROOM_SPLIT_QUERY);

  const synced = useRef<string | null>(null);
  const gameRef = useRef(selectedGame);
  gameRef.current = selectedGame;
  const syncRoomGame = useCallback((id: string, game: GameKey) => {
    if (synced.current === id) return;
    synced.current = id;
    if (gameRef.current !== game) setSelectedGame(game);
  }, [setSelectedGame]);

  // 방이 닫힐 때 빈 패널이 한 번 줄어든다 — 렌더 중에 앞 값과 견준다(효과로 하면 한 프레임 넓어졌다 줄어든다).
  const [previousRoom, setPreviousRoom] = useState(roomId);
  const [closing, setClosing] = useState(false);
  if (previousRoom !== roomId) {
    setPreviousRoom(roomId);
    setClosing(Boolean(previousRoom) && !roomId);
  }

  const context: BoardRoomOutletContext = { ...shell, syncRoomGame };
  const covered = Boolean(roomId) && !split;
  return <div className={`board-split${roomId ? ' has-room' : ''}`}>
    {/* 좁은 화면에서 방이 게시판을 덮는 동안 게시판은 초점 · 읽기에서 뺀다. */}
    <div className="board-split-board" aria-hidden={covered || undefined} {...{ inert: covered ? '' : undefined }}>
      <RoomBoardHome roomPanelOpen={Boolean(roomId)} />
    </div>
    {roomId ? <section className="room-panel" aria-label="방" key={roomId}>
      <div className="room-panel-inner">
        <div className="room-panel-bar">
          <button type="button" className="room-panel-back" onClick={() => navigate('/app/home')}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 5 8 12l7 7" /></svg>
            게시판으로
          </button>
          <span className="room-panel-bar-note">방에서 나가지 않아요</span>
        </div>
        <Outlet context={context} />
      </div>
    </section> : closing ? <div className="room-panel is-closing" aria-hidden="true" onAnimationEnd={() => setClosing(false)} /> : null}
  </div>;
}
