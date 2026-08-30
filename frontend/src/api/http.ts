import { API_BASE, USE_MOCK } from '../config';
import { handleMockRequest } from '../mocks/server';
import { ApiError } from './error';

export { ApiError, isApiError } from './error';


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

export function writeTokens(tokens: StoredTokens | null): void {
  try {
    if (tokens) localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens));
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage 접근이 막힌 브라우저에서도 앱은 동작해야 한다 */
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | undefined>;
}

function withQuery(path: string, query?: RequestOptions['query']): string {
  if (!query) return path;
  const entries = Object.entries(query).filter(([, v]) => v !== undefined && v !== '');
  if (entries.length === 0) return path;
  return `${path}?${entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&')}`;
}

/** REST 한 번의 호출. mock 모드에서는 네트워크 대신 in-memory mock server가 응답한다. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const fullPath = withQuery(path, options.query);

  if (USE_MOCK) {
    return handleMockRequest<T>(method, fullPath, options.body, readTokens()?.accessToken ?? null);
  }

  const token = readTokens()?.accessToken;
  const res = await fetch(`${API_BASE}${fullPath}`, {
    method,
    headers: {
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (res.status === 204) return undefined as T;
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new ApiError(res.status, `HTTP_${res.status}`, detail || res.statusText);
  }
  return (await res.json()) as T;
}
