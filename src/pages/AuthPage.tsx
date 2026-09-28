import { Link } from 'react-router-dom';
import { Logo } from '../components/Logo';
import { oauthStartPath } from '../api/client';
import type { SocialProvider } from '../api/types';

/**
 * 로그인 — 소셜만이다(카카오 · 디스코드 · D-35). 이메일 · 비밀번호 · 직접 가입은 백엔드에 없다.
 * 버튼은 XHR 이 아니라 **브라우저 이동**이다(`GET /api/v1/auth/oauth/{PROVIDER}/start` → 302 → 제공자 → 백엔드 콜백 → 프런트로 302).
 * 상대 경로라 프록시(로컬) · 같은 출처(운영)를 그대로 탄다.
 */
const PROVIDERS: { provider: SocialProvider; label: string }[] = [
  { provider: 'KAKAO', label: '카카오로 시작하기' },
  { provider: 'DISCORD', label: '디스코드로 시작하기' },
];

export function AuthPage() {
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
