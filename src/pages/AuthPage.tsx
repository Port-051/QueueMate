import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { Logo } from '../components/Logo';
import { oauthStartPath } from '../api/client';
import type { SocialProvider } from '../api/types';
import { useAuth } from '../state/AuthContext';

/**
 * 로그인 — 소셜만이다(카카오 · 디스코드 · D-35). 이메일 · 비밀번호 · 직접 가입은 백엔드에 없다.
 * 버튼은 XHR 이 아니라 **브라우저 이동**이다(`GET /api/v1/auth/oauth/{PROVIDER}/start` → 302 → 제공자 → 백엔드 콜백 → 프런트로 302).
 * 상대 경로라 프록시(로컬) · 같은 출처(운영)를 그대로 탄다.
 */
const PROVIDERS: { provider: SocialProvider; label: string }[] = [
  { provider: 'KAKAO', label: '카카오로 시작하기' },
  { provider: 'DISCORD', label: '디스코드로 시작하기' },
];

/** 백엔드 콜백이 실패를 `/login?error=…` 로 알린다. 지금 값은 `OAUTH_FAILED` 하나다(state 불일치 · 사용자가 거절 · 제공자 오류를 가르지 않는다). */
const ERROR_MESSAGES: Record<string, string> = {
  OAUTH_FAILED: '소셜 로그인에 실패했습니다. 다시 시도해주세요',
};

export function AuthPage() {
  const { status } = useAuth();
  const [params] = useSearchParams();
  const failure = params.get('error');
  const message = failure ? ERROR_MESSAGES[failure] ?? `소셜 로그인에 실패했습니다 (${failure})` : null;

  // 이미 로그인돼 있으면 홈이다 — 콜백이 로그인 성공을 `/` 로 돌려보내므로 랜딩도 같은 판정을 한다.
  if (status === 'authenticated') return <Navigate to="/app/home" replace />;

  return (
    <div className="auth">
      <aside className="auth-aside">
        <Link to="/" aria-label="QueueMate 홈"><Logo /></Link>
        <div>
          <h2>조건이 맞는 팀원과<br /><em>지금, 바로 플레이</em></h2>
          <p style={{ marginTop: 16 }}>리그 오브 레전드 · 발로란트 · 배틀그라운드</p>
        </div>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <h1>로그인</h1>
          <p className="hint" style={{ marginTop: 8 }}>소셜 계정으로 시작합니다. 처음이면 닉네임만 정하면 됩니다.</p>
          {message ? <p className="banner warn" role="alert" style={{ marginTop: 14 }}>{message}</p> : null}
          <div className="social-login">
            {PROVIDERS.map(({ provider, label }) => (
              <button key={provider} type="button" className={`social-btn s-${provider}`} onClick={() => window.location.assign(oauthStartPath(provider))}>
                {label}
              </button>
            ))}
          </div>
        </div>
      </main>
    </div>
  );
}
