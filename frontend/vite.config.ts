import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * 백엔드는 세 앱이다(루트 START_HERE.md §2). 운영은 CloudFront 한 도메인 아래 ALB 가 경로로 나누고,
 * 로컬은 이 프록시가 같은 일을 한다 — 그래서 브라우저가 보기에 네 서비스는 **같은 출처**다
 * (쿠키 `qm_access` · `Origin` 검사 · CORS 없음이 전부 이 전제 위에 있다 — platform/CLAUDE.md §5.1 (사)).
 *
 * Vite 5 는 키를 선언한 순서대로 `startsWith` 첫 매칭을 고르므로 **구체적인 경로를 먼저** 적는다.
 * 서비스에 CORS 를 넣지 않는다. SSE(`/api/v1/events`)는 http-proxy 가 그대로 스트리밍하므로 따로 옵션이 없다.
 */
const PLATFORM = 'http://localhost:8082';     // 계정 · 소셜 로그인 · 토큰 · 게임 프로필 · 게시판 · 방 · 친구 · 차단 · 신고
const MATCHING = 'http://localhost:8080';     // 매칭 요청 · heartbeat · 제안
const NOTIFICATION = 'http://localhost:8081'; // SSE

export default defineConfig(({ mode }) => {
  const { QUEUEMATE_PREVIEW_HOST, QUEUEMATE_MATCHING_URL } = loadEnv(mode, process.cwd(), 'QUEUEMATE_');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      // 원격 미리보기는 지정한 호스트만 허용한다.
      allowedHosts: QUEUEMATE_PREVIEW_HOST ? [QUEUEMATE_PREVIEW_HOST] : [],
      proxy: {
        '/api/v1/events': NOTIFICATION,
        '/api/v1/match-requests': QUEUEMATE_MATCHING_URL || MATCHING,
        '/api/v1/proposals': QUEUEMATE_MATCHING_URL || MATCHING,
        '/api': PLATFORM,
      },
    },
  };
});
