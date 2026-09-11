import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { isApiError } from '../api/error';
import { Button } from '../components/ui';
import { useAuth } from '../state/AuthContext';

/** 제공자가 거부/실패를 알릴 때 쓰는 코드. 원문을 그대로 보여주지 않는다. */
const MESSAGES: Record<string, string> = {
  ACCESS_DENIED: '소셜 로그인을 취소했습니다',
  INVALID_STATE: '로그인 요청이 만료됐습니다. 다시 시도해주세요',
  EXCHANGE_FAILED: '제공자와 통신하지 못했습니다. 잠시 후 다시 시도해주세요',
  ACCOUNT_UNAVAILABLE: '사용할 수 없는 계정입니다',
  // 앱 등록 전 개발 환경에서만 나온다. 운영에서는 버튼 자체가 없다.
  PROVIDER_NOT_CONFIGURED: '이 제공자는 아직 앱 등록이 되지 않았습니다',
};

/**
 * 소셜 로그인 콜백. 서버가 붙여준 일회용 코드를 토큰으로 바꾼다.
 *
 * 코드는 한 번만 쓸 수 있다. StrictMode의 이중 실행이나 새로고침으로 두 번 보내면
 * 두 번째는 401이 되므로, 이 화면에서 교환은 한 번만 시도한다.
 */
export function AuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { completeOAuth } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  const code = params.get('code');
  const failure = params.get('error');
  const redirect = params.get('redirect') ?? '/app/home';

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    if (failure || !code) {
      setError(MESSAGES[failure ?? ''] ?? '소셜 로그인에 실패했습니다');
      return;
    }
    completeOAuth(code)
      .then(() => navigate(redirect, { replace: true }))
      .catch((err) => setError(isApiError(err) ? err.message : '소셜 로그인을 완료하지 못했습니다'));
  }, [code, failure, redirect, completeOAuth, navigate]);

  return (
    <div className="onboarding">
      <div className="onboarding-card" style={{ maxWidth: 460, textAlign: 'center' }}>
        {error ? (
          <>
            <h1 style={{ fontSize: 22, fontWeight: 800 }}>로그인하지 못했습니다</h1>
            <p style={{ color: 'var(--muted)', marginTop: 10, fontSize: 14 }}>{error}</p>
            <Button variant="primary" size="lg" style={{ marginTop: 24 }} onClick={() => navigate('/login', { replace: true })}>
              로그인으로 돌아가기
            </Button>
          </>
        ) : (
          <>
            <h1 style={{ fontSize: 22, fontWeight: 800 }}>로그인 중입니다</h1>
            <p style={{ color: 'var(--muted)', marginTop: 10, fontSize: 14 }}>잠시만 기다려주세요.</p>
          </>
        )}
      </div>
    </div>
  );
}
