import { NavLink, Outlet, useLocation } from 'react-router-dom';
import type { ComponentType } from 'react';
import { useEffect, useState } from 'react';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useSocial } from '../state/SocialContext';
import { useConnectionStatus } from '../state/useConnectionStatus';
import { Logo } from './Logo';
import { Avatar, Modal } from './ui';
import {
  IconClock, IconHome, IconParty, IconUser,
} from './icons';

interface NavItem { to: string; label: string; icon: ComponentType<{ size?: number; filled?: boolean }>; }

const NAV: NavItem[] = [
  { to: '/app/home', label: '홈', icon: IconHome },
  { to: '/app/party', label: '파티룸', icon: IconParty },
  { to: '/app/friends', label: '친구', icon: IconUser },
  { to: '/app/recent', label: '최근 함께한 사람', icon: IconClock },
];

export function AppShell() {
  const { user } = useAuth();
  const { activePartyId, request, stream } = useMatch();
  const connection = useConnectionStatus(stream);
  const connectionLabel = connection === 'connected' ? '온라인' : connection === 'reconnecting' ? '재연결 중' : '서버 연결 중';
  const { receivedRequests } = useSocial();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      const target = location.hash ? document.getElementById(location.hash.slice(1)) : null;
      if (target) target.scrollIntoView({ block: 'start' });
      else window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [location.pathname, location.hash]);

  const partyTo = activePartyId ? `/app/party/${activePartyId}` : '/app/party';

  const navigation = (mobile = false) => (
    <nav className={mobile ? 'mobile-nav' : 'side-nav'} aria-label="주 메뉴">
      {NAV.map((item) => {
        const disabled = item.to === '/app/party' && !activePartyId;
        const MenuIcon = item.icon;
        return <NavLink key={item.to} to={item.to === '/app/party' ? partyTo : item.to}
          aria-label={item.label}
          aria-disabled={disabled} tabIndex={disabled ? -1 : undefined}
          title={disabled ? '매칭이 성사되면 파티룸이 열립니다' : undefined}
          onClick={(event) => { if (disabled) event.preventDefault(); else setMenuOpen(false); }}
          className={({ isActive }) => `nav-link${isActive && !disabled ? ' active' : ''}${disabled ? ' disabled' : ''}`}>
          {({ isActive }) => <>
            <span className="nav-icon"><MenuIcon size={mobile ? 24 : 28} filled={isActive && !disabled} /></span><span className="nav-label">{item.label}</span>
            {item.to === '/app/home' && request ? <span className="nav-dot" role="img" aria-label="매칭 중" /> : null}
            {item.to === '/app/friends' && receivedRequests.length > 0 ? <span className="nav-badge">{receivedRequests.length}</span> : null}
          </>}
        </NavLink>;
      })}
      {user ? <NavLink to="/app/me" className="nav-link nav-profile" aria-label={`${user.nickname} 프로필`} onClick={() => setMenuOpen(false)}>
        <span className="nav-icon"><Avatar name={user.nickname} avatarUrl={user.avatarUrl} size={mobile ? 28 : 32} status={connection === 'connected' ? 'online' : 'away'} /></span>
        <span className="nav-label"><span className="nav-nickname" title={user.nickname}>{user.nickname}</span><small className={connection === 'connected' ? '' : 'connecting'}>{connectionLabel}</small></span>
      </NavLink> : null}
    </nav>
  );

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Logo />
        {navigation()}
      </aside>
      <main className="main">
        <div className="mobile-page-actions" aria-label="빠른 메뉴">
          <button className="icon-btn" type="button" aria-label="메뉴 열기" aria-expanded={menuOpen} onClick={() => setMenuOpen(true)}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>
        </div>
        <Outlet />
      </main>
      {menuOpen ? <Modal title="메뉴" closeLabel="메뉴 닫기" onClose={() => setMenuOpen(false)}>{navigation(true)}</Modal> : null}
    </div>
  );
}
