import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './components/RequireAuth';
import { RequireOnboarding } from './components/RequireOnboarding';
import { AuthPage } from './pages/AuthPage';
import { HomePage } from './pages/HomePage';
import { MatchConditionPage } from './pages/MatchConditionPage';
import { MatchWaitingPage } from './pages/MatchWaitingPage';
import { FriendsPage } from './pages/FriendsPage';
import { MyInfoPage } from './pages/MyInfoPage';
import { PartyRoomPage } from './pages/PartyRoomPage';
import { RecentPlayersPage } from './pages/RecentPlayersPage';
import { SettingsPage } from './pages/SettingsPage';
import { ProposalPage } from './pages/ProposalPage';
import { ReservationNewPage } from './pages/ReservationNewPage';
import { ReservationsPage } from './pages/ReservationsPage';
import { LandingPage } from './pages/LandingPage';
import { OnboardingPage } from './pages/OnboardingPage';

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/signup" element={<AuthPage mode="signup" />} />
      <Route path="/onboarding" element={<RequireAuth><OnboardingPage /></RequireAuth>} />

      <Route path="/app" element={<RequireAuth><RequireOnboarding><AppShell /></RequireOnboarding></RequireAuth>}>
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<HomePage />} />
        <Route path="match" element={<MatchConditionPage />} />
        <Route path="match/waiting/:requestId" element={<MatchWaitingPage />} />
        <Route path="reservations" element={<ReservationsPage />} />
        <Route path="reservations/new" element={<ReservationNewPage />} />
        <Route path="proposals/:proposalId" element={<ProposalPage />} />
        <Route path="party" element={<PartyRoomPage />} />
        <Route path="party/:partyId" element={<PartyRoomPage />} />
        <Route path="friends" element={<FriendsPage />} />
        <Route path="recent" element={<RecentPlayersPage />} />
        <Route path="me" element={<MyInfoPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
