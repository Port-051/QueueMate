import { Navigate, useLocation } from 'react-router-dom';
import type { MatchComposerOptions } from '../components/MatchComposer';

/** Preserve existing links while all matching starts in the home dialog. */
export function MatchConditionPage() {
  const location = useLocation();
  const options = (location.state ?? {}) as MatchComposerOptions;
  return <Navigate to="/app/home" replace state={{ matchComposer: { ...options, mode: 'REALTIME' } }} />;
}
