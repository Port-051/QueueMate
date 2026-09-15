import { Navigate, useParams } from 'react-router-dom';

export function MatchWaitingPage() {
  const { requestId } = useParams<{ requestId: string }>();
  return <Navigate to="/app/home" replace state={{ requestId }} />;
}
