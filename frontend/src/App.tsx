import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { LandingPage } from './pages/LandingPage';
import { SimplePage } from './pages/SimplePage';

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<SimplePage title="로그인" />} />
      <Route path="/signup" element={<SimplePage title="회원가입" />} />
      <Route path="/onboarding" element={<SimplePage title="최초 설정" />} />
      <Route path="/app" element={<AppShell />}>
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<SimplePage title="홈" />} />
        <Route path="match" element={<SimplePage title="매칭 조건 설정" />} />
        <Route path="match/waiting/:requestId" element={<SimplePage title="매칭 대기" />} />
        <Route path="reservations" element={<SimplePage title="예약 매칭 관리" />} />
        <Route path="reservations/new" element={<SimplePage title="예약 매칭 설정" />} />
        <Route path="proposals/:proposalId" element={<SimplePage title="매칭 제안" />} />
        <Route path="party/:partyId" element={<SimplePage title="파티룸" />} />
        <Route path="friends" element={<SimplePage title="친구" />} />
        <Route path="recent" element={<SimplePage title="최근 함께한 사람" />} />
        <Route path="me" element={<SimplePage title="내 정보" />} />
        <Route path="settings" element={<SimplePage title="설정" />} />
      </Route>
    </Routes>
  );
}
