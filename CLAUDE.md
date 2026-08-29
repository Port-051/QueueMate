# CLAUDE.md — QueueMate Non-Negotiable Rules

Claude Code는 작업 전에 이 파일과 `docs/`를 읽어야 한다.

## 1. Product boundary
QueueMate는 **조건 기반 팀원 자동 랜덤 매칭** 서비스다.

반드시 지킨다:
- 지원 게임: LoL, VALORANT, PUBG만.
- 상대팀/VS/대전 상대를 만들거나 보여주지 않는다.
- 공개 사용자 탐색, 게시판, 길드, 피드, 팔로우, 좋아요, 공개 채팅방을 만들지 않는다.
- 프리미엄/과금 기능을 구현하지 않는다.
- 친구는 매칭 후 관계 유지 기능이며 SNS 추천 시스템이 아니다.
- 차단 관계의 사용자는 어떤 매칭에서도 같은 파티가 될 수 없다.

## 2. User-facing matching conditions
조건을 임의로 추가하지 않는다.

### LoL
- 게임 모드
- 희망 포지션
- 음성 사용
- 플레이 목적

### VALORANT
- 게임 모드
- 선호 역할군
- 음성 사용
- 플레이 목적

### PUBG
- 게임 모드
- 플레이 스타일
- 음성 사용
- 플레이 목적

### 예약 매칭 추가 조건
- 플레이 가능한 시간: 30분 단위 start/end
- 플레이할 양: `ONE_GAME` / `TWO_PLUS`

새 조건은 `docs/12_ELBOW_CONDITION_SELECTION.md` 절차를 거치기 전에는 추가 금지.

## 3. Architecture
- React SPA. Next.js로 변경 금지.
- Java Spring Boot modular monolith. 임의의 마이크로서비스 분리 금지.
- PostgreSQL = 영속 source of truth.
- Redis = 활성 매칭/예약 인덱스/lock/presence/rate-limit.
- Kafka/RabbitMQ 추가 금지. 현재 규모에서 불필요하다.
- WebRTC = 파티 음성 + 텍스트 DataChannel.
- Spring WebSocket = WebRTC signaling + 서버 이벤트만.

## 4. Correctness invariants
아래를 깨는 구현은 완료가 아니다.
- INV-1: 한 사용자는 활성 실시간 매칭 요청을 1개만 가진다.
- INV-2: 한 사용자는 동시에 하나의 활성 proposal에만 속한다.
- INV-3: 파티 인원은 mode의 target party size를 넘지 않는다.
- INV-4: proposal의 모든 참가자가 accept하기 전에는 party 확정 금지.
- INV-5: expired/declined/cancelled proposal은 다시 confirm될 수 없다.
- INV-6: block 관계 사용자는 같은 proposal/party에 들어갈 수 없다.
- INV-7: 동일 사용자의 PartyMember 중복 금지.
- INV-8: 게임별 hard rule 위반 파티 생성 금지.
- INV-9: 사용자는 시간이 겹치는 활성 예약을 중복 등록할 수 없다.
- INV-10: Redis 장애 시 중복 매칭을 감수하는 fallback 구현 금지. 새 매칭을 fail-closed 한다.

## 5. Contract first
`contracts/openapi.yaml`, `contracts/events.md`가 팀 간 계약이다.
- 소유 영역 밖 계약을 임의 변경하지 않는다.
- 변경 필요 시 먼저 contract 변경 commit을 만든다.
- Frontend는 mock adapter를 먼저 만들어 backend 완성 전에도 개발 가능해야 한다.

## 6. UI rules
- desktop web 16:9를 1차 기준으로 한다.
- 좌측 고정 navigation + dark navy/black + purple accent 디자인을 유지한다.
- `design/README.md`의 스크린샷은 시각 참고다. 이미지 속 잘못된 게임/과금/상대팀/조건은 구현하지 않는다.

## 7. Definition of done
기능 완료 조건:
1. happy path 구현
2. 실패/중복/timeout 처리
3. 테스트 추가
4. 로그/metric 포인트 추가
5. API contract 불일치 없음
6. 해당 invariant 검증

## 8. Commit convention
AngularJS commit convention을 따른다. 형식: `type(scope): subject`

- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- scope: `auth` `user` `party` `social` `realtime` `common` `matching` `reservation` `gameconfig` `frontend` `infra` `contracts` `harness` `docs`
- subject: 명령형 현재형, 50자 이내, 끝에 마침표 없음
- body: 무엇을/왜를 쓴다. 어떻게는 코드가 말한다
- footer: `BREAKING CHANGE: <설명>`, revert는 `revert: <원 subject>` + 원 commit hash

규칙:
- shared 파일(`contracts/**`, `docs/**`, `CLAUDE.md`) 변경과 feature 구현을 한 커밋에 섞지 않는다.
- scope는 `docs/10_TEAM_PARALLEL_PLAN.md`의 owner directory와 일치시킨다.
