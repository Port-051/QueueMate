/**
 * 실행 모드는 두 가지뿐이다.
 *
 * - `mock`  : `src/mocks/`의 in-memory 어댑터가 REST와 이벤트를 대신한다. 네트워크가 없다.
 * - `real`  : 진짜 HTTP/WebSocket을 쓴다. 무엇이 8080에 떠 있는지는 프론트가 알 바 아니다.
 *             Spring 백엔드일 수도 있고 `mock-server/server.js`일 수도 있다.
 *
 * "mock 서버(localhost:8080)"는 세 번째 모드가 아니라 **`real` 모드의 상대**다.
 * 프론트 코드에는 분기가 없고 8080에 무엇을 띄웠는지만 다르다.
 * 어느 쪽에 붙었는지는 `npm run dev*` 스크립트와 부팅 로그가 알려 준다.
 */
export type ApiMode = 'mock' | 'real';

/** backend가 없어도 개발할 수 있어야 한다 (CLAUDE.md §5). 값이 없으면 mock이다. */
export const API_MODE: ApiMode = import.meta.env.VITE_API_MODE === 'real' ? 'real' : 'mock';
export const USE_MOCK = API_MODE === 'mock';

/**
 * REST base. 기본값은 상대경로라 vite dev proxy(→ `localhost:8080`)를 그대로 탄다.
 * 다른 호스트에 직접 붙일 때만 `VITE_API_BASE=http://localhost:8080/api/v1` 처럼 준다.
 */
export const API_BASE: string = import.meta.env.VITE_API_BASE || '/api/v1';

/**
 * 소셜 로그인은 XHR이 아니라 브라우저 이동이라 `API_BASE`의 프록시를 타지 않는다.
 * base가 절대 URL이면 그 origin을 앞에 붙여야 같은 서버로 간다.
 */
export const API_ORIGIN: string = API_BASE.startsWith('http') ? new URL(API_BASE).origin : '';

/** WebSocket 경로. `/api/v1` 밖이다 (docs/14 §0.1). */
export const WS_PATH = '/ws';

/** handshake subprotocol (contracts/events.md). 첫 항목이 버전, 둘째가 `bearer.<access token>`이다. */
export const WS_PROTOCOL_VERSION = 'queuemate.v1';
export const WS_BEARER_PREFIX = 'bearer.';

/** 절대 URL로 강제할 때만 쓴다. 비면 현재 origin + `WS_PATH`. */
export const WS_URL_OVERRIDE: string = import.meta.env.VITE_WS_URL || '';

/** 어느 모드로 떴는지 한 줄로 말한다. 화면만 보고는 구별이 안 된다. */
export function describeApiMode(): string {
  return USE_MOCK
    ? 'QueueMate API mode: mock — in-memory adapter. 네트워크 요청이 나가지 않는다'
    : `QueueMate API mode: real — REST ${API_BASE}, WS ${WS_URL_OVERRIDE || WS_PATH} (vite proxy → localhost:8080)`;
}

// mock을 real로 착각하면 "동작하는 것처럼" 보인다. 개발 빌드에서는 매번 알려 준다.
if (import.meta.env.DEV) {
  // eslint-disable-next-line no-console
  console.info(describeApiMode());
}
