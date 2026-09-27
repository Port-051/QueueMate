# 실제 두 계정 방·채팅 파일럿

2026-09-27 사용자 승인 범위: 현재 UI에서 두 계정이 실제로 방 생성·입장·메시지 송수신을 확인한다.

Spring/PostgreSQL이 방과 메시지의 정본이다. `queuemate.rooms.enabled=true`로 명시적으로 켠다. 기존 proposal/party 계약을 바꾸지 않고 현재 방 UI의 소규모 파일럿 API를 추가한다. 방 생성부터 대화 가능하며, 정원 충족/방장 마감은 대화를 종료하지 않는다. 한 사용자 한 방, 이전 방 이동은 입장 성공과 한 트랜잭션이다. 권한·정원·포지션·모집 티어 범위·차단 관계를 서버에서 검사한다. 외부 게임사의 실제 큐 진입 가능 여부 및 Riot 전적 확장은 후속 범위다.

채팅은 현재 방 멤버만 보내고 읽는다. 최신 200개를 복원한다. 클라이언트 메시지 ID로 중복 전송을 제거한다. 마지막 멤버가 나가면 방과 메시지는 함께 삭제한다. 본문은 로그에 남기지 않는다. 이 방 파일럿의 저장 정책이 기존 docs/13의 채팅 비저장 방침에 우선한다. 음성 녹음은 하지 않는다. 실제 음성, 개인 DM 서버 연결, 서버 추천 엔진, 자동 마감은 이번 단계에 포함하지 않는다. 추천 UI는 공유된 서버 방 목록을 대상으로 기존 조건 계산을 사용하며 입장은 서버에서 재검사한다.

방 권한과 정원은 PostgreSQL 트랜잭션 잠금 및 멤버 유일 제약으로 보호한다. 신규 생성/입장은 Redis 장애 때 거절한다. 실시간 이벤트 유실은 재연결/포커스 및 주기적 스냅샷 재조회로 복구한다. 데모 저장소 데이터는 실제 데이터에 섞지 않는다.

## 로컬 실행

필요 도구: Docker(또는 OrbStack), Java 21, Node.js/npm, Python 3. 저장소 루트에서 실행한다.

```sh
# 터미널 1: 별도 DB/Redis와 실제 Spring 서버
./scripts/room-demo-server.sh
```

Spring 시작이 완료되면:

```sh
# 터미널 2: 데모 계정 준비와 프론트
python3 scripts/seed-room-demo.py
npm --prefix frontend ci
npm --prefix frontend run dev:rooms
```

- A: http://localhost:5174/login → `데모 A로 시작`
- B: http://127.0.0.1:5174/login → `데모 B로 시작`
- 두 호스트의 브라우저 저장소가 분리되므로 같은 내장 브라우저에서 동시 로그인할 수 있다.
- 계정: `demo-a@queuemate.local`, `demo-b@queuemate.local`, 비밀번호: `QueueMate123!`.
- 생성 스크립트는 재실행 가능하다. 게임 ID는 `QueueMateDemoA#DEMO`/`QueueMateDemoB#DEMO`라는 테스트용 등록값이며 게임사 본인 인증·실제 전적이 아니다.
- A에서 인원 2명, 내 포지션 탑, 찾는 포지션 서포터로 방을 만든다. B에서 빈자리 참여를 확인하고, A는 `방 채팅`으로 전환해 대화한다. 방은 가득 차도 채팅할 수 있다.

프론트/백엔드는 로컬 주소에만 바인딩한다. PostgreSQL 55432, Redis 56379를 사용해 기존 5432/6379 서비스와 분리한다. 방·메시지는 Docker 볼륨에 유지되어 서버 재시작 후에도 읽을 수 있다. 각 팀원이 자기 컴퓨터에서 실행하면 독립 DB이며, 같은 데이터가 자동 공유되지는 않는다. GitHub Pages 정적 데모에는 이 로컬 서버가 연결되지 않는다.

종료는 프론트·백엔드 터미널에서 Ctrl+C 후 `docker compose -f compose.room-demo.yml stop`. 볼륨은 유지한다.

## 검증

```sh
cd backend
./gradlew test --tests '*SharedRoomIntegrationTest' --tests '*EventFanoutIntegrationTest'
cd ../frontend
npm run build
# 위의 실제 파일럿 백엔드가 켜져 있어야 함. 임시 테스트 계정은 별도로 생성한다.
npx playwright test -c playwright.rooms-live.config.ts
# 기존 Mock UI와 오류·재시도 호환성
npx playwright test --project=room-decks --grep '다섯 명 방을|요약 확인 뒤|빈자리 참여 저장|채팅 저장이 실패|다른 방 이동'
```

2026-09-27 결과: 서버 통합 13개, 실제 서버 브라우저 시나리오 1개, 기존 Mock 회귀 5개 통과. 브라우저 시나리오는 두 독립 계정 로그인·공유 방 생성·참여·WS 이벤트 수신·메시지 실패 후 재전송·양방향 대화·새로고침 복원·VAL/PUBG 수동 전적 입력 제거를 확인한다. 실기 브라우저에서도 A/B 방과 메시지를 확인했다.

현재 변경은 소규모 파일럿이다. 목록 전체 조회·방 변경 전역 알림·단일 트랜잭션 잠금은 구현 단순화를 위한 선택이며 운영 규모에서는 페이지/커서, 게임별 구독 범위, 방별 잠금과 일관된 이동 잠금 순서로 개선해야 한다. 실제 음성 버튼은 비활성화하고 준비 중으로 표시한다.
