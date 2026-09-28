import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../state/AuthContext';

/** 게임 계정이 하나도 없으면 조건을 채울 수 없으므로 온보딩으로 보낸다. */
export function RequireOnboarding({ children }: { children: ReactNode }) {
  const { gameAccounts } = useAuth();
  if (gameAccounts.length === 0) return <Navigate to="/onboarding" replace />;
  return <>{children}</>;
}
