import { NavLink, Outlet, useLocation } from 'react-router-dom';
import type { ComponentType } from 'react';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useConnectionStatus } from '../state/useConnectionStatus';
import { useNotifications } from '../state/notifications';
import { useDirectMessages } from '../state/directMessages';
import { Logo } from './Logo';
import { Avatar, Modal } from './ui';
import { IconHome } from './icons';
import { IconDirectMessage, IconNotification, NotificationPopover } from './NotificationPopover';

interface NavItem { to: string; label: string; icon: ComponentType<{ size?: number; filled?: boolean }>; }

const NAV: NavItem[] = [
  { to: '/app/home', label: '홈', icon: IconHome },
  { to: '/app/messages', label: '메시지', icon: IconDirectMessage },
];

export function AppShell() {
  const { user } = useAuth();
  const { request, proposal, stream } = useMatch();
  const connection = useConnectionStatus(stream);
  const connectionLabel = connection === 'connected' ? '온라인' : connection === 'reconnecting' ? '재연결 중' : '서버 연결 중';
  const notifications = useNotifications();
  const messages = useDirectMessages(user?.id);
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [notificationAnchor, setNotificationAnchor] = useState<HTMLElement | null>(null);
  const mobileMenuButton = useRef<HTMLButtonElement>(null);
  const closeNotifications = () => setNotificationAnchor(null);
  useEffect(() => {
    setNotificationAnchor(null);
    setMenuOpen(false);
  }, [location.pathname, proposal?.id]);
  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      const target = location.hash ? document.getElementById(location.hash.slice(1)) : null;
      if (target) target.scrollIntoView({ block: 'start' });
      else window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [location.pathname, location.hash]);

  const navigation = (mobile = false) => (
    <nav className={mobile ? 'mobile-nav' : 'side-nav'} aria-label="주 메뉴">
      {NAV.map((item) => {
        const MenuIcon = item.icon;
        return <NavLink key={item.to} to={item.to}
          aria-label={item.label} title={item.label}
          onClick={() => { setMenuOpen(false); closeNotifications(); }}
          className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          {({ isActive }) => <>
            <span className="nav-icon"><MenuIcon size={mobile ? 24 : 28} filled={isActive} />
              {item.to === '/app/messages' && messages.unreadCount > 0 ? <span className={`nav-badge${messages.unreadCount > 99 ? ' nav-badge-long' : ''}`} aria-label={`안 읽은 메시지 ${messages.unreadCount}개`}>{messages.unreadCount > 99 ? '99+' : messages.unreadCount}</span> : null}
            </span><span className="nav-label">{item.label}</span>
            {item.to === '/app/home' && request ? <span className="nav-dot" role="img" aria-label="매칭 중" /> : null}
          </>}
        </NavLink>;
      })}
      <button className={`nav-link nav-notifications${notificationAnchor ? ' active' : ''}`} type="button" aria-label="알림" title="알림" aria-haspopup="dialog" aria-expanded={Boolean(notificationAnchor)} aria-controls={notificationAnchor ? 'notification-popover' : undefined} onClick={event => {
        if (notificationAnchor) closeNotifications();
        else { setNotificationAnchor(mobile ? mobileMenuButton.current : event.currentTarget); setMenuOpen(false); }
      }}><span className="nav-icon"><IconNotification size={mobile ? 24 : 28} filled={Boolean(notificationAnchor)} />{notifications.unreadCount > 0 ? <span className={`nav-badge${notifications.unreadCount > 99 ? ' nav-badge-long' : ''}`} aria-label={`안 읽은 알림 ${notifications.unreadCount}개`}>{notifications.unreadCount > 99 ? '99+' : notifications.unreadCount}</span> : null}</span><span className="nav-label">알림</span></button>
      {user ? <NavLink to="/app/me" className="nav-link nav-profile" aria-label={`${user.nickname} 프로필`} onClick={() => { setMenuOpen(false); closeNotifications(); }}>
        <span className="nav-icon"><Avatar name={user.nickname} avatarUrl={user.avatarUrl} size={mobile ? 28 : 32} status={connection === 'connected' ? 'online' : 'away'} /></span>
        <span className="nav-label"><span className="nav-nickname" title={user.nickname}>{user.nickname}</span><small className={connection === 'connected' ? '' : 'connecting'}>{connectionLabel}</small></span>
      </NavLink> : null}
    </nav>
  );

  return (
    <div className="app-shell">
      <aside className={`sidebar${notificationAnchor ? ' has-notifications-open' : ''}`}>
        <Logo />
        {navigation()}
      </aside>
      <main className="main">
        <div className="mobile-page-actions" aria-label="빠른 메뉴">
          <button ref={mobileMenuButton} className="icon-btn" type="button" aria-label="메뉴 열기" aria-expanded={menuOpen} onClick={() => { closeNotifications(); setMenuOpen(true); }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>
        </div>
        <Outlet />
      </main>
      {menuOpen ? <Modal title="메뉴" closeLabel="메뉴 닫기" onClose={() => setMenuOpen(false)}>{navigation(true)}</Modal> : null}
      {notificationAnchor ? <NotificationPopover items={notifications.items} unreadCount={notifications.unreadCount} anchor={notificationAnchor} onClose={closeNotifications} onRead={notifications.read} onReadAll={notifications.readAll} /> : null}
    </div>
  );
}
