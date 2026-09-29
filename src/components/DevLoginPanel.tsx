// TEMP-DEV-LOGIN — 이 파일 전체가 개발용이다. 걷어낼 때는 `grep -rn TEMP-DEV-LOGIN src` 로 찾아 통째로 지운다.
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import { useAuth } from '../state/AuthContext';

/**
 * TEMP-DEV-LOGIN — 개발용 로그인(2026-09-29 소유자 결정). 카카오 · Discord · Google 앱 키 없이 로컬에서 로그인 상태를 만든다.
 * 로그인 화면(`AuthPage`)이 `import.meta.env.DEV` 일 때만 그린다 — 운영 빌드에서는 그 조건이 `false` 로 접혀 이 모듈째 번들에서 빠진다.
 * 백엔드(platform)를 `DEV_LOGIN_ENABLED=true` 로 띄워야 한다 — 꺼져 있으면 `POST /auth/dev-login` 이 404 다.
 * 성공 뒤 흐름은 소셜 가입(`SocialSignupPage`)과 같다 — 쿠키가 바뀌었으니 `refreshSession()`(= `GET /users/me`)으로 세션을 맞추고 `/app/home`.
 * 게임 계정이 없으면 `RequireOnboarding` 이 `/onboarding` 으로 보낸다.
 */
export function DevLoginPanel() {
  const { refreshSession } = useAuth();
  const navigate = useNavigate();
  const [nickname, setNickname] = useState('dev-tester');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = nickname.trim();
    if (!trimmed) { setError('닉네임을 입력해주세요'); return; }
    setBusy(true);
    setError(null);
    try {
      await api.devLogin(trimmed);
      await refreshSession();
      navigate('/app/home', { replace: true });
    } catch (err) {
      if (isApiError(err) && err.status === 404) setError('백엔드의 DEV_LOGIN_ENABLED 가 꺼져 있습니다');
      else if (isApiError(err) && err.status === 400) setError(err.details[0] ?? err.message);
      // 백엔드가 꺼져 있으면 vite 프록시가 빈 본문의 5xx 를 준다(코드 `HTTP_5xx`).
      else if (isApiError(err) && err.code.startsWith('HTTP_5')) setError(`백엔드(platform 8082)에 닿지 못했습니다 (HTTP ${err.status})`);
      else setError(isApiError(err) ? err.message : '개발용 로그인에 실패했습니다');
      setBusy(false);
    }
  };

  return (
    <form className="dev-login" onSubmit={submit} aria-label="개발용 로그인">
      <p className="dev-login-note">개발 서버에서만 보입니다 · 백엔드를 <code>DEV_LOGIN_ENABLED=true</code> 로 띄우세요</p>
      <div className="dev-login-row">
        <input className="dev-login-input" aria-label="개발용 로그인 닉네임" value={nickname} maxLength={16} disabled={busy}
          autoComplete="off" spellCheck={false} onChange={(e) => setNickname(e.target.value)} />
        <button type="submit" className="dev-login-btn" disabled={busy}>{busy ? '들어가는 중…' : '개발용: 로그인 없이 들어가기'}</button>
      </div>
      {error ? <p className="dev-login-error" role="alert">{error}</p> : null}
    </form>
  );
}
