import type { UserProfile } from '../api/types';

/**
 * 온보딩(게임 계정 연결 화면 `/onboarding`)을 **로그인 직후에 한 번만** 권하기 위한 표시 — 사용자별 · 이 브라우저만.
 *
 * 2026-09-29 소유자 결정 "게임 계정 연결은 유저가 할 수 있도록 하고 선택적으로 할 수 있도록 하자" — 3단계(2026-09-28)의 "문지기(`RequireOnboarding`) 유지 · 건너뛰기 없음" 을 뒤집는다.
 * 그래서 앱(`/app/**`)은 게임 계정이 없어도 막지 않고, **로그인 직후의 목적지만** 고른다(`landingPath`) — 게임 계정이 0개이고 아직 온보딩을 지나간 적이 없으면 `/onboarding`,
 * 아니면 `/app/home`. 로그인 직후의 목적지를 정하는 자리는 넷이다 — 랜딩(`/` — 소셜 로그인의 콜백이 돌아오는 곳) · 로그인 화면 · 소셜 가입 · 개발용 로그인(`TEMP-DEV-LOGIN`).
 *
 * **지나갔다 = "나중에 할게요" 를 눌렀거나 계정을 연결하고 "시작하기" 를 눌렀다**(Claude 가 정한 세부 — 연결했다가 전부 끊은 사람에게 온보딩을 다시 권하지 않는다).
 * 값은 서버에 없다(백엔드는 게임 계정을 강제하지 않는다 — 카드의 `profile` 이 `null` 일 뿐). 저장이 막힌 브라우저(사생활 보호 창 등)에서는 이 탭이 살아 있는 동안만 기억한다.
 */
const KEY_PREFIX = 'qm.onboarding.done:';

/** 저장이 막혔을 때의 대신 — 이 탭에서만. */
const inMemory = new Set<string>();

export type OnboardingExit = 'skipped' | 'linked';

export function onboardingDone(userId: string): boolean {
  if (inMemory.has(userId)) return true;
  try {
    return localStorage.getItem(KEY_PREFIX + userId) !== null;
  } catch {
    return false;
  }
}

export function markOnboardingDone(userId: string, exit: OnboardingExit): void {
  inMemory.add(userId);
  try { localStorage.setItem(KEY_PREFIX + userId, exit); } catch { /* 저장 공간이 막혀도 화면은 간다 — 이 탭에서만 기억한다 */ }
}

/** 로그인 직후의 목적지 — 게임 계정이 없고 온보딩을 지나간 적이 없으면 `/onboarding`, 아니면 홈. 로그인이 안 됐으면(`null`) 홈(= `RequireAuth` 가 로그인으로 보낸다). */
export function landingPath(user: UserProfile | null): '/onboarding' | '/app/home' {
  if (!user) return '/app/home';
  return user.gameAccounts.length === 0 && !onboardingDone(String(user.userId)) ? '/onboarding' : '/app/home';
}
