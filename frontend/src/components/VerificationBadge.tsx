import './verification-badge.css';

/** A server-confirmed game-account verification, never inferred from rank or API stats. */
export function VerificationBadge({ verified }: { verified?: boolean }) {
  if (!verified) return null;
  return <span className="verification-badge" role="img" aria-label="게임 계정 인증됨" title="게임 계정 인증됨">
    <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m12 1.5 2.6 1.9 3.2-.1 1 3 2.6 1.9-1 3.1 1 3.1-2.6 1.9-1 3-3.2-.1L12 22.5l-2.6-1.9-3.2.1-1-3-2.6-1.9 1-3.1-1-3.1 2.6-1.9 1-3 3.2.1L12 1.5Z" fill="currentColor" /><path d="m8 12.2 2.8 2.8L16.3 9.4" fill="none" stroke="#10121c" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" /></svg>
  </span>;
}
