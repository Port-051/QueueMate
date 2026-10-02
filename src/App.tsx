import { MessagesPage } from './pages/MessagesPage';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './components/RequireAuth';
import { AuthPage } from './pages/AuthPage';
import { HomePage } from './pages/HomePage';
import { MatchConditionPage } from './pages/MatchConditionPage';
import { MatchWaitingPage } from './pages/MatchWaitingPage';
import { FriendsPage } from './pages/FriendsPage';
import { MyInfoPage } from './pages/MyInfoPage';
import { PartyRoomPage } from './pages/PartyRoomPage';
import { ProposalPage } from './pages/ProposalPage';
import { ReservationNewPage } from './pages/ReservationNewPage';
import { ReservationsPage } from './pages/ReservationsPage';
import { LandingPage } from './pages/LandingPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { SettingsRedirectPage } from './pages/SettingsRedirectPage';
import { SocialSignupPage } from './pages/SocialSignupPage';
import { PrivacyPage } from './pages/legal/PrivacyPage';
import { TermsPage } from './pages/legal/TermsPage';
import { useRoomSession } from './state/RoomSessionContext';

/** `/app/party` — 내 방(입장 표시 키)이 있으면 그 방으로, 없으면 홈으로. */
function MyRoomRedirect() {
  const { roomId } = useRoomSession();
  return <Navigate to={roomId ? `/app/party/${roomId}` : '/app/home'} replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      {/* 개인정보 처리방침 · 이용약관 — 로그인과 상관없이 열리고 리디렉트하지 않는다(구글 · Riot 심사자가 본다 · 2026-10-02 소유자 결정). 한국어만 — 영어판은 같은 날 없앴다. */}
      <Route path="/privacy" element={<PrivacyPage />} />
      <Route path="/terms" element={<TermsPage />} />
      {/* 소셜 콜백은 백엔드가 받아 이 네 경로로 302 한다 — `/`(로그인됨) · `/signup/social`(처음 온 사람) · `/login?error=` · `/settings?linked=|error=`(잇기).
          경로는 백엔드의 것이고 프런트가 맞춘다(2026-09-28 소유자 결정). 직접 가입 · 원본의 `/auth/callback` 코드 교환은 없다. */}
      <Route path="/login" element={<AuthPage />} />
      <Route path="/signup/social" element={<SocialSignupPage />} />
      <Route path="/settings" element={<SettingsRedirectPage />} />
      <Route path="/signup" element={<Navigate to="/login" replace />} />
      <Route path="/auth/callback" element={<Navigate to="/" replace />} />
      {/* 온보딩(게임 계정 연결)은 로그인 직후에 한 번 권할 뿐이다 — 건너뛸 수 있고 앱(`/app/**`)을 막지 않는다(2026-09-29 소유자 결정 — 3단계의 문지기 `RequireOnboarding` 을 지웠다).
          로그인 직후의 목적지는 `state/onboarding.ts` `landingPath` 가 고른다. */}
      <Route path="/onboarding" element={<RequireAuth><OnboardingPage /></RequireAuth>} />

      <Route path="/app" element={<RequireAuth><AppShell /></RequireAuth>}>
        <Route index element={<Navigate to="home" replace />} />
        {/* 게시판(`/app/home`)과 방(`/app/party/:roomId`)은 한 레이아웃이다 — 방에 들어가면 게시판이 왼쪽으로 밀리고 방이 오른쪽 패널로 열린다(2026-09-30 소유자 지시 · `pages/HomePage.tsx`).
            `roomId` 는 게시판 방이면 글 번호, 자동 매칭 방이면 UUID(= partyId). 둘 다 같은 방 화면이다(4단계). 오가도 게시판은 다시 그려지지 않는다.
            **자동 매칭의 제안 화면(`/app/proposals/:proposalId` — proposalId = partyId)도 같은 패널이다**(같은 날 소유자 지시 — "큐에서 매칭이 되었을 때도 새 화면이 아니라 게시판을 옆으로 치우고 방을 띄우는 식으로").
            확정되면 같은 패널이 그 파티의 방(`/app/party/{partyId}`)으로 바뀐다. */}
        <Route element={<HomePage />}>
          <Route path="home" />
          <Route path="party/:roomId" element={<PartyRoomPage />} />
          <Route path="proposals/:proposalId" element={<ProposalPage />} />
        </Route>
        <Route path="match" element={<MatchConditionPage />} />
        <Route path="match/waiting/:requestId" element={<MatchWaitingPage />} />
        <Route path="reservations" element={<ReservationsPage />} />
        <Route path="reservations/new" element={<ReservationNewPage />} />
        <Route path="party" element={<MyRoomRedirect />} />
        {/* 친구 관리와 서버에 보관하는 개인 메시지는 별도 화면이다(P-53). */}
        <Route path="friends" element={<FriendsPage />} />
        <Route path="messages/:userId?" element={<MessagesPage />} />
        <Route path="recent" element={<Navigate to="/app/friends?tab=recent" replace />} />
        <Route path="me" element={<MyInfoPage />} />
        <Route path="settings" element={<Navigate to="/app/me#settings" replace />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
