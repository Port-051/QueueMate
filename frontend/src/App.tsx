import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './components/RequireAuth';
import { RequireGameCatalog } from './components/RequireGameCatalog';
import { RequireOnboarding } from './components/RequireOnboarding';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
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

function LegacyFriendsRedirect() {
  const { search } = useLocation();
  const tab = new URLSearchParams(search).get('tab');
  const manage = tab === 'blocks' || tab === 'sent' ? tab : 'friends';
  return <Navigate to={`/app/messages?manage=${manage}`} replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/signup" element={<AuthPage mode="signup" />} />
      {/* 소셜 로그인 콜백. 서버가 브라우저를 이 경로로 돌려보낸다. */}
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route path="/onboarding" element={<RequireAuth><OnboardingPage /></RequireAuth>} />

      <Route path="/app" element={<RequireAuth><RequireOnboarding><RequireGameCatalog><AppShell /></RequireGameCatalog></RequireOnboarding></RequireAuth>}>
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<HomePage />} />
        <Route path="match" element={<MatchConditionPage />} />
        <Route path="match/waiting/:requestId" element={<MatchWaitingPage />} />
        <Route path="reservations" element={<ReservationsPage />} />
        <Route path="reservations/new" element={<ReservationNewPage />} />
        <Route path="proposals/:proposalId" element={<ProposalPage />} />
        <Route path="party" element={<PartyRoomPage />} />
        <Route path="party/:partyId" element={<PartyRoomPage />} />
        <Route path="messages" element={<DirectMessagesPage />} />
        <Route path="friends" element={<LegacyFriendsRedirect />} />
        <Route path="recent" element={<Navigate to="/app/messages" replace />} />
        <Route path="me" element={<MyInfoPage />} />
        <Route path="settings" element={<Navigate to="/app/me#settings" replace />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
