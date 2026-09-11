import { Navigate, useLocation } from 'react-router-dom';
import type { MatchComposerOptions } from '../components/MatchComposer';

export function ReservationNewPage() {
  const location = useLocation();
  const options = (location.state ?? {}) as MatchComposerOptions;
  return <Navigate to="/app/home" replace state={{ matchComposer: { ...options, mode: 'RESERVATION' } }} />;
}
