import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useSocial } from '../state/SocialContext';
import { Avatar } from './ui';
import {
  IconBell, IconCalendar, IconClock, IconHome, IconLogout, IconMatch, IconParty, IconSettings, IconUser,
} from './icons';

interface NavItem { to: string; label: string; icon: ReactNode; }

const NAV: NavItem[] = [
  { to: '/app/home', label: '홈', icon: <IconHome /> },
  { to: '/app/match', label: '매칭', icon: <IconMatch /> },
  { to: '/app/reservations', label: '예약 매칭', icon: <IconCalendar /> },
  { to: '/app/party', label: '파티룸', icon: <IconParty /> },
  { to: '/app/friends', label: '친구', icon: <IconUser /> },
  { to: '/app/recent', label: '최근 함께한 사람', icon: <IconClock /> },
  { to: '/app/me', label: '내 정보', icon: <IconUser /> },
  { to: '/app/settings', label: '설정', icon: <IconSettings /> },
];

const TITLES: [RegExp, string][] = [
  [/^\/app\/home/, '홈'],
  [/^\/app\/match\/waiting/, '매칭 대기'],
  [/^\/app\/match/, '매칭 조건 설정'],
  [/^\/app\/reservations\/new/, '예약 매칭 설정'],
  [/^\/app\/reservations/, '예약 매칭 관리'],
  [/^\/app\/proposals/, '매칭 제안'],
  [/^\/app\/party/, '파티룸'],
  [/^\/app\/friends/, '친구'],
  [/^\/app\/recent/, '최근 함께한 사람'],
  [/^\/app\/me/, '내 정보'],
  [/^\/app\/settings/, '설정'],
];

export function AppShell() {
  const { user, logout } = useAuth();
  const { activePartyId, request } = useMatch();
  const { receivedRequests } = useSocial();
  const location = useLocation();
  const navigate = useNavigate();

  const title = TITLES.find(([re]) => re.test(location.pathname))?.[1] ?? 'QueueMate';
  const partyTo = activePartyId ? `/app/party/${activePartyId}` : '/app/party';

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">Q</span>
          <span className="brand-name">Queue<span>Mate</span></span>
        </div>

        <nav className="side-nav">
          {NAV.map((item) => {
            const to = item.to === '/app/party' ? partyTo : item.to;
            const disabled = item.to === '/app/party' && !activePartyId;
            return (
              <NavLink
                key={item.to}
                to={to}
                aria-disabled={disabled}
                className={({ isActive }) => [
                  'nav-link',
                  isActive && !disabled ? 'active' : '',
                  disabled ? 'disabled' : '',
                ].filter(Boolean).join(' ')}
              >
                <span className="nav-icon">{item.icon}</span>
                {item.label}
                {item.to === '/app/match' && request ? <span className="nav-badge">1</span> : null}
                {item.to === '/app/friends' && receivedRequests.length > 0
                  ? <span className="nav-badge">{receivedRequests.length}</span>
                  : null}
              </NavLink>
            );
          })}
        </nav>

        <div className="side-foot">
          <div className="side-note">
            <b>음성으로 더 빠르게</b>
            <p>파티룸 음성과 채팅은 WebRTC로 직접 연결됩니다. 서버는 대화 내용을 저장하지 않습니다.</p>
          </div>
          {user ? (
            <div className="side-user">
              <Avatar name={user.nickname} size={36} status="online" />
              <div style={{ minWidth: 0 }}>
                <b style={{ fontSize: 14 }}>{user.nickname}</b>
                <small>온라인</small>
              </div>
            </div>
          ) : null}
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="topbar-title">{title}</div>
          <div className="topbar-right">
            <button type="button" className="icon-btn" aria-label="알림" onClick={() => navigate('/app/friends')}>
              <IconBell />
              {receivedRequests.length > 0 ? <span className="dot" /> : null}
            </button>
            {user ? (
              <div className="user-chip">
                <Avatar name={user.nickname} size={30} status="online" />
                <div>
                  <b>{user.nickname}</b>
                  <small>온라인</small>
                </div>
              </div>
            ) : null}
            <button type="button" className="icon-btn" aria-label="로그아웃" onClick={() => { void logout().then(() => navigate('/')); }}>
              <IconLogout />
            </button>
          </div>
        </header>
        <Outlet />
      </div>
    </div>
  );
}
