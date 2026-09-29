import type { SocialProvider } from '../api/types';

/**
 * 소셜 계정 잇기의 결과를 한 번만 보여 주기 위한 쪽지.
 * 백엔드 콜백이 `/settings?linked={PROVIDER}` · `/settings?error=SOCIAL_ALREADY_LINKED|PROVIDER_ALREADY_LINKED` 로 302 하면
 * `SettingsRedirectPage` 가 여기에 담고 `/app/me#settings` 로 보내며, `MyInfoPage` 가 꺼내 토스트 하나로 보여 준다(platform-api.md "잇기 · 끊기").
 * sessionStorage 라 같은 탭에서만 · 한 번만 산다.
 */
const KEY = 'qm.settings.notice';

export type SettingsLinkError = 'SOCIAL_ALREADY_LINKED' | 'PROVIDER_ALREADY_LINKED';
export interface SettingsNotice { linked?: SocialProvider; error?: SettingsLinkError | string; }

export function stashSettingsNotice(notice: SettingsNotice): void {
  try { sessionStorage.setItem(KEY, JSON.stringify(notice)); } catch { /* 저장 공간이 막혀도 화면은 열린다 — 쪽지만 잃는다 */ }
}

export function takeSettingsNotice(): SettingsNotice | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    return raw ? (JSON.parse(raw) as SettingsNotice) : null;
  } catch {
    return null;
  }
}

export const PROVIDER_LABEL: Record<SocialProvider, string> = { KAKAO: '카카오', DISCORD: '디스코드', GOOGLE: 'Google' };

/** 쪽지를 사람이 읽을 문구로. 모르는 `error` 값은 그대로 보여 준다(계약이 앞서갔을 수 있다). */
export function settingsNoticeMessage(notice: SettingsNotice): { text: string; tone: 'ok' | 'error' } | null {
  if (notice.linked) return { text: `${PROVIDER_LABEL[notice.linked] ?? notice.linked} 계정을 연결했습니다`, tone: 'ok' };
  if (notice.error === 'SOCIAL_ALREADY_LINKED') return { text: '이미 다른 사용자에게 연결된 소셜 계정입니다', tone: 'error' };
  if (notice.error === 'PROVIDER_ALREADY_LINKED') return { text: '같은 제공자의 다른 계정이 이미 연결돼 있습니다', tone: 'error' };
  if (notice.error) return { text: `소셜 계정을 연결하지 못했습니다 (${notice.error})`, tone: 'error' };
  return null;
}
