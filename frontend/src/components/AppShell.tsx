import { NavLink, Outlet } from 'react-router-dom';

const nav = [
  ['/app/home', '홈'],
  ['/app/match', '매칭'],
  ['/app/reservations', '예약 매칭'],
  ['/app/party/current', '파티룸'],
  ['/app/friends', '친구'],
  ['/app/recent', '최근 함께한 사람'],
  ['/app/me', '내 정보'],
  ['/app/settings', '설정'],
] as const;

export function AppShell() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">Q <span>QueueMate</span></div>
        <nav>
          {nav.map(([to, label]) => (
            <NavLink key={to} to={to} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="main"><Outlet /></main>
    </div>
  );
}
