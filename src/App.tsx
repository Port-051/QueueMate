import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './components/RequireAuth';
import { RequireOnboarding } from './components/RequireOnboarding';
import { AuthPage } from './pages/AuthPage';
import { HomePage } from './pages/HomePage';
import { MatchConditionPage } from './pages/MatchConditionPage';
import { MatchWaitingPage } from './pages/MatchWaitingPage';
import { DirectMessagesPage } from './pages/DirectMessagesPage';
import { MyInfoPage } from './pages/MyInfoPage';
import { PartyRoomPage } from './pages/PartyRoomPage';
import { ProposalPage } from './pages/ProposalPage';
import { ReservationNewPage } from './pages/ReservationNewPage';
import { ReservationsPage } from './pages/ReservationsPage';
import { LandingPage } from './pages/LandingPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { SettingsRedirectPage } from './pages/SettingsRedirectPage';
import { SocialSignupPage } from './pages/SocialSignupPage';
import { useRoomSession } from './state/RoomSessionContext';

/** `/app/party` — 내 방(입장 표시 키)이 있으면 그 방으로, 없으면 홈으로. */
function MyRoomRedirect() {
  const { roomId } = useRoomSession();
  return <Navigate to={roomId ? `/app/party/${roomId}` : '/app/home'} replace />;
}

function LegacyFriendsRedirect() {
  const { search } = useLocation();
  const tab = new URLSearchParams(search).get('tab');
  const manage = tab === 'blocks' || tab === 'sent' || tab === 'received' ? tab : 'friends';
  return <Navigate to={`/app/messages?manage=${manage}`} replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      {/* 소셜 콜백은 백엔드가 받아 이 네 경로로 302 한다 — `/`(로그인됨) · `/signup/social`(처음 온 사람) · `/login?error=` · `/settings?linked=|error=`(잇기).
          경로는 백엔드의 것이고 프런트가 맞춘다(2026-09-28 소유자 결정). 직접 가입 · 원본의 `/auth/callback` 코드 교환은 없다. */}
      <Route path="/login" element={<AuthPage />} />
      <Route path="/signup/social" element={<SocialSignupPage />} />
      <Route path="/settings" element={<SettingsRedirectPage />} />
      <Route path="/signup" element={<Navigate to="/login" replace />} />
      <Route path="/auth/callback" element={<Navigate to="/" replace />} />
      <Route path="/onboarding" element={<RequireAuth><OnboardingPage /></RequireAuth>} />

      <Route path="/app" element={<RequireAuth><RequireOnboarding><AppShell /></RequireOnboarding></RequireAuth>}>
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<HomePage />} />
        <Route path="match" element={<MatchConditionPage />} />
        <Route path="match/waiting/:requestId" element={<MatchWaitingPage />} />
        <Route path="reservations" element={<ReservationsPage />} />
        <Route path="reservations/new" element={<ReservationNewPage />} />
        <Route path="proposals/:proposalId" element={<ProposalPage />} />
        {/* 방 화면 — `roomId` 는 게시판 방이면 글 번호, 자동 매칭 방이면 UUID(= partyId). 둘 다 같은 화면이다(4단계). */}
        <Route path="party" element={<MyRoomRedirect />} />
        <Route path="party/:roomId" element={<PartyRoomPage />} />
        <Route path="messages" element={<DirectMessagesPage />} />
        <Route path="friends" element={<LegacyFriendsRedirect />} />
        {/* 친구 · 차단 · 최근 함께한 사람은 메시지 화면의 친구 관리 패널(`?manage=`)이다(5단계). `pages/FriendsPage` · `RecentPlayersPage` 는 라우트 밖이다. */}
        <Route path="recent" element={<Navigate to="/app/messages?manage=recent" replace />} />
        <Route path="me" element={<MyInfoPage />} />
        <Route path="settings" element={<Navigate to="/app/me#settings" replace />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
