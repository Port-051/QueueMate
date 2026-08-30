import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './components/RequireAuth';
import { RequireOnboarding } from './components/RequireOnboarding';
import { AuthPage } from './pages/AuthPage';
import { HomePage } from './pages/HomePage';
import { MatchConditionPage } from './pages/MatchConditionPage';
import { MatchWaitingPage } from './pages/MatchWaitingPage';
import { LandingPage } from './pages/LandingPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { SimplePage } from './pages/SimplePage';

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
        <Route path="reservations" element={<SimplePage title="예약 매칭 관리" />} />
        <Route path="reservations/new" element={<SimplePage title="예약 매칭 설정" />} />
        <Route path="proposals/:proposalId" element={<SimplePage title="매칭 제안" />} />
        <Route path="party" element={<SimplePage title="파티룸" />} />
        <Route path="party/:partyId" element={<SimplePage title="파티룸" />} />
        <Route path="friends" element={<SimplePage title="친구" />} />
        <Route path="recent" element={<SimplePage title="최근 함께한 사람" />} />
        <Route path="me" element={<SimplePage title="내 정보" />} />
        <Route path="settings" element={<SimplePage title="설정" />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
