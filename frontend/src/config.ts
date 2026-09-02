/** backend가 없어도 개발할 수 있도록 mock adapter가 기본이다(CLAUDE.md §5). */
export const API_MODE: 'mock' | 'real' = import.meta.env.VITE_API_MODE === 'real' ? 'real' : 'mock';
export const USE_MOCK = API_MODE === 'mock';
export const API_BASE = '/api/v1';
export const WS_PATH = '/ws';
/** handshake subprotocol (contracts/events.md). 첫 항목이 버전, 둘째가 `bearer.<access token>`이다. */
export const WS_PROTOCOL_VERSION = 'queuemate.v1';
export const WS_BEARER_PREFIX = 'bearer.';
