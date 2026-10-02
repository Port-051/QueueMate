import { useCallback, useRef, useState } from 'react';
import { Outlet, useMatch, useNavigate, useOutletContext } from 'react-router-dom';
import type { GameKey } from '../api/types';
import type { AppShellOutletContext } from '../components/AppShell';
import { RoomBoardHome } from '../rooms/RoomBoardHome';
import { ROOM_SPLIT_QUERY, useMediaQuery } from '../rooms/roomPanel';
import { useRoomSession } from '../state/RoomSessionContext';
import { PartyRoomPage } from './PartyRoomPage';
import '../rooms/room-panel.css';

/** 방 화면(`PartyRoomPage`)이 받는 맥락 — 게임 고르기(`AppShell`) + 방의 게임으로 게시판을 한 번 맞추기. */
export interface BoardRoomOutletContext extends AppShellOutletContext {
  /** 게시판 방의 글을 처음 읽었을 때 — 그 방을 처음 여는 것이면 게시판의 게임을 방의 게임으로 맞춘다(딥 링크 · 새로 고침). 같은 방은 한 번만. */
  syncRoomGame(roomId: string, game: GameKey): void;
}

/**
 * 게시판과 방을 한 레이아웃에서 유지한다. 데스크톱 홈은 참여 중인 방을 계속 표시한다.
 * PartyRoomPage를 같은 위치에 렌더해 /party/:roomId ↔ /home 이동 시 초안과 패널 상태를 보존한다.
 * 모바일 홈은 게시판만 표시하고 /party/:roomId는 방을 덮어 연다. 숨겨도 전역 PartySession의 연결은 이어진다.
 * 자동 매칭 제안은 Outlet을 사용하며, 수락 뒤 같은 패널에서 파티룸으로 전환한다.
 */
export function HomePage() {
  const shell = useOutletContext<AppShellOutletContext>();
  const { selectedGame, setSelectedGame } = shell;
  const party = useMatch('/app/party/:roomId');
  const proposal = useMatch('/app/proposals/:proposalId');
  const { roomId: activeRoomId } = useRoomSession();
  const split = useMediaQuery(ROOM_SPLIT_QUERY);
  const roomId = party?.params.roomId ?? (!proposal && split ? activeRoomId : null);
  // 패널에 여는 것 — 방이거나 자동 매칭의 제안. 제안 → 방은 같은 id(partyId)라 패널이 그대로 남고 안만 바뀐다.
  const panelId = roomId ?? proposal?.params.proposalId ?? null;
  const navigate = useNavigate();

  const synced = useRef<string | null>(null);
  const gameRef = useRef(selectedGame);
  gameRef.current = selectedGame;
  const syncRoomGame = useCallback((id: string, game: GameKey) => {
    if (synced.current === id) return;
    synced.current = id;
    if (gameRef.current !== game) setSelectedGame(game);
  }, [setSelectedGame]);

  // 방이 닫힐 때 빈 패널이 한 번 줄어든다 — 렌더 중에 앞 값과 견준다(효과로 하면 한 프레임 넓어졌다 줄어든다).
  const [previousPanel, setPreviousPanel] = useState(panelId);
  const [closing, setClosing] = useState(false);
  if (previousPanel !== panelId) {
    setPreviousPanel(panelId);
    setClosing(Boolean(previousPanel) && !panelId);
  }

  const context: BoardRoomOutletContext = { ...shell, syncRoomGame };
  const covered = Boolean(panelId) && !split;
  return <div className={`board-split${panelId ? ' has-room' : ''}`}>
    {/* 좁은 화면에서 방이 게시판을 덮는 동안 게시판은 초점 · 읽기에서 뺀다. */}
    <div className="board-split-board" aria-hidden={covered || undefined} {...{ inert: covered ? '' : undefined }}>
      <RoomBoardHome roomPanelOpen={Boolean(panelId)} />
    </div>
    {panelId ? <section className="room-panel" aria-label={roomId ? '방' : '빠른매치 제안'} key={panelId}>
      <div className="room-panel-inner">
        <div className="room-panel-bar">
          <button type="button" className="room-panel-back" onClick={() => navigate('/app/home')}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 5 8 12l7 7" /></svg>
            게시판으로
          </button>
          <span className="room-panel-bar-note">{roomId ? '방에서 나가지 않아요' : '제안은 그대로예요'}</span>
        </div>
        {roomId ? <PartyRoomPage key={roomId} activeRoomId={roomId} onRoomGame={syncRoomGame} /> : <Outlet context={context} />}
      </div>
    </section> : closing ? <div className="room-panel is-closing" aria-hidden="true" onAnimationEnd={() => setClosing(false)} /> : null}
  </div>;
}
