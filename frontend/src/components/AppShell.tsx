import { NavLink, Outlet, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useSocial } from '../state/SocialContext';
import { useConnectionStatus } from '../state/useConnectionStatus';
import { Logo } from './Logo';
import { Avatar, Button, Modal } from './ui';
import {
  IconClock, IconHome, IconParty, IconSettings, IconUser,
} from './icons';

interface NavItem { to: string; label: string; icon: ReactNode; }

const NAV: NavItem[] = [
  { to: '/app/home', label: '홈', icon: <IconHome /> },
  { to: '/app/party', label: '파티룸', icon: <IconParty /> },
  { to: '/app/friends', label: '친구', icon: <IconUser /> },
  { to: '/app/recent', label: '최근 함께한 사람', icon: <IconClock /> },
];

export function AppShell() {
  const { user } = useAuth();
  const { activePartyId, request, stream } = useMatch();
  const connection = useConnectionStatus(stream);
  const connectionLabel = connection === 'connected' ? '온라인' : connection === 'reconnecting' ? '재연결 중' : '서버 연결 중';
  const { receivedRequests } = useSocial();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  useEffect(() => { window.scrollTo({ top: 0, left: 0, behavior: 'auto' }); }, [location.pathname]);

  const partyTo = activePartyId ? `/app/party/${activePartyId}` : '/app/party';

  const navigation = (mobile = false) => (
    <nav className={mobile ? 'mobile-nav' : 'side-nav'} aria-label="주 메뉴">
      {NAV.map((item) => {
        const disabled = item.to === '/app/party' && !activePartyId;
        return <NavLink key={item.to} to={item.to === '/app/party' ? partyTo : item.to}
          aria-disabled={disabled} tabIndex={disabled ? -1 : undefined}
          title={disabled ? '매칭이 성사되면 파티룸이 열립니다' : undefined}
          onClick={(event) => { if (disabled) event.preventDefault(); else setMenuOpen(false); }}
          className={({ isActive }) => `nav-link${isActive && !disabled ? ' active' : ''}${disabled ? ' disabled' : ''}`}>
          <span className="nav-icon">{item.icon}</span><span>{item.label}</span>
          {item.to === '/app/home' && request ? <span className="nav-badge">1</span> : null}
          {item.to === '/app/friends' && receivedRequests.length > 0 ? <span className="nav-badge">{receivedRequests.length}</span> : null}
        </NavLink>;
      })}
    </nav>
  );

  const account = (
    <nav className="sidebar-account" aria-label="내 계정">
      {user ? <NavLink to="/app/me" className={({ isActive }) => `account-profile${isActive ? ' active' : ''}`} aria-label="내 정보" title="내 정보" onClick={() => setMenuOpen(false)}>
        <Avatar name={user.nickname} avatarUrl={user.avatarUrl} size={36} status={connection === 'connected' ? 'online' : 'away'} />
        <span><b>{user.nickname}</b><small className={connection === 'connected' ? '' : 'connecting'}>{connectionLabel}</small></span>
      </NavLink> : null}
      <NavLink to="/app/settings" className={({ isActive }) => `icon-btn account-settings${isActive ? ' active' : ''}`} aria-label="설정" title="설정" onClick={() => setMenuOpen(false)}><IconSettings /></NavLink>
    </nav>
  );

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Logo />
        {navigation()}
        {account}
      </aside>
      <main className="main">
        <div className="mobile-page-actions" aria-label="빠른 메뉴">
          <button className="icon-btn" type="button" aria-label="메뉴 열기" aria-expanded={menuOpen} onClick={() => setMenuOpen(true)}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>
        </div>
        <Outlet />
      </main>
      {menuOpen ? <Modal title="메뉴" onClose={() => setMenuOpen(false)} foot={<Button onClick={() => setMenuOpen(false)}>메뉴 닫기</Button>}>{navigation(true)}{account}</Modal> : null}
    </div>
  );
}
