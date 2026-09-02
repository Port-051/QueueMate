/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 'mock'(기본) 또는 'real'. real이면 실제 backend REST/WebSocket에 붙는다. */
  readonly VITE_API_MODE?: 'mock' | 'real';
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
