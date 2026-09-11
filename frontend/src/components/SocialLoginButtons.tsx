import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import type { OAuthProviderView } from '../api/types';
import { API_ORIGIN, USE_MOCK } from '../config';
import { useAuth } from '../state/AuthContext';

/**
 * 소셜 로그인 진입점.
 *
 * 서버가 자격 증명이 설정된 제공자만 내려주므로 여기서 목록을 만들지 않는다.
 * 설정되지 않은 제공자의 버튼을 그려두면 사용자가 누른 뒤에야 실패를 알게 된다.
 */
export function SocialLoginButtons({ redirectTo, onError }: { redirectTo: string; onError(message: string): void }) {
  const { completeOAuth } = useAuth();
  const navigate = useNavigate();
  const [providers, setProviders] = useState<OAuthProviderView[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.listOAuthProviders()
      .then((list) => { if (!cancelled) setProviders(list); })
      // 목록을 못 받아도 이메일 로그인은 살아 있어야 한다. 조용히 버튼만 감춘다.
      .catch(() => { if (!cancelled) setProviders([]); });
    return () => { cancelled = true; };
  }, []);

  if (providers.length === 0) return null;

  const start = async (provider: OAuthProviderView) => {
    if (!USE_MOCK) {
      // 제공자 동의 화면으로 나갔다가 /auth/callback으로 돌아온다. XHR로는 못 하는 이동이다.
      const url = new URL(API_ORIGIN + provider.authorizeUrl, window.location.origin);
      url.searchParams.set('redirect', redirectTo);
      window.location.assign(url.toString());
      return;
    }
    // mock 모드에는 나갔다 올 제공자가 없다. 리다이렉트 구간을 건너뛰고 교환부터 한다.
    setBusy(true);
    try {
      await completeOAuth(`mock-${provider.provider.toLowerCase()}`);
      // real 모드에서는 콜백 화면이 하는 일이다. 여기서는 그 화면을 거치지 않는다.
      navigate(redirectTo, { replace: true });
    } catch {
      onError('소셜 로그인을 완료하지 못했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="social-login">
      <div className="social-divider"><span>소셜 계정으로 계속하기</span></div>
      {providers.map((p) => (
        <button
          key={p.provider}
          type="button"
          className={`social-btn s-${p.provider}`}
          disabled={busy}
          onClick={() => void start(p)}
        >
          {p.displayName}로 시작하기
        </button>
      ))}
    </div>
  );
}
