/**
 * 실행 모드는 하나다 — 진짜 백엔드 셋(platform 8082 · matching 8080 · notification 8081)에 붙는다.
 * 어느 앱으로 갈지는 `vite.config.ts` 의 프록시가 경로로 나눈다. 브라우저 안의 가짜 서버(mock)는 2026-09-28 에 지웠다.
 * 실시간은 SSE `GET /api/v1/events` 하나다(`api/sse.ts`) — WebSocket 은 없다.
 */

/**
 * REST base. 기본값은 상대경로라 vite dev proxy 를 그대로 탄다(운영은 같은 출처의 ALB 가 경로로 나눈다).
 * 다른 호스트에 직접 붙일 때만 `VITE_API_BASE=http://localhost:8082/api/v1` 처럼 준다 — 그때는 SSE 도 그 호스트로 간다.
 */
export const API_BASE: string = import.meta.env.VITE_API_BASE || '/api/v1';
