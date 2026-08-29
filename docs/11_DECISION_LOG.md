# 11. Decision Log

변경 시 날짜/근거/영향을 추가한다.

## Fixed decisions
1. 웹 서비스로 개발한다.
2. Frontend는 React + TypeScript.
3. Backend는 Java Spring Boot.
4. PostgreSQL을 영속 DB로 사용한다.
5. Redis를 active matchmaking/lock/index에 사용한다.
6. WebRTC로 파티 음성 + 텍스트 DataChannel을 처리한다.
7. Spring WebSocket은 server event + WebRTC signaling에만 사용한다.
8. 지원 게임은 LoL / VALORANT / PUBG 세 개다.
9. 매칭은 상대팀이 아니라 같은 파티 팀원 구성이다.
10. 실시간 매칭과 예약 매칭을 제공한다.
11. 예약은 기본 조건 + 플레이 가능 시간 + 플레이할 양만 추가한다.
12. condition creep를 막기 위해 Elbow 기반 검토를 적용한다.
13. 친구/차단/최근 함께한 사람/신고는 필수다.
14. 커뮤니티/게시판/길드/피드/프리미엄은 범위 밖이다.
15. modular monolith를 유지한다. 현재는 microservice로 나누지 않는다.
16. 인증은 JWT access + refresh token을 사용한다. (2026-08-30)
    - 근거: 서버 인스턴스를 N개로 늘려도 인증이 공유 상태를 타지 않고, WebSocket
      핸드셰이크에 토큰을 그대로 실을 수 있다. Redis 장애가 인증까지 번지지 않아
      INV-10의 fail-closed 범위를 새 매칭으로 한정할 수 있다.
    - 영향: refresh rotation을 필수로 한다. 정지/삭제 계정 즉시 차단은 stateless로
      불가능하므로 Redis denylist를 두고, denylist 조회 실패는 fail-closed 처리한다.
      access token TTL은 짧게 잡아 무효화 지연을 줄인다.
