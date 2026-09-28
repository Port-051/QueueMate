import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { hasErrorCode, isApiError } from '../api/error';
import type { SocialSignupPending } from '../api/types';
import { Logo } from '../components/Logo';
import { Button, Field } from '../components/ui';
import { useAuth } from '../state/AuthContext';
import { PROVIDER_LABEL } from '../state/settingsNotice';

/**
 * `/signup/social` — 소셜로 **처음** 온 사람이 닉네임 하나를 정하는 화면(platform-api.md "소셜 로그인" · D-35).
 * 백엔드 콜백이 쿠키 `qm_social_signup`(10분)을 주고 여기로 302 한다. `GET /auth/social/pending` 이 401 이면 그 쿠키가 없는 것이라 `/login` 으로.
 * `POST /auth/social/signup {nickname}` 은 201 과 함께 로그인 쿠키를 주므로 `refreshSession()`(= `GET /users/me`)으로 세션을 맞춘 뒤 홈으로 간다.
 */
export function SocialSignupPage() {
  const { status, refreshSession } = useAuth();
  const navigate = useNavigate();
  const [pending, setPending] = useState<SocialSignupPending | null>(null);
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.getSocialSignupPending()
      .then((p) => { if (cancelled) return; setPending(p); setNickname(p.suggestedNickname ?? ''); })
      .catch((err) => {
        if (cancelled) return;
        // 가입 대기 토큰이 없거나 만료됐다(10분) — 로그인부터 다시.
        if (isApiError(err) && err.status === 401) navigate('/login', { replace: true });
        else setLoadFailed(true);
      });
    return () => { cancelled = true; };
  }, [navigate]);

  // 이미 로그인된 사람은 가입 화면이 아니다(콜백은 로그인한 채 오면 "잇기" 라 여기로 보내지 않는다).
  if (status === 'authenticated') return <Navigate to="/app/home" replace />;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = nickname.trim();
    if (trimmed.length < 2 || trimmed.length > 16) { setError('닉네임은 2~16자로 입력해주세요'); return; }
    setBusy(true);
    setError(null);
    try {
      await api.socialSignup({ nickname: trimmed });
      await refreshSession();
      navigate('/app/home', { replace: true });
    } catch (err) {
      if (hasErrorCode(err, 'NICKNAME_TAKEN')) setError('이미 사용 중인 닉네임입니다');
      else if (hasErrorCode(err, 'VALIDATION_FAILED')) setError(isApiError(err) ? err.details[0] ?? err.message : '입력을 확인해주세요');
      else if (hasErrorCode(err, 'NO_PENDING_SOCIAL_SIGNUP')) { navigate('/login', { replace: true }); return; }
      else if (hasErrorCode(err, 'SOCIAL_ALREADY_LINKED')) setError('이미 가입된 소셜 계정입니다. 로그인으로 돌아가 다시 시도해주세요');
      else setError(isApiError(err) ? err.message : '가입을 완료하지 못했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth">
      <aside className="auth-aside">
        <Link to="/" aria-label="QueueMate 홈"><Logo /></Link>
        <div>
          <h2>거의 다 됐어요<br /><em>닉네임만 정하면</em> 시작합니다</h2>
          <p style={{ marginTop: 16 }}>닉네임은 파티원에게 보이는 이름입니다.</p>
        </div>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <h1>닉네임 정하기</h1>
          {pending ? <p className="sub">{PROVIDER_LABEL[pending.provider] ?? pending.provider} 계정으로 처음 오셨네요.</p> : null}
          {loadFailed ? (
            <>
              <p className="hint">가입 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</p>
              <Button variant="primary" size="lg" block style={{ marginTop: 18 }} onClick={() => navigate('/login', { replace: true })}>로그인으로 돌아가기</Button>
            </>
          ) : (
            <form className="auth-form" onSubmit={submit}>
              <Field label="닉네임" hint="2~16자 · 다른 사람과 겹칠 수 없습니다" error={error ?? undefined}>
                <input className="input" disabled={!pending || busy} autoFocus maxLength={16} placeholder="닉네임을 입력하세요"
                  value={nickname} onChange={(e) => setNickname(e.target.value)} />
              </Field>
              <Button type="submit" variant="primary" size="lg" block disabled={!pending || busy}>
                {busy ? '처리 중...' : '시작하기'}
              </Button>
            </form>
          )}
        </div>
      </main>
    </div>
  );
}
