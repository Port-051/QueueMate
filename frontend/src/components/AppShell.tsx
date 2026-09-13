import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import type { ComponentType } from 'react';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useNotifications } from '../state/notifications';
import { useDirectMessages } from '../state/directMessages';
import { Logo } from './Logo';
import { Avatar, Modal } from './ui';
import { IconHome } from './icons';
import { IconDirectMessage, IconNotification, NotificationPanel } from './NotificationPanel';

interface NavItem { to: string; label: string; icon: ComponentType<{ size?: number; filled?: boolean }>; }

const NAV: NavItem[] = [
  { to: '/app/home', label: '홈', icon: IconHome },
  { to: '/app/messages', label: '메시지', icon: IconDirectMessage },
];

export function AppShell() {
  const { user } = useAuth();
  const { request, proposal } = useMatch();
  const notifications = useNotifications();
  const messages = useDirectMessages(user?.id);
  const location = useLocation();
  const [navigationPicked, setNavigationPicked] = useState(false);
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
          onClick={() => { setMenuOpen(false); setNavigationPicked(true); closeNotifications(); }}
          className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          {({ isActive }) => <>
            <span className="nav-icon"><MenuIcon size={24} filled={isActive} />
              {item.to === '/app/messages' && messages.unreadCount > 0 ? <span className={`nav-badge${messages.unreadCount > 99 ? ' nav-badge-long' : ''}`} aria-label={`안 읽은 메시지 ${messages.unreadCount}개`}>{messages.unreadCount > 99 ? '99+' : messages.unreadCount}</span> : null}
            </span><span className="nav-label">{item.label}</span>
            {item.to === '/app/home' && request ? <span className="nav-dot" role="img" aria-label="매칭 중" /> : null}
          </>}
        </NavLink>;
      })}
      <button className={`nav-link nav-notifications${notificationAnchor ? ' active' : ''}`} type="button" aria-label="알림" title="알림" aria-haspopup="dialog" aria-expanded={Boolean(notificationAnchor)} aria-controls="notification-panel" onClick={event => {
        if (notificationAnchor) closeNotifications();
        else { setNotificationAnchor(mobile ? mobileMenuButton.current : event.currentTarget); setMenuOpen(false); }
      }}><span className="nav-icon"><IconNotification size={24} filled={Boolean(notificationAnchor)} />{notifications.unreadCount > 0 ? <span className={`nav-badge${notifications.unreadCount > 99 ? ' nav-badge-long' : ''}`} aria-label={`안 읽은 알림 ${notifications.unreadCount}개`}>{notifications.unreadCount > 99 ? '99+' : notifications.unreadCount}</span> : null}</span><span className="nav-label">알림</span></button>
      {user ? <NavLink to="/app/me" className="nav-link nav-profile" aria-label="프로필" title="프로필" onClick={() => { setMenuOpen(false); setNavigationPicked(true); closeNotifications(); }}>
        <span className="nav-icon"><Avatar name={user.nickname} avatarUrl={user.avatarUrl} size={24} /></span>
        <span className="nav-label">프로필</span>
      </NavLink> : null}
    </nav>
  );

  return (
    <div className="app-shell">
      <aside onPointerLeave={() => setNavigationPicked(false)} className={`sidebar${navigationPicked ? ' navigation-picked' : ''}${notificationAnchor ? ' has-notifications-open' : ''}`}>
        <div className="sidebar-navigation" aria-hidden={Boolean(notificationAnchor)} {...{ inert: notificationAnchor ? '' : undefined }}>
          <Link to="/app/home" className="sidebar-brand-link" aria-label="QueueMate 홈" onPointerEnter={() => setNavigationPicked(false)} onClick={() => { setMenuOpen(false); setNavigationPicked(true); closeNotifications(); }}>
            <Logo />
          </Link>
          {navigation()}
        </div>
        <NotificationPanel items={notifications.items} unreadCount={notifications.unreadCount} anchor={notificationAnchor} onClose={closeNotifications} onRead={notifications.read} onReadAll={notifications.readAll} />
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
    </div>
  );
}
