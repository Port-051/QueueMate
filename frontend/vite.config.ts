import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const { QUEUEMATE_PREVIEW_HOST } = loadEnv(mode, process.cwd(), 'QUEUEMATE_');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      // 원격 미리보기는 지정한 호스트만 허용한다. API와 WebSocket도 같은 프록시를 쓴다.
      allowedHosts: QUEUEMATE_PREVIEW_HOST ? [QUEUEMATE_PREVIEW_HOST] : [],
      proxy: {
        '/api': 'http://localhost:8080',
        '/ws': { target: 'ws://localhost:8080', ws: true },
      },
    },
  };
});
