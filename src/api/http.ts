import { API_BASE } from '../config';
import { ApiError, toApiError } from './error';
import type { SessionUser } from './types';

export { ApiError, isApiError, hasErrorCode, errorMessage, toApiError } from './error';
export type { ErrorCode, ErrorResponse } from './error';

/**
 * 인증은 쿠키다 (platform-api.md "공통" · "access 토큰" · "refresh 토큰" · D-24 · D-26).
 *
 * - access 는 `qm_access`(HttpOnly · RS256 JWT · 15분), refresh 는 `qm_refresh`(HttpOnly · 불투명 UUID · 7일 ·
 *   `Path=/api/v1/auth` — 2026-10-02 에 `/api/v1/auth/refresh` 에서 넓어졌다). **프런트는 토큰을 보지도 저장하지도 않는다** — `Authorization` 헤더 · localStorage 가 없다.
 * - 브라우저가 같은 출처(프록시 · 운영은 한 도메인)라 쿠키가 저절로 붙는다. `credentials: 'include'` 는 base 가 절대 URL 일 때를 위한 보험이다.
 * - 401 이면 `POST /api/v1/auth/refresh`(본문 없음 · 쿠키만)를 **한 번** 부르고 같은 요청을 다시 보낸다. 재발급이 401 `INVALID_REFRESH_TOKEN` 이면
 *   로그아웃 상태다 — `onAuthLost` 로 AuthContext 에 알린다. `/api/v1/auth/**` 는 인증이 필요 없는 경로라 재시도하지 않는다 —
 *   **회원 탈퇴 `DELETE /api/v1/auth/account` 하나만 예외다**(`ACCOUNT_PATH` — access 가 있어야 하는 요청이라 401 이면 재발급 뒤 다시 보낸다 ·
 *   platform 의 `CookieBearerTokenResolver#ACCOUNT_PATH` · `SecurityConfig` 와 같은 금 — 2026-10-02 · P-48).
 */

/** `/auth/**` 가운데 access 가 있어야 하는 하나 — 회원 탈퇴(`api/client.ts` `deleteMe`). 401 이면 다른 요청처럼 재발급을 한 번 해 본다. */
const ACCOUNT_PATH = '/auth/account';

/** access 가 살아나지 못했을 때 앱에 알린다. AuthContext 가 익명 상태로 되돌린다. */
type AuthLostHandler = () => void;
let onAuthLost: AuthLostHandler | null = null;
export function setAuthLostHandler(handler: AuthLostHandler | null): void {
  onAuthLost = handler;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | undefined>;
  /** 401을 만나도 재발급을 시도하지 않는다. 재발급 호출 자신과 `/auth/**` 가 쓴다. */
  noRetry?: boolean;
}

function withQuery(path: string, query?: RequestOptions['query']): string {
  if (!query) return path;
  const entries = Object.entries(query).filter(([, v]) => v !== undefined && v !== '');
  if (entries.length === 0) return path;
  return `${path}?${entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&')}`;
}

/**
 * 본문 없는 응답(204 · 202)과 빈 201 이 있다. `res.json()`은 빈 본문에서 예외를 던지므로 텍스트로 먼저 읽고 판단한다.
 */
async function readBody<T>(res: Response): Promise<T> {
  if (res.status === 204 || res.status === 202) return undefined as T;
  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

/**
 * 재발급. 동시에 여러 요청이 401을 만나도 **한 번만** 돈다(single-flight).
 * refresh 는 rotation 이라(`GETDEL`) 두 번 부르면 뒤엣것이 401 이 된다 — 그래서 묶는다.
 *
 * 성공하면 새 쿠키 둘이 응답에 실려 온다(본문은 `{userId, nickname}`). 실패는 전부 401 `INVALID_REFRESH_TOKEN` 이고
 * 그때 서버가 refresh 쿠키를 지운다 — 프런트는 `onAuthLost` 를 부르고 `null` 을 돌려준다.
 * SSE(`api/sse.ts`)가 401 로 닫혔을 때도 이것을 부른 뒤 `EventSource` 를 새로 만든다.
 */
let refreshing: Promise<SessionUser | null> | null = null;

export function refreshSession(): Promise<SessionUser | null> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    try {
      const res = await send('POST', '/auth/refresh', {});
      if (!res.ok) throw toApiError(res.status, await res.text().catch(() => ''), res.statusText);
      return await readBody<SessionUser>(res);
    } catch {
      onAuthLost?.();
      return null;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

/** REST 한 번의 호출. 쿠키가 자격 증명이다. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const fullPath = withQuery(path, options.query);

  const res = await send(method, fullPath, options);

  // 401이면 한 번만 재발급하고 같은 요청을 다시 보낸다. 실패하면 그대로 던진다.
  // `/auth/**` 는 인증이 필요 없는 경로라 401 이 "토큰이 없다"가 아니다(예: 가입 대기 토큰이 없다 · 재발급 실패). 회원 탈퇴(`ACCOUNT_PATH`)만 예외다.
  if (res.status === 401 && !options.noRetry && (!fullPath.startsWith('/auth/') || fullPath === ACCOUNT_PATH)) {
    const session = await refreshSession();
    if (session) {
      const retried = await send(method, fullPath, options);
      return finish<T>(retried);
    }
  }
  return finish<T>(res);
}

function send(method: string, fullPath: string, options: RequestOptions): Promise<Response> {
  return fetch(`${API_BASE}${fullPath}`, {
    method,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json;charset=UTF-8' }),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
}

async function finish<T>(res: Response): Promise<T> {
  if (!res.ok) {
    // 모든 4xx/5xx는 `{code, message, details}`다. 인증 필터가 막은 401도 같다 (platform-api.md "공통").
    const raw = await res.text().catch(() => '');
    throw toApiError(res.status, raw, res.statusText, res.headers.get('Retry-After'));
  }
  try {
    return await readBody<T>(res);
  } catch {
    throw new ApiError(res.status, 'MALFORMED_RESPONSE', '서버 응답을 해석하지 못했습니다');
  }
}
