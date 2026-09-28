/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ROUTER_MODE?: 'browser' | 'hash';
  /** 'mock'(기본) 또는 'real'. real이면 실제 REST/WebSocket에 붙는다. */
  readonly VITE_API_MODE?: 'mock' | 'real';
  /** REST base override. 기본값 `/api/v1` (vite proxy → localhost:8080). */
  readonly VITE_API_BASE?: string;
  /** WebSocket 절대 URL override. 기본값은 현재 origin + `/ws`. */
  readonly VITE_WS_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
