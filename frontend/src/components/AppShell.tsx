import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { ComponentType } from 'react';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useRoomSession } from '../state/RoomSessionContext';
import { useSocial } from '../state/SocialContext';
import { Logo } from './Logo';
import { GameBadge } from './GameSymbol';
import { availableGames } from '../domain/gameConfig';
import type { GameKey } from '../api/types';
import { Avatar, Modal } from './ui';
import { IconHome, IconParty, IconPaperPlane } from './icons';
import { SiteFooter } from './SiteFooter';
import { ROOM_SPLIT_QUERY } from '../rooms/roomPanel';

interface NavItem { to: string; label: string; icon: ComponentType<{ size?: number; filled?: boolean }>; }
export interface AppShellOutletContext { selectedGame: GameKey; setSelectedGame(game: GameKey): void; }

/** 게시판 화면 — 게시판만(`/app/home`)이든 방 패널이 열렸든(`/app/party/{roomId}` · 자동 매칭의 제안 `/app/proposals/{partyId}`) 같은 레이아웃이다(`pages/HomePage.tsx`). */
const isPanelRoute = (pathname: string) => pathname.startsWith('/app/party/') || pathname.startsWith('/app/proposals/');
const isBoardRoute = (pathname: string) => pathname === '/app/home' || isPanelRoute(pathname);

/** 홈 · 친구 관리 · 실제 서버 개인 메시지(P-53). 방의 음성 연결은 화면 이동에도 유지된다. */
const NAV: NavItem[] = [
  { to: '/app/home', label: '홈', icon: IconHome },
  { to: '/app/friends', label: '친구', icon: IconParty },
  { to: '/app/messages', label: '메시지', icon: IconPaperPlane },
];

