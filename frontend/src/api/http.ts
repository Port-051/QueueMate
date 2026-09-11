import { API_BASE, USE_MOCK } from '../config';
import { ApiError, toApiError } from './error';
import type { TokenResponse } from './types';

export { ApiError, isApiError, hasErrorCode, errorMessage, toApiError } from './error';
export type { ErrorCode, ErrorResponse } from './error';

const TOKEN_KEY = 'qm.tokens';

export interface StoredTokens { accessToken: string; refreshToken: string; }

export function readTokens(): StoredTokens | null {
  try {
    const raw = localStorage.getItem(TOKEN_KEY);
    return raw ? (JSON.parse(raw) as StoredTokens) : null;
  } catch {
    return null;
  }
}

/**
 * token이 바뀌면 알린다. 재발급으로 access token이 갈리면 WebSocket도 새 token으로
 * 다시 붙어야 한다. handshake 때 한 번만 인증하므로 기존 연결은 갱신되지 않는다.
 */
const tokenListeners = new Set<(tokens: StoredTokens | null) => void>();

export function subscribeTokens(listener: (tokens: StoredTokens | null) => void): () => void {
  tokenListeners.add(listener);
  return () => tokenListeners.delete(listener);
}

export function writeTokens(tokens: StoredTokens | null): void {
  try {
    if (tokens) localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens));
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage 접근이 막힌 브라우저에서도 앱은 동작해야 한다 */
  }
  tokenListeners.forEach((l) => l(tokens));
}

/** access token이 살아나지 못했을 때 앱에 알린다. AuthContext가 로그아웃 처리를 한다. */
type AuthLostHandler = () => void;
let onAuthLost: AuthLostHandler | null = null;
export function setAuthLostHandler(handler: AuthLostHandler | null): void {
  onAuthLost = handler;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | undefined>;
  /** 인증 헤더를 붙이지 않는다. auth 엔드포인트 4개가 쓴다 (docs/14 §0.4). */
  anonymous?: boolean;
  /** 401을 만나도 재발급을 시도하지 않는다. 재발급 호출 자신이 쓴다. */
  noRetry?: boolean;
}

function withQuery(path: string, query?: RequestOptions['query']): string {
  if (!query) return path;
  const entries = Object.entries(query).filter(([, v]) => v !== undefined && v !== '');
  if (entries.length === 0) return path;
  return `${path}?${entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&')}`;
}

/**
 * 계약상 본문 없는 응답은 204뿐이지만 201도 빈 본문이 온다 (`POST /reports`).
 * `res.json()`은 빈 본문에서 예외를 던지므로 텍스트로 먼저 읽고 판단한다.
 */
async function readBody<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

/**
 * access token 재발급. 동시에 여러 요청이 401을 만나도 한 번만 돈다.
 * refresh token은 rotate되므로 두 번 부르면 뒤엣것이 재사용 판정으로 401이 된다.
 */
let refreshing: Promise<StoredTokens | null> | null = null;

async function refreshTokens(): Promise<StoredTokens | null> {
  if (refreshing) return refreshing;
  const stored = readTokens();
  if (!stored?.refreshToken) return null;

  refreshing = (async () => {
    try {
      const next = await request<TokenResponse>('/auth/refresh', {
        method: 'POST',
        body: { refreshToken: stored.refreshToken },
        anonymous: true,
        noRetry: true,
      });
      const tokens = { accessToken: next.accessToken, refreshToken: next.refreshToken };
      writeTokens(tokens);
      return tokens;
    } catch {
      writeTokens(null);
      onAuthLost?.();
      return null;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

/** REST 한 번의 호출. mock 모드에서는 네트워크 대신 in-memory adapter가 응답한다. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const fullPath = withQuery(path, options.query);

  if (USE_MOCK) {
    // 실제 서버 모드에서 테스트 데이터 초기화나 브라우저 API에 의존하지 않는다.
    const { handleMockRequest } = await import('../mocks/server');
    return handleMockRequest<T>(method, fullPath, options.body, readTokens()?.accessToken ?? null);
  }

  const res = await send(method, fullPath, options, readTokens()?.accessToken ?? null);

  // 401이면 한 번만 재발급하고 같은 요청을 다시 보낸다. 실패하면 그대로 던진다.
  if (res.status === 401 && !options.anonymous && !options.noRetry) {
    const tokens = await refreshTokens();
    if (tokens) {
      const retried = await send(method, fullPath, options, tokens.accessToken);
      return finish<T>(retried);
    }
  }
  return finish<T>(res);
}

async function send(
  method: string,
  fullPath: string,
  options: RequestOptions,
  token: string | null,
): Promise<Response> {
  const useAuth = !options.anonymous && Boolean(token);
  return fetch(`${API_BASE}${fullPath}`, {
    method,
    headers: {
      Accept: 'application/json',
      // 계약은 바디가 있는 요청에만 Content-Type을 요구한다. 없으면 415다 (docs/14 §0.2).
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json;charset=UTF-8' }),
      ...(useAuth ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
}

async function finish<T>(res: Response): Promise<T> {
  if (!res.ok) {
    // 모든 4xx/5xx는 `{code, message}`다. 인증 필터가 막은 401도 같다 (docs/14 §0.6).
    const raw = await res.text().catch(() => '');
    throw toApiError(res.status, raw, res.statusText);
  }
  try {
    return await readBody<T>(res);
  } catch {
    throw new ApiError(res.status, 'MALFORMED_RESPONSE', '서버 응답을 해석하지 못했습니다');
  }
}
