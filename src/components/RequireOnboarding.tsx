import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../state/AuthContext';

/**
 * 게임 계정이 하나도 없으면 게임 계정 연결 화면으로 보낸다 — 판정은 `GET /users/me` 의 `gameAccounts` 다(2026-09-28 소유자 결정 —
 * 온보딩은 게임 계정 화면으로의 전환이다. 백엔드는 강제하지 않는다). 그 화면의 API 전환(`PUT /users/me/game-accounts/{game}`)은 3단계다.
 */
export function RequireOnboarding({ children }: { children: ReactNode }) {
  const { gameAccounts } = useAuth();
  if (gameAccounts.length === 0) return <Navigate to="/onboarding" replace />;
  return <>{children}</>;
}