export function AppShell() {
  const { user } = useAuth();
  const { request, proposal } = useMatch();
  const { roomId } = useRoomSession();
  // "친구" 배지 = 받은 친구 요청(대기 중인 것만 온다 — `GET /friend-requests?direction=RECEIVED`) 수. 다시 세는 때는 `SocialContext` 가 정한다 —
  // 로그인 직후 · `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` · 수락 · 거절 · 취소 직후 · SSE 재연결 · 탭 복귀(타이머로 묻지 않는다).
  const { receivedRequests } = useSocial();
  const requestCount = receivedRequests.length;
  const location = useLocation();
  const navigate = useNavigate();
  const [selectedGame, setSelectedGame] = useState<GameKey>('LOL');
  const [navigationPicked, setNavigationPicked] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname, proposal?.partyId]);
  // 게시판(`/app/home`)과 방(`/app/party/…`)은 한 화면이다 — 게시판이 왼쪽에 남으니 둘 사이를 오갈 때는 맨 위로 올리지 않는다(2026-09-30 · `pages/HomePage.tsx`).
  const boardRoute = isBoardRoute(location.pathname);
  const previousPath = useRef(location.pathname);
  useEffect(() => {
    const stayOnBoard = isBoardRoute(previousPath.current) && isBoardRoute(location.pathname) && !location.hash;
    previousPath.current = location.pathname;
    if (stayOnBoard) return;
    const frame = window.requestAnimationFrame(() => {
      const target = location.hash ? document.getElementById(location.hash.slice(1)) : null;
      if (target) target.scrollIntoView({ block: 'start' });
      else window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [location.pathname, location.hash]);

  const gameNavigation = (mobile = false) => <div className={`sidebar-games${mobile ? ' mobile-games' : ''}`} role="group" aria-label="게임 선택">
    {availableGames().map(game => <button key={game.key} type="button" className={`nav-link game-nav-link${selectedGame === game.key ? ' active' : ''}`} aria-label={`${game.name} 매칭`} aria-pressed={selectedGame === game.key} onClick={() => {
      setSelectedGame(game.key); setMenuOpen(false); setNavigationPicked(true);
      // 넓은 화면에서 방 패널이 열려 있으면 게시판(왼쪽)만 바꾸고 방은 그대로 둔다. 좁은 화면은 방이 게시판을 덮고 있어 게시판으로 간다(방에서 나가지는 않는다).
      const keepRoom = isPanelRoute(location.pathname) && window.matchMedia(ROOM_SPLIT_QUERY).matches;
      if (!keepRoom && (location.pathname !== '/app/home' || location.search)) navigate('/app/home');
    }}><span className="nav-icon"><GameBadge game={game.key} size={28} className="game-nav-logo" /></span><span className="nav-label">{game.shortName}</span></button>)}
  </div>;

  const navigation = (mobile = false) => (
    <nav className={mobile ? 'mobile-nav' : 'side-nav'} aria-label="주 메뉴">
      {NAV.map((item) => {
        const MenuIcon = item.icon;
        // 방 패널이 열린 화면(`/app/party/…`)도 게시판 = 홈이다.
        const alsoActive = item.to === '/app/home' && boardRoute;
        return <NavLink key={item.to} to={item.to}
          aria-label={item.label} title={item.label}
          onClick={() => { setMenuOpen(false); setNavigationPicked(true); }}
          className={({ isActive }) => `nav-link${isActive || alsoActive ? ' active' : ''}`}>
          {({ isActive }) => <>
            <span className="nav-icon"><MenuIcon size={24} filled={isActive || alsoActive} />
              {item.to === '/app/friends' && requestCount > 0 ? <span className={`nav-badge${requestCount > 99 ? ' nav-badge-long' : ''}`} aria-label={`받은 친구 요청 ${requestCount}개`}>{requestCount > 99 ? '99+' : requestCount}</span> : null}
            </span><span className="nav-label">{item.label}</span>
            {item.to === '/app/home' && (request || roomId) ? <span className="nav-dot" role="img" aria-label={request ? '빠른매치 중' : '방에 참여 중'} /> : null}
          </>}
        </NavLink>;
      })}
      {user ? <NavLink to="/app/me" className="nav-link nav-profile" aria-label="프로필" title="프로필" onClick={() => { setMenuOpen(false); setNavigationPicked(true); }}>
        <span className="nav-icon"><Avatar userId={user.userId} name={user.nickname} size={24} /></span>
        <span className="nav-label">프로필</span>
      </NavLink> : null}
    </nav>
  );

  return (
    <div className="app-shell">
      <aside onPointerLeave={() => setNavigationPicked(false)} className={`sidebar${navigationPicked ? ' navigation-picked' : ''}`}>
        <div className="sidebar-navigation">
          <div className="sidebar-top">
            <Link to="/app/home" className="sidebar-brand-link" aria-label="QueueMate 홈" onPointerEnter={() => setNavigationPicked(false)} onClick={() => { setMenuOpen(false); setNavigationPicked(true); }}>
              <Logo />
            </Link>
            {gameNavigation()}
          </div>
          {navigation()}
        </div>
      </aside>
      <main className="main">
        <div className="mobile-page-actions" aria-label="빠른 메뉴">
          <button className="icon-btn" type="button" aria-label="메뉴 열기" aria-expanded={menuOpen} onClick={() => setMenuOpen(true)}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>
        </div>
        {/* 내 방(입장 표시 키)이 있는데 방 패널이 닫힌 다른 화면이면 — 새로 열었을 때의 복구 · 다른 화면에서의 안내(4단계). 게시판은 제 안내("방으로 돌아가기")가 있다. */}
        {roomId && !boardRoute ? <div className="banner room-session-banner" role="status">방에 들어가 있어요. <Link className="room-session-link" to={`/app/party/${roomId}`}>방으로 돌아가기</Link></div> : null}
        <Outlet context={{ selectedGame, setSelectedGame } satisfies AppShellOutletContext} />
        {/* 처리방침 · 약관 링크와 Riot 고지문 — 앱 화면에서는 작게, 본문 아래 끝에(2026-10-02 소유자 결정 · Riot 은 "readily visible" 을 요구한다). */}
        <SiteFooter variant="compact" />
      </main>
      {menuOpen ? <Modal title="메뉴" closeLabel="메뉴 닫기" onClose={() => setMenuOpen(false)}>{gameNavigation(true)}{navigation(true)}</Modal> : null}
    </div>
  );
}
