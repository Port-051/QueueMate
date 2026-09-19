# AWS 아키텍처 — matching 서버는 어디에 있나

`docs/aws-architecture.drawio`를 **평문 XML로 직접 파싱해서** 글로 옮긴 것이다.
그림 파일만 남겨두면 draw.io 없이는 읽을 수 없고, 나중에 "이 화살표가 뭐였지"를
복원할 수 없다. 그래서 노드와 간선을 전부 표로 적어 둔다.

- 원본: `docs/aws-architecture.drawio` (queueMate `feature/frontend` 브랜치의 `design/aws-architecture.drawio` 복사본)
- 그림 제목: **Cloud Architecture** / 부제: `QueueMate — 게임 팀원 자동 매칭 · AWS ap-northeast-2 | ECS Fargate · SQS FIFO · SSE · WebRTC`
- 그림이 그리는 것은 **Stage 2 (ECS Fargate)** 구성이다. 현재 개발 단계는 Stage 1(단일 EC2 + Docker Compose)이므로 이 그림은 목표 상태다.

> **[2026-09-19 추가] 결정 D-9(`docs/11_DECISION_LOG.md`, #22 개정)로 `/ws` 경로와
> `app:realtime` 의 WebSocket 은 없어진다.** `WEBRTC_SIGNAL` 도 SSE 로 받아 SSE 는 14종이 아니라
> 15종이 되고, 시그널을 보내는 쪽은 `app:platform` 의 REST `POST` 다.
> **그림과 아래 표에는 아직 반영되지 않았다** — 표는 그림을 그대로 옮긴 것이라 그림과 같이
> 고쳐야 한다. 아래에서 `/ws` · `WS(WEBRTC_SIGNAL)` · `SSE 14종` · "SSE + WebSocket" 을 만나면
> 이 문단을 함께 봐라.
>
> **같은 날 결정 D-12 로 `BlockChanged.fifo` 는 없어진다**(차단은 `app:platform` 이 `social.blocks` 에
> 저장하면 끝이고, `app:matching` 은 확정 직전에 그 테이블을 직접 조회한다). **결정 D-13 으로
> `PartyClosed.fifo` 의 소비자는 `app:platform` 하나다** — `app:matching` 은 그 큐를 읽지 않는다.
> 이것도 **그림과 아래 표에는 반영되지 않았다.** 아래에서 `BlockChanged.fifo` · "차단 목록 갱신" ·
> "`BlockChanged.fifo`의 유일한 소비자"를 만나면 이 문단을 함께 봐라.
>
> **같은 날 결정 D-15 로 예약(등록 REST + 짝 찾기 배치)은 AWS Lambda 로 빠진다.** `app:reservation-batch`
> (Fargate 상시 1개)는 **`app:reservation`(Lambda)** 이 대체하고, 예약 REST 도 `app:platform` 이 아니라
> `app:reservation` 의 일이 된다. HTTP 진입점과 CloudFront 경로 라우팅, VPC 배치는 미정이다. 이것도
> **그림과 아래 표에는 반영되지 않았다.** 아래에서 `app:reservation-batch` · "상시 1개 · 내부 1분 주기" ·
> "예약 REST … 는 `app:platform`이 서빙한다"를 만나면 이 문단을 함께 봐라. **결정 D-17 로 예약 배치는 1분 주기가 아니라
> 요일 구분에 따라 하루 중 정해진 시각에만 돈다** — 아래의 "내부 1분 주기"도 함께 걸러 읽는다.
>
> **같은 날 결정 D-16 으로 방이 별도 서비스 `app:room` 으로 분리된다.** 입장 · 나가기 · 강퇴 · 정원 · 접속
> 확인 · 시그널 `POST` · `WEBRTC_SIGNAL` 발행은 `app:room` 의 일이고, `app:room` 은 Redis 만 쓴다(RDS 에 붙지
> 않는다). 모집 글 · 목록 · 방장 확정 · 계정 · 친구 · 차단은 `app:platform` 에 남는다. 배포 단위는
> `app:matching` / `app:platform` / `app:room` / `app:realtime` / `app:reservation`(Lambda) 다섯이 된다.
> **그림과 아래 표에는 `app:room` 상자가 없다.** ALB 경로 라우팅은 미정이다.
>
> **같은 날 결정 D-18 로 Stage 1(단일 EC2 + Docker Compose)을 적용하지 않는다.** 이 그림이 그리는 Stage 2
> (ECS Fargate) 구성이 목표 상태가 아니라 **배포 기준**이 된다. 위 머리말의 "현재 개발 단계는 Stage 1 … 이
> 그림은 목표 상태다"는 그렇게 걸러 읽는다.

---

## 0. 한 줄 요약

**이 저장소 = 그림의 `app:matching` 상자 하나다.**

`app:matching`은 App Private Subnet의 ECS Fargate 서비스로, **ALB로부터 HTTP를 받고 /
ElastiCache Redis에 매칭 상태를 쓰고 / RDS에 차단 검증만 읽으러 가고 / SQS FIFO 2개에
각각 발행·소비하고 / 사용자 알림은 Redis Pub/Sub에 publish만 하고 직접 보내지 않는다.**

---

## 1. 그림의 컨테이너 계층

```
Client Environment          웹 클라이언트 A / B (React SPA)
External Services           Cloudflare TURN
AWS Cloud
└─ VPC · ap-northeast-2
   └─ 가용 영역 A · ap-northeast-2a
      ├─ Public Subnet        ALB (ALB 서브넷)
      ├─ App Private Subnet   ECS Cluster · Fargate
      │                       ├─ app:platform
      │                       ├─ app:matching        ← 이 저장소
      │                       ├─ app:realtime  (★)
      │                       └─ app:reservation-batch
      └─ Data Private Subnet  RDS PostgreSQL / ElastiCache for Redis
```

VPC 밖(리전 서비스): Route 53, CloudFront, S3, ECR, ACM, CloudWatch, Secrets Manager,
SQS FIFO 3개.

### 그림에 적힌 각 상자의 설명 (라벨 원문)

| 노드 | 라벨 |
|---|---|
| `app:platform` | `app:platform · Fargate` — account · party · social / REST 전용 · stateless |
| **`app:matching`** | **`app:matching · Fargate` — matching · gameconfig / 매칭 루프 · Lua atomic claim** |
| `app:realtime` | `★ app:realtime · Fargate` — 연결 전담 / SSE 14종 + WS(WEBRTC_SIGNAL) |
| `app:reservation-batch` | `app:reservation-batch · Fargate` — 예약 매칭 배치 / 상시 1개 · 내부 1분 주기 |
| RDS | `Amazon RDS · PostgreSQL Primary (Writer)` — 단일 인스턴스 · 7스키마 · 스키마별 롤 |
| ElastiCache | `ElastiCache for Redis Primary` — 매칭 큐 · Lua · presence · block read model · Pub/Sub |
| ALB | `Amazon ALB` — 경로 라우팅 · idle timeout 300초 |
| CloudFront | `Amazon CloudFront` — 웹 정적 + API 오리진 |
| S3 | `Amazon S3` — React SPA 호스팅 · OAC |
| Cloudflare TURN | 직결 실패 시 중계 (15~25%) · 관리형 |

---

## 2. 간선 전부 (그림의 화살표 25개)

`≫` 로 표시한 4개가 `app:matching`이 직접 걸린 간선이다.

### 진입 경로 (동기 요청)

| From | 라벨 | To |
|---|---|---|
| 웹 클라이언트 A | 도메인 조회 | Route 53 |
| 웹 클라이언트 A | HTTPS | CloudFront |
| CloudFront | 웹 정적 요청 · OAC | S3 |
| CloudFront | `/api/**` · `/events` · `/ws` | ALB |
| ALB | `/api/v1/**` | app:platform |
| ALB ≫ | **매칭 · 예약 CRUD** | **app:matching** |
| ALB | `/events`(SSE) · `/ws` | app:realtime |

> ⚠️ **그림의 라벨 오류 1건 (고치지 않고 여기 기록한다).**
> ALB → `app:matching` 간선의 라벨이 `매칭 · 예약 CRUD`인데, **예약 CRUD는
> `app:matching`이 서빙하지 않는다.**
> `docs/11_DECISION_LOG.md` #24와 `docs/14_ARCHITECTURE_RATIONALE.md` §9가
> **예약 REST(`/api/v1/reservations` CRUD + INV-9 검증)는 `app:platform`이 서빙한다**고
> 못 박는다. `app:reservation-batch`는 REST를 아예 서빙하지 않고,
> `app:matching`이 받는 것은 **실시간 매칭 REST뿐**이다.
> 즉 이 간선의 정확한 라벨은 `매칭 REST`이고, `예약 CRUD`는 위쪽
> ALB → `app:platform` 간선(`/api/v1/**`)에 속한다.
> **그림은 원본 보존을 위해 수정하지 않았다.** 그림을 읽을 때 이 문장을 함께 봐라.

### DB / Redis 접근

| From | 라벨 | To |
|---|---|---|
| app:platform | account · party · social | RDS |
| app:matching ≫ | **동기 SELECT `social.blocks` (INV-6)** | RDS |
| app:matching ≫ | **대기열 · Lua 선점** | ElastiCache Redis |
| app:reservation-batch | 예약 조회 · 제안 저장 | RDS |

`app:matching`이 RDS를 치는 것은 **INV-6 차단 검증 SELECT 하나뿐**이다.
`matching` 롤은 `social` 스키마를 못 읽고, 승인된 유일한 크로스 스키마 예외인
`social.blocks`만 SELECT 할 수 있다 (docs/11 D-1 — 뷰를 두는 원안은 폐기했다).

### SQS FIFO (서버 간 작업 지시)

| 큐 | 방향 | GroupId | 그림 간선 |
|---|---|---|---|
| `ProposalConfirmed.fifo` | matching → platform (파티 생성) | `proposalId` · DLQ | app:matching ≫ *outbox 발행* → 큐 → *파티 생성* → app:platform |
| `PartyClosed.fifo` | platform → platform (recent_players) | `partyId` · DLQ | app:platform → 큐 → *recent_players 구축* → app:platform |
| `BlockChanged.fifo` | platform → matching (차단 갱신) | 차단 쌍 · 순서 보장 · DLQ | app:platform → 큐 ≫ *차단 목록 갱신* → **app:matching** |

즉 `app:matching`은 **`ProposalConfirmed.fifo`의 유일한 생산자**이고
**`BlockChanged.fifo`의 유일한 소비자**다.

### Redis Pub/Sub → SSE (사용자 알림)

| From | 라벨 | To |
|---|---|---|
| app:matching ≫ | **publish `MATCH_*` 5종** | ElastiCache Redis |
| app:reservation-batch | publish `RESERVATION_*` 2종 | ElastiCache Redis |
| app:platform | publish `PARTY_*`·`FRIEND_*` 7종 | ElastiCache Redis |
| ElastiCache Redis | subscribe | app:realtime |
| app:realtime | SSE 14종 (heartbeat 15~30초) | 웹 클라이언트 A |

**`app:matching`은 브라우저에 직접 이벤트를 보내지 않는다.** Redis에 publish까지만 하고,
연결을 들고 있는 `app:realtime`이 SSE로 배달한다. 5 + 2 + 7 = 14종이 맞아떨어진다.

### WebRTC (매칭 범위 밖)

| From | 라벨 | To |
|---|---|---|
| 웹 클라이언트 A | WebRTC 직결 · Opus 음성 + 텍스트 | 웹 클라이언트 B |
| 웹 클라이언트 A / B | TURN relay | Cloudflare TURN |

주석: `Opus 음성 + 텍스트 DataChannel / 서버 경유 없음`.

---

## 3. `app:matching`의 통신 상대 — 정리

| 상대 | 방향 | 무엇을 | 이 저장소의 구현 상태 |
|---|---|---|---|
| ALB (← 클라이언트) | 수신 | 실시간 매칭 REST | **구현됨 (2026-09-17 갱신)** — `POST`/`GET`/`DELETE /api/v1/match-requests` 와 `POST /proposals/{id}/accept\|decline` 이 전부 동작한다 (`MatchingController` · `ProposalController`). 501 스텁은 없어졌다. 다만 **조회 경로가 계약과 다르다** — 계약 `GET /match-requests/{requestId}` vs 구현 `GET /match-requests?userId=` (`contracts/README.md` #5). `GET /games` 는 여전히 없다 |
| ElastiCache Redis | 읽기·쓰기 | gameconfig 읽기, 활성 요청 선점, 파티 색인, Lua atomic claim | **구현됨** (`backend/src/main/resources/redis/*.lua`) |
| ElastiCache Redis | 분산 락 | 후보 풀 락 (`qm:lock:pool:*`, Redisson) | **구현됨** (`redisLock/PoolLock.java`, `config/redis/RedissonConfig.java`) |
| ElastiCache Redis | publish | `MATCH_*` 5종 알림 | **구현됨 (2026-09-16 갱신)** — `notification/PushPublisher.java` 가 `qm:pubsub:push:{userId}` 로 **5종 전부** 발행한다. `MATCH_PROPOSAL_EXPIRED` 는 `service/ProposalExpiryService`(만료 스위퍼), `MATCH_CONFIRMED` 는 `service/ProposalService#accept()`(확정 뒷정리)가 낸다 |
| RDS `social.blocks` | 읽기 | INV-6 검증 | **미구현** — 부르는 코드는 있다 (`LolCandidateRule#canJoin` 의 차단 선필터). 그런데 Flyway 미도입이라 스키마가 없어 기본 실행에서는 그 조회가 실패한다 |
| SQS `ProposalConfirmed.fifo` | 발행 | 확정된 제안 → 파티 생성 | **미구현** — AWS SDK 의존성이 없다. 확정과 Redis 쪽 뒷정리(`proposal/cleanup-confirmed.lua`)·`MATCH_CONFIRMED` 알림까지는 붙었고, `matching.outbox` 기록과 발행만 남았다 |
| SQS `BlockChanged.fifo` | 소비 | 차단 목록 갱신 | **미구현** |
| CloudWatch | 송신 | 로그·지표 | 부분 — `/actuator` 노출은 켜져 있다 (`application.yaml`) |
| Secrets Manager | 읽기 | DB 자격증명 | 아직 없음 — `application.yaml` 이 `DB_URL`/`DB_USER`/`DB_PASSWORD` 환경변수로 받는다 (기본값은 H2 인메모리) |
| ECR | — | 태스크 이미지 | 해당 없음 (Dockerfile 없음) |

**`app:matching`이 절대 하지 않는 것** (그림상 다른 상자의 책임):
- 브라우저와 직접 연결을 유지하는 것 (→ `app:realtime`)
- 파티를 DB에 만드는 것 (→ `app:platform`이 `ProposalConfirmed.fifo`를 소비해서 한다)
- 예약 REST를 서빙하는 것 (→ `app:platform`)
- 예약 짝 찾기 배치 (→ `app:reservation-batch`)
- 인증/JWT 발급 (→ `app:platform`의 account)

---

## 4. 그림의 각주 6개 (라벨 원문)

1. 배포 단위 4개. `app:realtime`만 장수명 연결(SSE + WebSocket)을 보유하고 나머지 셋은 stateless다.
2. 서버 간 작업 지시는 transactional outbox + SQS FIFO(유실 시 데이터가 어긋난다), 사용자 알림은 Redis Pub/Sub → SSE(놓쳐도 새로고침으로 복구). 성격이 달라 도구를 나눴다.
3. Redis Streams는 컨슈머 그룹으로 읽으면 순서가 깨져 `BlockCreated` / `BlockRemoved` 역전이 가능하다. SQS FIFO는 `MessageGroupId` 단위로 순서를 보장한다.
4. ~~INV-6은 2단계로 보증한다. 후보 필터링은 Redis O(1), 최종 claim 직전에는 `shared_read.blocked_pairs`를 동기 SELECT 한다.~~ **바뀌었다 (docs/11 D-1·D-2).** 확정 직전 `social.blocks` 동기 SELECT 한 겹으로 한다. Redis 선필터는 보류. 그림 파일(`aws-architecture.drawio`)은 아직 원안 그대로다.
5. 음성·텍스트는 브라우저 직결이라 서버를 거치지 않는다. Fargate는 host network가 없어 coturn을 올릴 수 없으므로 Cloudflare 관리형 TURN을 쓴다.
6. 실제로는 2개 가용영역에 분산한다. NAT Gateway는 월 $43이라 쓰지 않고 public subnet + 보안그룹 인바운드 차단으로 대체한다. RDS Multi-AZ와 ElastiCache Replica는 비용 2배라 도입하지 않았다.

## 5. 범례

| 선 | 뜻 |
|---|---|
| — | 동기 요청 |
| — | SQS FIFO (서버 간 작업 지시) |
| — | Redis Pub/Sub → SSE (사용자 알림) |
| — | WebRTC 미디어 (브라우저 직결) |

---

## 6. 이 그림을 다시 파싱하는 방법

`.drawio`가 평문 XML이라 아래 한 줄로 노드/간선을 다시 뽑을 수 있다.
(그림이 바뀌면 이 문서도 같이 고쳐야 한다.)

```bash
python3 - <<'EOF'
import re, xml.etree.ElementTree as ET
cells = {c.get('id'): c for c in ET.parse('docs/aws-architecture.drawio').getroot().iter('mxCell')}
def lbl(i):
    c = cells.get(i)
    if c is None: return f'<{i}>'
    return ' '.join(re.sub(r'<[^>]+>', ' ', c.get('value') or '').split()) or f'<{i}>'
for i, c in cells.items():
    if c.get('edge') == '1':
        print(f'{lbl(c.get("source")):40s} --{lbl(i):35s}--> {lbl(c.get("target"))}')
EOF
```
