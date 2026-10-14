/**
 * Google Analytics 4 — 퍼널(랜딩 → 시작하기 → 서비스 도착 → 가입 완료 → 매칭 신청)의 단계별 사람 수를 보려는 것이다(2026-10-13 소유자 지시).
 *
 * - 측정 ID 는 빌드 환경변수 `VITE_GA_MEASUREMENT_ID`(`G-…`)다. **없으면 아무것도 하지 않는다** — 개발 서버(e2e) · 측정 ID 가 없는 빌드에서는 스크립트도 안 받고 이벤트도 안 보낸다.
 *   운영 빌드의 값은 `.env.production` 에 둔다(측정 ID 는 모든 방문자의 HTML 에 그대로 실리는 값이라 비밀이 아니다).
 * - 설치 코드는 GA 가 주는 `<head>` 스니펫과 같은 일을 한다(gtag.js 로드 · `gtag('js')` · `gtag('config')`). 여기서 하는 이유는 ID 를 환경변수로 바꿔 끼우려는 것뿐이다.
 * - 페이지 조회는 GA4 의 "향상된 측정"(데이터 스트림 설정 · 기본 켜짐)이 주소가 바뀔 때마다 스스로 적는다 — SPA 라도 따로 보내지 않는다.
 * - 보내는 이벤트는 {@link FunnelEvent} 다섯뿐이고 **회원 번호 · 닉네임 · 이메일 같은 개인정보는 싣지 않는다.** GA4 는 IP 주소를 저장하지 않는다(구글 문서 — 수집 즉시 버린다).
 * - 처리방침(`pages/legal/PrivacyPage.tsx` 제2 · 6 · 8 · 10조)이 이 도구를 적는다 — 보내는 것 · 쿠키가 바뀌면 같이 고친다(`CLAUDE.md` §4).
 */

/** 퍼널의 단계 — GA 권장 이름을 쓸 수 있는 것(`sign_up`)은 그것을, 나머지는 우리 이름. 이벤트 이름은 GA 탐색 보고서의 퍼널 단계가 된다. */
export type FunnelEvent =
  /** 랜딩의 "시작하기" 를 눌렀다(→ 로그인 화면). */
  | 'start_click'
  /** 로그인 화면에서 소셜 제공자 버튼을 눌렀다 — `method` 에 제공자. */
  | 'login_start'
  /** 소셜 가입(닉네임 정하기)을 끝냈다 — `method` 에 제공자. */
  | 'sign_up'
  /** 로그인한 채 앱(`/app/**`)에 처음 들어왔다 — 브라우저 세션마다 한 번. */
  | 'app_enter'
  /** 빠른매치를 신청해 대기열에 들어갔거나(QUEUED) 맞는 방에 바로 들어갔다(ROOM) — `result` 에 어느 쪽. */
  | 'match_request';

type Gtag = (...args: unknown[]) => void;
declare global {
  interface Window { dataLayer?: unknown[]; gtag?: Gtag }
}

const MEASUREMENT_ID: string = (import.meta.env.VITE_GA_MEASUREMENT_ID as string | undefined) ?? '';

let installed = false;

/** 측정 ID 가 있을 때만 gtag.js 를 싣는다 — 앱이 시작할 때 한 번(`main.tsx`). */
export function initAnalytics(): void {
  if (installed || !MEASUREMENT_ID || typeof document === 'undefined') return;
  installed = true;
  window.dataLayer = window.dataLayer ?? [];
  // GA 스니펫 그대로 — arguments 객체를 밀어 넣는다(배열로 바꾸면 gtag.js 가 읽지 못한다).
  window.gtag = function gtag() { window.dataLayer!.push(arguments); } as Gtag;
  window.gtag('js', new Date());
  window.gtag('config', MEASUREMENT_ID);
  const script = document.createElement('script');
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(MEASUREMENT_ID)}`;
  document.head.appendChild(script);
}

/** 퍼널 이벤트 하나 — 설치되지 않았으면(측정 ID 없음) 조용히 아무것도 안 한다. 값에 개인정보를 넣지 않는다. */
export function track(event: FunnelEvent, params: Record<string, string | number | boolean> = {}): void {
  if (!installed || !window.gtag) return;
  try { window.gtag('event', event, params); } catch { /* 분석이 앱을 멈추게 하지 않는다 */ }
}

const APP_ENTER_KEY = 'qm.ga.appEntered';

/** `app_enter` 는 브라우저 세션(탭)마다 한 번 — 새로 고침 · 화면 이동마다 세지 않는다. */
export function trackAppEnterOnce(): void {
  if (!installed) return;
  try {
    if (sessionStorage.getItem(APP_ENTER_KEY)) return;
    sessionStorage.setItem(APP_ENTER_KEY, '1');
  } catch { /* 저장소를 못 쓰면 그냥 한 번 보낸다 */ }
  track('app_enter');
}

export const analyticsEnabled = (): boolean => installed;
