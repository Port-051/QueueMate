import { useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { DevLoginPanel } from '../components/DevLoginPanel'; // TEMP-DEV-LOGIN
import { Logo } from '../components/Logo';
import { SocialProviderIcon } from '../components/SocialProviderIcon';
import { oauthStartPath } from '../api/client';
import type { SocialProvider } from '../api/types';
import { useAuth } from '../state/AuthContext';

/**
 * 로그인 — 소셜만이다(카카오 · Discord · Google — D-35 · Google 은 2026-09-29 소유자 결정). 이메일 · 비밀번호 · 직접 가입은 백엔드에 없다.
 * 버튼은 XHR 이 아니라 **브라우저 이동**이다(`GET /api/v1/auth/oauth/{PROVIDER}/start` → 302 → 제공자 → 백엔드 콜백 → 프런트로 302).
 * 상대 경로라 프록시(로컬) · 같은 출처(운영)를 그대로 탄다.
 *
 * 화면은 원본 프런트의 로그인 카드(어두운 카드 · 제목 · 가운데 글자 구분선 · 점선 박스)에 우리 버튼 셋만 넣은 것이다(2026-09-29 소유자 지시).
 * 원본의 이메일 · 비밀번호 칸 · 로그인 버튼 · 회원가입 링크 · 네이버는 없다. 마지막으로 누른 제공자에 "최근 사용" 배지가 붙는다.
 */
const PROVIDERS: { provider: SocialProvider; label: string }[] = [
  { provider: 'KAKAO', label: '카카오로 시작하기' },
  { provider: 'DISCORD', label: 'Discord로 시작하기' },
  { provider: 'GOOGLE', label: 'Google로 시작하기' },
];

/**
 * 마지막으로 누른 제공자. 로그인이 끝났는지는 모른다 — "누른 것" 을 적는다(콜백은 백엔드가 받아 이 화면으로 돌아오지 않을 수 있다).
 * 이 브라우저의 편의일 뿐이라 서버에 보내지 않고, 저장이 막힌 브라우저(사생활 보호 창 등)에서는 배지 없이 그린다.
 */
const LAST_PROVIDER_KEY = 'qm.lastProvider';

function readLastProvider(): SocialProvider | null {
  try {
    const value = localStorage.getItem(LAST_PROVIDER_KEY);
    return PROVIDERS.find((p) => p.provider === value)?.provider ?? null;
  } catch {
    return null;
  }
}

function rememberProvider(provider: SocialProvider): void {
  try { localStorage.setItem(LAST_PROVIDER_KEY, provider); } catch { /* 저장 공간이 막혀도 로그인은 간다 — 배지만 잃는다 */ }
}

/** 백엔드 콜백이 실패를 `/login?error=…` 로 알린다. 지금 값은 `OAUTH_FAILED` 하나다(state 불일치 · 사용자가 거절 · 제공자 오류를 가르지 않는다). */
const ERROR_MESSAGES: Record<string, string> = {
  OAUTH_FAILED: '소셜 로그인에 실패했습니다. 다시 시도해주세요',
};

export function AuthPage() {
  const { status } = useAuth();
  const [params] = useSearchParams();
  const [lastProvider] = useState(readLastProvider);
  const failure = params.get('error');
  const message = failure ? ERROR_MESSAGES[failure] ?? `소셜 로그인에 실패했습니다 (${failure})` : null;

  // 이미 로그인돼 있으면 홈이다 — 콜백이 로그인 성공을 `/` 로 돌려보내므로 랜딩도 같은 판정을 한다.
  if (status === 'authenticated') return <Navigate to="/app/home" replace />;

  const start = (provider: SocialProvider) => {
    rememberProvider(provider);
    window.location.assign(oauthStartPath(provider));
  };

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
          {message ? <p className="login-error" role="alert">{message}</p> : null}
          <div className="social-login">
            <div className="social-divider"><span>소셜 계정으로 계속하기</span></div>
            {PROVIDERS.map(({ provider, label }) => {
              const recent = provider === lastProvider;
              return (
                <button key={provider} type="button" className={`social-btn s-${provider}${recent ? ' is-recent' : ''}`}
                  aria-label={recent ? `${label} (최근 사용)` : label} onClick={() => start(provider)}>
                  <SocialProviderIcon provider={provider} size={20} />
                  <span>{label}</span>
                  {recent ? <span className="social-recent" aria-hidden="true">최근 사용</span> : null}
                </button>
              );
            })}
          </div>
          {/* TEMP-DEV-LOGIN — 개발 서버에서만 그린다(원본의 점선 "데모 계정" 박스 자리). 운영 빌드에서는 `import.meta.env.DEV` 가 `false` 로 바뀌어 이 줄과 DevLoginPanel 모듈이 빠진다. */}
          {import.meta.env.DEV ? <DevLoginPanel /> : null}
        </div>
      </main>
    </div>
  );
}
