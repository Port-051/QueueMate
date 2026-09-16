# 11. Decision Log

> 매칭 엔진에 걸리는 결정을 어디서 가져왔는지는 맨 아래
> [queueMate 본 저장소에서 병합한 결정 (feature/frontend 브랜치)](#queuemate-본-저장소에서-병합한-결정-featurefrontend-브랜치)
> 절을 본다.

> **경로 표기 주의 (2026-09-11 추가).** 이 파일은 **기록**이라 기존 항목의 본문을 고치지 않는다.
> 2026-09-11 에 스프링/Gradle 프로젝트를 저장소 루트에서 `backend/` 아래로 옮겼다
> (`src/` · `build.gradle` · `settings.gradle` · `gradle/` · `gradlew`). 그러므로 아래 항목이
> `src/...` 로 적은 경로는 지금의 `backend/src/...` 이고, `./gradlew ...` 는 `backend/` 안에서
> 돌린다. `contracts/` · `docs/` · `seed/` · `load-test/` · `redis-ha-lab/` 은 루트 그대로다.

> **낡은 항목 주의 (2026-09-11 추가).** 같은 이유로 기존 항목을 고치지 않았지만,
> 그 사이 코드가 바뀌어 **#34 · O-3 · O-4 · O-7 · D-6 의 일부 서술이 사실이 아니게 됐다.**
> 무엇이 어떻게 바뀌었는지는 맨 아래
> [문서 정합성 점검 기록 (2026-09-11)](#문서-정합성-점검-기록-2026-09-11) 절에 있고,
> 겹치면 그 절이 우선한다. 특히 `join-or-create-party*.lua` 라는 파일은 지금 **없다.**

변경 시 날짜/근거/영향을 추가한다.

## Fixed decisions
1. 웹 서비스로 개발한다.
2. Frontend는 React + TypeScript.
3. Backend는 Java Spring Boot.
4. PostgreSQL을 영속 DB로 사용한다.
5. Redis를 active matchmaking/lock/index에 사용한다.
6. WebRTC로 파티 음성 + 텍스트 DataChannel을 처리한다.
7. ~~Spring WebSocket은 server event + WebRTC signaling에만 사용한다.~~ (2026-08-31)
   **개정됨 → #22.** 서버→클라 이벤트 14종은 SSE(`GET /api/v1/events`)로 보내고,
   Spring WebSocket(`/ws`)은 `WEBRTC_SIGNAL` 전용이다.
8. 지원 게임은 LoL / VALORANT / PUBG 세 개다.
9. 매칭은 상대팀이 아니라 같은 파티 팀원 구성이다.
10. 실시간 매칭과 예약 매칭을 제공한다.
11. 예약은 기본 조건 + 플레이 가능 시간 + 플레이할 양만 추가한다.
12. condition creep를 막기 위해 Elbow 기반 검토를 적용한다.
13. 친구/차단/최근 함께한 사람/신고는 필수다.
14. 커뮤니티/게시판/길드/피드/프리미엄은 범위 밖이다.
15. 배포 단위를 `app:platform` / `app:matching` / `app:realtime` /
    `app:reservation-batch` 4개로 나눈다. 그 이상 분리하지 않는다.
    (2026-08-31, 기존 "modular monolith 유지" 결정을 개정. 같은 날 3개 → 4개로 재개정하며
    `app:api`를 `app:platform`으로 개명)
    - 담는 모듈:
      - `app:platform` — account / party / social / reservation / common. REST 전용,
        stateless. 예약 REST도 여기가 서빙한다 (#24).
      - `app:matching` — matching / gameconfig / common. 스케일 기준은 Redis 큐 depth.
      - `app:realtime` — realtime / common. 연결 전담. 스케일 기준은 동시 연결 수.
      - `app:reservation-batch` — reservation / matching / gameconfig / common (#24).
    - 근거: 네 영역은 **재시작 비용이 비대칭**이다. REST 앱은 무중단 교체가 자유롭지만
      연결 보유 앱은 재연결 비용을 물고, 매칭 엔진 재시작은 진행 중인 claim을 흔들며,
      배치는 재시작 시 주기를 건너뛴다. 이걸 한 프로세스에 묶으면 가장 비싼 쪽 기준으로만
      배포하게 된다. 여기에 장애 격리(한 영역의 OOM/무한루프가 나머지를 죽이지 않음)와
      독립 배포 주기가 더해진다.
    - `app:api` → `app:platform` 개명 근거: `api`는 프로토콜 축 이름인데 `app:matching`도
      REST를 서빙하므로 구분 기준이 못 되고, 내용(account+party+social)을 설명하지 못한다.
      네이밍을 도메인 축으로 통일한다.
    - 영향: Gradle 멀티모듈로 전환해 `module:*`은 라이브러리, `app:*`이 배포 단위가 된다.
      docs/10의 owner directory가 이 구조에 맞춰 바뀐다. 서비스 간 통신이 인프로세스
      호출에서 이벤트로 바뀌므로 경계를 넘는 동기 호출을 새로 만들지 않는다.
      **독립 스케일은 Stage 2(ECS Fargate)부터 실현된다** (#20). Stage 1은 단일 EC2의
      Docker Compose이므로 4개 컨테이너가 한 호스트에 뜬다. 그래도 지금 경계를 잡아두는
      이유는 나중에 프로세스를 쪼개는 비용이 훨씬 크기 때문이다.
16. 인증은 JWT access + refresh token을 사용한다. (2026-08-30)
    - 근거: 서버 인스턴스를 N개로 늘려도 인증이 공유 상태를 타지 않고, WebSocket
      핸드셰이크에 토큰을 그대로 실을 수 있다. Redis 장애가 인증까지 번지지 않아
      INV-10의 fail-closed 범위를 새 매칭으로 한정할 수 있다.
    - 영향: refresh rotation을 필수로 한다. 정지/삭제 계정 즉시 차단은 stateless로
      불가능하므로 Redis denylist를 두고, denylist 조회 실패는 fail-closed 처리한다.
      access token TTL은 짧게 잡아 무효화 지연을 줄인다.
17. DB는 database-per-service가 아니라 schema-per-service로 나눈다. PostgreSQL
    인스턴스 1개에 `account` `gameconfig` `matching` `reservation` `party` `social`
    `shared_read` 7스키마를 둔다. (2026-08-31, `shared_read`는 #19에서 추가)
    - 근거: 인프라 비용 제약. DB 인스턴스를 서비스 수만큼 띄우는 비용을 감당하지 않으면서
      소유 경계는 유지할 수 있는 절충안이다. 테이블을 복제하지 않고 기존 테이블을
      스키마에 배정하는 것뿐이다.
    - 영향: 크로스 스키마 FK 금지, 크로스 스키마 JOIN 금지. 스키마별 DB 롤로 권한을
      격리해 각 앱은 자기 모듈 롤로만 접속한다. Flyway 마이그레이션은
      `db/migration/<schema>/`로 분리해 모듈 오너만 자기 폴더를 수정한다.
      물리적으로 한 DB를 공유하므로 스키마 변경 협의 비용과 개발 시점 결합은 감수한다.
18. ~~모듈 간 통신은 transactional outbox + Redis Streams로 한다.~~ (2026-08-31)
    **개정됨 → #21.** transactional outbox는 유지하되 전송 경로는 Redis Streams가 아니라
    SQS FIFO다. 아래 근거 중 "Redis는 이미 스택에 있다" 부분은 순서 보장 요구를
    과소평가한 판단이었다. 적용 항목(어떤 이벤트가 누구에게 가는지)은 #21에서 그대로 이어진다.
    - 근거: Kafka/RabbitMQ 금지 제약 아래에서도 비동기 통신이 필요하고, Redis는 이미
      스택에 있으므로 Streams 사용은 새 인프라 추가가 아니다. 같은 DB를 쓰므로 outbox
      테이블을 각 스키마에 두면 상태 변경과 이벤트 발행의 원자성을 확보할 수 있다.
    - 영향: 전달 보장이 at-least-once이므로 소비자는 반드시 멱등해야 한다. Redis가
      큐/이벤트버스/차단캐시 3역할을 겸하므로 Redis 장애 시 fail-closed 범위가 넓어진다
      (INV-10). 소비자는 consumer group으로 읽고 pending/재처리 경로를 갖춰야 한다.
    - 적용:
      - INV-6: `social`이 block 이벤트 발행 → `matching`이 Redis read model
        (`qm:block:{userId}` Set) 유지 → hot path에서 O(1) 조회.
        크로스 스키마 JOIN이나 동기 REST 호출로 풀지 않는다.
      - proposal 확정 → party 생성: `matching`이 `ProposalConfirmed` 발행 →
        `party`가 소비해 파티 생성.
      - recent_players: `party`가 `PartyClosed`(멤버 목록 포함) 발행 →
        `social`이 자기 `social.recent_players` 테이블 구축.
19. INV-6은 2단계로 보장한다. 후보 필터링은 Redis read model로, 최종 claim 직전 재검증은
    `shared_read.blocked_pairs` 뷰 동기 조회로 한다. (2026-08-31, #18의 INV-6 적용을 보강)
    - 근거: 이벤트 기반 read model에는 `social`의 block 저장 시점과 `matching`의 Redis 반영
      시점 사이에 전파 지연 창이 있고, 그 창에서 claim이 통과하면 INV-6이 깨진다. 불변식은
      확률적 보장이 허용되지 않으므로 결정적 검증 지점이 하나 필요하다. REST 재검증은
      검증과 claim 사이에 창이 다시 생겨 문제를 풀지 못하고, 뷰는 atomic claim과 같은
      트랜잭션 안에서 읽을 수 있다. 파티 확정은 초당 수 회라 동기 읽기 비용도 무시할 만하다.
    - 영향: `shared_read` 스키마를 신설하고 `social`이 소유하는 read-only 뷰
      `blocked_pairs`(양방향 정규화)를 둔다. `matching` 롤에는 이 뷰의 SELECT만 부여하고
      `social` 스키마 자체 권한은 주지 않는다. 최종 claim 로직이 Redis 단독에서
      DB 재검증 → Redis Lua atomic claim의 2단계로 바뀐다. 크로스 스키마 JOIN 금지의
      유일한 승인 예외이며, 훗날 DB를 분리할 때 이 뷰가 추출 지점을 표시한다.

20. 배포는 3단계 로드맵으로 간다. Stage 1 단일 EC2 + Docker Compose(현재) →
    Stage 2 ECS Fargate 4서비스 → Stage 3 직접 구축 k8s. (2026-08-31)
    - 근거: 배포 단위를 4개로 나눈 이유(#15)는 Stage 2부터 값을 하는데, 지금 규모에서
      관리형 오케스트레이터를 먼저 켜면 비용만 앞당겨 낸다. 반대로 마지막을 EKS가 아니라
      직접 구축 k8s로 두는 이유는 컨트롤 플레인 학습이 목적이기 때문이다 (#26).

      | Stage | 구성 | 월 |
      |---|---|---|
      | 1 (현재) | 단일 EC2 t4g.large + Docker Compose. PostgreSQL·Redis도 컨테이너. Caddy로 TLS+라우팅 | ~$73 |
      | 2 | ECS Fargate 4서비스. RDS + ElastiCache 강제(Fargate에 영속 스토리지 없음). ALB. ARM(Graviton) 권고. public subnet + SG 차단으로 NAT $43 회피 권고. Cloud Map | ~$169 |
      | 3 | 직접 구축 k8s(k3s로 시작 → kubeadm). EKS 아님 | ~$85 |

    - Stage 2에서 Service Connect가 아니라 Cloud Map을 쓴다. Service Connect는 Envoy
      사이드카를 주입하는데, Cloud Map은 DNS 기반이라 Stage 3의 k8s Service와 1:1로 대응한다.
    - **단계가 바뀌어도 안 바뀌는 것**: 모듈 경계 / 7스키마 + DB 롤 / outbox + SQS 계약 /
      INV 검증 / 배포 단위당 컨테이너 이미지 1개 / 환경변수 기반 설정 /
      `/health/live`·`/health/ready` 분리 / SIGTERM graceful shutdown /
      프로세스 로컬 상태 금지 / stdout JSON 로그 / `/actuator/prometheus` 노출.
    - 이행 트리거: Stage 1 → 2는 단일 호스트 리소스가 한계에 닿거나 무중단 배포가 필요해질 때.
      Stage 2 → 3은 k8s 운영 경험이 목표로 올라올 때. 트래픽이 아니라 필요가 트리거다.
    - 영향: **현재 기준은 Stage 1이다.** k8s 매니페스트/HPA/sticky session을 전제한 구현을
      지금 만들지 않는다. 운영 문서(docs/09)는 Stage 1을 본문으로, Stage 2/3을 표로 적는다.
21. 앱 간 도메인 이벤트는 transactional outbox + **SQS FIFO**로 전달한다.
    (2026-08-31, #18을 개정)
    - 큐 3개: `ProposalConfirmed.fifo` / `PartyClosed.fifo` / `BlockChanged.fifo`.
      각각 DLQ와 `maxReceiveCount`를 둔다.
    - `MessageGroupId`: block은 **정규화된 차단 쌍**(`min(id):max(id)`), 나머지는
      aggregate id(`proposalId` / `partyId`).
    - `MessageDeduplicationId`에 outbox id를 넣어 relay 재발행의 중복을 5분 창에서 억제한다.
      그래도 소비자 멱등성 요구는 유지한다.
    - 근거: Redis Streams는 컨슈머 그룹으로 읽는 순간 순서가 깨져 `BlockCreated`와
      `BlockRemoved`의 역전이 가능하다. 차단이 해제로 뒤집히면 INV-6이 깨진다.
      FIFO는 `MessageGroupId` 단위 순서를 보장하고, 재시도·DLQ·적체 알람을 코드가 아니라
      설정으로 얻는다. 서울 리전 FIFO는 100만 요청당 $0.50이고 월 100만 건이 무료라
      이 규모에서 실질 비용은 $0이다.
    - `CLAUDE.md` §3의 Kafka/RabbitMQ 금지는 유지한다. 그 취지가 "브로커를 직접 운영하지
      마라"이므로 완전관리형 SQS는 예외다. 기존의 "Redis Streams는 예외" 문구는 삭제한다.
    - 적용(#18에서 이어짐):
      - INV-6: `social`이 `BlockChanged` 발행 → `matching`이 Redis read model
        (`qm:block:{userId}` Set) 유지 → hot path에서 O(1) 조회.
        크로스 스키마 JOIN이나 동기 REST 호출로 풀지 않는다.
      - proposal 확정 → party 생성: `matching`이 `ProposalConfirmed` 발행 →
        `party`가 소비해 파티 생성.
      - recent_players: `party`가 `PartyClosed`(멤버 목록 포함) 발행 →
        `social`이 자기 `social.recent_players` 테이블 구축.
    - 영향: Redis에서 이벤트버스 역할이 빠지므로 Redis 장애 반경이 좁아진다. Redis가
      죽어도 이벤트는 SQS가 재시도한다. 대신 SQS DLQ 적체 감시가 새 운영 항목이 된다.
22. 서버→클라 push는 `app:realtime`이 전담한다. 이벤트 14종은 SSE, `WEBRTC_SIGNAL`
    1종만 WebSocket이다. (2026-08-31, 기존 "WebSocket 15종" 방식을 개정)
    - SSE 엔드포인트는 `GET /api/v1/events`이고 `Last-Event-ID`로 재개한다.
      WebSocket은 `/ws`를 유지하되 `WEBRTC_SIGNAL`만 실어 나른다.
    - 근거: 14종은 전부 서버→클라 단방향이다. 양방향 프로토콜이 필요한 것은 클라→서버가
      있는 signaling 하나뿐이다. SSE는 HTTP라 재연결·재개가 프로토콜에 들어 있어
      직접 만들 코드가 줄어든다.
    - 전달 경로: 발행 앱 → **Redis Pub/Sub** → `app:realtime`(연결을 들고 있는 인스턴스가
      로컬에서 userId로 필터) → SSE. 브로드캐스트 + 로컬 필터이므로 **sticky session이 필요 없다.**
    - 알림은 휘발성이라 SQS로 옮기지 않는다. "내구성 있는 도메인 이벤트(SQS, #21)"와
      "휘발성 실시간 알림(Pub/Sub)"은 다른 도구다.
    - 운영 제약: ALB `idle timeout` 300초, SSE heartbeat 15~30초 필수.
    - 영향: `contracts/events.md`가 전송 2종으로 갈라진다. 이벤트별 발행 주체를 계약에 적는다.
23. 예약 매칭은 1분 주기 배치 단일 경로로만 실행한다. 최소 리드타임은 30분이다. (2026-08-31)
    - "등록 시 즉시 candidate scan"을 제거한다. 등록 경로와 배치 경로 두 개를 유지하면
      같은 매칭 로직에 진입점이 둘이 되어 race와 테스트 조합이 2배가 되는데, 예약은
      애초에 즉시성이 요구되지 않는 기능이라 그 복잡도를 살 이유가 없다.
    - 슬롯 시작까지 30분 미만이면 등록을 `400`으로 거부한다. 그 이하 리드타임은 실시간
      매칭이 담당한다. 배치 주기가 1분이므로 리드타임이 짧으면 매칭 성사 자체를 보장할 수 없다.
    - 시간 기반 tier 완화: 슬롯까지 2시간 초과 → Tier 0만 / 2시간~30분 → Tier 1까지 /
      30분 이내 → Tier 2까지. 남은 시간이 줄수록 성사를 우선한다.
    - 영향: `queuemate.reservation.sweep-ms: 30000`이 `batch.interval-ms: 60000` +
      `batch.min-lead-minutes: 30`으로 바뀐다. `qm:reservation:slot:*` Redis 슬롯 인덱스는
      배치가 콜드 패스이므로 제거 후보다 (D7).
24. 예약 배치를 `app:reservation-batch`라는 4번째 배포 단위로 분리한다. 상시 1개만 띄우고
    스케일아웃하지 않는다. (2026-08-31)
    - 근거: 배치는 스케일 축이 없다. 늘려도 처리량이 늘지 않고 같은 예약을 두 인스턴스가
      집는 헛일만 는다. 반면 `app:matching`은 큐 depth로 늘려야 하므로, 배치를 거기 두면
      "늘리면 안 되는 것"과 "늘려야 하는 것"이 한 프로세스에 묶인다.
    - 실행 모델: `runOnce()` 코어 + `BATCH_MODE=daemon|oneshot` 두 진입점.
      oneshot은 종료코드 0/1 규약을 따른다. 나중에 CronJob/RunTask로 전환해도 코드 변경 0이다.
    - `qm:lock:reservation-sweep:{game}:{mode}`는 전역 `qm:lock:reservation-batch` 한 줄로
      줄인다. 단일 인스턴스이므로 실행이 겹쳐도 atomic claim이 INV-2/INV-7을 보장한다.
      겹침은 정합성 사고가 아니라 헛일일 뿐이다.
    - `RESERVATION_*` 이벤트의 발행 주체는 `app:reservation-batch`다.
    - 영향: 예약 REST(`/api/v1/reservations` CRUD + INV-9 검증)는 `app:platform`이 서빙한다.
      `module:reservation`은 라이브러리이므로 `app:platform`과 `app:reservation-batch`가
      함께 의존하고, `reservation` 스키마 롤도 두 앱 모두에 부여한다.
    - 영향: 배치가 안 도는 것이 조용한 장애가 되므로 deadman 감시
      (`reservation_batch_last_success_epoch_seconds`)를 필수 운영 항목으로 둔다.
25. TURN은 Cloudflare 관리형을 사용한다. coturn 전용 EC2를 두지 않는다. (2026-08-31)
    - 근거: 데모 규모에서 Cloudflare TURN은 월 $0(무료 1,000GB)이고 coturn 전용 EC2는
      트래픽과 무관하게 월 $23 고정이다. 포트 개방·인증서·남용 방지 운영 부담도 사라진다.
      Fargate에는 host network가 없어 Stage 2에서는 coturn을 올릴 수도 없다.
    - 단기 credential 발급 주체는 `app:realtime`이다 (docs/13).
    - `infra/coturn/`은 **로컬 개발용으로만 유지한다.** 현재 고정 credential이므로
      공개 배포에 쓰지 않는다.
    - Stage 3(직접 구축 k8s)에서는 `hostNetwork` DaemonSet으로 자체 운영 복귀가 가능하다.
      그때 재검토한다.
    - 음성/텍스트는 브라우저 직결이며 서버를 경유하지 않는다는 원칙은 그대로다.
26. 아래 대안을 기각한다. (2026-08-31)
    - **API Gateway 앱**: Ingress가 이미 단일 진입점이고 BFF 조합 엔드포인트가 0개다.
      횡단 관심사는 `module:common`이 컴파일 타임에 더 강하게 보장한다. 홉과 SPOF만 늘린다.
    - **`app:party` 분리**: account/social과 스케일 방식·재시작 비용·배포 주기가 전부 같다.
      파티는 엔드포인트 2개로 셋 중 가장 작다. (트래픽 확보 후 재검토 — D6)
    - **파티/배치를 요청 시 기동**: 요청 구동이라 Fargate 시작 30~90초가 매칭 성사 직후에
      걸린다. 절약액은 월 $10~15뿐이다.
    - **Lambda**: Stage 3가 직접 구축 k8s인데 Lambda는 컨테이너가 아니라 이행이 불가능하고,
      "배포 단위 = 컨테이너 이미지 1개" 원칙(#20)과 충돌한다. Spring Boot 콜드스타트와
      DB 커넥션 폭발 문제도 있다.
    - **EventBridge → RunTask 1분 배치**: Fargate는 이미지 pull부터 과금하고 최소 1분을
      부과한다. 시작에 30~90초가 걸려 월 과금이 1,080시간이 되는데 상시 실행은 730시간이다.
      상시보다 비싸다.
    - **EKS**: 컨트롤 플레인을 감춰 학습 목적에 맞지 않고 월 $73이 더 든다.
    - **Redis Streams**: 컨슈머 그룹 사용 시 순서를 보장하지 않는다 (#21).
27. 실시간 매칭의 **진행 중 상태는 Redis에만** 두고 **확정된 것만 DB에 쓴다.**
    `match_requests` 테이블을 만들지 않는다. (2026-09-01, docs/14 §17·§19)

    | 무엇 | 어디 |
    |---|---|
    | 게임 모드 설정 | Redis 캐시 (DB가 원본, 미스 시 로드, 변경 시 키 삭제로 즉시 반영) |
    | 매칭 요청 | Redis만 — `qm:request:{id}` HASH + 큐 ZSET |
    | 진행 중 proposal | Redis — `qm:proposal:{id}` HASH + TTL |
    | 수락 집계 | Redis HASH + Lua (#28) |
    | 확정된 매칭 | DB — `match_proposals` + `proposal_members` + `outbox`, **단일 트랜잭션** |

    - 근거: 대기 중 요청은 몇 초~몇 분 살다 사라지는 상태라 영속 기록 가치가 낮은데,
      DB에도 두면 요청 접수·큐 이동·거절 복귀마다 write가 붙고 두 저장소가 어긋날 지점만
      늘어난다. 원자성이 정말 필요한 지점은 **확정 하나뿐**이고 그 하나는 이 방식에서도
      단일 DB 트랜잭션으로 지켜진다. INV-10이 이미 "Redis 장애 = 새 매칭 fail-closed"라
      Redis가 죽었을 때 DB로 매칭을 이어갈 계획 자체가 없다. 그렇다면 복제할 이유도 없다.
      게임 모드 설정을 앱 메모리가 아니라 Redis 캐시로 두는 것도 같은 이유다.
      운영자가 mode를 닫으려는데 전 인스턴스 재배포가 필요하면 피처 플래그가 의미를 잃는다.
    - 기각한 대안: **전부 DB(`match_requests` 테이블)**. 대기 중 요청을 DB에 복제해도
      INV-10 때문에 그것으로 매칭을 이어갈 수 없다. 이중 기록 비용만 남고 얻는 것이 없다.
    - 영향: `match_requests` 테이블 삭제. `proposal_members.acceptance` 컬럼 삭제
      (확정된 것만 들어오므로 항상 `ACCEPTED`). `proposal_members.source_request_id`는
      Redis-only id라 FK를 걸 수 없다. **`match_proposals.condition_snapshot_json` 신설** —
      요청이 사라져도 "어떤 조건으로 확정된 매칭인지"를 남기기 위해서다 (docs/06).
    - 영향: 확정 구간이 두 저장소에 걸친다. **DB 먼저 쓰고 Redis를 정리하는 순서**로 완화한다.
    - 영향: 매칭 "시도" 지표는 DB 집계가 불가능하다. Prometheus 메트릭으로만 남는다.
28. proposal 수락 집계는 **Redis HASH + Lua 스크립트**로 한다. (2026-09-01, docs/14 §18)
    - `qm:proposal:members:{proposalId}`를 SET에서 **HASH**(`userId -> PENDING|ACCEPTED|DECLINED`)로
      바꾼다. 참가자 목록과 수락 상태를 같은 키에 둔다.
    - Lua 1회 실행에서 제안 생존 확인(INV-5) + 참가자 확인 + 중복 수락 차단(INV-4) +
      전원 판정 + 상태 전이를 모두 처리한다. `GET → 애플리케이션 판단 → SET`으로 만들지 않는다.
    - 근거: DB 집계는 수락 5건에 트랜잭션 5번이지만 이 방식은 전원 수락 시점에 1번만 친다.
      atomic claim에도 이미 Lua를 쓰므로 방식이 통일된다.
    - 기각한 대안: **Redis `INCR` 카운터** — 중복 수락을 못 막아 5명 중 3명만 눌러도
      확정될 수 있다(INV-4 위반). **`SADD` + `SCARD`** — 중복은 막지만 "아직 안 누름"과
      "거절함"을 구분할 수 없다. **DB 매 수락 집계** — 트랜잭션 낭비.
    - 영향: 수락 집계 전용 키를 따로 두지 않는다. 두 키로 갈리면 서로 어긋날 수 있고,
      하나의 HASH여야 Lua 한 번으로 전원 판정까지 끝난다 (docs/07 §5-1).
    - 영향: 진행 중 수락 상태를 REST로 내려줄 때 DB가 아니라 Redis에서 읽는다.
29. **Redis 장애 시 매칭 큐 재구축 불가를 감수한다.** (2026-09-01, #27의 귀결)
    - 근거: 재구축의 입력이 될 데이터가 DB에 없다(#27). 남길 수 있는 방법은 결국 요청을
      DB에 복제하는 것뿐이고, 그건 #27에서 기각한 안이다. 진행 중 매칭은 수명이 수 분이라
      유실 비용이 낮고, 사용자가 다시 요청하면 회복된다.
    - 기각한 대안: **queue rebuild admin operation**. 입력 데이터가 없어 구현 자체가 불가능하다.
      **부분 복원**(살아남은 키만으로 매칭 재개) — 어떤 요청이 유실됐는지 알 수 없어
      INV-1/INV-2 보장이 깨진다.
    - 영향: 장애 시 절차는 fail-closed 유지 → block read model 재적재 → 매칭 재개 →
      **사용자 재요청**이다. 서버가 대신 재등록하지 않는다 (docs/09 §6, docs/07 §10).
      확정된 파티는 DB + outbox에 있어 영향받지 않는다.
    - 영향: `docs/09`의 "active DB records로 queue rebuild job"과 `docs/07`의
      "DB의 active request로 queue 재구성하는 admin operation" 서술을 폐기한다.
    - 재검토 트리거: 매칭 대기 시간이 길어져 유실 비용이 커질 때. 그때는 요청 DB 복제가
      아니라 Redis 자체의 이중화(replica + failover)를 먼저 본다.
30. 실시간 매칭 **착수 범위를 축소한다**: LoL만 / Tier 0(조건 완전 일치)만 /
    차단 검증 제외 / 알림 payload 확정은 나중. (2026-09-01)
    - 근거: 매칭 엔진의 어려움은 게임 수나 tier 완화가 아니라 **Lua atomic claim과
      수락 집계의 동시성**에 있다. 그 한 축을 먼저 끝내야 나머지가 얹힌다.
      게임 3종·tier 완화·INV-6 2단계 검증은 전부 이 축 위에 붙는 확장이다.
    - 기각한 대안: **전 범위 동시 착수**. 동시성 버그와 조건 매칭 버그가 섞이면
      원인 분리가 안 된다.
    - 영향: 착수 순서는 멀티모듈 전환·인증 → Redis 요청 접수 3종 → Lua atomic claim →
      스캔/조합 → Lua 수락 집계 + 만료 sweeper → Flyway 3테이블 + 확정 트랜잭션이다.
      **5단계까지 DB가 등장하지 않는다** (docs/15 §7).
    - 영향: 축소는 **착수 범위**일 뿐 제품 범위가 아니다. INV-6은 유예가 아니라
      순서상 뒤에 오는 것이며, 차단 검증 없이 배포하지 않는다.

31. **파티 인원이 가변인 게임 모드는 인원을 사용자 조건으로 추가하지 않고 modeKey를
    인원별로 쪼갠다.** (2026-09-01)
    - 배경: docs/02 §6은 party target size를 derived condition(사용자가 안 고르고
      시스템이 계산)으로 분류한다. 전제는 "모드가 정해지면 인원도 정해진다"였는데,
      LoL 자유 랭크처럼 파티 인원이 여러 값으로 가변인 모드가 이 전제를 깬다.
    - 결정: modeKey를 인원별로 나눈다 (예: 자유 랭크 2인/3인/5인을 서로 다른 modeKey로
      표현). 사용자 화면에는 인원별로 별개 모드로 보이지만, 시스템 입장에서는
      "모드 하나 = 인원 하나"가 유지되어 party target size는 여전히 derived condition이고
      **사용자 조건 개수는 4개 그대로다.** 게임이 금지하는 인원은 해당 modeKey를 아예
      만들지 않는 것으로 배제된다. 파티 인원 1인 모드는 만들지 않는다(팀원을 붙여주는
      서비스이므로 혼자인 파티는 의미가 없다).
    - 기각한 대안: **인원수를 5번째 사용자 조건으로 추가**. 조건 증가는 candidate pool을
      파편화시키고 docs/12 elbow 절차 대상이 된다.
    - 영향: docs/02 §3(LoL mode config 예), §6(derived conditions)에 반영. 지원할
      구체 모드 목록은 아직 확정이 아니다.

32. 실시간 매칭의 대기 상태를 **"큐에 줄 선 사람"이 아니라 "아직 안 찬 파티"로
    표현한다.** (2026-09-03)
    - 배경: 원래 설계는 `qm:queue:{game}:{mode}` ZSET에 사용자를 줄 세우고, 스캔이
      짝을 찾아 파티를 만드는 방식이었다.
    - 결정: 큐를 없애고 파티를 그릇으로 둔다. 대기 중인 혼자는 **1인 파티**다.
      사용자는 파티에 들어가거나, 들어갈 파티가 없으면 새로 만든다.
    - 근거: 정원이 3명 이상인 모드에서 큐 방식은 "후보 N-1명을 모아 한 번에 claim,
      실패하면 전체 롤백"이 되어 부분 확정 상태와 롤백 경로를 다뤄야 한다. 파티를
      그릇으로 두면 **한 번에 한 명씩** 넣으면 되므로 롤백할 중간 상태가 생기지 않는다.
    - 기각한 대안: **큐 유지 + N명 동시 claim**. 2인 모드에서는 동등하지만 5인 모드에서
      복잡도가 급증한다.
    - 영향: `qm:queue:*` 키를 제거한다. `claim-request.lua`에서 ZADD가 빠지고,
      활성 요청 선점(INV-1)만 담당한다. `MatchRequestService.enqueue()`는
      `join()`으로 개명한다.

33. **후보 탐색을 없앤다. 파티를 "아직 필요한 keyValue"로 역색인한다.** (2026-09-03)
    - 결정: `qm:party:open:{game}:{mode}:{voice}:{purpose}:needs:{keyValue}` ZSET에
      그 값을 아직 못 채운 파티만 담는다. 조건은 키 이름에 넣어 같은 색인에 있으면
      조건이 같음이 보장된다.
    - 근거: 조건별로 큐를 나눠도 파티를 순회하며 비교하면 O(N)이다. 특히 같은 포지션
      사용자가 몰리면 새로 온 사람이 앞의 파티를 전부 실패시키고 뒤까지 간다. 색인을
      뒤집으면 **내 값이 비어 있는 파티만** 모여 있어 맨 앞 하나를 꺼내면 끝이다(O(1)).
      검색을 최적화한 것이 아니라 검색이 필요 없는 구조로 바꾼 것이다.
    - 기각한 대안: (a) **자바에서 파티 목록을 가져와 비교** — 읽은 정보가 낡아 Lua가
      어차피 재확인해야 하므로 헛수고다. (b) **Lua 안에서 파티를 순회** — 후보 수에
      상한이 없으면 Redis 전체가 블로킹된다. 특히 쓰기를 한 스크립트는 `SCRIPT KILL`이
      불가능해 `SHUTDOWN NOSAVE`만 남는다(redis.io Programmability).
    - 영향: 왕복이 후보 수와 무관하게 고정된다. 색인 갱신은 삽입과 같은 원자 단위 안에서
      일어나므로 색인이 항상 정확하고, **claim 재시도 루프가 사라진다.**

34. **파티 찾기와 만들기를 하나의 Lua로 합친다.** (2026-09-03)
    - 결정: `join-or-create-party.lua` 하나가 "색인 조회 → 있으면 합류, 없으면 생성 →
      색인 갱신 → active-request에 partyId 기록"을 한 덩어리로 수행한다. 반환은
      `{코드, partyId, 현재인원}`이며 코드는 1=생성, 2=합류, 3=합류 후 정원 참,
      -1=설정과 맞지 않는 keyValue다.
    - 근거: 나누면 "파티가 있다"는 응답과 실제 합류 사이에 마지막 자리가 차버리는 틈이
      생기고, 그때 다시 생성 경로로 돌아가는 왕복이 반복된다. 합치면 그 틈이 없다.
      #33의 색인 덕분에 스크립트 안에 순회 루프가 없어 블로킹 시간도 짧다.
    - 기각한 대안: **create와 join을 별도 스크립트로 유지**. 1인 파티 중복 생성은 정합성
      문제가 아니라는 이유로 한때 채택했으나, 색인 도입으로 순회가 사라지면서 근거가
      없어졌다.
    - 영향: 코드 3(정원 참)이 제안(proposal) 생성 트리거가 된다.

35. 파티 참가자를 파티 HASH 안에 **`member:{userId} = keyValue`로 기록한다.**
    (2026-09-03)
    - 결정: 별도 키(`qm:party:{id}:positions`) 없이 `qm:party:{partyId}` HASH 하나에
      메타데이터(`partyId`/`size`/`target`/`createdAt`)와 참가자를 함께 담는다.
    - 근거: `HGETALL` 한 번으로 파티 정보·참가자 목록·각자의 keyValue가 모두 나온다.
      파티룸 화면도 이 한 번의 조회로 그릴 수 있다.
    - 기각한 대안: **keyValue를 필드로 쓰기**(`TOP = userId`). `positionUniqueness=false`인
      모드(칼바람)는 여러 참가자가 같은 keyValue를 가지므로 서로를 덮어쓴다. 방향을 뒤집어
      userId를 필드로 써야 항상 유일하다.
    - 영향: 필드 이름 충돌을 막기 위해 참가자 필드에 `member:` 접두사를 붙인다.
    - 알려진 함정: 합류 시 색인에서 파티를 빼는 동작은 `positionUniqueness=true`일 때만
      옳다. 중복을 허용하는 모드는 정원이 찰 때까지 같은 값을 더 받을 수 있으므로 그대로
      둬야 한다. 이 조건을 빠뜨려 칼바람 5인 파티가 2/2/1로 쪼개지는 버그가 실제로
      발생했다.

## Deferred decisions
아직 결정하지 않는다. 지금 정하면 근거 없이 정하는 것이 되는 항목들이다.
번호 체계를 Fixed decisions와 분리한다.

- **D1. k8s 도입 시점과 배포판(k3s → kubeadm 전환 시점 포함)**
  - 재검토 트리거: Stage 2가 안정 운영에 들어가고 k8s 운영 경험이 목표 우선순위로 올라올 때.
- **D2. Stage 2 VPC 구성 (public subnet + SG 차단 vs private subnet + NAT)**
  - 재검토 트리거: Fargate 서비스를 실제로 올릴 때. NAT 월 $43을 감당할 근거가 생기거나
    컴플라이언스 요구가 붙으면 private subnet으로 간다.
- **D3. Stage 3에서 PostgreSQL/Redis를 클러스터 안에 둘지 관리형(RDS/ElastiCache)에 남길지**
  - 재검토 트리거: Stage 3 착수 시점. 스테이트풀 워크로드를 직접 운영할 의사가 있는지로 갈린다.
- **D4. Fargate Spot 적용 범위**
  - 재검토 트리거: Stage 2 비용이 예산을 넘을 때. 중단 내성이 있는 것부터
    (`app:reservation-batch`가 후보) 검토한다.
- **D5. 배치 주기 재조정 (1분이 맞는지)**
  - 재검토 트리거: 예약 매칭 성사까지 걸린 시간과 배치 1회 실행 시간의 실측이 쌓였을 때.
- **D6. `app:party` 재분리**
  - 재검토 트리거: 파티 트래픽이 확보되어 `app:platform`과 스케일 축이 실제로 갈릴 때
    (#26의 기각 근거가 무너지는 시점).
- **D7. `qm:reservation:slot:*` Redis 슬롯 인덱스 제거**
  - 재검토 트리거: 배치가 인덱스 걸린 DB 쿼리만으로 목표 시간 안에 도는지 실측했을 때.
    돌면 제거한다 (#23).

---

## queueMate 본 저장소에서 병합한 결정 (feature/frontend 브랜치)

병합 작업일: 2026-09-06.
출처: 저장소 `queueMate`, 브랜치 `feature/frontend`, 파일 `docs/11_DECISION_LOG.md`
(367줄, Fixed #1~#35 + Deferred D1~D7).

### 먼저 확인한 사실

**출처 파일과 이 파일은 바이트 단위로 동일하다.** (`diff` 결과 차이 0)
즉 이 파일은 이미 `feature/frontend` 시점의 결정 로그 전체를 담고 있고, 병합으로 새로
들여올 본문이 없다. 그래서 결정 본문을 다시 복사하지 않고, **매칭 엔진에 걸리는 결정이
어느 번호인지만 아래에 색인**한다. 기존 항목은 하나도 지우거나 고치지 않았다.

**충돌 없음.** 출처와 내용이 같으므로 기존 항목과 어긋나는 병합 항목이 없다.
(출처 파일 안에서 이미 표시된 개정 관계 — #7 → #22, #18 → #21, #15의 3개 → 4개 재개정,
#32가 #5의 큐 ZSET 전제를 대체 — 는 원문 그대로 유지된다.)

### 매칭 엔진에 걸리는 결정 색인

| # | 요지 | 매칭에 왜 걸리나 |
|---|---|---|
| #5 | Redis를 active matchmaking/lock/index에 쓴다 | 엔진의 저장소 전제. 단 "큐"라는 형태는 #32에서 파티 그릇으로 대체됐다 |
| #8 | 지원 게임은 LoL / VALORANT / PUBG | 조건 스키마의 상한. 본 MVP는 #30에 따라 LoL만 |
| #9 | 매칭은 상대팀이 아니라 같은 파티 팀원 구성 | 제품 경계. proposal은 하나의 party만 의미한다 |
| #15 | 배포 단위 4개 중 `app:matching` = matching + gameconfig, 스케일 기준 = Redis 큐 depth | 이 저장소가 떼어낸 박스가 바로 그것이다 |
| #17 | schema-per-service 7스키마 + 스키마별 DB 롤 | `matching` 롤은 `social` 스키마를 못 읽는다. 크로스 스키마 JOIN 금지 |
| #19 | INV-6 2단계 보장 (Redis 필터 → `shared_read.blocked_pairs` 동기 SELECT) | **이 저장소에서 뒤집었다 — 아래 D-1 참고.** 뷰 대신 `social.blocks`를 직접 읽고, Redis 선필터는 보류했다 |
| #21 | 앱 간 도메인 이벤트는 outbox + SQS FIFO | `matching`이 `ProposalConfirmed.fifo` 발행 / `BlockChanged.fifo` 소비 |
| #26 | 대안 기각 목록 중 **Redis Streams** | 컨슈머 그룹으로 읽으면 순서가 깨져 INV-6이 뒤집힌다 |
| #27 | 진행 중 상태는 Redis에만, 확정된 것만 DB. `match_requests` 테이블 없음 | 이 저장소 전체의 전제 |
| #28 | 수락 집계는 Redis HASH + Lua 원자 실행 | INV-4/INV-5를 Lua 한 번에 판정한다 |
| #29 | Redis 장애 시 큐 재구축 불가를 감수 (INV-10) | fail-closed. 부분 복원 금지 |
| #30 | 착수 범위 축소 — LoL만 / Tier 0만 / 차단 검증 제외 | 본 MVP의 현재 구현 범위를 정한 결정 |
| #31 | 파티 인원이 가변인 모드는 modeKey를 인원별로 쪼갠다 | `targetPartySize`가 derived condition으로 유지된다 (`GAME_CONFIG.md`) |
| #32 | 대기 상태를 "큐에 줄 선 사람"이 아니라 **"아직 안 찬 파티"** 로 표현 | `qm:queue:*` 제거. INV-1 선점만 남았고, 그건 `claim-request.lua` 가 한다. (`HSETNX` 한 명령으로 내렸다가 **되돌렸다 — 아래 D-5 참고**) |
| #33 | 후보 탐색을 없애고 파티를 "아직 필요한 keyValue"로 역색인 | `qm:party:open:{game}:{mode}:{voice}:{purpose}:needs:{keyValue}` ZSET |
| #34 | 파티 찾기와 만들기를 하나의 Lua로 합친다 | `join-or-create-party.lua`. 반환 코드 3(정원 참)이 proposal 트리거. **이 저장소에서 `create-or-check-party-untiered.lua`로 개명했다 — 아래 D-6 참고** |
| #35 | 참가자를 파티 HASH 안에 `member:{userId} = keyValue`로 기록 | `HGETALL` 한 번으로 파티 전체를 읽는다 |
| D7 | `qm:reservation:slot:*` 제거 후보 | 예약 인덱스라 실시간 매칭에는 직접 영향 없음 |

### 매칭 범위 밖이라 색인하지 않은 결정

#1~#4, #6, #7, #10~#14, #16, #18, #20, #22~#25, D1~D6.
(프론트엔드, 인증/JWT, 예약 REST·배치, SSE/WebSocket, WebRTC/TURN, 배포 로드맵, 파티/소셜
REST 등.) 본문은 위쪽에 그대로 남아 있으니 필요하면 그 자리에서 읽는다.

### 색인된 결정 중 이 저장소가 아직 구현하지 않은 것

결정과 코드가 어긋난다는 뜻이 아니라, #30(착수 범위 축소)에 따라 순서상 뒤에 오는 것들이다.

- **#19 INV-6 2단계 검증** — DB가 없어 `shared_read.blocked_pairs` 경로가 아예 없다.
  #30이 "차단 검증 제외"를 착수 범위로 명시했고, 동시에 "차단 검증 없이 배포하지 않는다"고
  못 박았다.
- **#21 outbox → SQS FIFO** — AWS SDK 의존성이 없다.
- **#27의 DB 쪽 절반** — `match_proposals` / `proposal_members` / `outbox` 테이블이 없다.
  Redis 쪽 절반(요청·진행 중 상태를 Redis에만 둔다)은 그대로 지켜진다.
- **#28 수락 집계 Lua** — `ProposalStatus` / `AcceptanceStatus` enum만 있고 이를 쓰는
  서비스와 Lua가 아직 없다.
- **#17 스키마/롤 격리** — DB가 없어 적용 대상이 없다.

---

## 복원 시점 관찰 기록 (2026-09-06)

문서 복원 작업 중 **실제 코드를 읽어 확인한 사실**만 적는다.
**기존 항목은 하나도 지우거나 고치지 않았다.** 새 결정을 내리는 절이 아니라,
결정 로그와 코드가 어긋난 지점을 남겨 두는 절이다. 해소되면 정식 결정 항목으로 올려라.

### O-1. `VoicePreference.OPTIONAL` 제거를 기록한 결정 항목이 이 로그에 없다

- 사실: `src/main/java/com/queuemate/matching/domain/VoicePreference.java`의 enum 값이
  **`REQUIRED, NO_VOICE` 둘뿐**이다. `OPTIONAL`이 제거돼 있다.
- 사실: 그 클래스 주석이 제거 근거로 **`(docs/11 #31)`** 을 인용한다.
- 사실: 그런데 이 로그의 **#31은 "파티 인원이 가변인 게임 모드는 modeKey를 인원별로
  쪼갠다"** 이고, OPTIONAL과 무관하다. 이 로그의 Fixed decisions는 **#35까지**다.
- 즉 코드가 가리키는 결정 항목이 이 로그에 존재하지 않는다. 결정 로그가 그 시점보다
  낡았거나(#36 이후가 유실), 코드 주석의 번호가 잘못된 것이다.
- 제거 자체의 근거는 코드 주석에 온전히 남아 있다 — "음성 여부 무관"은 매칭 시점에는
  제약이 아니지만 파티 성립 후 협상을 강요하므로, 매칭 전에 답이 정해지지 않는 조건은
  조건이 아니라는 것.
- 영향: `contracts/openapi.yaml`(원본, queueMate `feature/frontend`)은 아직
  `[REQUIRED, OPTIONAL, NO_VOICE]` 세 값이다. **코드가 맞고 계약이 낡았다.**
  `contracts/README.md` 불일치 표 #1 참고.
- 할 일: 이 제거를 정식 결정 항목(#36 등)으로 올리고, 계약 변경 커밋을 본 저장소에 낸다.

### O-2. `docs/02`의 tier / 조건 완화는 코드에 존재하지 않는다

- 사실: `tier`로 `src/`를 grep하면 **0건**이다.
- 이것은 누락이 아니라 **#30**("Tier 0 = 조건 완전 일치만")의 귀결이다.
- 현재 "완전 일치"는 알고리즘이 아니라 **#33의 Redis 키 분할**로 달성된다.
  조건(`game`/`modeKey`/`voice`/`purpose`/`keyValue`)이 전부 색인 키 이름에 들어가므로,
  조건이 다른 요청은 애초에 같은 후보 집합에 존재할 수 없다.
- 기록해 두는 이유: `docs/02_MATCH_CONDITION_SCHEMA.md`만 읽고 tier 코드를 찾으면
  "빠뜨렸다"고 오해하게 된다. **없는 게 맞다.**

### O-3. 매칭 결과를 클라이언트에게 알리는 경로가 하나도 없다

- 사실: `SseEmitter` / `WebSocketConfig` / `@MessageMapping` / `ServerSentEvent` 가
  `src/` 전체에 **0건**이다. Redis Pub/Sub publish 코드도 없다.
- 사실: `@GetMapping` 이 `src/` 전체에 **0건**이라 계약의
  `GET /api/v1/match-requests/{id}` 폴링 경로도 없다.
- 사실: `POST /api/v1/match-requests` 는 `201 {requestId, status:QUEUED}` 만 돌려주고
  파티 배정 결과를 기다리지 않는다(`MatchTrigger`가 `@Async`).
- 방증: `load-test/match_latency.py` 가 매칭 성사를 감지하려고 HTTP가 아니라
  **Redis를 직접 폴링**한다(`HGET qm:user:active-request:{uid} partyId` →
  `HGET qm:party:{partyId} size`). HTTP로 알 수 있었다면 그렇게 짤 이유가 없다.
- #22(SSE 14종)와 `contracts/events.md` 상 배달 주체는 `app:realtime`이 맞지만,
  그러려면 이 앱이 Redis Pub/Sub에 publish 는 해야 한다. 그 절반도 없다.
- 미결: 이 앱 안에서 폴링 `GET`으로 먼저 닫을지, 계약대로 publish 만 하고
  `app:realtime`을 기다릴지가 정해지지 않았다.

### O-4. proposal 이후 단계가 전부 미구현이다 (#30에 따른 순서상 상태)

- `rule/lol/LolCandidateRule.java`의 `canJoin()` 안, `create-or-check-party-untiered.lua`가
  반환 코드 `3`(합류 후 정원 참)을 돌려주는 자리에
  `// TODO: code == 3 이면 정원이 찼다. 제안(proposal)을 만들 차례.` 만 있다.
  **정원이 차도 아무 일이 일어나지 않는다.**
- `ProposalStatus` / `AcceptanceStatus` enum은 있으나 이를 쓰는 코드가 0건이다.
  따라서 **#28(수락 집계 Lua)이 아직 코드에 없고 INV-4/INV-5가 지켜질 자리가 없다.**
- `application.yaml`에 `queuemate.proposal.ttl-seconds`(20) /
  `queuemate.scan.interval-ms`(1000) / `queuemate.sweep.interval-ms`(1000) 설정 자리는
  이미 있으나 읽는 코드가 없다.
- 결정과 코드가 어긋난다는 뜻이 아니라 #30이 정한 착수 순서상 뒤에 오는 것들이다.

### O-5. 게임 3종 중 구현체는 LoL 하나뿐이며, 미구현 게임은 400으로 떨어진다

- 사실: `GameConditionValidator` 구현체는 `LolConditionValidator` 1개,
  `CandidateRule` 구현체는 `LolCandidateRule` 1개다.
  `rule/pubg`, `rule/valorant`, `validation/pubg`, `validation/valorant`,
  `domain/pubg`, `domain/valorant` 는 **빈 디렉터리**다.
- 동작: `game: "PUBG"` 요청은 `MatchConditionValidator`의 `orElseThrow` →
  `IllegalArgumentException("지원하지 않는 게임: PUBG")` →
  `GlobalExceptionHandler#handleIllegalArgument` → **400 `BAD_REQUEST`** 다.
  500으로 터지지는 않는다.
- 문제: **미구현 게임과 잘못된 조건이 같은 400으로 묶여** 클라이언트가 구분할 수 없다.
  `INVALID_MATCH_CONDITION`(조건 오류)과 다른 코드를 주는 편이 낫다. 게임을 늘릴 때
  이 지점을 함께 정리해라.
- 이 축소 자체는 **#30**이 정한 것이며 #8(지원 게임 3종)과 충돌하지 않는다.
  #8은 제품 경계, #30은 착수 범위다.

### O-6. 되돌리기 중 관찰된 시드 ↔ 구현 불일치 (관찰 후 해소됨)

- 이 문서 복원 작업 도중(2026-09-06 15:51) `src/`와 `seed/`의 파일 11개가 IntelliJ
  Local History 되돌리기로 한꺼번에 바뀌었다.
- **바뀌기 전** 상태: `seed/gameconfig.redis`에 PUBG 모드 2개(`DUO`/`SQUAD`) 시드와
  `PubgCandidateRule.uniqueness()`를 언급하는 주석이 있었고
  `ConcurrencyTestSupport`에 PUBG 헬퍼가 있었으나, `PubgCandidateRule` /
  `PubgConditionValidator` / `PubgPlayStyle` 은 **존재하지 않았다.**
  즉 시드와 테스트 지원 코드가 없는 구현을 전제하고 있었다.
- **바뀐 후(현재)** 상태: 시드와 테스트 헬퍼 모두 LoL 전용으로 돌아갔다.
  시드 마지막 줄이 `seed done: LoL 12 modes`이고 `docs/GAME_CONFIG.md`의
  "모드 12개"와 일치한다. **불일치는 현재 없다.**
- 교훈으로 남길 것: **게임을 추가할 때 시드 · 테스트 헬퍼 · 구현체를 같은 커밋에
  함께 넣는다.** 시드만 앞서 나가면 없는 게임이 설정상 존재하는 것처럼 보이고,
  요청이 들어와야 비로소 400으로 드러난다.

### O-7. 존재하지 않는 클래스를 가리키는 주석

- `rule/CandidateRule.java`의 javadoc이 "배관은 `AbstractCandidateRule`에 있다.
  새 게임은 거기서 추상 메서드 3개만 채운다"고 하는데, `AbstractCandidateRule`은
  `src/` 어디에도 **없다**.
- 게임을 늘릴 때 그 문장을 근거로 상위 클래스를 찾지 마라. 만들거나 주석을 고쳐야 한다.

### 관찰 방법

위 사실은 전부 `src/`·`seed/`를 직접 읽고 grep 해서 확인했다.
재확인 명령은 `START_HERE.md` §4.4에 모아 두었다.
코드를 고치기 전에 그 명령들을 다시 돌려 이 절이 아직 유효한지 확인해라.

---

## 이 저장소에서 내린 결정 (2026-09-07)

> 위 색인은 queueMate 본 저장소의 결정을 옮긴 것이다. 이 절은 **이 저장소에서 새로
> 내렸거나 원안을 뒤집은 결정**을 기록한다. 원안과 어긋나면 여기가 우선한다.
> 본 저장소와 합칠 때 정리가 필요하다.

### D-1. INV-6 검증을 뷰가 아니라 `social.blocks` 직접 조회로 한다 (#19 대체)

**결정.** `shared_read` 스키마와 `blocked_pairs` 뷰를 만들지 않는다.
`matching` 롤에 `social.blocks`의 SELECT 권한을 직접 준다.
스키마 분리와 크로스 스키마 금지는 유지하며, **예외는 이 테이블 하나뿐이다.**

**근거.**

- 뷰가 하는 일은 `LEAST`/`GREATEST` 방향 정규화 하나뿐인데, 조회를 양방향으로 쓰면
  그 정규화가 애초에 필요 없다. 층을 하나 더 유지할 값이 크지 않다.
- 정규화가 오히려 함정을 만든다. 뷰가 정하는 low/high 순서를 호출부가 흉내 내려 하면
  기준이 어긋나 있는 차단을 조용히 못 찾을 수 있다(`V1__init_schema.sql:247-248`이
  friendships에 대해 같은 경고를 남겼다). 방향을 그대로 두고 양쪽을 다 보는 쪽이
  틀릴 여지가 없다.
- 스키마 하나와 뷰 하나를 위해 마이그레이션·권한·문서를 유지할 비용이 남는다.

**영향.**

- `docs/WHY_POSTGRESQL.md` §3-1이 이 결정에 맞춰 다시 쓰였다.
- `CLAUDE.md` §3과 INV-6 행이 `social.blocks` 기준으로 바뀌었다.
- 조회 코드는 `block/Block.java`, `block/BlockRepository.java`. 두 쿼리 모두 방향을
  따지지 않는다.
- `social`의 테이블 구조에 결합된다. `blocks`의 컬럼이 바뀌면 이 저장소도 고쳐야 한다.
  뷰가 주던 "database view as API"와 DB 물리 분리 시의 추출 지점 표시는 없어진다.
  분리 시점이 실제로 오면 그때 뷰를 세우는 편이 낫다고 봤다.

### D-2. INV-6의 Redis 선필터를 보류한다 (#19의 1단계)

**결정.** `qm:block:{userId}` read model과 `BlockChanged.fifo` 소비를 지금 만들지 않는다.
파티 확정 직전 DB 조회 한 겹만 둔다.

**근거.**

- 선필터는 **정확성을 책임지지 않는다.** 최종 보증은 확정 직전 DB 조회다.
  정확성을 담당하지 않는 층을 먼저 만들 이유가 없다.
- 선필터가 막아 주는 것은 "차단된 두 사람이 같은 후보 풀에서 반복해서 매칭됐다가
  확정 직전에 깨지는 것"이다. 그것이 실제로 관측될 때 붙이면 된다.
- 지금 붙이려면 SQS·AWS SDK가 따라온다. 얻는 값에 비해 무겁다.

**주의할 점(붙일 때).**

- 차단이 0명인 사용자는 Redis SET이 만들어지지 않아 "캐시 안 됨"과 구분되지 않는다.
  대부분의 사용자가 여기 해당하므로 표시 방법이 따로 필요하다.
- 캐시에는 TTL이 있어야 한다. 없으면 이후 생긴 차단이 영영 반영되지 않는다.
- 확정 직전 검증에는 **캐시를 쓰지 않는다.** 거기가 마지막 보루다.

### D-3. 차단 조회를 위해 DB 의존성을 들였다

**결정.** `spring-boot-starter-data-jpa` + H2(기본) / PostgreSQL 드라이버를 추가한다.
기본 datasource는 H2 인메모리이며 `DB_URL`로 PostgreSQL을 가리킬 수 있다.

**영향.**

- `CLAUDE.md` §3의 "DB 의존성이 아예 없다"가 더 이상 사실이 아니어서 수정했다.
  **진행 중인 매칭 상태는 여전히 Redis가 원본이다.** DB는 차단 조회에만 쓴다.
- JPA가 붙은 뒤로는 datasource 설정 없이 앱이 뜨지 않는다.
  `START_HERE.md`의 "컨텍스트 테스트는 Redis 없이도 통과한다"는 서술이 낡았다.
- `ddl-auto: none`이다. 스키마는 Flyway가 만들어야 하는데 아직 도입하지 않았다.
  그래서 `BlockRepository`를 부르면 지금은 실패한다.
- H2로는 스키마별 롤·GRANT 격리를 재현할 수 없다. 권한 경계는 PostgreSQL에서만
  실제로 검증된다.

### D-4. 차단 엔티티의 키는 일련번호, 사용자 id는 `String`으로 둔다

**결정.** `Block` 은 `@GeneratedValue(IDENTITY)` 를 붙인 `Long id` 를 PK로 갖는다.
`blockerId` / `blockedId` 는 `String` 이다. 복합 키(`@IdClass`)와 그 키 클래스는 없앴다.

**근거.**

- **사용자 id 타입을 `String` 으로 통일한다.** 이 저장소가 요청에서 받는
  `CreateMatchRequestCommand.userId` 가 `String` 이므로 조회 파라미터도 같은 타입이어야
  변환 지점이 생기지 않는다.
- **`app:platform` 이 `social.blocks` 를 설계할 때 일련번호 PK를 쓴다는 전제**다.
  그러면 `@IdClass` · 키 클래스 · `equals`/`hashCode` 가 전부 필요 없어져 파일이 3개에서
  2개로 줄고, 읽는 사람이 이해할 것도 줄어든다.

**주의.**

- `docs/WHY_POSTGRESQL.md` 는 사용자 id를 `uuid` 로 서술하고 있다
  (`V1__init_schema.sql:247-248` 의 uuid 비교 경고, `init.sql` 의 `pgcrypto`).
  **이 결정과 어긋나므로 queueMate 본 저장소의 스키마·문서도 같이 맞춰야 한다.**
  맞추기 전까지는 이 저장소의 코드가 앞서 있는 상태다.
- `(blocker_id, blocked_id)` 조합의 UNIQUE 제약은 여전히 필요하다. 같은 사람을 두 번
  차단할 수 없기 때문이다. 그 제약은 테이블을 소유한 `app:platform` 이 건다.
- `@GeneratedValue` 는 **붙이지 않았다.** `@Immutable` 이라 INSERT 자체를 만들지 않아
  동작하지 않는데, 붙여두면 "이 앱이 Block 을 만든다"고 잘못 읽힌다. 채번은
  `app:platform` 의 몫이고 그 사실은 javadoc 에 적었다.

### D-5. INV-1 선점을 Lua에서 `HSETNX` 한 명령으로 내린다 (되돌림)

> **이 결정은 적용했다가 되돌렸다 (2026-09-07).** `claim-request.lua` ·
> `RedisConfig.claimRequestScript` 빈 · `NaiveVsLuaComparisonTest` 이름이 전부 돌아왔고,
> `MatchCancelService` 의 `game` 널 가드도 없어졌다. 아래 본문은 **그때 무엇을 왜 했는지의
> 기록**으로 남긴다. 결론은 맨 끝 **"되돌린 이유"** 다.

**결정.** `claim-request.lua` 를 삭제하고, `MatchRequestService#join()` 이
`HSETNX qm:user:active-request:{userId} requestId {requestId}` 로 자리를 잡는다.
1이면 선점 성공, 0이면 이미 활성 요청이 있다(→ 409). 선점에 성공하면 나머지 필드
(`game`/`modeKey`/`voicePreference`/`playPurpose`/`keyValue`)를 `HSET` 한 번으로 이어 쓴다.

**근거.**

- **Lua가 하던 일이 이미 Redis 명령 하나에 들어 있다.** `claim-request.lua` 는
  `EXISTS` → `HSET` 이었는데, `HSETNX` 가 정확히 "없을 때만 쓴다"를 원자로 한다.
  같은 보장을 위해 스크립트 파일 · `RedisScript` 빈 · `ARGV` 를 평평한 배열로 펴는
  변환 코드 · Lua/자바 두 언어를 유지할 이유가 없다.
- **원자성 규칙이 요구하는 것은 Lua가 아니라 원자 실행이다.** "확인과 쓰기 사이에
  다른 요청이 끼어들 틈이 없을 것"이 지켜지면 수단은 상관없다. `create-or-check-party-untiered.lua`
  처럼 여러 키를 조건부로 오가는 동작은 여전히 Lua여야 하지만, INV-1은 그렇지 않다.
- 읽는 사람이 INV-1을 확인하려고 다른 파일과 다른 언어로 건너가지 않아도 된다.

**대가 — 부분 기록 창.** Lua는 전체를 한 번에 썼지만 이제 선점과 내용 기록이 나뉜다.
그 사이에 HASH에 `requestId` 하나만 있는 찰나가 생긴다.
그 창에서 `MatchCancelService#cancel()` 이 이 HASH를 읽으면 `game` 이 없어
`GameKey.valueOf(null)` 이 NPE로 터지고 500이 나간다.

**막은 방법.**

- `MatchCancelService#cancel()` 은 `game` 이 없으면 **아직 성립하지 않은 요청**으로 보고
  `CancelResult.NOT_FOUND` 를 돌려준다(404). 내용을 이어 쓰는 `HSET` 은 한 명령이므로
  필드가 일부만 적히는 경우는 없다. `game` 하나만 보면 충분하다.
- 지우지는 않는다. 그 찰나에 `DEL` 하면 뒤따라오는 `HSET` 이 `requestId` 없는 HASH를
  되살려 더 나쁜 상태가 된다. 읽는 쪽은 견디기만 한다.
- 선점 뒤 내용 기록이 실패하면 `join()` 이 잡은 자리를 되돌린다. 그대로 두면 게임을
  알 수 없어 취소도 못 하는 요청이 남는다 (이 키에는 TTL이 없다).
  되돌리기까지 실패하면 Redis가 죽은 것이므로 원인을 그대로 올려 INV-10대로 503이 된다.

**영향.**

- `src/main/resources/redis/claim-request.lua` 삭제, `RedisConfig.claimRequestScript` 빈 삭제.
  남은 Lua는 `create-or-check-party-untiered.lua` / `leave-party.lua` 둘이다.
- `NaiveVsLuaComparisonTest` → `NaiveVsAtomicComparisonTest` 로 이름을 바꿨다.
  대조군(순진한 `EXISTS`-후-`HSET`)은 그대로 두고 비교 대상만 `HSETNX` 로 바꿨다.
  100스레드 × 5라운드에서 순진한 방식 누적 중복 495건, `HSETNX` 0건 — 숫자가 바뀌지 않았다.
- `ActiveRequestConcurrencyTest` 는 손대지 않고 그대로 통과한다.
- `CLAUDE.md` §4 INV-1 행 · 회귀 테스트 표 · 원자성 규칙, `START_HERE.md` 의 코드 구조 ·
  요청 흐름 · Redis 키 표를 이 결정에 맞춰 고쳤다.
- `docs/CONCURRENCY_TESTS.md` 와 `docs/WHY_POSTGRESQL.md` 는 아직 `claim-request.lua` 를
  근거로 서술한다. 이 결정에 맞춰 따로 손봐야 한다.

**되돌린 이유.**

- **Lua는 실무에서 널리 쓰이는 표준 도구다.** 프로덕션 패턴(`SCRIPT LOAD` + `EVALSHA`)이
  이미 정립돼 있고, 이 저장소도 그 방식으로 돌고 있다. 피해야 할 비주류 기법이 아니다.
- **Redisson의 락도 내부 구현이 Lua다.** 락으로 바꾸는 것은 Lua를 없애는 것이 아니라
  라이브러리 안으로 숨기는 것이다. 숨긴다고 Lua를 안 읽어도 되는 것이 아니라,
  문제가 생겼을 때 남의 Lua를 읽게 된다.
- **판단 기준은 "로직이 어디서 끝나는가"다.** 로직이 Redis 안에서만 끝나면 Lua,
  외부 시스템(결제 API·DB)이 끼면 락 — 이것이 업계에서 쓰는 선이다.
  파티 배정은 전부 Redis 안에서 끝난다. Lua 쪽이다.
- **쪼갠 결과 중간 상태가 두 개 생겼다.** 부분 기록 창(`requestId` 만 있는 HASH)과
  "선점만 되고 내용이 안 써진 요청". 둘 다 Lua에는 없던 문제였고, 위 **대가** ·
  **막은 방법** 절이 통째로 그 두 상태를 견디기 위한 코드였다.
  없앤 복잡도보다 새로 만든 복잡도가 컸다.

**남은 문제와 대안.** 이 결정이 지목한 "읽기 어려움" 자체는 여전히 사실이다.
다만 답은 Lua를 없애는 것이 아니라 **호출부를 정리하는 것**이다 — 스크립트마다
자바 래퍼 클래스를 두어 호출부가 KEYS/ARGV 배열을 직접 조립하지 않게 하고,
부팅 시 `SCRIPT LOAD` 로 문법 오류를 기동 시점에 드러내는 방향.

### D-6. 파티 배정 Lua를 티어 유무로 둘로 나눈다

**결정.** 배정 스크립트를 **티어를 보는 것 / 안 보는 것** 두 갈래로 둔다.
기존 `join-or-create-party.lua`는 티어를 보지 않으므로
`create-or-check-party-untiered.lua`로 개명했다. 로직은 한 줄도 바꾸지 않았다.
티어를 보는 `join-or-create-party-tiered.lua`는 **아직 없다.**

**근거.**

- 한 스크립트 안에 티어 분기를 넣으면 파티 HASH를 한 번 더 읽고 범위를 비교하는
  가지가 늘어난다. Lua는 읽는 사람이 원자성을 눈으로 확인하는 코드라 분기가 늘수록
  그 확인이 어려워진다. `CLAUDE.md` §4가 Lua 안의 루프와 복잡도를 경계하는 것도
  같은 이유다 — 쓰기를 한 스크립트는 `SCRIPT KILL`이 안 된다 (#33).
- 이름이 동작을 말하게 두면 호출부(`CandidateRule`)에서 어느 쪽을 쓰는지가 드러난다.
  분기를 자바에 두고 Lua는 각자 한 가지만 하게 한다.

**영향.**

- `RedisConfig.joinOrCreatePartyScript()` → `joinOrCreatePartyUntieredScript()`.
  `LolCandidateRule`의 주입 필드명도 같이 바뀌었다.
- `leave-party.lua` · `PoolLock` · `LolTier`의 주석, `CLAUDE.md` §4,
  `START_HERE.md`, `docs/CONCURRENCY_TESTS.md`, `docs/WHY_POSTGRESQL.md`,
  `docs/02`의 부록 A가 새 이름으로 바뀌었다.
  *(후주 2026-09-14 — **D-8 에서 `domain/lol/LolTier.java` 는 삭제됐다.** 여기 적힌
  그 파일의 주석은 더 이상 존재하지 않는다. 티어 값의 원본은
  `qm:gameconfig:LOL:tier` ZSET 이다.)*
- 위 색인의 **#34와 Fixed decisions의 34번은 queueMate 원문 verbatim이라 옛 이름
  `join-or-create-party.lua`를 그대로 둔다.** 그 두 곳을 읽을 때는 이 항목을 우선한다.
- `-tiered` 쪽은 아직 설계도 열려 있다 — 티어를 색인 키에 넣을지, 스크립트 안에서
  파티 티어 범위와 대조할지는 `docs/02` A-3과 `domain/lol/LolTier.java`의 주석에 있다.
  *(후주 2026-09-14 — 그 설계는 **D-8** 에서 닫혔다. 티어는 색인 키에 들어간다
  (`…:needs:{keyValue}:{tier}` 격자). 그리고 **`domain/lol/LolTier.java` 는 삭제됐으므로**
  여기 가리킨 주석은 읽을 수 없다 — 티어 값의 원본은 `qm:gameconfig:LOL:tier` ZSET 이고,
  근거는 D-8 에 있다.)*

---

### D-7. 매칭 Lua를 게임별로 나눈다 (2026-09-11)

**결정.** 배정·취소 Lua를 **게임별 디렉터리**로 나눈다. 게임이 늘어도 한 게임의 수정이
다른 게임 스크립트에 닿지 않게 한다.

```
redis/
├── shared/
│   └── claim-request.lua      게임을 보지 않는다. INV-1 은 사용자 단위다
├── lol/
├── pubg/
└── valorant/
```

`claim-request.lua` 는 공유로 남긴다. "이 사용자가 이미 대기 중인가"만 보고 게임을
구분하지 않으며, 배그를 하다 롤 큐를 또 잡는 것도 막아야 하므로 게임별로 나누면 오히려
INV-1 이 깨진다.

**2026-09-11 에 옮겼다.** 처음에는 배그/발로란트를 만들 때 함께 옮길 생각이었으나,
구조를 먼저 세워 두는 편이 새 게임을 붙일 때 헷갈리지 않는다고 보아 앞당겼다.
`RedisConfig` 의 `read()` 경로 6개만 바뀌었고 **빈 이름은 그대로 두었으므로 주입부는
건드리지 않았다.** 옮긴 뒤 동시성 3종 + `PushNotificationTest` 13건이 통과하는 것을
확인했다 — Lua 는 클래스패스에서 읽으므로 경로가 틀리면 빈 생성에서 터진다.

**근거.**

- **이름이 이미 어긋나 있다.** `needs:{keyValue}` 는 "이 자리를 아직 못 채웠다"는 뜻이고
  롤 포지션에서 나온 말이다. 칼바람은 `needs:NONE`, 배그는 `needs:steam` 이 되는데
  둘 다 "자리가 비었다"가 아니라 "그 값을 가진 열린 파티"라는 뜻이다. 바이트는 같아도
  의미가 갈렸다. 게임별로 나누면 각 게임이 사실을 말하는 키 이름을 쓸 수 있다.
- **한 게임의 수정이 다른 게임에 닿는 것을 구조적으로 막는다.** 실제로 2026-09-08 에
  티어 쪽 인자 배치를 바꾸다 티어 없는 스크립트까지 `#ARGV - 12` 로 따라 바뀌어야 하는
  상황이 나왔고, 인자 조립을 갈라서 되돌렸다.
- **배그·발로란트가 코드 0줄이라 지금이 가장 싼 시점이다.** `rule/pubg`, `rule/valorant`,
  `domain/pubg`, `domain/valorant` 에 `.gitkeep` 만 있다.

**기각한 반론 — 되돌릴 때 이 항목부터 읽어라.**

- **지금까지 Lua 에 온 변경은 전부 게임 무관 인프라였다.** 티어 격자 도입(D-6),
  claim TTL 과 `EXISTS` 가드, 반환 코드 `-2` 추가 — 어느 것도 "게임 규칙이 달라서"가
  아니었다. 나누면 이런 변경마다 N 벌에 같은 내용을 넣어야 한다. 2026-09-08 의
  `EXISTS` 가드 + `PERSIST` 는 스크립트 **4개**에 같은 내용을 넣은 작업이었고,
  게임별로 나뉘어 있었으면 8개였다.
- **게임 규칙 패치는 Lua 를 건드리지 않는다.** `tierRule` / `maxTierGap` /
  `tier-range` 를 Redis 에 둔 이유가 그것이다 (docs/GAME_CONFIG.md).
  *(후주 2026-09-14 — **D-8 에서 `maxTierGap` 필드는 없어졌다.** 지금 Redis 에 있는 것은
  `tierRule`(`NONE` / `EXIST`) · `tier-range` 표 · 티어 사다리 `qm:gameconfig:LOL:tier`
  ZSET 셋이다. **이 반론 자체는 D-8 로 오히려 강해졌다** — 티어 규칙이 통째로 데이터가
  되어, 롤이 듀오 제한을 또 바꿔도 고칠 것은 시드의 표뿐이다.)* 롤이 티어 제한을
  바꾸든 배그가 12단계를 14단계로 바꾸든 `HSET` 한 줄이고 배포도 없다. 즉 "패치 때문에
  Lua 가 갈릴 것"이라는 예상은 지금 구조에서는 성립하지 않는다.
- **되돌리는 비용이 비대칭이다.** 합쳐진 것을 나누는 것은 파일 복사 한 번이지만,
  흩어진 것을 합치는 것은 이미 어긋난 N 벌을 대조해 통합하는 일이다. 그래서 일반적으로는
  "달라질 때 나눈다"가 안전한 방향이다. 이 결정은 그 방향을 **의도적으로 거슬렀다** —
  위의 이름 어긋남이 이미 일어난 divergence 라고 보았기 때문이다.

**대가 — 이것이 따라오지 않으면 나눈 값이 없다.**

**게임마다 불변식 동시성 테스트가 있어야 한다.** 현재 테스트 3종
(`ActiveRequestConcurrencyTest` / `NaiveVsLuaComparisonTest` / `PartyJoinConcurrencyTest`)은
전부 LoL 경로만 탄다. 스크립트가 한 벌일 때는 그 한 벌의 테스트가 전부를 지켰지만,
나누는 순간 다른 게임 스크립트는 **아무도 실행하지 않는 코드**가 된다.

그러면 이 결정이 막으려던 것이 오히려 쉽게 일어난다 — 합쳐져 있을 때는 잘못된 수정이
테스트에서 시끄럽게 깨지지만, 나뉘어 있으면 LoL 테스트만 통과하고 다른 게임은
조용히 고장 난 채 배포된다. `CLAUDE.md` §6 의 완료 조건 3(테스트 추가)이 게임마다
적용된다는 뜻이다.

**영향.**

- `redis/` 아래에 게임별 디렉터리. `RedisConfig` 의 `read()` 경로와 `RedisScript` 빈
  이름이 게임을 담게 된다. **빈 이름 = 주입 필드명**이라 (같은 타입 빈이 여럿이다)
  이름을 바꿀 때 주입부도 함께 고쳐야 한다.
- 게임별로 키 접두사를 다시 지을 수 있다. `needs:` 를 그대로 쓸지 게임마다 정직한
  이름으로 바꿀지는 각 게임을 만들 때 정한다.
- 인프라 성격 변경(가드·TTL·반환 코드)은 앞으로 게임 수만큼 반복된다. 그런 변경을 할
  때는 **전 게임 디렉터리를 훑었는지 확인**해야 한다.

**되돌릴 조건.** 세 게임 스크립트가 오래도록 바이트까지 같고, 인프라 변경마다 N 배
비용만 내고 있다면 합치는 쪽을 재검토한다. 그때는 위 "기각한 반론"이 그대로 근거가 된다.

---

### D-8. LoL 티어를 자바 enum 에서 Redis ZSET 으로 옮기고 단(division)까지 표현한다. `tierRule` 을 `NONE` / `EXIST` 둘로 줄인다 (2026-09-14)

**결정.** 티어 값·순서·개수의 원본을 `domain/lol/LolTier.java` 에서
`qm:gameconfig:LOL:tier` **ZSET** 으로 옮긴다. score 는 사다리의 **단계 번호**다
(`0 UNRANKED`, `1 IRON_4` … `31 CHALLENGER`, 32개). 티어에 **단(division)** 이 들어간다
(`GOLD` → `GOLD_4`/`GOLD_3`/`GOLD_2`/`GOLD_1`). `tierRule` 의 값은 `NONE` / `EXIST`
**둘뿐**이며 `TABLE` · `WINDOW` 와 `maxTierGap` 필드는 없앴다.

**무엇이 바뀌었나.**

1. **`domain/lol/LolTier.java` 삭제.** 자바에 티어 이름을 아는 코드가 한 줄도 없다.
   값 검증은 표 조회 하나가 겸한다 — 표에 없으면 규칙을 모르는 값이다
   (`validation/lol/LolConditionValidator.java#validTier()`).
2. **단이 들어갔다.** 단이 있는 티어는 `IRON`~`DIAMOND` 7개(각 4단), 단이 없는 값은
   `UNRANKED` · `MASTER` · `GRANDMASTER` · `CHALLENGER`. **숫자가 클수록 낮다**
   (골드4 → 골드1 → 플래티넘4). 라이엇 표기 그대로다.
3. **`tierRule` 은 "티어를 보나 안 보나"만 답한다.** `ModeConfig` 레코드가 `maxTierGap` 을
   잃었고, 자바에서 `"TABLE"` / `"WINDOW"` 문자열 비교가 전부 없어졌다.
   시드는 `HSET` 으로 지워지지 않는 필드를 `HDEL qm:gameconfig:LOL:RANKED_FLEX_* maxTierGap`
   으로 걷어낸다 (멱등하다).
4. **티어 허용 범위 표를 갖는 모드가 1개에서 4개가 됐다** — `RANKED_SOLO` 에 더해
   `RANKED_FLEX_2` / `_3` / `_5`.
5. **Lua 가 칸 키를 스스로 조립한다.** 예전에는 자바가 (포지션 x 티어) 격자를 평평하게 펴
   KEYS 로 전부 넘기고 Lua 가 `KEYS[2 + (p-1)*T + t]` 로 칸을 찾았다. 지금은 **티어 접미사가
   없는** needs 키를 넘기고 Lua 가 `':' .. 티어이름` 을 붙인다. KEYS 개수가 최대 192개에서
   **`4 + 포지션 개수`로 고정**됐다.
6. **`tierRule` 해석 자리가 자바에서 Lua 로 옮겨갔다.** `TieredAssigner#tierRange()` ·
   `TierRange` 레코드 · `addTierArgs()` 가 없어지고, Lua 가 직접 tier-range 표를 읽어
   티어 사다리(`ZRANK`)로 순번을 환산한다. 파티 HASH 의 `tierLo` / `tierHi` 는
   `ZRANK + 1`(1부터 시작하는 사다리 순번)이다.
   *(후주 2026-09-16 — **`+ 1` 이 없어졌다.** 지금은 `ZRANK` 값 그대로라 0부터다. Q-1 참고.)*

**근거.**

- **enum → 데이터.** 단을 넣거나 라이엇이 티어를 하나 추가할 때마다 재배포해야 했다.
  gameconfig 를 데이터로 뺀 것(`CLAUDE.md` §3)과 **같은 논리인데 티어만 예외로 코드에
  남아 있었다.** 배그·발로란트마다 enum 을 또 만들 필요도 없어진다.
- **score 가 단계 번호인 것의 값.** 두 티어의 score 를 빼면 "몇 단계 차이"가 그대로 나온다.
  롤의 "다이아는 2단 이내"와 배그의 "12단계 이내"가 **모두 뺄셈 하나**가 된다.
- **`TABLE` / `WINDOW` 를 없앤 이유는 그 값이 실제 데이터와 어긋날 수 있었기 때문이다.**
  `WINDOW` 라고 적어 놓고 `maxTierGap` 을 빠뜨리면 폭이 0 이 되어 자기 티어하고만
  매칭되는데 **에러가 나지 않았다.** enum 이 "어떤 방식으로 보나"에 답하고 있었는데,
  그 방식은 **어떤 데이터가 있는지 보면 이미 안다.** 지금은 "표가 있으면 그 표대로"가
  전부라 설정이 거짓말할 수 없다.
- **격자를 KEYS 로 안 넘기는 이유.** 단이 들어가면 칸이 (포지션 6 x 티어 32) = **192개**가
  된다(전에는 66칸). 호출마다 그만큼을 넘겨야 했다.

**조사로 확정한 것 (라이엇 공식 + 실사용자 제보).**

실제 규칙은 두 줄이다.

- 다이아가 안 낀 조합 → **±1 티어**
- 다이아가 낀 조합 → **±2 단(division)**
- 마스터 이상 → 듀오 불가 (KR 전용 조항)

공식 원문 (2026-09-14 확인,
`https://support.riotgames.com/ko-kr/league-of-legends/gameplay/ranked-tiers-divisions-and-queues`):

> "다이아몬드 플레이어는 랭크가 친구와 **세 단계 이상 차이 나면** 함께 게임을 시작할 수 없습니다."
> "**한국 서버의 플레이어는 마스터 티어 이상의 플레이어와 게임을 플레이할 수 없습니다.**"

**공식 문서의 티어별 표는 이 규칙을 티어 단위로 뭉개 적은 것이라 행끼리 모순된다.**
에메랄드 행은 티어 전체의 합집합(`Platinum - Diamond III`)이고 다이아 행은 사실상
다이아 IV 기준(`Emerald II - Diamond II`)이라, `에메랄드IV + 다이아III` ·
`에메랄드II + 다이아II` · `다이아III + 다이아I` 세 조합이 **어느 행을 읽느냐로 답이
뒤집힌다.** 실제 플레이어 제보와 대조한 결과 **표가 아니라 설명 문장이 맞다.**

| 조합 | 단 차이 | 실제 |
|---|---|---|
| 에메1 + 다이아3 | 2 | 가능 |
| 에메2 + 다이아3 | 3 | 불가 (독립 제보 3건 일치) |
| 에메2 + 다이아4 | 2 | 가능 |
| 에메3 + 다이아4 | 3 | 불가 |

출처: `https://reddit.com/r/leagueoflegends/comments/1r10rng/` (2026-02-10),
`/comments/1teoc62/` (2026-05-16), `/comments/1pgdf9t/` (2025-12-07).

자유 랭크는 규정이 아예 다르다 (같은 공식 문서의 Flex 절):

> "there are **no Flex rank restrictions for Diamond and below**"
> "If you want to play with Masters and up, however, you'll have to be **at least Emerald**."

**표를 어떻게 만들었나.**

- **솔랭** — 위 규칙을 단 단위로 편 32줄. `UNRANKED` 와 `MASTER` / `GRANDMASTER` /
  `CHALLENGER` 는 `SOLO_ONLY`.
- **자랭** — **두 덩어리**다. `UNRANKED`~`DIAMOND_1` 전부 `UNRANKED:DIAMOND_1`,
  `MASTER`~`CHALLENGER` 전부 `MASTER:CHALLENGER`. **`SOLO_ONLY` 가 하나도 없다** —
  자유 랭크는 마스터 이상도 자기들끼리 파티가 된다. KR 전용 제한 문장은 공식 문서의
  Solo/Duo 절 안에만 있고 Flex 절에는 없다.

**대칭성과 전이성이 왜 다르게 걸리는지가 이 설계의 핵심이다.**

- 우리 엔진은 **"파티를 만든 사람의 범위"** 로 후보를 찾는다(범위는 만들 때 한 번 정해지고
  합류할 때 다시 계산하지 않는다 — `create-or-check-party-tiered.lua` 머리 주석).
  표가 비대칭이면 **누가 먼저 큐를 눌렀느냐로 결과가 갈린다.** 그래서 네 표 모두 대칭이어야 한다.
- **전이(transitive)는 솔랭에서 성립하지 않는다** — 에메2↔다이아4 되고 다이아4↔다이아2
  되지만 에메2↔다이아2 는 안 된다. 솔랭은 정원이 2라 상관없다.
- **자랭은 정원이 3인/5인이라 전이가 반드시 성립해야 한다.** 에메랄드는 아이언과도 되고
  마스터와도 되는데 아이언+마스터는 불가라, 범위 하나로 표현하면 아이언·에메랄드·마스터가
  한 파티에 모여 **게임에서 큐가 안 잡힌다.** **덩어리로 끊으면 같은 덩어리 안의 모두가
  같은 범위를 가져 대칭과 전이가 저절로 성립한다.**
- 기계 검증 결과: 네 표 모두 **비대칭 0건**, 자랭 3개는 **전이 위반 0건**,
  솔랭은 전이 위반 564건(의도된 것).

**기각한 대안.**

- **`maxTierGap` 을 설정 필드로 남긴다** — 낮에는 이쪽으로 기울었다(배그의 균일한 ±12 를
  표로 풀면 손으로 26줄을 쓰게 되고 26군데서 틀릴 수 있다는 이유). **뒤집었다.** 필드가
  하나 더 있으면 "표와 폭 중 무엇이 이기는가"라는 답할 필요 없는 질문이 생기고, 위의
  `WINDOW` + 누락 구멍이 이름만 바꿔 남는다. 배그도 표로 풀되 **생성 스크립트로 만들면
  손으로 쓸 일이 없다** — 26군데를 손으로 쓰는 것이 문제였지 표가 문제가 아니었다.
- **자랭에서도 `UNRANKED` 를 `SOLO_ONLY` 로** — 공식에 언랭크 규정이 없고, 자유 랭크는
  친구끼리 하는 큐라 막으면 **배치 전 계정이 이 모드를 통째로 못 쓴다.** 다이아 이하
  덩어리에 넣었다. 솔랭만 `SOLO_ONLY` 다.
- **자랭 범위를 공식 그대로(에메랄드 이상은 마스터와 가능) 표현** — 범위 하나로는 전이를
  지킬 수 없다. 아래 **대가** 참고.

**대가 — 버린 것.**

- **에메랄드/다이아 ↔ 마스터 (자유 랭크).** 공식상 되는 조합인데 덩어리로 끊느라 버렸다.
  마스터 이상은 인원이 극소수라 잃는 것이 작다고 판단했다.
- **`UNRANKED` 를 솔랭에서 `SOLO_ONLY` 로.** 예전에는 언랭끼리 붙였는데, 그 칸에 사람이
  모이지 않아 **영영 매칭되지 않는 대기**가 됐다. 큐에 넣기 전에 거절하는 편이 낫다.
- **숨은 MMR 은 영구 사각지대다.** 라이엇은 표시 티어가 아니라 MMR 로도 막는다(공식 문서에
  명시). 실제로 다이아 구간에서 "듀오 안 됨"의 1순위 원인이 이것이다 — 둘 다 다이아3
  LP 4점 차이인데 차단된 제보가 있다. **우리는 MMR 을 볼 방법이 없고, 라이엇 계정 연동이
  붙어도 안 보인다.** 이 표를 지켜도 게임에서 막히는 조합이 남는다.

**되돌릴 조건 / 나중에 볼 것.**

- 라이엇이 듀오 제한을 또 바꾸면(에이펙스 듀오 규칙은 2021년 이후 **다섯 번** 바뀌었다)
  **표만 고치면 된다. 코드는 안 바뀐다 — 그것이 이 설계의 값이다.**
- **사다리 중간에 티어를 끼워 넣으면 안 된다.** 이미 만들어진 파티가 `tierLo` / `tierHi` 에
  **순번**을 들고 있어서, 운영 중에 사다리를 바꾸면 그 파티들이 엉뚱한 칸을 가리킨다.
  비어 있을 때 바꿔라.
- 티어 사다리에 **`NONE` 이라는 티어를 넣으면 안 된다.** `leave-party.lua` 가 "티어를 안 보는
  모드"를 가리키는 신호로 쓰는 값이다.

**검증 상태.** 동시성 3종 7건 + `PushNotificationTest` 6건 = **13건 전부 통과.**
다만 새 Lua 3개(`create-or-check-party-tiered.lua` · `join-party-tiered.lua` ·
`leave-party.lua`)가 실제로 도는 것은 `PushNotificationTest` 의 티어 모드 케이스와
취소 케이스뿐이다. D-7 의 **대가**(게임마다 불변식 테스트가 있어야 한다)가 티어 축에도
그대로 적용된다 — 티어 격자를 도는 동시성 테스트는 아직 없다.

---

## 문서 정합성 점검 기록 (2026-09-11)

> 이 파일은 **기록**이라 위 항목들의 본문을 고치지 않는다. 2026-09-11 에 `backend/` 아래
> 코드를 다시 읽어 문서와 대조한 결과, **낡아서 사실이 아니게 된 항목**들이 나왔다.
> 지우는 대신 여기에 "무엇이 어떻게 바뀌었나"만 적는다. 겹치면 이 절이 우선한다.
>
> 코드를 고친 기록이 아니다 — 그 사이에 들어온 변경을 **뒤늦게 문서에 반영한** 기록이다.
> 각 변경을 내린 결정 항목이 이 로그에 없다는 것 자체가 O-1 과 같은 종류의 구멍이다.

### P-1. 배정 Lua가 3개에서 6개로 늘었고, `join-or-create-party*.lua` 라는 파일은 없다

현재 `backend/src/main/resources/redis/` 에 있는 것은 6개다.

| 파일 | 역할 |
|---|---|
| `claim-request.lua` | 활성 요청 선점 (INV-1). `EXISTS` + `HSET` + `EXPIRE 60` |
| `create-or-check-party-untiered.lua` | 후보 파티 찾기, 없으면 새로 만들고 들어감 |
| `create-or-check-party-tiered.lua` | 위의 (포지션 x 티어) 격자판 |
| `join-party.lua` | 이미 찾아 둔 기존 파티에 들어감 |
| `join-party-tiered.lua` | 위의 격자판 |
| `leave-party.lua` | 취소. 티어 유/무 한 벌로 처리 |

**D-6 이 "아직 없다"고 적은 티어판이 생겼다.** 다만 이름이 D-6 의 예고와 다르다 —
`join-or-create-party-tiered.lua` 가 아니라 `create-or-check-party-tiered.lua` +
`join-party-tiered.lua` **두 개**다. 찾기와 합류를 쪼갠 이유는 P-2 에 있다.

`CLAUDE.md` §4 의 INV-2 / INV-3 / INV-7 줄은 **존재하지 않는 파일
`join-or-create-party-untiered.lua` 를 가리키고 있었다.** 2026-09-11 에 고쳤다.
`#34` 와 `D-6` 은 기록이라 옛 이름을 그대로 둔다 — 그 이름이 보이면 옛 이름이다.

### P-2. 배정이 "찾기"와 "합류" 두 스크립트로 쪼개졌다. 그 틈은 락이 막는다

`create-or-check-party-*.lua` 는 후보를 찾으면 **넣지 않고 멤버 목록만 돌려준다**(코드 `2`).
차단 검증(자바)을 거친 뒤 `join-party*.lua` 가 실제로 넣는다.

**#34("파티 찾기와 만들기를 하나의 Lua로 합친다")가 노린 원자성이 그 지점에서 사라졌다.**
대신 `redis/PoolLock.java`(Redisson `RLock`, 키 `qm:lock:pool:` + 풀 식별자)가 후보를 훑는
루프 전체를 감싼다. 락 단위는 `game:mode:voice:purpose` 까지이고 **keyValue 를 락 키에
넣으면 안 된다** — 파티 하나가 자기가 못 채운 keyValue 여럿의 색인에 동시에 올라가 있어,
넣으면 서로 다른 락을 잡아 아무것도 막지 못한다 (그 클래스 주석).

그 결과 **INV-3 은 더 이상 Lua 혼자 지키지 않는다.** `join-party.lua` 의 `HINCRBY size 1`
에는 target 확인 분기가 없다. 초과를 막는 것은 ① 후보가 needs 색인에서만 나온다는 것과
② 선택부터 합류까지가 한 락 안이라는 것, 두 겹이다. **락을 건너뛰는 호출부를 만들면
INV-3 이 깨진다.**

`PoolLock` 의 클래스 주석은 "지금은 아직 아무 데서도 쓰이지 않는다"고 적혀 있으나
`LolCandidateRule#canJoin` 이 실제로 쓴다 — **주석이 낡았다.**

### P-3. claim 에 TTL 이 붙고 배정 스크립트에 `EXISTS` 가드가 생겼다

`claim-request.lua` 가 마지막에 `EXPIRE 60` 을 건다. 자리만 잡고 배정 전에 앱이 죽으면
그 키가 영원히 남아 사용자가 매칭도 새 요청도 못 하게 되기 때문이다. 배정에 성공한
스크립트 4개가 `PERSIST` 로 그 만료를 뗀다.

배정 스크립트 4개에는 `EXISTS userKey == 0` 이면 **`-2` 를 돌려주고 아무것도 하지 않는**
가드가 있다. `HSET` 은 없는 키를 새로 만들기 때문에, 그냥 진행하면 `partyId` 하나만 든
반쪽짜리 활성 요청이 되살아나고 그 상태로는 취소가 `game` 필드를 못 읽어 터진다.

60초의 근거는 스크립트 주석에 있다 — claim 부터 배정까지 설정상 상한이 7초
(차단 조회 300ms + 락 대기 3s + 락 유지 3s)라 8배 여유다.

### P-4. O-3 갱신 — 알림 경로가 생겼다. 남은 것은 "지금 상태" 조회다

O-3("매칭 결과를 클라이언트에게 알리는 경로가 하나도 없다")은 절반 해소됐다.

`notification/PushPublisher.java` 가 `qm:pubsub:push:{userId}` 채널에
`{type, eventId, occurredAt, payload}` JSON 을 publish 한다 (docs/14 §5·§6, docs/07 §7-2 대로
SSE 배달은 `app:realtime` 몫이다). `notification/PushEventType.java` 5종 중 **3종이 실제로
발행된다** — `MATCH_QUEUE_UPDATED` / `MATCH_PROPOSAL_CREATED`(`rule/lol/*Assigner.java`) ·
`MATCH_CANCELLED`(`rule/lol/PartyLeaver.java`). 나머지 2종은 확정·만료가 없어 발행 코드가 없다.

`publish()` 는 **어떤 예외도 밖으로 내보내지 않는다.** 알림은 휘발성이고, 여기서
`DataAccessException` 을 올리면 `GlobalExceptionHandler` 가 503 으로 바꿔 **이미 성립한
매칭이 실패로 뒤집힌다.** INV-10 은 "Redis 없이 새 매칭을 만들지 마라"이지 "알림이
실패하면 매칭을 취소해라"가 아니다. 그 대가로 발행이 틀려도 조용하므로
`backend/src/test/java/.../notification/PushNotificationTest.java`(6건)가 실제로 구독해서 본다.

**남은 구멍은 조회다.** `GET /match-requests/{id}` 는 **501** 이다 (P-5).

### P-5. O-4 갱신 — proposal 껍데기와 조회 스텁이 생겼다. 알맹이는 그대로 없다

| 생긴 것 | 상태 |
|---|---|
| `controller/ProposalController.java` | `POST /api/v1/proposals/{id}/accept`·`decline`, 성공 시 **204** (계약은 200 — `contracts/README.md` #6-1). **제안 id 는 partyId 다** |
| `domain/AcceptResult.java` / `DeclineResult.java` | 응답 갈래 enum. 컨트롤러가 이 값으로 HTTP 코드를 정한다 |
| `service/ProposalService.java` | **시그니처뿐이다.** 두 메서드 모두 `UnsupportedOperationException` — 부르면 500 |
| `MatchingController#getMatchRequest` | `GET /match-requests/{id}` → **501 `NOT_IMPLEMENTED`**. 왜 필요한지와 같이 정해야 할 것(userId 로 찾는 판, `MatchRequestView` 와의 차이)이 메서드 주석에 있다 |

**O-4 의 결론은 그대로다** — 수락 집계(#28) · 확정 · 만료 sweeper 가 전부 없다.
`@Scheduled` 가 0건이고 `queuemate.proposal.ttl-seconds` / `queuemate.sweep.interval-ms` 를
읽는 코드도 없다. 정원이 차면 알림만 나가고 그 뒤가 없다.

**INV-4 의 트리거 지점이 바뀌었다.** `CLAUDE.md` 가 적고 있던
"`LolCandidateRule.canJoin()` 안, Lua 반환 코드 `3`" 은 두 군데 다 틀렸다. 실제로는
`rule/lol/UntieredAssigner.java#joinParty()` / `TieredAssigner.java#joinParty()` 의
`JOINED_AND_FULL` 분기이고 **반환 코드는 `2`** 다. #34 와 O-4 가 적은 "코드 3" 은
`join-or-create-party.lua` 시절 값이다 (기록이라 그대로 둔다).

### P-6. INV-6 — 차단 **선필터**가 배정 경로에 들어왔다. 스키마가 없어 실제로는 실패한다

D-2 는 "Redis 선필터(`qm:block:{userId}`)를 보류한다"였다. 들어온 것은 그 Redis 선필터가
아니라 **DB 선필터**다.

`LolCandidateRule#canJoin` 이 **락을 잡기 전에** `BlockRepository#findBlockedUserIds` 로
내 차단 목록을 한 번 가져오고(락 안에서 DB 를 치면 유지 시간을 넘겨 락이 저 혼자 풀린다),
Lua 가 돌려준 후보 파티 멤버 목록을 `LolScriptSupport#blockedWith` 로 거른다.
차단이면 다음 후보를 보고, 상한(`MAX_CANDIDATE_SCAN = 50`)까지 전부 차단이면 새 파티를 만든다.

**그런데 `social.blocks` 스키마가 없다.** Flyway 미도입이고 `application.yaml` 이
`ddl-auto: none` 이라 기본 실행(H2)에 그 테이블이 없다. 배정은 `@Async` 안이므로
**요청은 201 로 나가고 배정만 조용히 실패한다.** 테스트만
`ConcurrencyTestSupport` 의 `ddl-auto=create-drop` + `backend/src/test/resources/schema.sql` 로
빈 테이블을 만들어 통과한다 — 즉 "차단이 없는 경우"만 검증되고 있다.

**확정 직전 최종 검증(D-1)은 여전히 미구현이다.** 확정 자체가 없다.
#30 의 "차단 검증 없이 배포하지 않는다"는 그대로 유효하다.

### P-7. O-7 갱신 — 낡은 주석이 더 늘었다

O-7 이 지적한 `rule/CandidateRule.java` 의 `AbstractCandidateRule`(없는 클래스)에 더해,
**코드 주석 세 군데가 자기보다 낡았다.**

- `redis/PoolLock.java` — "지금은 아직 아무 데서도 쓰이지 않는다" → `LolCandidateRule` 이 쓴다
- `domain/lol/LolTier.java` — "아직 매칭에 쓰이지 않는다 / 티어를 보는 배정 스크립트는
  아직 없다" → 둘 다 아니다 (`TieredAssigner` + `-tiered` 스크립트 2개)
  *(후주 2026-09-14 — **이 낡은 주석은 고쳐서 없앤 것이 아니라 파일과 함께 사라졌다.**
  **D-8 에서 `domain/lol/LolTier.java` 가 삭제됐다** — 인용 대상 자체가 없다.
  티어 값의 원본은 `qm:gameconfig:LOL:tier` ZSET 이다.)*
- `create-or-check-party-untiered.lua` · `join-party.lua` 머리 — "티어를 보는 모드는
  `join-or-create-party-tiered.lua` 가 담당한다 (아직 없다)" → 이름도 틀리고 없지도 않다

**주석을 사실로 믿지 마라.** 이번 점검은 문서만 고쳤고 코드는 건드리지 않았다.

### P-8. 문서에 아직 반영하지 않은 것

- **`matching/failover/` 패키지와 `application-sentinel.yaml`** — Redis 페일오버 재시도
  실험 자산. `queuemate.failover.retry.enabled` 기본 `false` 라 꺼져 있으면 빈이 하나도
  안 만들어진다. 절차는 `redis-ha-lab/docs/failover-retry-guide.md` 에 있다고 적혀 있다.
  이 결정을 기록한 항목이 이 로그에 없다.
- **`config/RedissonConfig.java` 의 Sentinel 갈래** — 위와 같은 축이다.

### 점검 방법

`backend/` 아래 소스와 `seed/` 를 직접 읽고 대조했다. 재확인 명령은
`START_HERE.md` §4.4 에 갱신해 두었다. **빌드와 테스트는 돌리지 않았다** —
코드를 고치지 않았으므로 확인 대상이 문서뿐이었다.
---

## 문서 정합성 점검 기록 (2026-09-16)

> 위 항목들은 **기록**이라 본문을 고치지 않는다. 그 뒤 코드에 들어온 변경 중
> 문서와 어긋나게 된 것만 여기에 적는다. 겹치면 이 절이 우선한다.

### Q-1. `tierLo` / `tierHi` 가 `ZRANK + 1` 에서 **`ZRANK` 그대로**(0부터)가 됐다

D-8 의 6번 항목("`ZRANK + 1`(1부터 시작하는 사다리 순번)")이 낡았다. 쓰는 쪽이 `ZRANK` 를
그대로 적고, 읽는 쪽이 `ZRANGE lo hi` 에 그대로 넘긴다. 티어를 안 보는 모드의 자리 채움
값도 `tierLo = tierHi = 1` 에서 **`0`** 으로 바뀌었다. LoL · PUBG 스크립트 8개를 같이 고쳤다.

**근거.** `+ 1` 은 쓰는 자리와 읽는 자리 둘로 갈려 있었다 — 한쪽만 고치면 칸이 한 칸씩
어긋나는데 에러가 나지 않는다. 사다리 순번을 쓰는 곳은 `ZRANGE` 하나뿐이고 `ZRANGE` 가
0부터 세므로, 환산하지 않는 편이 맞출 자리가 하나도 없는 모양이다.

**바뀌지 않은 것.** 사다리 중간에 값을 끼워 넣으면 이미 만들어진 파티의 `tierLo`/`tierHi` 가
엉뚱한 칸을 가리킨다는 것은 그대로다(D-8 "되돌릴 조건"). 저장하는 것이 여전히 이름이 아니라
순번이기 때문이다. **큐가 비어 있을 때 바꿔라.**

### Q-2. VALORANT 디렉터리가 채워지기 시작했다 — 티어 스크립트 2개

D-7 의 트리에서 `(아직 없음)` 이던 `valorant/` 에 **2개**가 들어왔다 —
`create-or-check-party-tiered.lua` / `join-party-tiered.lua`. 색인은 (역할군 x 티어) 격자이고
역할군(`DUELIST` / `INITIATOR` / `CONTROLLER` / `SENTINEL`)은 LoL 포지션처럼 한 파티에서
겹치지 않는다. 자바 쪽은 `domain/valorant/ValorantRole.java` 와
`validation/valorant/ValorantConditionValidator.java` 뿐이다.

**아직 없는 것**: 티어를 안 보는 모드의 Lua, 취소 Lua, `rule/valorant`(빈 디렉터리),
`config/redis/valorant`(스크립트 빈), `seed/gameconfig.redis` 의 VALORANT 항목. 시드가 없으니
지금 VALORANT 요청은 validator 가 모드 설정을 못 읽어 400 이고, 이 스크립트 2개는
**아직 한 번도 실행되지 않는다.** D-7 의 대가(게임마다 불변식 테스트가 있어야 한다)가
PUBG 에 이어 VALORANT 에도 밀려 있다.

### Q-3. 티어 범위를 언제 정하는가가 **게임마다 다르다**

LoL 과 PUBG 는 파티가 받아들일 티어 범위를 **만든 사람 기준으로 생성 시 한 번** 정하고
그 뒤 바꾸지 않는다. **VALORANT 는 합류할 때마다 좁힌다** — `join-party-tiered.lua` 가
파티 범위를 "지금 범위 ∩ 들어온 사람의 tier-range 줄" 로 바꿔, 옛 범위 칸 전부에서 파티를
빼고 **아직 빈 역할군만** 새 범위 칸에 다시 올린다(정렬값은 지금 시각이 아니라 파티의
`createdAt` 이다 — now 를 쓰면 이 파티만 색인에서 가장 새 것으로 밀려 먼저 기다린 파티보다
늦게 잡힌다).

**근거.** 발로란트 규칙은 "파티 최고 티어 <= 한계(파티 최저 티어)" 하나다. tier-range 표의
한 줄은 "그 티어와 **둘이** 같이 갈 수 있는 구간"이라, 정원이 3인이 되면 방장 줄 하나로는
모자란다. 좁혀 두면 색인의 칸이 늘 "지금 이 파티에 들어올 수 있는 티어"와 같아져서,
찾기 쪽에 후보를 거르는 분기가 필요 없다.

**그래서 VALORANT 에만 딸린 것이 둘이다.**

| 무엇 | 뜻 | 누가 다루나 |
|---|---|---|
| `qm:party:needs-roles:{partyId}` SET | 아직 비어 있는 역할군. 범위를 좁힌 뒤 어느 칸을 다시 만들지가 이 목록이다 | create 가 `SADD`, join 이 합류 때 `SREM`, 정원이 차면 `DEL`. **취소(미구현)도 되돌려야 한다** |
| 파티 HASH 의 `minTier` / `maxTier` | 지금까지 들어온 사람의 최저·최고 티어 순번(`ZRANK`, 0부터) | 지금은 **읽는 곳이 없다.** 취소가 남은 사람 기준으로 범위를 되돌릴 때 쓸 값이다 |

KEYS 배치도 LoL 과 다르다 — `KEYS[5]` 가 역할군·티어가 없는 **밑동** needs 키이고
`KEYS[6..]` 이 역할군별 needs 키다. 칸은 `KEYS[5 + p] .. ':' .. 티어이름` 으로 조립하고,
밑동은 `needs-roles` 에서 받은 역할군 이름으로 칸을 다시 만들 때 쓴다.

### 점검 방법

`backend/src/main/resources/redis/` 의 Lua 15개와 `seed/gameconfig.redis` 를 직접 읽고
문서와 대조했다. **코드는 건드리지 않았고 빌드·테스트도 돌리지 않았다** — 고친 것은 문서뿐이다.
