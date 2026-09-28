import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../state/AuthContext';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <div className="page"><div className="skeleton" style={{ height: 240 }} /></div>;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}
