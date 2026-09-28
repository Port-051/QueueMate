import { Navigate, useSearchParams } from 'react-router-dom';
import type { SocialProvider } from '../api/types';
import { stashSettingsNotice } from '../state/settingsNotice';

/**
 * `/settings` — 백엔드가 소셜 계정 잇기의 결과를 돌려보내는 자리다(`FRONT_BASE_URL` + `/settings?linked=…` · `?error=…`. 소유자 결정 —
 * 콜백 경로는 프런트가 백엔드에 맞춘다). 화면은 없다 — 쪽지를 담고 프로필의 설정 절(`/app/me#settings`)로 보낸다.
 */
export function SettingsRedirectPage() {
  const [params] = useSearchParams();
  const linked = params.get('linked');
  const error = params.get('error');
  if (linked) stashSettingsNotice({ linked: linked as SocialProvider });
  else if (error) stashSettingsNotice({ error });
  return <Navigate to="/app/me#settings" replace />;
}
