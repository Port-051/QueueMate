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

17. Redis는 Sentinel(master 1 + replica 2 + sentinel 3, 정족수 2)로 운영한다. (2026-09-11)
    - 근거: INV-10이 Redis 장애에 fail-closed이므로 지금은 Redis가 죽으면 매칭이 통째로
      멈춘다. docs/07 §10이 이미 운영 replication을 전제하고 있었고, Sentinel은 그
      전제에 대한 표준적인 구현이다.
    - 영향: 복제가 비동기라 failover 때 guard write가 유실될 수 있다. INV-2는 그때까지
      Redis claim 하나에만 걸려 있었으므로 `active_proposal_claims` 테이블을 먼저
      도입해 DB PK로 받게 했다 (V3). 읽기는 master로 고정한다. 복제가 밀리면
      `min-replicas-to-write`로 write를 거부한다. 셋 다 매칭 가용성을 정합성보다
      뒤에 두는 선택이다.
    - 배제한 대안: 단일 인스턴스 + `restart: always`. 프로세스 사망만 막고 노드 손실은
      못 막는다. 멀티 노드 배포가 목표라 선택하지 않았다.

18. 실시간 매칭은 주기 tick이 아니라 대기열 변경 시점에 돈다. (2026-09-11)
    - 근거: 새 대기자 없이 새 파티가 생길 수 없다. 1초 tick은 아무도 기다리지 않는
      게임×모드까지 초당 한 번씩 Redis를 읽으면서, 정작 매칭 지연은 tick 간격만큼 만들었다.
      bucket 구조(docs/07 §3.1) 덕분에 "어느 자리에서 돌아야 하는가"를 이미 알고 있다.
    - 영향: 매칭은 커밋 뒤 별도 스레드에서 돈다. 요청-응답 안에서 돌리지 않는 원칙(docs/03 §11)은
      유지된다. trigger 유실에 대비해 긴 주기 sweep을 안전망으로 남기고,
      `queuemate.match.proposal.created{source}`로 둘을 갈라 센다. sweep이 일하기 시작하면
      주기를 줄이는 것이 아니라 유실 원인을 고친다.
    - 배제한 대안: tick 간격만 줄이기. 지연과 빈 읽기가 함께 늘어 둘 중 어느 것도 해결하지 못한다.

