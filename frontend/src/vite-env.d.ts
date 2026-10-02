/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ROUTER_MODE?: 'browser' | 'hash';
  /** REST base override. 기본값 `/api/v1` (vite proxy 가 경로별로 8080 · 8081 · 8082 로 나눈다). */
  readonly VITE_API_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
