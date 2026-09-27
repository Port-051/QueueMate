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

> **낡은 항목 주의 (2026-09-19 추가).** **#22 와, #7 에 달린 "개정됨 → #22" 줄은 D-9 로
> 개정됐다.** WebSocket(`/ws`)은 없어지고 `WEBRTC_SIGNAL` 도 SSE 로 받는다(SSE 14종 → 15종).
> 같은 이유로 두 항목의 본문은 고치지 않았다. D-9 는
> [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 D-9 가 우선한다.

> **낡은 항목 주의 (2026-09-19 추가).** **#14 의 "게시판" 금지와 `docs/00_PRODUCT_SPEC.md` §6 의
> 해당 부분("게시판/LFG 글 작성")은 D-11 로 개정됐다.** 파티 모집 게시판이 제품에 들어온다. #14 의
> 나머지(커뮤니티/길드/피드/프리미엄)는 그대로 범위 밖이다. 같은 이유로 #14 의 본문은 고치지 않았고,
> `docs/00` 은 원문 사본이라 고치지 않았다. **D-9 의 "같은 파티원인지 확인"도 게시판 방에서는 "같은 방에
> 들어와 있는 사람인지 확인"으로 읽는다** (D-9 본문은 고치지 않았다). D-11 은
> [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 D-11 이 우선한다.

> **낡은 항목 주의 (2026-09-19 추가).** **#21 의 큐 3개 가운데 `BlockChanged.fifo` 와 그 "적용 — INV-6"
> 줄(그리고 같은 내용인 #18 · #19 의 1단계 `qm:block:{userId}` read model)은 D-12 로 폐기됐다.** D-2 의
> "보류"도 D-12 로 "폐기"가 됐다. **#21 의 `PartyClosed.fifo` 는 D-13 으로 소비자가 `app:platform` 하나로
> 확정됐다** — `app:matching` 은 그 큐를 읽지 않는다. 같은 이유로 그 항목들의 본문은 고치지 않았다
> ("매칭 엔진에 걸리는 결정 색인" 표의 #19 · #21 줄에는 그 표의 관례대로 D-12 · D-13 을 가리키는 말만 덧붙였다). D-12 ·
> D-13 은 [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 그쪽이 우선한다.

> **낡은 항목 주의 (2026-09-19 추가).** **#24 의 "예약 REST 는 `app:platform` 이 서빙한다, `module:reservation`
> 을 두 앱이 함께 의존한다"와 #26 의 Lambda 기각은 D-15 로 개정됐다.** 예약(등록 REST + 짝 찾기 배치)은
> Lambda 로 빠지고, `app:reservation-batch` 는 `app:reservation` 이 대체한다. 같은 이유로 #15 의 모듈 목록
> (`app:platform` 의 reservation, `app:reservation-batch`)도 걸러 읽는다. #23(1분 주기 · 리드타임 30분)과
> #24 의 나머지(배치는 하나만, `runOnce()` 코어, deadman 감시)는 그대로다. 같은 이유로 그 항목들의 본문은
> 고치지 않았다. D-15 는 [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면
> D-15 가 우선한다.

> **낡은 항목 주의 (2026-09-19 추가).** **#15 의 배포 단위 구성과 #26 의 `app:party` 분리 기각은 D-16 으로
> 개정됐다.** 방을 별도 서비스 `app:room` 으로 분리한다. **D-9 의 시그널 `POST` 수신 앱과 D-11 의 6번 · 16번의
> "`app:platform`" 은 `app:room` 으로 읽는다**(방 안의 일 · 활성 요청 키 · 시그널). 배포 단위는 이제
> `app:matching` / `app:platform` / `app:room` / `app:realtime`(notification) / `app:reservation`(Lambda) 다섯이다.
> 같은 이유로 그 항목들의 본문은 고치지 않았다. D-16 은
> [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 D-16 이 우선한다.

> **낡은 항목 주의 (2026-09-19 추가).** **#23 의 "1분 주기"와 그에 딸린 리드타임 · tier 완화 서술은 D-17 로
> 개정됐다.** 예약 짝 찾기 배치는 요일 구분에 따라 하루 중 정해진 시각에만 돈다. #23 의 "배치 단일 경로"
> (등록 시 즉시 탐색을 하지 않는다)는 그대로다. 최소 리드타임 30분과 시간 기반 tier 완화가 어떻게 되는지는
> **미정**이다. #24 의 "`BATCH_MODE=daemon`" · "상시 1개"도 D-15 · D-17 로 걸러 읽는다. 같은 이유로 그 항목들의
> 본문은 고치지 않았다. D-17 은 [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고,
> 겹치면 D-17 이 우선한다.

> **낡은 항목 주의 (2026-09-19 추가).** **#20 의 "현재 기준은 Stage 1이다"와 Stage 1 → 2 이행 트리거는 D-18 로
> 개정됐다.** Stage 1(단일 EC2 + Docker Compose)을 적용하지 않고, 배포 기준은 Stage 2(ECS Fargate)다.
> "k8s 매니페스트/HPA/sticky session 을 전제한 구현을 만들지 않는다"와 "단계가 바뀌어도 안 바뀌는 것"은
> 그대로다. #15 · #24 등 다른 항목이 "Stage 1 은 단일 EC2 의 Docker Compose 이므로 …"라고 적은 곳도 같이 걸러
> 읽는다. 같은 이유로 그 항목들의 본문은 고치지 않았다. D-18 은
> [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 D-18 이 우선한다.

> **낡은 항목 주의 (2026-09-19 추가).** **D-11 16번("9번과 15번은 활성 요청 키 하나로 지킨다")과 그에 딸린
> D-11 의 "아직 미정"(16번 세부) · "영향" 대목, D-16 의 "`app:room` 이 활성 요청 키를 쓰고 지운다" · "이 저장소의
> 코드는 바뀌지 않는다"는 D-19 로 개정됐다.** 키를 둘로 나눈다 — `app:matching` 은 활성 요청 키
> `qm:user:active-request:{userId}` 를, `app:room` 은 입장 표시 키 `qm:user:active-room:{userId}` 를 쓰고, 상대
> 키는 `EXISTS` 로 있는지만 본다. 방에 있는 사용자의 매칭 요청은 409 `IN_ROOM` 이다. **D-13 · D-16 이 가능성으로
> 적은 "방(파티)이 닫힐 때 방을 맡는 앱이 활성 요청 키를 지워 `status=PARTY` 를 푼다"도 없어졌다.** 위 D-16
> 주의가 "활성 요청 키"의 주어를 `app:room` 으로 읽으라고 한 것도 D-19 로 걸러 읽는다. D-11 의 9번 · 15번(규칙
> 자체)은 그대로다. 같은 이유로 그 항목들의 본문은 고치지 않았다. D-19 는
> [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 D-19 가 우선한다.

> **낡은 항목 주의 (2026-09-20 추가).** **D-11 "아직 미정"의 "14번(차단)의 범위" 가운데 첫 물음(방장과의
> 사이에서만인가, 방 안에 있는 누구와든인가)은 D-20 으로 정해졌다 — 방 안의 누구와든이다.** 입장권을 내줄 때도
> 같은 규칙이다. **D-16 "두 앱을 잇는 방법" 2번이 적은 `app:platform` 의 방 키 읽기("방이 살아 있는가 · 몇
> 명인가")는 D-20 으로 멤버 SET 의 `SMEMBERS`(누가 있는가)까지 늘었다** — 게시판 목록이 방 안 사람들의 카드를
> 보여 주기 때문이다. 읽기만 하고 쓰지 않는다는 점은 그대로다. **D-11 · D-16 "영향"의 "알림 서비스는 바뀌지
> 않는다"도 D-20 으로 걸러 읽는다** — 목록을 F5 없이 갱신하려고 알림 서비스에 "주제 채널 구독"이 생긴다(정해졌고
> 구현 전이다). D-11 14번의 규칙 자체(차단 관계면 목록에 보이지 않는다 · 방향을 가리지 않는다)와 "공개 사용자
> 탐색 금지"는 그대로다. 같은 이유로 그 항목들의 본문은 고치지 않았다. D-20 은
> [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 D-20 이 우선한다.

> **낡은 항목 주의 (2026-09-20 추가).** **D-11 · D-16 "아직 미정"의 방 안의 일 — 방 키의 이름 · 구조 · 수명, 접속 확인의
> 방법과 주기, 방장 이탈의 판단(명시적 나가기만인가 연결 끊김도인가), 방이 처음 만들어지는 시점, 13번(방장 확정)의
> 대상과 확정 뒤의 빈자리, 만석일 때 눌렀을 때의 응답, `app:room` 이 발행하는 알림의 종류와 이름 — 과 D-9 "아직 미정"의
> 시그널 `POST` 경로 · 요청/응답 스키마 · `payload` 는 D-21 로 정해졌다.** `app:room` 의 요청 아홉 가지가 구현돼 있고
> 계약은 `../room/contracts/room-api.md` 다 — D-9 "아직 미정"의 "시그널 `POST` 의 구현은 아직 없다"도 걸러 읽는다.
> **D-16 "두 앱을 잇는 방법" 3번이 가능성으로 적은 "`app:platform` 이 '닫힘' 표시를 쓰고 `app:room` 이 확인한다"와 D-9 의
> *(제안 사항)* "`PUBLISH` 반환값(구독자 수)을 `POST` 응답에 싣는다"는 받지 않았다.** 확정 표시는 `app:room` 이 자기 키
> `qm:room:{roomId}:confirmed` 에 쓰고 `app:platform` 은 읽기만 한다 — 같은 절 2번의 "쓰지는 않는다(단 아래 3)"에서 단서가
> 없어진다. **D-11 11번의 "방장이 나가면"은 방장의 연결 끊김도 포함한다**(방의 수명은 방장의 접속 확인만 늘린다). 입장권 ·
> 인증 · 강퇴당한 사람의 재입장 · 자동 매칭으로 확정된 파티의 방 · `status=PARTY` 해제는 그대로 미정이다. 같은 이유로 그
> 항목들의 본문은 고치지 않았다. D-21 은 [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면
> D-21 이 우선한다.

> **낡은 항목 주의 (2026-09-20 추가).** **D-20 (다)의 "채널 이름은 `qm:pubsub:board:{game}` 이다" · "클라이언트는 SSE 를 열 때
> 쿼리 파라미터 `topics=board:LOL` 로 게시판 구독을 알린다" · "알림 서비스가 그 채널을 구독해 '게시판을 보고 있는 연결'에 SSE
> 로 흘려보낸다" · "연결이 사용자 채널 말고 주제 채널도 구독할 수 있다"와, D-20 · D-21 "아직 미정"의 "`app:room` 이 어느 게임의
> 채널에 발행할지를 어떻게 아는가" · D-20 "아직 미정"의 "한 연결이 여러 주제를 구독할 때의 `topics` 표기"는 D-22 로
> 개정됐다.** 게시판 채널은 게임을 구분하지 않는 **`qm:pubsub:board` 하나**이고, **`topics` 파라미터는 없앴다** — 알림 서비스는
> 게시판 신호를 살아 있는 모든 SSE 연결에 그대로 보내고, **거르는 것은 클라이언트다**(게시판 페이지를 보고 있으면 목록을 다시
> 받고, 아니면 무시한다). 발행하는 쪽이 게임을 알 필요가 없어 위 두 미정은 **물음째 없어졌다.** D-20 · D-21 과 위 D-20 주의가
> "구현 전"이라고 적은 알림 서비스의 게시판 채널 구독은 **구현됐다**(`app:room` · `app:platform` 의 발행은 아직 없다). D-20 ·
> D-21 "영향"이 계약에 올릴 것으로 든 "`topics` 파라미터"도 D-22 로 걸러 읽는다. `type` `BOARD_CHANGED` · `payload` `{}` ·
> `roomId` 를 싣지 않는다는 점 · 발행하는 앱과 시점 · 이 신호가 "다시 받아라"일 뿐이라는 점 · 알림 서비스가 본문을 열어 보지
> 않는다는 점은 그대로다. 같은 이유로 그 항목들의 본문은 고치지 않았다. D-22 는
> [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 D-22 가 우선한다.

> **낡은 항목 주의 (2026-09-21 추가).** **D-21 결정 4 의 "방장이 나가면 방을 통째로 없앤다 — … 확정 뒤에도 마찬가지다" · 결정 6 의
> "방장이 나가면(또는 말없이 사라지면) 확정 전과 똑같이 방이 통째로 없어진다" · 결정 3 의 "방의 수명(방장 키 · 멤버 SET · 확정 표시
> 키)은 방장의 신호만 늘린다"와, D-20 "감수하는 것"의 "수명이 다해 없어진 방은 신호를 내지 못한다"(D-21 · D-22 "감수하는 것"이
> 그대로라고 받은 것 포함), D-20 · D-21 · D-22 가 `app:room` 의 게시판 채널 신호 발행을 "구현 전" · "아직 없다"고 적은 곳(위 D-22
> 주의의 "`app:room` · `app:platform` 의 발행은 아직 없다" 포함)은 D-23 으로 개정됐다.** **확정한 방은 방장이 나가도 없어지지 않는다 —
> 방장 자리를 넘긴다.** 방장이 나가기를 부르면 입장 표시 키가 이 방을 가리키는(살아 있는) 멤버 가운데 한 명이 넘겨받고, 넘겨받을
> 사람이 없을 때만 방을 없앤다. 방장이 말없이 사라지면 — 확정한 방에 한해 일반 멤버의 접속 확인도 멤버 SET 과 확정 표시 키의
> 수명을 늘리고(방장 키는 늘리지 않는다), 방장 키가 만료된 뒤 처음 접속 확인을 보낸 멤버가 방장이 된다. **새 반환값 · 새 알림 · 새
> `payload` 필드는 없다** — 클라이언트는 `ROOM_MEMBER_LEFT` 의 `userId` 가 방장이면 방 안 사람 목록을 다시 조회한다. **"방장 키가
> 있다 = 방이 있다"와 방 키의 형식은 그대로이고, 확정한 방에서는 방장 키의 값이 바뀔 수 있다.** **확정하지 않은 방은 D-21
> 그대로다** — 방장이 나가면(명시적 나가기든 연결 끊김이든) 방이 통째로 없어지고 방의 수명은 방장의 신호만 늘린다. **`app:room` 의
> 게시판 채널 신호 발행은 구현됐고**(`app:platform` 의 발행은 아직 없다), 발행 시점에 **"접속 확인이 '방이 없어졌다'를 돌려줄 때"가
> 더해졌다** — 수명이 다해 없어진 방도 남아 있던 멤버의 다음 접속 확인(늦어도 1분 뒤)에서 신호가 나간다. 방장 혼자 있던 방은
> 여전히 신호를 못 낸다. 같은 이유로 그 항목들의 본문은 고치지 않았다. D-23 은
> [이 저장소에서 내린 결정](#이-저장소에서-내린-결정-2026-09-07) 절에 있고, 겹치면 D-23 이 우선한다.

> **낡은 항목 주의 (2026-09-26 추가).** **`app:platform` 쪽에서 소유자가 2026-09-21 ~ 2026-09-26 에 정한 것을 D-24 ~ D-34 로 옮겼다.**
> 그 가운데 옛 항목을 개정하는 것은 아래다. 같은 이유로 그 항목들의 본문은 고치지 않았고, 개정된 항목의 머리에 "**낡음 — D-xx 가 개정**" 한 줄만 달았다.
> 겹치면 D-24 ~ D-34 가 우선한다. 사실의 원본은 `../platform/CLAUDE.md` 와 `../platform/contracts/platform-api.md`(맨 아래 P-1 ~ P-23)다.
>
> - **인증(#16 · D-14)** — **#16 의 "Redis denylist, 조회 실패 시 fail-closed" 는 D-24 로 개정됐다 — access denylist 를 두지 않는다.** D-14 "아직 미정"의 쿠키 속성 · CSRF(`SameSite=Lax` +
>   `Origin` 검사) · 서명(RS256 — `app:platform` 만 서명, 공개 키는 환경변수, JWKS 없음) · 클레임(`token_use` 포함) · SSE 와 토큰 만료 · 로컬 CORS 는 D-24 로, 수명(access 15분 · refresh 7일)과
>   refresh 의 Redis 키(`qm:auth:refresh:{uuid}` · `GETDEL` rotation)는 D-26 으로 정해졌다.
> - **사용자 식별자(D-4 · D-14 결정 3)** — **"사용자 id 는 로그인 아이디 문자열"은 D-25 로 개정됐다 — `userId` 는 사용자 번호(bigint identity)이고 JWT 의 `sub` 는 그 십진 문자열이다.**
>   로그인 아이디는 `loginId` 로 따로 있다(**← 2026-09-26 에 없어졌다 — D-35**). **D-4 의 "`String` 으로 통일하면 변환 지점이 생기지 않는다"는 뒤집혔다** — `blocks` 의 두 칸이 bigint 가 됐다(`Block.java` 를 고쳐야 한다 — `HANDOFF.md` §0-4 (가)).
> - **DB(#17 · D-1 · D-3)** — **#17 의 schema-per-service · 크로스 스키마 FK/JOIN 금지 · 스키마별 DB 롤, D-1 의 "`matching` 롤에 `social.blocks` SELECT 권한 · 스키마 분리 유지", D-3 의 "H2 로는
>   롤 · GRANT 격리를 재현할 수 없다"는 D-34 로 낡았다.** 롤은 없고(2026-09-22) `app:platform` 의 스키마는 **`public` 하나**이며 JOIN · FK 가 허용된다(2026-09-26). **이 저장소가 읽는 테이블은
>   `blocks` 하나 그대로이고 이름이 `social.blocks` 에서 `public.blocks` 가 됐다.** 이 파일 곳곳의 "`social.blocks`" 는 `blocks` 로 읽는다. D-1 의 "뷰 대신 테이블을 양방향으로 직접 조회"는 그대로다.
> - **gameconfig(#15)** — **"gameconfig 는 `app:matching` 의 모듈이다"는 그대로이지만, D-29 로 `app:platform` 도 `qm:gameconfig:{GAME}:{MODE}`(`EXISTS`) · `:tier`(`ZSCORE`)를 읽어**
>   모집 글의 `mode` 와 게임 계정의 `tier` 를 검증한다(쓰지 않는다 · fail-open). `SharedKeys.GAMECONFIG_PREFIX` 와 seed 의 키 모양을 바꿀 때는 `app:platform` 과 같이 바꾼다.
> - **방(#15 · D-9 · D-11 · D-16 · D-19 ~ D-23)** — **D-16 의 `app:room` 분리는 D-33 으로 되돌려졌다 — 방 안의 일은 `app:platform` 의 `room` 패키지다.** 배포 단위는 다시 넷이다
>   (`app:matching` / `app:platform` / `app:realtime`(`notification`) / `app:reservation`(Lambda)). 포트 8083 · 입장권 · 방 만들기 요청(`POST /api/v1/rooms/{roomId}`) ·
>   확정 기록 요청(`POST /api/v1/posts/{postId}/confirm`)이 없어졌고, **글 쓰기가 방을 같이 만들며 방장 확정은 `POST /api/v1/rooms/{roomId}/confirm` 한 요청이 Redis 와 DB 를 같이 쓴다.**
>   D-16 주의가 "`app:room` 으로 읽으라"고 한 D-9 · D-11 6번의 `app:platform` 은 **다시 원문 그대로 맞다.** D-19 ~ D-23 의 "`app:room`" 은 `app:platform`(의 `room` 패키지)으로 읽는다.
>   **D-19 의 키 약속 자체는 그대로다** — 활성 요청 키는 이 앱이 쓰고 `app:platform` 은 `EXISTS` 만, 입장 표시 키는 `app:platform` 이 쓰고 이 앱은 `EXISTS` 만. D-21 결정 2(방 만들기 요청) ·
>   결정 6(확정 표시를 `app:room` 이 쓰고 `app:platform` 이 읽어 기록)은 D-33 으로 개정됐고, D-23 "아직 미정"의 "확정된 글에서 방장 키가 없을 때"는 D-33 으로 정해졌다(만료시키지 않는다).
>   `../room/…` 을 가리키는 경로는 합치기 전의 기록이다.
> - **게시판 목록(D-20)** — **D-20 의 ③(찾는 포지션 가운데 이미 방 안에 있는 것의 강조 — `filledPositions`)은 D-31 로 없어졌다.** ①(인원) · ②(방 안 사람들의 카드) · ④(F5 없이 갱신)는 그대로다.
>   목록의 페이지 · 정렬 · 보존 · `game` 필수(D-28), 방에 다른 사람이 있으면 글을 못 고치는 것(D-32), 전적(D-27 · D-30)은 옛 항목을 개정하지 않는 새 규칙이다.

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
    **낡음 — D-15 · D-16 · D-33 이 개정.** 배포 단위는 D-33(2026-09-25) 뒤로 `app:matching` / `app:platform` /
    `app:realtime`(`notification`) / `app:reservation`(Lambda) 넷이다. gameconfig 모듈은 그대로 `app:matching` 의 것이고,
    그 값을 `app:platform` 도 읽는다(D-29, 2026-09-24).
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
    **낡음 — D-24 가 개정.** access denylist 를 두지 않는다(2026-09-21). 세부는 D-14 · D-24 · D-26.
    - 근거: 서버 인스턴스를 N개로 늘려도 인증이 공유 상태를 타지 않고, WebSocket
      핸드셰이크에 토큰을 그대로 실을 수 있다. Redis 장애가 인증까지 번지지 않아
      INV-10의 fail-closed 범위를 새 매칭으로 한정할 수 있다.
    - 영향: refresh rotation을 필수로 한다. 정지/삭제 계정 즉시 차단은 stateless로
      불가능하므로 Redis denylist를 두고, denylist 조회 실패는 fail-closed 처리한다.
      access token TTL은 짧게 잡아 무효화 지연을 줄인다.
17. DB는 database-per-service가 아니라 schema-per-service로 나눈다. PostgreSQL
    인스턴스 1개에 `account` `gameconfig` `matching` `reservation` `party` `social`
    `shared_read` 7스키마를 둔다. (2026-08-31, `shared_read`는 #19에서 추가)
    **낡음 — D-34 가 개정.** 스키마별 DB 롤은 두지 않고(2026-09-22), `app:platform` 의 스키마는 `public` 하나이며
    테이블 사이의 JOIN · FK 를 허용한다(2026-09-26). 크로스 스키마 금지 · `db/migration/<schema>/` 분리도 낡았다.
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
| #15 | 배포 단위 4개 중 `app:matching` = matching + gameconfig, 스케일 기준 = Redis 큐 depth | 이 저장소가 떼어낸 박스가 바로 그것이다. (배포 단위 구성은 그 뒤 바뀌었다 — 예약은 Lambda 로 D-15, 방은 `app:room` 으로 D-16 — 그 분리는 D-33 으로 되돌려져 방은 `app:platform` 의 `room` 패키지다. `app:matching` 상자는 그대로다. gameconfig 값은 `app:platform` 도 읽는다 — D-29) |
| #17 | schema-per-service 7스키마 + 스키마별 DB 롤 | `matching` 롤은 `social` 스키마를 못 읽는다. 크로스 스키마 JOIN 금지. (**D-34 로 낡았다** — 롤은 없고 `app:platform` 의 스키마는 `public` 하나다. 이 앱이 읽는 것은 `blocks` 하나 그대로다) |
| #19 | INV-6 2단계 보장 (Redis 필터 → `shared_read.blocked_pairs` 동기 SELECT) | **이 저장소에서 뒤집었다 — 아래 D-1 참고.** 뷰 대신 `social.blocks`를 직접 읽고, Redis 선필터는 보류했다 (보류는 **D-12 로 폐기**가 됐다) |
| #21 | 앱 간 도메인 이벤트는 outbox + SQS FIFO | `matching`이 `ProposalConfirmed.fifo` 발행 / `BlockChanged.fifo` 소비. **`BlockChanged.fifo` 는 폐기됐다 — 아래 D-12 참고.** `PartyClosed.fifo` 도 이 앱은 읽지 않는다 (D-13) |
| #26 | 대안 기각 목록 중 **Redis Streams** | 컨슈머 그룹으로 읽으면 순서가 깨져 INV-6이 뒤집힌다. (같은 #26 의 **Lambda 기각**은 예약에 한해 개정됐다 — 아래 D-15 참고. 매칭에는 걸리지 않는다) |
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
- **#17 스키마/롤 격리** — DB가 없어 적용 대상이 없다. (**D-34 로 물음째 없어졌다** — 롤이 없고 스키마는 `public` 하나다.)

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

> **낡음 — D-34 가 개정(2026-09-26 표시).** "`matching` 롤에 SELECT 권한" · "스키마 분리와 크로스 스키마 금지는 유지"가 낡았다 — 롤은 없고 테이블은 `public.blocks` 다. 뷰 없이 양방향으로 직접 조회하는 것은 그대로다.

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

> **낡음 — D-34 가 개정(2026-09-26 표시).** "H2 로는 스키마별 롤 · GRANT 격리를 재현할 수 없다"는 격리할 롤이 없어 물음째 없어졌다.

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

> **낡음 — D-25 가 개정(2026-09-26 표시).** 사용자 id 는 사용자 번호(bigint)가 됐고 `blocks` 의 두 칸도 bigint 다 — "`String` 으로 통일하면 변환 지점이 생기지 않는다"는 뒤집혔다. 일련번호 PK · `@IdClass` 를 없앤 것은 그대로다.

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

### D-9. WebSocket(`/ws`)을 없앤다 — `WEBRTC_SIGNAL` 도 SSE 로 받고, 보내는 쪽은 `app:platform` 의 REST `POST` 다 (#22 개정, 2026-09-19)

> **다시 맞다 — D-33(2026-09-26 표시).** D-16 이 "시그널 `POST` 를 받는 앱"을 `app:room` 으로 읽게 했으나, D-33 으로 `app:room` 이 `app:platform` 에 합쳐져 원문의 `app:platform` 이 다시 맞다(그 `room` 패키지).

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템 전체 결정인 #22 의 개정이다. 원본 결정 로그가
> 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때 #22 의
> 개정 항목으로 올려야 한다. **#22 와 #7 의 본문은 고치지 않았다** (이 파일은 기록이다).

**결정.**

1. **WebSocket(`/ws`)을 없앤다.** `WEBRTC_SIGNAL` 도 다른 알림과 같은 SSE(`GET /api/v1/events`)로
   **받는다.** SSE 계약은 14종 → **15종**이 되고, 서버→클라 전송은 SSE 한 종류만 남는다.
2. **시그널을 보내는 방향(클라→서버)은 REST `POST` 다. 이 `POST` 는 `app:platform` 이 받는다.**
   `app:platform` 은 보내는 사람과 받는 사람이 **같은 파티원인지 확인한 뒤**
   `qm:pubsub:push:{상대 userId}` 에 봉투 `{type:"WEBRTC_SIGNAL", eventId, occurredAt, payload}` 를
   `PUBLISH` 한다. 파티를 소유한 앱이 `app:platform` 이라 그 확인을 할 수 있는 곳이 거기다.
3. **알림 서비스(`app:realtime`, 지금 이름은 `notification`)는 바뀌지 않는다.** 그 서비스는
   `type` 을 해석하지 않고 그대로 흘려보내므로 `WEBRTC_SIGNAL` 도 그대로 배달된다.

**근거.**

- #22 가 WebSocket 을 남긴 유일한 이유는 "클라→서버 방향이 있다"였다. 그 방향은 `POST` 로 충분하다.
- #22 와 docs/14 §5 가 "WebSocket 하나로 전부"를 기각한 논리 — "15종 중 1종 때문에 나머지의
  재연결·재개를 직접 구현하게 된다" — 가 **거꾸로도 성립한다.** 1종 때문에 WebSocket 스택 전체
  (의존성, 두 번째 연결 목록, WS 용 하트비트·재연결·인증)를 새로 만들고 운영하는 것이 과하다.
  `EventSource` 는 재연결이 표준에 들어 있고 WebSocket 은 직접 짜야 한다.
- `POST` 는 평범한 HTTP 라 `Authorization` 헤더를 붙일 수 있다. WebSocket 은 브라우저에서
  헤더를 붙일 수 없다.
- 알림 서비스가 파티 상태를 읽지 않아도 된다. "받은 것을 그대로 배달만 한다"는 그 서비스의
  경계가 유지된다.
- 시그널링은 통화를 맺을 때만 잠깐 오가는 소량 메시지다. WebSocket 의 상시 양방향이 필요한
  부하가 아니다.

**감수하는 것 / 운영 제약.**

- 통화를 시작할 때 ICE 후보마다 `POST` 가 나간다(5인 파티는 10쌍). **HTTP/2 를 전제한다.**
- 여러 `POST` 는 **도착 순서가 보장되지 않는다.** 받는 쪽은 offer 가 오기 전에 도착한 ICE 후보를
  모아 두어야 한다.
- Redis Pub/Sub 은 받는 쪽 SSE 가 끊긴 순간의 시그널을 버린다. **이것은 WebSocket 이어도 같다.**
  시그널은 서버에 저장되지 않으므로 상태 조회로 복구할 수 없고, **복구는 클라이언트가 한다.**
  복구의 기준점은 서버에 저장된 **파티원 목록**이다.
  - 답이 없으면 offer 를 다시 보낸다.
  - SSE 재연결 시 파티원 목록과 실제 peer 연결을 비교해 빠진 상대에게 재협상한다.
  - peer 연결이 `failed` 면 ICE restart 를 한다.
  - 동시 offer 충돌을 피하는 규칙을 둔다(예: perfect negotiation 의 polite/impolite, 또는
    userId 비교).
- *(제안 사항)* `PUBLISH` 반환값(그 순간 구독자 수)을 `POST` 응답에 실어 주면 클라이언트가
  "상대가 지금 연결 안 됨(0)"을 알고 기다리지 않고 재시도할 수 있다. 다만 0 이 아니어도 브라우저
  도착을 보장하지는 않는다.
- 음성 데이터 자체는 브라우저 직결(또는 TURN 중계)이라 서버를 거치지 않는다. 통화가 맺어진 뒤
  SSE 가 끊겨도 통화는 유지된다.

**바뀌지 않는 것.** #25 (Cloudflare 관리형 TURN, 단기 credential 발급 주체는 `app:realtime`)는
그대로다.

**아직 미정 — 임의로 지어내지 않는다.**

- `POST` 엔드포인트의 **경로와 요청/응답 스키마.** (후보로 `POST /api/v1/parties/{partyId}/signals`
  를 생각할 수 있으나 **후보일 뿐 정해진 것이 아니다.**)
- `WEBRTC_SIGNAL` 의 **`payload` 스키마.** 정해야 할 항목: 보낸 사람 식별자 / 파티 식별자 /
  종류(offer · answer · ICE candidate) / SDP 또는 candidate 본문 / 재협상 시도를 구분할 식별자.
- `app:platform` 은 이 저장소에 없다. 시그널 `POST` 의 구현은 아직 없다.

**#22 의 다른 낡은 서술 — 이미 다른 곳에서 바뀌었다.** 이 항목이 새로 정한 것은 아니고, #22 를
읽을 때 같이 걸러 읽으라고 적어 둔다.

- "`Last-Event-ID` 로 재개한다" — **재개하지 않는다.** 2026-09-18 에 "놓친 알림은 다시 보내지
  않고, 클라이언트가 재연결 직후 상태를 조회한다"로 바뀌었다 (`contracts/events.md` "재연결" 절).
- "연결을 들고 있는 인스턴스가 로컬에서 userId 로 필터 / 브로드캐스트 + 로컬 필터" — 지금은
  **사용자별 `SUBSCRIBE qm:pubsub:push:{userId}`** 다. 패턴 구독이 아니다
  (`../notification/CLAUDE.md` §5). sticky session 이 필요 없다는 결론은 그대로다.

**영향.**

- 이 저장소(`app:matching`)의 코드는 바뀌지 않는다. `WEBRTC_SIGNAL` 의 발행 주체는
  `app:platform` 이고, **`app:matching` 이 발행하는 것은 여전히 `MATCH_*` 5종**이다.
- `contracts/events.md` 의 전송 표가 SSE 하나로 줄고 14종이 15종이 됐다. 원본(queueMate
  `feature/frontend`)보다 이 사본이 앞서간 변경이라 `contracts/README.md` 에 기록해 두었다.
  `contracts/openapi.yaml` 은 고치지 않았다 — 시그널 `POST` 는 `app:platform` 소관이다.
- `docs/14_ARCHITECTURE_RATIONALE.md` §5 와 `docs/AWS_ARCHITECTURE.md` 에는 본문을 두고 이
  항목을 가리키는 주석만 달았다. `docs/aws-architecture.drawio` 그림에는 `/ws` 가 아직 남아 있다.
- 알림 서비스는 WebSocket 의존성, 두 번째 연결 목록, WS 용 하트비트·재연결·인증이 통째로 필요
  없어진다.

---

### D-10. SSE 하트비트를 주석 줄이 아니라 이름 있는 이벤트로 보내고, 재접속 대기 시간을 연결마다 흩는다 (#22 운영 제약 보강, 2026-09-19)

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템 전체 결정인 #22 의 운영 제약("SSE heartbeat
> 15~30초 필수")을 보강한다. 원본 결정 로그가 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에
> 먼저 적는다. 본 저장소와 합칠 때 #22 의 보강 항목으로 올려야 한다. **#22 의 본문은 고치지
> 않았다** (이 파일은 기록이다). #22 는 하트비트의 형식을 정하지 않았으므로 뒤집는 것은 없다.

**결정.**

1. **하트비트는 SSE 주석 줄이 아니라 이름 있는 이벤트다.** 알림 서비스(`app:realtime`, 지금 이름은
   `notification`)가 15~30초마다(현재 구현 20초) `event: heartbeat` / `data: heartbeat` 를 보낸다.
   `data` 의 내용에는 의미가 없다 — 데이터가 빈 이벤트는 브라우저가 디스패치하지 않아서 싣는다.
2. **클라이언트는 `es.addEventListener("heartbeat", ...)` 로 받아 연결을 감시한다.** 마지막으로
   하트비트(또는 알림)를 받은 시각을 기록하고, 일정 시간 아무것도 오지 않으면 연결을 닫고 새로 연 뒤
   상태를 조회한다(SSE 먼저, 그다음 조회). 기준 시간은 **권장값**이다 — 서버 간격의 상한이
   30초이므로 60초(두 번 연속 놓침) 정도.
3. **알림(envelope)은 계속 이름 없는 이벤트다.** `onmessage` 로 오고, 하트비트는 `onmessage` 로
   오지 않는다. 통로가 갈리므로 알림 처리 코드가 하트비트를 JSON 파싱할 일이 없다.
4. **서버는 연결할 때 한 번 SSE `retry:` 필드로 재접속 대기 시간을 내려 준다.** 값은 연결마다
   무작위다(현재 구현 기본 1000~2000ms, 설정으로 바꿀 수 있다). 알림마다 붙이지 않는다 — 브라우저가
   마지막 값을 기억한다. 클라이언트가 따로 할 일은 없다(`EventSource` 가 알아서 쓴다).

**근거.**

- 주석 줄은 `EventSource` 가 자바스크립트에 전달하지 않는다. 그래서 클라이언트는 **종료 신호 없이
  길만 사라진** 연결을 알아챌 재료가 없었다 — 공유기 재부팅, 와이파이는 잡혀 있는데 인터넷만 끊김,
  절전 복귀, 서버가 있는 기계가 통째로 죽음, 중간 프록시가 말없이 버림.
- 서버는 주기마다 **쓰기** 때문에 실패로 곧 안다. 브라우저는 **읽기만** 해서 실패할 일이 없고,
  운영체제가 알아챌 때까지 환경에 따라 1분 안쪽~수 분이 걸린다. 매칭 제안 수명이 20초라 그 시간이
  길다.
- `retry:` 를 흩는 이유는 재배포 직후 모든 클라이언트가 같은 순간에 재접속하고 각자 상태 조회까지
  하는 몰림을 피하려는 것이다.

**바뀌지 않는 것.** 하트비트의 기존 역할은 그대로다 — 프록시·로드밸런서 유휴 타임아웃(Stage 2 ALB
300초)보다 짧게 바이트를 흘리는 것, 서버가 쓰기 실패로 죽은 연결을 찾아 정리하는 것. 간격
15~30초(#22)도 그대로다. 연결 직후 서버가 보내는 `connected` 는 여전히 주석 줄이고 계약 대상이
아니다(클라이언트는 `onopen` 을 쓴다).

**감수하는 것.**

- 탭마다 20초에 한 번 자바스크립트가 깨어난다. 무시할 수준이다.
- **프런트가 감시 타이머를 구현해야 효과가 있다.** 리스너를 등록하지 않아도 동작에는 문제가 없지만
  (이벤트가 버려질 뿐이다) 그러면 감시를 하지 않는 것이다.

**영향.**

- 이 저장소(`app:matching`)의 코드는 바뀌지 않는다. 하트비트와 `retry:` 는 알림 서비스가 SSE 에 직접
  싣는 것이고 Redis Pub/Sub 을 거치지 않는다.
- `contracts/events.md` 의 "heartbeat" 절을 고쳐 쓰고, "재연결" 절에 `retry:` 항목을 넣었다. 원본
  (queueMate `feature/frontend`)보다 이 사본이 앞서간 변경이라 `contracts/README.md` 의 A-3 · A-4 에
  기록해 두었다.
- `docs/14_ARCHITECTURE_RATIONALE.md` §5 와 `docs/AWS_ARCHITECTURE.md` 는 고치지 않았다. 둘 다
  "heartbeat 15~30초"라는 간격만 적고 형식은 말하지 않아 낡아지지 않았다.

---

### D-11. 파티 모집 게시판을 제품에 넣는다 — 모집 글이 곧 파티방이고, 들어온 사람은 음성으로 바로 말을 건다 (#14 개정, 2026-09-19)

> **다시 맞다 — D-33(2026-09-26 표시).** 6번의 "`app:platform`(파티 모듈)"은 D-33 으로 다시 원문 그대로 맞다(방 안의 일은 그 `room` 패키지). 16번은 D-19 그대로다(주어 `app:room` → `app:platform`).

> **이 결정은 매칭 엔진의 결정이 아니다.** 제품 경계를 정한 #14 의 개정이다. 원본 결정 로그가
> 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때 #14 의
> 개정 항목으로 올려야 한다. **#14 의 본문은 고치지 않았다** (이 파일은 기록이다).

**결정.**

1. **"파티 모집 게시판"을 제품에 넣는다.** #14 · `docs/00_PRODUCT_SPEC.md` §6 · `CLAUDE.md` §1 이
   금지하던 것 가운데 **게시판 금지를 뒤집는다.**
2. **모집 글을 올리면 그것이 곧 파티방이다.** 자동 매칭으로 확정된 뒤에 생기는 파티방과 **같은
   개념의 방**을 쓴다. 게시판용 방을 따로 만들지 않는다.
3. **다른 사용자는 게시판에서 글을 보고 누르면 그 방 안으로 들어온다.**
4. **방 안에서는 서비스가 제공하는 음성(WebRTC, 브라우저 직결 — #6)으로 바로 말을 건다.**
5. **자동 매칭이 기본 경로이고, 게시판 모집은 그 옆의 두 번째 경로다.** 제품 정의("조건 기반 팀원
   자동 랜덤 매칭")는 그대로다.
6. **이 기능은 `app:platform`(파티 모듈)에 들어간다.** 파티를 소유한 앱이 거기다(#15).

방의 규칙 (같은 날 확정):

7. **방에 들어오는 것은 "둘러보러 온 상태"다.** 곧바로 파티원이 되는 것이 아니다.
8. **방장은 입장을 승인/거절하지 않는다** — 누르면 바로 들어온다. 단 **방장은 들어온 사람을 내보낼
   수 있다(강퇴).**
9. **자동 매칭 대기 중인 사용자는 게시판 방에 들어갈 수 없다.**
10. **한 방의 최대 인원은 방장 포함 5명이다.** 둘러보는 사람도 이 5명에 들어간다. 방에 있는 사람
    전부가 음성 메시에 붙으므로 **이 값이 곧 음성 인원 상한**이다.
11. **방장이 나가면 글은 지우지 않고 "만료"로 표시한다.** 만료된 글은 목록에 남지만 **눌러도 더 이상
    들어갈 수 없다.**
12. **음성 제어 두 가지를 제공한다** — 자기 마이크를 켜고 끄는 것, 특정 사람의 소리를 개별로 듣지
    않게/다시 듣게 하는 것. *(구현 메모)* 둘 다 브라우저에서 끝난다. 마이크는 자기 오디오 트랙을 끄는
    것이고 개별 음소거는 그 사람의 오디오 재생을 끄는 것이다. 음성은 서버가 중계하지 않으므로(#6)
    **서버 API 가 필요 없다.**
13. **둘러보는 상태에서 파티원이 되는 것은 방장이 확정한다.** 그리고 **방장이 확정하면 그 방에는 더
    이상 새 사람이 들어올 수 없다** — 모집이 닫힌다.
14. **차단 관계가 있으면 그 방이 목록에 아예 보이지 않는다.** 들어가지 못하게 막는 것이 아니라
    **보이지 않게** 한다. INV-6 이 방향을 가리지 않는 것과 같이 **어느 쪽이 차단했든** 보이지 않는다.
    검사 주체는 `social.blocks` 를 소유한 `app:platform` 자신이다 — 목록을 만들 때 거른다. 같은 앱
    안의 조회라 서비스 경계를 넘지 않는다.
15. **반대 방향도 막는다 — 게시판 방에 들어가 있는 사용자는 자동 매칭을 돌릴 수 없다.** 9번과 짝이다.
    즉 **한 사용자는 자동 매칭 대기와 게시판 방 중 한 번에 하나만** 할 수 있다. INV-2("한 사용자는
    동시에 하나의 파티에만")의 취지와 같다.
16. **9번과 15번은 `app:matching` 의 "활성 요청" Redis 키 하나로 지킨다.** 키는
    **`qm:user:active-request:{userId}`**(HASH)이고 원본 상수는 `redisKeys/SharedKeys.java` 의
    `ACTIVE_REQUEST_PREFIX = "qm:user:active-request:"` 다. `app:matching` 은 이 키로 "한 사용자는 한
    번에 하나의 요청만"(INV-1)을 지킨다 — 매칭 요청을 받으면 `redis/shared/claim-request.lua` 가
    `EXISTS` 로 이 키를 보고, 있으면 `0` 을 돌려주어 새 요청을 받지 않는다(409).
    - **사용자가 게시판 방에 들어올 때 `app:platform` 이 이 키에 값을 써 넣고, 방에서 나갈 때 지운다.**
    - 9번(대기 중이면 방에 못 들어간다): `app:platform` 이 써 넣으려 할 때 **이미 키가 있으면 입장을
      거절**한다.
    - 15번(방에 있으면 매칭을 못 돌린다): 키가 있으므로 **`claim-request.lua` 의 기존 검사가 그대로
      거절**한다. `app:matching` 에 새 검사 로직을 넣지 않아도 된다.
    - **서비스 간 호출이 없다** — #15 의 "경계를 넘는 동기 호출을 새로 만들지 않는다"와 부딪히지
      않는다. 두 앱은 이미 같은 Redis 를 쓴다(알림 발행).
    - **이것은 "`app:platform` 은 매칭 Redis 키를 직접 만지지 않는다"는 규칙의 예외이고, 예외는 이 키
      하나뿐이다.** `qm:party:*` · `qm:proposal:*` · `qm:gameconfig:*` 등 나머지 매칭 키는 여전히
      `app:platform` 이 읽지도 쓰지도 않는다.
    - **키 접두사는 알림 채널 접두사와 같은 위험을 갖는다.** `app:platform` 이 따로 적다가 오타를 내면
      컴파일도 테스트도 통과한 채로 검사가 조용히 무력화된다 — `app:platform` 은 늘 "키 없음"을 보고
      입장시키고, `app:matching` 은 `app:platform` 이 쓴 키를 보지 못한다. `app:platform` 안에서도 상수
      한 곳에만 두고, 원본이 `SharedKeys` 임을 적는다.
    - *(코드 메모)* 게임별 배정 스크립트(`redis/{lol,valorant,pubg}/create-or-check-party-untiered.lua` ·
      `create-or-check-party-tiered.lua` · `join-party.lua` · `join-party-tiered.lua`)와 `leave-party.lua`
      도 이 키를 `EXISTS` 로 보지만 **뜻이 반대다** — 키가 **없으면** 멈춘다(claim 의 60초 TTL 이 먼저
      끝났는지 확인하는 것이다). 새 요청을 막는 자리는 `claim-request.lua` 하나다.

**D-9 와의 관계 — D-9 의 "파티원"은 게시판 방에서 "방에 들어와 있는 사람"으로 읽는다.** D-9 는 시그널
`POST` 를 받으면 "보내는 사람과 받는 사람이 같은 **파티원**인지 확인"한다고 적었다. 게시판 방에서는
들어온 사람이 파티원이 아니므로(7번) 이 확인은 **"같은 방에 들어와 있는 사람인지 확인"** 이 된다.
확정 전에는 둘러보는 사람도 음성에 붙어 말을 걸 수 있어야 하기 때문이다. 방장이 확정한 뒤(13번)의
방에서는 방에 있는 사람과 파티원이 같아질 수 있으나 13번의 세부가 미정이라 단정하지 않는다. 자동
매칭으로 생긴 파티방에서는 D-9 가 그대로다. **D-9 의 본문은 고치지 않았다** (이 파일은 기록이다).

**근거.** 목적은 **모집의 응답성**이다.

- 자동 랜덤 매칭만으로는 채워지지 않는 수요가 있다 — **같이 할 사람을 직접 확인하고 고르고 싶은**
  사용자다.
- 글과 메시지로 하는 기존 방식의 모집은 **서로의 응답을 기다리는 시간이 길다.** 음성으로 말을 걸면
  응답이 빠르다.
- **방을 만든 사람**은 다른 일을 하고 있다가도 목소리가 들리면 바로 반응할 수 있다.
- **들어온 사람**은 말을 걸어 보고 응답이 없으면 바로 나가서 다른 방을 찾을 수 있다.
- 방과 음성은 이미 제품 범위 안에 있다(#6 의 파티 음성, D-9 의 시그널링). 새로 생기는 것은 **방으로
  들어가는 진입로 하나**다.

**여전히 금지인 것.**

- 길드, 피드, 팔로우, 좋아요, 프리미엄/과금(#14), 상대팀/VS(#9).
- **공개 사용자 탐색** — 사람을 검색하고 둘러보는 기능은 여전히 만들지 않는다. 게시판이 보여 주는
  것은 **사람 목록이 아니라 모집 글(방) 목록**이다.
- **공개 채팅방** — 이제 이 말은 **파티 모집과 무관한 잡담용 공개방을 만들지 않는다**는 뜻이다.
  모집 글에 딸린 방은 그 예외다.

**아직 미정 — 임의로 지어내지 않는다.** 나머지는 개발하면서 정한다. 구현하다 해당 지점에 닿으면
그때 묻고 정한다. 임의로 정해 구현하지 않는다.

- **13번(방장 확정)의 세부.**
  - 확정하는 대상이 **그 순간 방에 있는 전원**인가, **방장이 고른 사람만**인가. 고르지 않은 사람은
    확정 순간 방에서 나가게 되는가.
  - 확정된 뒤 글은 목록에서 어떻게 보이는가 — 만료(11번)와 같은 표시인가 다른 표시인가.
  - 확정된 방이 자동 매칭으로 생긴 파티방과 **같은 기능(Ready 등)** 을 갖는가. "최근 함께한 사람"은
    확정된 파티원 기준으로 기록하는가.
  - 확정 뒤 빈자리가 생기면 다시 모집을 열 수 있는가.
- **14번(차단)의 범위.**
  - 차단 관계를 **방장과의 사이에서만** 보는가, **방 안에 있는 누구와든** 보는가 — 방에 이미 들어와
    있는 제3자와 차단 관계인 사용자에게 그 방이 보이는가.
  - 목록을 본 뒤에 차단이 생긴 경우, 이미 같은 방에 있는 두 사람 사이에 차단이 생긴 경우.
- **16번(활성 요청 키)의 세부.** 방법은 정해졌고 서비스 간 호출이 없어 #15 와 부딪히지 않는다.
  남은 것은 아래다.
  - **HASH 에 무엇을 써 넣는가.** `app:matching` 이 넣는 필드는 `requestId` / `game` / `modeKey` /
    `voicePreference` / `playPurpose` / `keyValue` / `tier` / `queuedAt` / `partyId`(+ 확정 시
    `status=PARTY`)다(`service/MatchRequestService.java`, `START_HERE.md` 의 키 표). 게시판 입장에는
    이 필드들이 없다. 게시판 표시를 어떤 필드 · 값으로 하는가.
  - **"없으면 쓴다"를 원자적으로 하는 방법.** 확인과 쓰기 사이에 매칭 요청이 끼어들면 둘 다 성립한다.
    HASH 라 단순 `SET NX` 가 안 된다. **대기 중인 요청의 HASH 에는 `status` 필드가 없다** — `status` 는
    확정 뒤 `redis/proposal/cleanup-confirmed.lua` 가 `'PARTY'` 를 찍을 때만 생긴다. 그래서
    `HSETNX status` 는 대기 중인 요청 위에서도 성공해 버려 답이 아니다. `app:matching` 은 같은 일을
    `claim-request.lua` 에서 `EXISTS` → `HSET` → `EXPIRE 60` 을 Lua 스크립트 하나로 묶어 푼다.
    `app:platform` 의 구현 방법은 정해지지 않았다.
  - **`app:matching` 이 게시판 표시가 든 키를 읽을 때.** 지금 코드 그대로라면
    `service/MatchQueryService.java#find()`(`GET /api/v1/match-requests?userId=`)는 `status` 가
    `PARTY` 가 아니고 `partyId` 가 없는 HASH 를 **`QUEUED`(매칭 대기 중)** 라고 답한다. `requestId` ·
    `queuedAt` 은 비어 나간다. `service/MatchCancelService.java#cancel()` 은 HASH 의 `game` ·
    `voicePreference` · `playPurpose` 를 `valueOf` 로 읽으므로 그 필드가 없으면 예외로 끝나고, 있으면
    `leave-party.lua` 가 `requestId` 불일치(`-1`)로 지우지 않고 돌아간다. **새 검사 로직은 필요 없지만,
    남이 쓴 값을 읽는 쪽(상태 조회 · 취소)은 손봐야 할 수 있다.** 이 사용자를 무엇이라고 답할지도
    미정이다 — `MatchRequestStatus`(`IDLE` / `QUEUED` / `PROPOSED` / `MATCHED` / `CANCELLED`)에
    해당하는 값이 없다.
  - **안 지워지는 경우.** `app:platform` 이 키를 지우지 못하고 죽거나 연결이 끊기면 그 사용자는 매칭도
    방 입장도 못 하게 된다. TTL 을 둘지, 갱신을 어떻게 할지, `app:matching` 의 수명 정책과 어떻게
    맞출지. (`app:matching` 은 claim 때 60초 TTL 을 걸고 배정에 성공한 스크립트가 `PERSIST` 로 뗀다.
    그 뒤로는 TTL 이 없고, 지우는 것은 게임별 `leave-party.lua` 다 — 취소 · 거절 · 제안 만료가 모두
    `MatchCancelService#cancel()` 을 거쳐 이 스크립트를 부른다.)
  - **언제 지우는가.** 나가기 · 강퇴 · 방 만료는 분명하다. **방장이 확정한 뒤(13번)에는 언제
    지우는가** — 파티가 끝날 때인가. 이것은 `app:matching` 의 미해결 문제 "확정된 사용자의
    `status=PARTY` 를 누가 푸는가"(`HANDOFF.md` ①)와 **같은 문제**다. `app:platform` 이 이 키에 쓸 수
    있게 된 이상, 파티가 닫힐 때 `app:platform` 이 이 키를 지우는 것이 그 문제의 한 가지 해법이 될
    **수 있다.** 가능성일 뿐 정해진 것이 아니다.
- 강퇴당한 사람이 **다시 들어올 수 있는가.**
- 5명이 **가득 찼을 때** 목록에 어떻게 보이고, 눌렀을 때 어떻게 되는가.
- 방장이 나간 것을 **무엇으로 판단하는가** — 명시적 나가기만인가, 연결이 끊긴 경우도인가, 얼마나
  기다리는가.
- 만료된 글을 **언제까지 목록에 두는가.**
- 모집 글에 담는 것(게임 · 모드 · 원하는 조건 · 한 줄 소개 등), 목록의 정렬 · 필터.
- **어떤 알림 종류를 쓰는가** — 기존 `PARTY_*` 재사용인가 새 `type` 인가. 계약 원본이 이 컴퓨터에 없어
  `PARTY_*` · `FRIEND_*` 7종은 이름조차 이 저장소에 없다(`contracts/events.md` 에 나오는 것은 예시로 든
  `PARTY_MEMBER_JOINED` 하나뿐이다).
- **도배 글 대응**과 신고의 연결. 신고 기능은 이미 필수다(#13). (음성 쪽은 12번의 개별 음소거와 8번의
  강퇴가 정해졌다.)
- 모든 엔드포인트의 경로 · 스키마, 테이블.

**영향.**

- 이 기능은 `app:platform` 에 들어간다. 매칭 엔진에 게시판 코드를 넣지 않는다. 매칭 알고리즘과
  제안 · 수락 · 확정 흐름은 바뀌지 않는다.
- **9번 · 15번의 검사 방법은 확정됐다(16번).** `app:matching` 의 기존 검사(`claim-request.lua`)가
  그대로 일하므로 **새 검사 로직은 넣지 않는다.** 다만 **남이 쓴 값을 읽는 쪽(상태 조회 · 취소)은
  손봐야 할 수 있다**(위 "아직 미정"). HASH 의 필드 구성이 정해지기 전에 이 저장소에 그 용도의
  코드를 넣지 않는다.
- **활성 요청 키가 두 앱이 쓰는 키가 됐다.** 지금까지 `qm:user:active-request:{userId}` 는
  `app:matching` 만 썼다. 이제 `app:platform` 도 쓰고 지운다. 이 키의 접두사나 필드를 바꾸려면
  `app:platform` 과 같이 바꾼다.
- 차단(14번)은 `app:platform` 안에서 끝난다. `app:matching` 의 INV-6 검증(D-1)은 바뀌지 않는다.
- **시그널링 권한 확인의 문구가 바뀐다.** `app:platform` 의 시그널 `POST` 는 게시판 방에서 "같은
  파티원"이 아니라 **"같은 방에 들어와 있는 사람"** 을 확인한다(위 "D-9 와의 관계"). 확인하는 곳이
  `app:platform` 이라는 점과 알림 서비스가 관여하지 않는다는 점은 D-9 그대로다.
- 알림 서비스(`app:realtime`, 지금 이름은 `notification`)는 바뀌지 않는다. 방 입장/퇴장 알림과
  `WEBRTC_SIGNAL` 을 다른 알림과 똑같이 흘려보낼 뿐이다.
- `CLAUDE.md` §1 의 제품 경계 줄을 이 결정에 맞춰 고쳤다. **`docs/00_PRODUCT_SPEC.md` 는 원문
  사본이라 고치지 않았다** — §6 의 "게시판/LFG 글 작성"은 이 항목으로 걸러 읽는다.

---

### D-12. `BlockChanged.fifo` 를 폐기한다 — 차단은 `social.blocks` 에 저장하면 끝이다 (#21 개정 · D-2 의 "보류"를 "폐기"로, 2026-09-19)

> **이 결정은 매칭 엔진만의 결정이 아니다.** 시스템 전체 결정인 #21(그리고 #19 의 1단계)의 개정이다.
> 원본 결정 로그가 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때
> #21 의 개정 항목으로 올려야 한다. **#21 · #19 · D-2 의 본문은 고치지 않았다** (이 파일은 기록이다).

**원안.** #21 · #19 는 `app:platform`(social)이 차단이 바뀔 때 `BlockChanged.fifo` 로 발행하고,
`app:matching` 이 소비해 Redis 에 차단 목록 read model(`qm:block:{userId}` Set)을 만들어 매칭 때
선필터로 쓰게 했다. D-2 는 이것을 **"보류"** 했다.

**결정. 보류가 아니라 폐기다.**

1. 차단/해제는 `app:platform` 이 `social.blocks` 에 **DB 트랜잭션으로 저장하면 끝**이다.
2. `app:matching` 은 확정 직전에 `social.blocks` 를 직접 조회하는 한 겹으로 INV-6 을 지킨다 (D-1 그대로).
3. **만들지 않는 것** — `BlockChanged.fifo` 큐, `social.outbox` 의 차단 이벤트, `qm:block:{userId}`
   read model.

**근거.**

- D-2 의 근거가 그대로 성립한다 — 선필터는 정확성을 책임지지 않고, 최종 보증은 확정 직전 DB 조회이며,
  붙이려면 SQS · AWS SDK 가 따라와 얻는 값에 비해 무겁다.
- 큐 하나와 그에 딸린 발행 · 소비 · DLQ · 멱등 처리가 통째로 없어진다. #21 이 FIFO 를 고른 근거 중
  "`BlockCreated` / `BlockRemoved` 의 순서 역전" 문제도 큐가 없으면 생기지 않는다.
- 받는 쪽이 없는 채로 보내는 쪽만 만들 이유가 없다.

**감수하는 것.** 차단된 두 사람이 같은 후보 풀에서 반복해서 맞붙었다가 확정 직전에 깨지는 일이 생길
수 있다 (D-2 가 적은 그대로다).

**다시 열 조건.** 그 반복 충돌이 실제로 관측되면 그때 다시 본다. 그때 주의할 점은 D-2 의 "주의할 점"에
적혀 있다.

**영향.**

- 이 저장소(`app:matching`)가 **소비하는 SQS 큐는 없다.** 걸린 큐는 `ProposalConfirmed.fifo` 발행
  하나다. `CLAUDE.md` §3 · §4(INV-6), `contracts/events.md` 의 SQS 표, `contracts/README.md`(A-5),
  `START_HERE.md`, `HANDOFF.md` §0 ④ 를 이에 맞췄다.
- 파티 모집 게시판의 차단(D-11 14번 — 목록에서 보이지 않게)은 `app:platform` 안에서 `social.blocks` 를
  직접 읽어 거르므로 이 큐와 무관하다.
- `docs/AWS_ARCHITECTURE.md` 에는 머리 주석만 달았다(표는 그림을 옮긴 것이다).
  `docs/aws-architecture.drawio` 그림, `docs/07_REDIS_DESIGN.md` · `docs/03_MATCHING_ENGINE_SPEC.md` ·
  `docs/14_ARCHITECTURE_RATIONALE.md` · `docs/WHY_POSTGRESQL.md` 에는 `BlockChanged.fifo` 와
  `qm:block:{userId}` 가 아직 남아 있다.

---

### D-13. **(절반 낡음 — 게시판 파티는 큐 없이 닫는다. D-36)** `PartyClosed.fifo` 의 소비자는 `app:platform` 하나다 — `app:matching` 은 이 큐를 읽지 않는다 (#21 구체화, 2026-09-19)

> **이 결정은 매칭 엔진만의 결정이 아니다.** 시스템 전체 결정인 #21 의 구체화다. 원본 결정 로그가
> 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때 #21 의 개정
> 항목으로 올려야 한다. **#21 의 본문은 고치지 않았다** (이 파일은 기록이다). #21 은 이 큐를
> "`party` 가 발행 → `social` 이 소비"로 적었으므로 뒤집는 것은 없다 — 어긋나 있던 것은 이 저장소의
> 안내 문서들이다.

**배경.** #21 과 `docs/AWS_ARCHITECTURE.md` 는 이 큐를 platform(party) → platform(social,
`recent_players`)로 적는다. 그런데 이 저장소의 `CLAUDE.md`(INV-2 · INV-4 행) · `START_HERE.md` ·
`HANDOFF.md` 는 `app:matching` 이 `PartyClosed` 를 소비해 확정된 사용자의 `status=PARTY` 를 푸는 것을
전제로 적고 있었다.

**결정.** `PartyClosed.fifo` 의 **소비자는 `app:platform` 하나다.** `app:matching` 은 이 큐를 읽지 않는다.

**근거.** SQS 큐 하나를 서로 다른 일을 하는 두 앱이 읽으면 메시지를 **나눠 갖게 된다** — 한쪽이 가져간
메시지는 다른 쪽이 보지 못한다. 둘 다 받아야 한다면 큐를 둘로 나누는 구조가 필요한데 그럴 값이 없다.

**그래서 남는 문제 — 아직 미정.** 확정된 사용자의 `status=PARTY`(키는
`qm:user:active-request:{userId}`)를 **누가 푸는가**는 여전히 정해지지 않았다 (`HANDOFF.md` §0 ①).
D-11 16번으로 `app:platform` 이 이 키에 쓸 수 있게 됐으므로 "파티가 닫힐 때 `app:platform` 이 이 키를
지운다"가 한 가지 가능한 해법이다. **가능성일 뿐 정해진 것이 아니다.**

*(메모 — 결정이 아니다)* 보내는 쪽도 받는 쪽도 같은 앱이므로, 이 큐를 SQS 로 둘지 같은 트랜잭션 안에서
처리할지는 `app:platform` 을 구현할 때 다시 볼 수 있다 (#26 이 `app:party` 분리를 기각했다).

**영향.**

- 이 저장소에 `PartyClosed` 소비자를 만들지 않는다. `CLAUDE.md` §3 · §4(INV-2 · INV-4 행),
  `START_HERE.md`, `HANDOFF.md` §0 ① · ③, `contracts/README.md`(12번 행 · A-6), `contracts/events.md`
  를 이에 맞췄다.
- `HANDOFF.md` §0 ① 의 후보 셋 가운데 "`PartyClosed.fifo` 소비"가 닫혔다. 나머지 후보와 위의 새
  가능성은 열려 있다.
- 코드 주석 세 곳(`controller/ProposalController.java` · `service/ProposalService.java` ·
  `redis/proposal/cleanup-confirmed.lua`)은 아직 `PartyClosed` 소비를 이 앱의 남은 일로 적고 있다.
  코드는 이번에 고치지 않았다.

---

### D-14. access 토큰은 쿠키로 주고받는 JWT, refresh 토큰은 Redis 에 저장하는 불투명 UUID 다 (#16 구체화, 2026-09-19)

> **일부 낡음 — D-24 · D-25 · D-26 이 개정(2026-09-26 표시).** 결정 3 의 "사용자 식별자는 로그인 아이디 문자열"은 D-25 로 사용자 번호가 됐고, "아직 미정"의 대부분은 D-24(서명 · 쿠키 · CSRF · 클레임 · denylist 없음) · D-26(수명 · refresh 키)으로 정해졌다.

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템 전체 결정인 #16 의 구체화다. 원본 결정 로그가
> 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때 #16 의 구체화
> 항목으로 올려야 한다. **#16 의 본문은 고치지 않았다** (이 파일은 기록이다).

**결정.**

1. **access 토큰은 JWT 이고 쿠키로 주고받는다.** 클라이언트가 `Authorization` 헤더에 싣지 않는다.
   브라우저가 요청마다 쿠키를 자동으로 붙인다.
2. **refresh 토큰은 JWT 가 아니라 불투명한 UUID 다.** **Redis 에 저장**하고(UUID → 사용자), 재발급 때
   Redis 에서 찾아 확인한다. 폐기는 Redis 에서 지우면 끝이다.
3. 발급 주체는 `app:platform`(account 모듈)이다 (#16 그대로). 사용자 식별자는 가입할 때 정한 로그인
   아이디 문자열이다 (`../platform/CLAUDE.md` §3.5).

**#16 과의 관계 — 뒤집지 않고 구체화한다.** #16 은 "JWT access + refresh, refresh rotation 필수, Redis
denylist, denylist 조회 실패 시 fail-closed, 짧은 access TTL"이다. D-14 는 그중 **refresh 의 형태**를 JWT 가
아닌 Redis 의 UUID 로 정했다. **rotation 은 그대로 필수다** — 재발급 때 옛 UUID 를 지우고 새 UUID 를
준다. access 토큰용 denylist 를 따로 둘지는 아래 미정으로 보낸다. #16 의 근거 가운데 "WebSocket
핸드셰이크에 토큰을 그대로 실을 수 있다"는 D-9 로 WebSocket 이 없어지면서 이미 뜻을 잃었다.

**D-9 와의 관계.** D-9 가 근거로 적은 "`POST` 는 평범한 HTTP 라 `Authorization` 헤더를 붙일 수 있다"는
이 결정으로 **"쿠키가 자동으로 붙는다"** 로 읽는다. 결론(시그널을 보내는 길은 REST `POST`)은 그대로다.
**D-9 의 본문은 고치지 않았다.**

**근거.**

- **`EventSource` 는 요청 헤더를 붙일 수 없다.** 알림 서비스의 SSE 인증이 "쿼리 파라미터 토큰이냐
  쿠키냐"로 미정이었는데(`../notification/CLAUDE.md` §7), 쿠키면 브라우저가 SSE 연결에도 자동으로
  붙인다. 쿼리 파라미터 토큰은 URL 이 로그 · 히스토리 · 프록시에 남는다.
- 세 서비스가 한 도메인 아래 경로로 나뉜다(`docs/AWS_ARCHITECTURE.md` — CloudFront 가 `/api/**` ·
  `/events` 를 ALB 로 보낸다). 같은 출처라 쿠키 하나가 세 서비스 모두에 간다.
- `HttpOnly` 쿠키는 자바스크립트가 읽을 수 없어 XSS 로 토큰이 새는 길이 막힌다.
- refresh 를 Redis 의 불투명 값으로 두면 **서버가 즉시 무효화**할 수 있다(로그아웃 · 탈취 대응). JWT
  refresh 는 만료 전까지 스스로 유효해서 별도 목록이 필요하다.

**아직 미정 — 임의로 지어내지 않는다.**

- **쿠키 속성.** `HttpOnly` · `Secure` · `SameSite`(Lax 냐 Strict 냐) · `Path` · `Domain` · 수명. refresh
  쿠키의 `Path` 를 재발급 경로로 좁힐지.
- **CSRF 대응.** 쿠키 인증은 브라우저가 자동으로 붙이므로, 상태를 바꾸는 요청(`POST` 등)이 다른
  사이트에서 위조될 수 있다. `SameSite` 만으로 갈지, CSRF 토큰이나 `Origin` 검사를 더할지. **시그널
  `POST`, 방 입장, 차단, 매칭 요청이 전부 해당된다.**
- **다른 두 서비스가 access 토큰을 검증하는 법.** 서명 방식(대칭 HS256 — 세 서비스가 비밀 키를 공유 /
  비대칭 RS256 — `app:platform` 만 서명하고 나머지는 공개 키로 검증)과 키를 나눠 갖는 방법. JWT 클레임
  구성(`sub` = 로그인 아이디, 만료 등).
- access · refresh 의 **수명.** access 토큰 denylist 를 둘지 — #16 은 두라고 했다. refresh 가 Redis 에
  있어 즉시 무효화가 가능해졌으므로 access 수명을 짧게 잡고 생략할 수 있는지.
- **refresh 의 Redis 키 이름 · 값 · TTL.** 매칭 키(`qm:party:*` 등)와 겹치지 않는 접두사여야 한다. 한
  사용자가 여러 기기에서 로그인할 때 refresh 를 몇 개 허용하는지. rotation 때 옛 값이 다시 쓰이면(탈취
  신호) 어떻게 하는지.
- **SSE 와 토큰 만료.** 알림 서비스는 연결을 30분마다 끊고 브라우저가 다시 붙는다. 그때 access 토큰이
  만료돼 있으면 서버는 401 을 주는데, **`EventSource` 는 200 이 아닌 응답을 받으면 재접속을 영구히
  멈춘다.** 그래서 클라이언트는 SSE 오류를 받으면 토큰을 재발급받고 `EventSource` 를 새로 만들어야
  한다 — 이 순서를 계약(`contracts/events.md` "재연결")에 넣을지, SSE 연결 수명과 access 수명을 어떻게
  맞출지.
- **개발 환경.** 로컬에서는 세 서비스와 프런트 개발 서버가 서로 다른 포트라 출처가 다르다(쿠키는
  포트를 가리지 않지만 CORS 는 가린다). `fetch` 의 `credentials`, `EventSource` 의 `withCredentials`,
  CORS 의 허용 출처(자격증명을 실으면 `*` 를 쓸 수 없다) 설정.
- `app:matching` 은 지금 요청 본문/쿼리의 `userId` 를 그대로 믿는다. **언제 토큰에서 꺼내도록
  바꾸는지**, 그 전까지의 임시 식별.

**영향.**

- 알림 서비스의 인증 미정("쿼리 파라미터 토큰이냐 쿠키냐")이 **쿠키로 풀렸다.** 구현은 아직이다.
- `app:matching` 은 아직 토큰을 보지 않는다. 요청의 `userId` 를 그대로 쓴다(`contracts/README.md`
  불일치 표 2 · 3번의 임시 조치가 그대로다).
- **세 서비스 모두 access 토큰 검증 코드가 필요해진다.** 발급은 `app:platform` 하나다.
- `contracts/openapi.yaml` 에 `securitySchemes` 가 없다는 기존 구멍(`contracts/events.md` "미해결 계약
  구멍")은 bearer 가 아니라 **cookie 방식**(`apiKey` · `in: cookie`)으로 채워야 한다. 쿠키 이름은 미정이다.
- refresh 토큰이 Redis 에 놓인다. #16 이 말한 "Redis 장애가 인증까지 번지지 않는다"는 access 토큰
  검증에는 그대로 성립하지만, **재발급은 Redis 가 있어야 한다.**

---

### D-15. 예약(등록 REST + 짝 찾기 배치)을 AWS Lambda 로 뺀다 — 상시 서버를 두지 않고 Spring Boot 를 쓰지 않는다 (#24 · #26 일부 개정, 2026-09-19)

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템 전체 결정인 #24 와 #26(의 Lambda 기각)의 개정이다.
> 원본 결정 로그가 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때
> #24 · #26 의 개정 항목으로 올려야 한다. **#15 · #23 · #24 · #26 의 본문은 고치지 않았다** (이 파일은
> 기록이다).

**원안.** #24 는 예약 REST(`/api/v1/reservations` CRUD + INV-9 검증)를 `app:platform` 이 서빙하고, 짝
찾기 배치는 `app:reservation-batch`(상시 1개, 스케일아웃 없음)가 돌게 했다. `module:reservation` 은
라이브러리로 두 앱이 함께 의존하고 `reservation` 스키마 롤도 두 앱 모두에 준다. #26 은 Lambda 를
기각했다.

**결정.**

1. **예약(등록 REST + 짝 찾기 배치)을 AWS Lambda 로 뺀다.** 상시 서버를 두지 않는다.
2. **예약 등록 REST 는 `app:platform` 의 일이 아니게 된다.** 등록과 배치가 한 코드 덩어리(예약 서비스)에
   같이 있고, 그것이 Lambda 함수들로 실행된다. 이 단위를 **`app:reservation`** 이라 부른다 — 기존
   문서의 `app:reservation-batch` 를 대체한다.
3. **Spring Boot 를 쓰지 않는다.** 프레임워크 없는 가벼운 핸들러로 만든다. 언어 · 라이브러리 선택은
   미정이다.

**바뀌지 않는 것.** #23 의 "배치 단일 경로"(등록 시 즉시 탐색을 하지 않는다), INV-9, "배치는 하나만
돌아야 한다"(#24), `RESERVATION_*` 의 발행 주체가 예약 쪽이라는 점(#24 — 이름만
`app:reservation` 이 된다), #24 의 `runOnce()` 코어 + 진입점 분리 구조. **#23 의 주기 · 리드타임 · tier
완화는 D-17 이 개정한다.**

**근거.**

- 예약은 요청이 드물고 배치는 정해진 때에 잠깐 도는 일이다(주기는 D-17). 그것 때문에 서버 하나를 상시
  띄워 두는 비용이 아깝다.
- **#24 의 구조에는 풀리지 않은 문제가 있었다.** 등록(`app:platform`)과 배치(별도 앱)가 같은
  `reservation` 스키마와 같은 예약 코드를 나눠 써야 하는데, 지금 저장소들은 단일 모듈 구조라
  (`CLAUDE.md` §3 "단일 모듈이다 … 멀티모듈로 되돌리지 마라") 두 앱이 코드를 공유할 방법이 없다.
  등록과 배치를 한 덩어리로 합치면 이 문제가 사라진다.
- 예약은 다른 `app:platform` 모듈(계정 · 방 · 소셜)과 같은 트랜잭션으로 묶일 일이 없다. 사용자 id 와
  자기 테이블만 본다.
- "배치는 하나만 돌아야 한다"(#24)는 Lambda 의 동시 실행 수 제한으로 지킬 수 있다.
- 정해진 시각에 한 번 도는 배치(D-17)는 하루치를 한꺼번에 처리한다. Lambda 의 최대 실행 시간 15분 안에
  끝나는지는 규모에 달렸다 — D-17 의 미정이다.

**#26 의 Lambda 기각 근거와의 관계.**

- (a) **"Spring Boot 콜드스타트"** — Spring Boot 를 쓰지 않으므로 해당하지 않는다. 단 JVM 을 쓴다면
  JVM 자체의 시작 시간은 남는다(아래 미정).
- (b) **"DB 커넥션 폭발"** — 동시 실행 수를 제한하면 연결 수의 상한이 정해진다.
- (c) **"Stage 3 k8s 이행 불가, '배포 단위 = 컨테이너 이미지 1개' 원칙(#20)과 충돌"** — **감수한다.**
  다만 #24 가 정한 "`runOnce()` 코어 + 진입점 분리" 구조를 유지하면 Lambda 핸들러는 얇은 진입점 하나일
  뿐이라, 나중에 컨테이너로 되돌리는 비용이 작다.
- #26 의 "EventBridge → RunTask 1분 배치는 상시보다 비싸다"는 Fargate RunTask 이야기다(이미지 pull
  부터 과금, 최소 1분 부과). Lambda 에는 해당하지 않는다. 주기가 바뀌었으므로(D-17) 이 비교 자체가 더는
  걸리지 않는다.

**감수하는 것 / 주의.**

- 배포 방식 · 로컬 실행 · 로그 확인이 나머지 서비스(컨테이너)와 달라진다. 도구가 하나 는다.
- **VPC 안의 Lambda 는 기본적으로 인터넷에 나갈 수 없다.** PostgreSQL · Redis 에 붙으려면 VPC 안에 둬야
  하는데, 그 상태에서 AWS API(SQS 등)나 외부를 부르려면 NAT 게이트웨이나 VPC 엔드포인트가 필요하다.
  **NAT 게이트웨이는 상시 서버보다 비쌀 수 있다.** 비용 때문에 Lambda 로 가는 것이므로, 이 함정에
  빠지면 결정의 근거가 무너진다.
- 위 (c) — Stage 3 이행과 #20 의 원칙에서 예약만 예외가 된다.
- deadman 감시(#24 의 `reservation_batch_last_success_epoch_seconds`)의 필요성은 그대로다. 배치가 안
  도는 것은 여전히 조용한 장애다. 수단은 미정이다.

**아직 미정 — 임의로 지어내지 않는다.**

- **제안 · 수락을 어떻게 하는가 — 예약을 만들 때 가장 먼저 정해야 하는 것이다.** `docs/04` §8 은
  "예약도 realtime 과 동일한 proposal acceptance 모델을 사용한다"고 적는다. 그 장치(파티 HASH, 수락자
  SET, 만료 스위퍼, Lua)는 전부 `app:matching` 안에 있다. 예약 쪽이 따로 구현하는지, `app:matching` 의
  장치를 쓰는지, 쓴다면 어떻게 연결하는지.
- **언어와 런타임.** JVM 을 유지할지(그 경우 시작 시간 대책, DB 접근을 무엇으로 할지), 다른 언어로
  갈지. `app:matching` 과 조건 · 티어 규칙을 맞춰야 한다는 점(`docs/04` — 예약도 같은 조건 체계와 시간
  기반 tier 완화를 쓴다)이 선택에 영향을 준다.
- **HTTP 진입점**(API Gateway / 함수 URL / ALB 대상 그룹)과 CloudFront 경로 라우팅 — 같은 출처라야
  D-14 의 쿠키가 간다. 예약 Lambda 가 access 토큰을 검증하는 법.
- 동시 실행 수 상한의 값, 배치를 깨우는 트리거(언제 도는지는 D-17 — 정해진 시각), `RESERVATION_*` 알림 발행(Redis `PUBLISH` — VPC 안 Redis 에
  닿아야 한다), Redis claim(`docs/04` §6 "Redis 는 claim 용")을 유지할지.
- **`reservation` 스키마의 마이그레이션(Flyway)을 누가 실행하는가.** Spring/Flyway 가 없는 Lambda 가
  스스로 하기 어렵다. `app:platform` 이 대신 갖는지, 별도 절차인지.
- **로컬에서 Lambda 를 어떻게 돌리는가.**
- deadman 감시의 수단.

**영향.**

- 이 저장소(`app:matching`)의 코드는 바뀌지 않는다. 다만 **제안 · 수락 장치를 예약이 쓰기로 정해지면
  영향이 온다**(위 미정 첫 항목).
- `app:platform` 은 예약 REST 를 서빙하지 않는다. #15 의 모듈 목록(`app:platform` — account / party /
  social / **reservation** / common)에서 reservation 이 빠지고, `app:reservation-batch` 자리는
  `app:reservation`(Lambda)이 된다. 배포 단위 수는 그대로 4개다. `../platform/CLAUDE.md` 와
  `../platform/README.md` 를 이에 맞췄다.
- `CLAUDE.md` §2 · §4(INV-9 행) · §9, `contracts/events.md` 의 `RESERVATION_*` 발행 주체 표기,
  `contracts/README.md`(A-7)를 맞췄다. `docs/AWS_ARCHITECTURE.md` 에는 머리 주석만 달았다(표 · 그림은
  그대로다).
- `docs/04_RESERVATION_MATCHING_SPEC.md` · `docs/14_ARCHITECTURE_RATIONALE.md` ·
  `docs/07_REDIS_DESIGN.md` · `docs/WHY_POSTGRESQL.md` 는 사본이라 고치지 않았다 — `app:reservation-batch`
  와 "예약 REST 는 `app:platform`" 서술이 남아 있다.

---

### D-16. 방을 별도 서비스 `app:room` 으로 분리한다 — 오래 남는 것은 `app:platform`(PostgreSQL), 금방 사라지는 것은 `app:room`(Redis) (#15 · #26 일부 개정, D-9 · D-11 의 주어 개정, 2026-09-19)

> **낡음 — D-33 이 되돌림(2026-09-26 표시).** `app:room` 은 2026-09-25 에 `app:platform` 에 합쳐졌다. 입장권 · 방 키 읽기 예외 · 포트 8083 · "배포 단위 다섯"이 전부 낡았다. 남는 것은 "오래 남는 것은 PostgreSQL, 금방 사라지는 것은 Redis"라는 구분뿐이다 — 이제 한 앱 안의 패키지 구분이다.

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템 전체 결정인 #15(배포 단위 구성)와 #26(`app:party` 분리
> 기각)의 개정이다. 원본 결정 로그가 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본
> 저장소와 합칠 때 #15 · #26 의 개정 항목으로 올려야 한다. **#15 · #26 · D-9 · D-11 · D-13 의 본문은 고치지
> 않았다** (이 파일은 기록이다).

**원안.** #15 는 파티를 `app:platform` 의 모듈로 두었고, #26 은 `app:party` 분리를 기각했다. D-11 6번은
파티 모집 게시판을 통째로 `app:platform`(파티 모듈)에 넣었고, D-11 16번은 활성 요청 키를 쓰고 지우는 앱을,
D-9 는 시그널 `POST` 를 받는 앱을 `app:platform` 으로 적었다.

**결정. 방을 별도 서비스 `app:room` 으로 분리한다. 나누는 기준은 "무엇을 어디에 저장하느냐"다.**

| | `app:platform` | `app:room` |
|---|---|---|
| 다루는 것 | 오래 남는 것 — 모집 글, 글의 상태, 확정된 파티, 계정 · 친구 · 차단 · 신고 | 금방 사라지는 것 — 지금 방에 누가 있나 |
| 저장소 | PostgreSQL | **Redis 만. PostgreSQL 을 쓰지 않는다** |

기능 분담:

- **`app:platform`** — 모집 글 쓰기 · 수정, 게시판 목록, **차단 관계 거르기**(D-11 14번 — `social.blocks` 와
  닉네임이 같은 앱에 있어 서비스 경계를 넘지 않는다), 글의 상태(모집 중 / 확정 / 만료), **방장 확정과 파티원
  기록**(D-11 13번 — DB 에 남기는 일이다. "최근 함께한 사람"도 같은 앱 안에서 기록한다),
  `ProposalConfirmed.fifo` 소비(자동 매칭으로 확정된 파티를 DB 에 만든다 — #21 그대로), 계정 · 인증(D-14) ·
  친구 · 차단(D-12) · 신고.
- **`app:room`** — 입장 · 나가기 · **강퇴**(D-11 8번), **정원 5명 검사**(D-11 10번 — 동시에 여러 명이 누를 때
  원자적으로 세야 한다), **활성 요청 키(`qm:user:active-request:{userId}`) 쓰고 지우기**(D-11 9 · 15 · 16번),
  접속 확인과 방장 이탈 감지(D-11 11번의 "방장이 나가면"을 판단하는 쪽), **시그널 `POST`**(D-9 — "같은 방에
  들어와 있는 사람인지" 확인한 뒤 `WEBRTC_SIGNAL` 발행), 입장 · 퇴장 · 강퇴 알림 발행.
- 마이크 켜고 끄기 · 개별 음소거(D-11 12번)는 여전히 브라우저에서 끝난다. 어느 서버의 일도 아니다.

배포 단위는 이제 **다섯**이다 — `app:matching` / `app:platform` / `app:room` / `app:realtime`(지금 이름은
`notification`) / `app:reservation`(Lambda, D-15).

**D-11 · D-9 · D-13 과의 관계.**

- **D-11 6번** "이 기능은 `app:platform`(파티 모듈)에 들어간다" → 글 · 목록 · 확정은 `app:platform`, **방 안의
  일은 `app:room`** 으로 갈린다.
- **D-11 16번** "`app:platform` 이 활성 요청 키에 값을 써 넣고 지운다" → **그 일을 하는 앱이 `app:room` 이
  된다.** "매칭 Redis 키를 만지는 예외는 이 키 하나"라는 규칙도 `app:room` 으로 옮겨 간다. **`app:platform` 은
  이제 활성 요청 키를 만지지 않는다.** 16번의 나머지(키 하나로 양방향을 지킨다, `claim-request.lua` 의 기존
  검사가 거절한다, 서비스 간 호출이 없다, 접두사 위험, 16번 세부 미정)는 그대로이고 주어만 `app:room` 으로
  바뀐다. D-11 "영향"의 "활성 요청 키가 두 앱이 쓰는 키가 됐다"에서 두 번째 앱도 `app:room` 이다.
- **D-9** "시그널 `POST` 는 `app:platform` 이 받는다" → **`app:room` 이 받는다.** D-9 가 든 근거(그 확인을 할 수
  있는 곳)가 `app:room` 으로 옮겨 갔기 때문이다 — 지금 방에 누가 있는지를 아는 앱이 `app:room` 이다.
- **D-13** 의 "보내는 쪽도 받는 쪽도 같은 앱"이라는 메모는 그대로 성립한다(확정과 "최근 함께한 사람"이 둘 다
  `app:platform` 이다). `PartyClosed.fifo` 를 SQS 로 둘지는 여전히 `app:platform` 을 구현할 때 본다.
- 같은 이유로 D-9 · D-11 의 본문은 고치지 않았다. 그 항목들에서 방 안의 일 · 활성 요청 키 · 시그널 `POST` 의
  주어로 나오는 `app:platform` 은 `app:room` 으로 읽는다.

**#26 과의 관계.** #26 은 `app:party` 분리를 "account/social 과 스케일 방식 · 재시작 비용 · 배포 주기가 전부
같다. 파티는 엔드포인트 2개로 셋 중 가장 작다 (트래픽 확보 후 재검토 — D6)"로 기각했다. **그 근거가 D-11 로
무너졌다.** 방은 게시판 입장, 정원, 강퇴, 접속 확인, 시그널링, 활성 요청 키까지 맡게 되어 가장 큰 덩어리가
됐고, 부하의 모양(방에 있는 전원의 주기적 접속 확인, 통화를 시작할 때 몰리는 시그널)이 계정 · 친구의 "가끔
오는 요청"과 다르다. 다만 #26 이 기각한 것은 **"파티 전체"의 분리**였고, D-16 은 **파티의 오래 남는 부분(글 ·
확정)은 `app:platform` 에 두고 사라지는 부분만** 뗀다.

**근거.**

- **저장소가 다르다**(PostgreSQL 대 Redis). 방 안의 상태는 `app:matching` 의 대기 중 파티와 같은 성질이다.
- **부하의 모양이 다르다**(위).
- **장애가 번지지 않는다.** 방 쪽 문제로 로그인 · 게시판이 죽지 않는다.
- **목록과 글을 `app:platform` 에 두었기 때문에** 분리의 가장 큰 비용이던 "목록을 그릴 때마다 차단 · 프로필을
  다른 서비스에서 가져오기"가 생기지 않는다. PostgreSQL 을 쓰는 앱은 `app:platform` 하나로 남고(`app:matching`
  의 `social.blocks` 읽기 예외는 D-1 그대로다), `app:room` 은 DB 읽기 권한조차 필요 없다.
- 확정과 "최근 함께한 사람"이 같은 앱(`app:platform`) 안이라 그 사이에 큐가 필요 없다.

**두 앱을 잇는 방법 — 방향은 정해졌고 세부는 미정이다.** 서비스 간 호출을 만들지 않는다 (#15).

1. **입장권.** 글을 누르면 브라우저가 먼저 `app:platform` 에 입장권을 요청한다. `app:platform` 은 글이 모집
   중인지 · 차단 관계가 아닌지 확인하고 **서명된 입장권**(방 식별자, 방장이 누구인지, 만료 시각 등)을 준다.
   브라우저가 그것을 들고 `app:room` 에 간다. `app:room` 은 **서명만 검증**하고 `app:platform` 에 묻지 않는다
   (access 토큰을 각 서비스가 스스로 검증하는 것과 같은 원리다). 확정되거나 만료된 글에는 `app:platform` 이
   입장권을 내주지 않으므로, "확정 뒤에는 새 사람이 못 들어온다"(D-11 13번)와 "만료된 글은 눌러도 못
   들어간다"(11번)가 `app:room` 이 글의 상태를 몰라도 지켜진다.
2. **`app:platform` 이 방의 Redis 키를 읽기만 한다.** 목록을 만들 때 글마다 "방이 살아 있는가 · 몇 명인가"를
   Redis 에서 조회한다(가득 찬 방 표시). 방이 사라져 있으면 그 자리에서 글을 만료로 바꾼다. 방장 확정 때도
   같은 키에서 현재 인원을 읽어 파티원으로 기록한다. **이것은 `app:platform` 이 `app:room` 의 키를 "읽는"
   예외이고, 쓰지는 않는다**(단 아래 3).
3. **확정 순간의 경쟁.** 방장이 확정을 누르는 찰나에, 미리 받아 둔 입장권으로 누가 들어올 수 있다.
   `app:platform` 이 확정 직전에 Redis 에 "닫힘" 표시를 먼저 쓰고 `app:room` 이 입장을 처리할 때 그 표시를
   같이 확인하는 식으로 막을 수 있다 — **가능한 방법일 뿐 정해진 것이 아니다.** 이 방법을 쓰면
   `app:platform` 이 `app:room` 의 키 공간에 **쓰는** 예외가 하나 생긴다.

**감수하는 것.**

- **`app:room` 은 상시로 띄운다. 방마다, 또는 방이 생길 때 띄우는 방식이 아니다.** 방의 상태는 Redis 에 있고
  서버는 그 Redis 로 들어가는 문일 뿐이라, 서버 하나가 모든 방을 받는다. 방이 생길 때 서버를 띄우는 것은
  방의 상태를 프로세스 메모리에 들고 있는 구조(게임 서버 같은)에서 쓰는 방식이고, 상태를 앱 메모리에 두지
  않는다는 이 서비스의 설계 규칙과 반대다. 컨테이너를 요청 시 기동하면 시작에 30~90초가 걸린다는 점은 #26 이
  이미 적었다("파티/배치를 요청 시 기동" 기각).
- **Lambda 로 시작하지 않는다 — 불가능해서가 아니라 맞지 않아서다.** `app:room` 은 상태를 Redis 에 두는 무상태
  요청-응답 서버라 기술적으로는 Lambda 로도 돌 수 있다. 그러나 시그널은 지연에 민감한데 통화를 시작하는
  순간에 몰려서 콜드스타트와 겹치기 쉽고, 몰릴 때 인스턴스가 여러 개 뜨면 Redis 연결 수가 같이 늘며,
  Spring Boot 를 쓰는 한 콜드스타트가 길다. **트래픽이 아주 적은 동안은 Lambda 가 더 쌀 수 있다.**
- **비용 — 다시 볼 조건.** 서비스마다 따로 과금되는 배포 환경에서는 `app:room` 을 상시로 두는 것이 비용이
  되므로 Lambda 로 옮기는 것이 선택지가 된다. **지금은 상시로 시작하고, 핵심 로직을 Spring 에 묶지 않게
  지어(평범한 클래스 + Lua) 나중에 진입점만 바꿀 수 있게 한다.**
- **두 앱이 Redis 키 형식을 약속해야 한다.** `app:platform` 이 읽는 방 키의 이름 · 구조를 `app:room` 이 혼자
  바꿀 수 없다. 알림 채널 접두사, 활성 요청 키에 이은 **세 번째 공유 약속**이고, 오타 하나로 컴파일 · 테스트가
  통과한 채 조용히 어긋나는 성질이 같다.
- 입장권 서명 키를 두 앱이 나눠 갖는다.
- **글의 만료가 즉시가 아니다.** 방장이 나간 직후 DB 의 글은 아직 "모집 중"이고, 누가 목록을 보거나 입장권을
  요청할 때 만료로 바뀐다. 목록을 그리는 순간에 걸러지므로 사용자에게 보이는 차이는 없다.

**아직 미정 — 임의로 지어내지 않는다.**

- **방 키의 이름 · 구조 · 수명.**
- **접속 확인의 방법과 주기.** 브라우저가 주기적으로 신호를 보내 입장 기록의 수명을 늘리는 방식이 **가능한
  방법**이다 — 같은 신호로 활성 요청 키의 수명도 늘리면 D-11 16번 세부의 "안 지워지는 경우"가 같이 풀린다.
- **입장권의 형식 · 서명 방식 · 수명 · 담는 정보.**
- **방이 처음 만들어지는 시점** — 방장이 글을 쓴 직후 입장권으로 들어올 때인가.
- **자동 매칭으로 확정된 파티의 방은 어떻게 생기는가.** `app:platform` 이 파티를 DB 에 만든 뒤 파티원이
  입장권으로 들어오는 것이 자연스럽지만 정해지지 않았다.
- **강퇴당한 사람의 재입장** — 입장권으로 막을지, `app:room` 이 기억할지.
- **`status=PARTY` 해제**(`HANDOFF.md` §0 ①). 활성 요청 키를 다루는 앱이 `app:room` 이 됐으므로 "방이 닫힐 때
  `app:room` 이 지운다"가 한 가지 가능성이다. D-13 이 적은 가능성의 주어도 `app:room` 으로 바뀐다.
- `app:room` 이 발행하는 알림의 종류와 이름(기존 `PARTY_*` 재사용인가 새 `type` 인가 — D-11 의 미정 그대로).
- 포트(8083 을 **제안**), access 토큰 검증(D-14 의 미정과 같다).
- 그 밖에 D-11 의 미정 목록 전부.

**영향.**

- 이 저장소(`app:matching`)의 코드는 바뀌지 않는다. 활성 요청 키를 같이 쓰는 상대가 `app:platform` 에서
  `app:room` 으로 바뀔 뿐이다. D-11 이 적은 "남이 쓴 값을 읽는 쪽(상태 조회 · 취소)은 손봐야 할 수 있다"는
  그대로다.
- **`WEBRTC_SIGNAL` 의 발행 주체가 `app:room` 이 된다.** `contracts/events.md` 와 `contracts/README.md`(A-8)를
  맞췄다. 방 입장 · 퇴장 알림을 어느 앱이 어떤 `type` 으로 내는지는 미정이다.
- `CLAUDE.md` §1, `START_HERE.md`, `HANDOFF.md` §0 ① 의 주어를 맞췄다. `docs/AWS_ARCHITECTURE.md` 에는 머리
  주석만 달았다(표 · 그림은 그대로다).
- 새 작업 폴더 `../room/`(같은 저장소의 `room` 브랜치)에 `CLAUDE.md` · `README.md` 를 두었다.
  `../platform/CLAUDE.md` · `../platform/README.md` 에서 방 안의 일을 "하지 않는 일"로 옮겼고,
  `../notification/CLAUDE.md` 의 `WEBRTC_SIGNAL` 서술을 맞췄다.
- 알림 서비스는 바뀌지 않는다. 누가 발행하든 받은 것을 그대로 흘려보낸다.

---

### D-17. 예약 짝 찾기 배치는 1분마다 돌지 않는다 — 요일 구분에 따라 하루 중 정해진 시각에만 돈다 (#23 개정, 2026-09-19)

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템 전체 결정인 #23 의 개정이다. 원본 결정 로그가 있는
> queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때 #23 의 개정 항목으로
> 올려야 한다. **#23 · #24 의 본문은 고치지 않았다** (이 파일은 기록이다).

**원안.** #23 은 "예약 매칭은 **1분 주기** 배치 단일 경로로만 실행한다. 최소 리드타임은 30분이다"였고,
슬롯까지 남은 시간에 따라 tier 를 넓히는 시간 기반 완화를 두었다(`batch.interval-ms: 60000` +
`batch.min-lead-minutes: 30`).

**결정.** **예약 짝 찾기 배치는 1분마다 돌지 않는다. 요일 구분에 따라 하루 중 정해진 시각에만 돈다.**
시각은 설정값으로 둔다. *(예시일 뿐 확정 값이 아니다)* 평일은 오후 5시, 주말은 오후 2시.

**바뀌지 않는 것.**

- #23 의 **"배치 단일 경로"** — 등록 시 즉시 후보를 찾지 않는다. 바뀌는 것은 주기다.
- INV-9(시간이 겹치는 활성 예약 금지), "배치는 하나만 돌아야 한다"(#24), `runOnce()` 코어 + 진입점 분리(#24).
- 예약은 `app:reservation`(AWS Lambda)이 맡는다 (D-15).

**근거.**

- 예약은 애초에 즉시성이 요구되지 않는 기능이다 (#23 본문이 이미 그렇게 적는다). 1분마다 돌 이유가 약하다.
- 정해진 시각에 한 번 도는 편이 Lambda 실행(D-15)과도 맞는다 — 상시로 도는 것이 없어진다.

**그래서 미정이 되는 것 — 임의로 지어내지 않는다.** #23 의 나머지는 1분 주기를 전제로 한 것이라 대부분
다시 정해야 한다.

- **최소 리드타임 30분.** #23 은 그 이유를 "배치 주기가 1분이므로 리드타임이 짧으면 매칭 성사 자체를 보장할
  수 없다"로 적었다. 주기가 하루 한 번이 되면 이 규칙의 뜻이 달라진다 — **그날 배치 시각 이후에 시작하는
  슬롯만 그 배치가 짝지을 수 있다.** 등록 마감을 언제로 두는가(배치 시각 전까지인가), 배치 시각보다 이른
  슬롯 · 배치 직후에 시작하는 슬롯의 등록을 받는가, 배치가 지난 뒤에 들어온 그날 예약은 어떻게 되는가.
- **시간 기반 tier 완화**(#23 — "슬롯까지 2시간 초과 → Tier 0 만 / 2시간~30분 → Tier 1 까지 / 30분 이내 →
  Tier 2 까지. 남은 시간이 줄수록 성사를 우선한다"). 이것은 배치가 여러 번 돌면서 시간이 흐를수록 조건을
  푸는 구조였다. **한 번만 도는 배치에서는 그대로 성립하지 않는다** — 한 번의 실행 안에서 Tier 0 → 1 → 2
  순으로 단계를 밟는가, 실행 시점의 슬롯까지 남은 시간으로 한 번만 정하는가, 완화를 없애는가.
- **제안 · 수락 — 가장 큰 문제다. D-15 미정의 첫 항목과 이어진다.** `docs/04` §8 은 "예약도 realtime 과
  동일한 proposal acceptance 모델을 사용한다"고 적고, 실시간 제안의 수명은 기본 20초다
  (`queuemate.proposal.ttl-seconds`). **정해진 시각에 한꺼번에 제안이 나가면 그 순간 사용자가 화면 앞에
  있다는 보장이 없다.** 알림은 SSE 하나라 페이지를 열어 둔 사용자에게만 닿는다(`contracts/events.md`).
  - 수락 시한을 길게 두는가, 수락 없이 자동 확정하는가.
  - 페이지 밖의 사용자에게 어떻게 알리는가. Web Push 는 "나중에 보완으로"로 분류돼 있다
    (`docs/WHY_SPRING_BOOT.md` §5-3).
  - 수락하지 않은 사람이 있으면 그 파티는 어떻게 되는가 — 다음 배치는 내일이다.
- **재시도와 deadman 감시.** 한 번 실패하면 그날의 예약 전체가 짝을 못 찾는다. #24 의 deadman 감시
  (`reservation_batch_last_success_epoch_seconds`)가 1분 주기일 때보다 중요해진다. 수단은 미정이다.
- **배치 시각을 어떻게 나누는가** — 요일 · 공휴일 · 게임별. **시간대** — 저장은 UTC 이고 사용자는 KST 다.
  트리거 시각을 어느 기준으로 적는가.
- **실행 시간.** 한 번의 실행이 하루치를 다 처리한다. Lambda 최대 실행 시간 15분 안에 끝나는지는 규모에
  달렸고, 넘을 경우의 분할 방법.

**영향.**

- 이 저장소(`app:matching`)의 코드는 바뀌지 않는다. 다만 예약의 제안 · 수락을 `app:matching` 의 장치로
  하기로 정해지면, 20초 시한과 "페이지를 열어 둔 사용자에게만 닿는 알림"이 그대로는 맞지 않는다(위 미정).
- **D-15 의 본문을 이 결정에 맞춰 고쳤다**(D-15 는 아직 커밋되지 않은 항목이었다) — "바뀌지 않는 것"의 #23
  서술, 근거의 "1분에 한 번"과 "1분을 넘기면 다음 회차와 겹친다" 논리, 미정의 트리거 항목.
- `docs/AWS_ARCHITECTURE.md` 에는 머리 주석만 달았다. `contracts/README.md`(A-9)에 원본에 반영할 것을 적었다.
- `docs/04_RESERVATION_MATCHING_SPEC.md` §6 · §6-1 · §6-2, `docs/14_ARCHITECTURE_RATIONALE.md`,
  `docs/07_REDIS_DESIGN.md` 는 사본이라 고치지 않았다 — "1분 주기"와 그에 딸린 리드타임 · tier 완화 서술이
  남아 있다.

---

### D-18. Stage 1(단일 EC2 + Docker Compose)을 적용하지 않는다 — 배포 기준은 Stage 2(ECS Fargate)다 (#20 개정, 2026-09-19)

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템 전체 결정인 #20 의 개정이다. 원본 결정 로그가 있는
> queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때 #20 의 개정 항목으로
> 올려야 한다. **#20 의 본문은 고치지 않았다** (이 파일은 기록이다).

**원안.** #20 은 배포를 3단계 로드맵으로 두었다 — Stage 1 단일 EC2 + Docker Compose(현재) → Stage 2 ECS
Fargate 4서비스 → Stage 3 직접 구축 k8s. "**현재 기준은 Stage 1이다.**" 이행 트리거는 "Stage 1 → 2는 단일
호스트 리소스가 한계에 닿거나 무중단 배포가 필요해질 때"였다.

**결정.** **Stage 1 을 적용하지 않는다.** #20 의 로드맵에서 Stage 1 을 건너뛴다. **배포 기준은 Stage 2(ECS
Fargate — 서비스마다 태스크, RDS + ElastiCache, ALB)** 가 된다.

**바뀌지 않는 것.**

- **k8s 매니페스트 / HPA / sticky session 을 전제한 구현을 만들지 않는다**(#20). 기준이 Stage 2 가 돼도 같다.
- #20 의 "단계가 바뀌어도 안 바뀌는 것" — 모듈 경계 / 스키마 + DB 롤 / outbox + SQS 계약 / INV 검증 / 배포
  단위당 컨테이너 이미지 1개 / 환경변수 기반 설정 / `/health/live` · `/health/ready` 분리 / SIGTERM graceful
  shutdown / 프로세스 로컬 상태 금지 / stdout JSON 로그 / `/actuator/prometheus` 노출. (컨테이너 이미지 원칙의
  예외는 예약 Lambda 하나다 — D-15.)
- #20 의 Stage 2 서술 — RDS + ElastiCache, ALB, ARM(Graviton) 권고, public subnet + SG 차단으로 NAT 회피 권고,
  Service Connect 가 아니라 Cloud Map.
- 로컬 개발 방법. 테스트용 Redis · PostgreSQL 은 그대로 Docker 로 띄운다. 바뀌는 것은 배포 기준이지 개발
  방법이 아니다.

**근거.** 기록된 근거가 없다 — 결정만 남긴다.

*(후주 2026-09-19 — 같은 날 근거가 확정됐다.)* **서버를 관리할 사람이 없다.** 팀은 세 명이고 운영체제 패치 ·
호스트 장애 대응 · 디스크와 백업 관리에 쓸 손이 없다. 그래서 서버를 직접 두지 않는 쪽(서버리스 — ECS Fargate,
그리고 예약의 Lambda — D-15)으로 간다. 상시로 띄우는 용도라면 같은 사양의 EC2 가 Fargate 보다 싸고, EC2 는
PostgreSQL · Redis 를 컨테이너로 같이 돌릴 수 있어 RDS · ElastiCache · ALB 의 고정비까지 피할 수 있다는 점은
알고서 감수한다 — 아래 "감수하는 것"의 비용 차이는 서버 관리 부담을 사는 값이다. "EC2 위의 ECS"(ECS 의 관리는
쓰되 실행은 직접 둔 EC2 에서)도 같은 이유로 고르지 않았다.

**감수하는 것.** #20 의 표는 월 비용을 **Stage 1 ~$73 / Stage 2 ~$169 / Stage 3 ~$85** 로 적는다. Stage 2 가
Stage 1 보다 비싸다. #20 이 Stage 1 을 둔 근거는 "배포 단위를 4개로 나눈 이유(#15)는 Stage 2부터 값을 하는데,
지금 규모에서 관리형 오케스트레이터를 먼저 켜면 비용만 앞당겨 낸다"였다. **이 결정은 그 근거를 알고서
뒤집는다.**

**귀결.** Stage 2 에서는 서비스마다 따로 과금된다.

- (a) **상시로 띄우는 서비스의 수가 곧 비용이다.** 예약을 Lambda 로 뺀 것(D-15)과 같은 방향의 압력이다.
- (b) **`app:room` 을 상시 태스크로 둘지 Lambda 로 둘지가 실제 비용 문제가 된다** (D-16 의 "다시 볼 조건").
- (c) #20 의 Stage 2 서술은 "Fargate 4서비스"인데 지금 배포 단위는 **다섯**이다 — `app:matching` /
  `app:platform` / `app:room` / `app:realtime` / `app:reservation`, 그중 `app:reservation` 은 Lambda 다
  (D-15 · D-16). **~$169 는 예전 구성 기준의 수치다.**
- (d) 로컬 개발은 그대로다(위 "바뀌지 않는 것").

**아직 미정 — 임의로 지어내지 않는다.**

- **Stage 2 의 세부.** RDS · ElastiCache 사양. NAT 회피 방법 — #20 과 `docs/AWS_ARCHITECTURE.md` 는 "public
  subnet + SG 차단으로 NAT 회피 권고"라고 적는다. Lambda 가 VPC 안에서 Redis · PostgreSQL 에 닿는 방법과
  D-15 의 NAT 함정.
- **시연하지 않는 기간에 내려 두는 운영**(태스크 수 0, DB 중지 등)을 할지.
- **Stage 3(직접 구축 k8s)를 여전히 목표로 두는지.**

**영향.**

- 이 저장소(`app:matching`)의 코드는 바뀌지 않는다. `CLAUDE.md` §3 의 배포 기준 줄을 고쳤다.
- `../notification/CLAUDE.md` §6, `../platform/CLAUDE.md` §6, `../room/CLAUDE.md` §6 의 배포 기준을 고쳤다.
- **아직 커밋되지 않은 D-15 · D-16 의 본문을 이 결정에 맞춰 고쳤다** — D-15 미정의 "Stage 1 기준과의 관계",
  D-16 의 비용 문단.
- `docs/AWS_ARCHITECTURE.md` 의 그림은 원래 Stage 2(ECS Fargate) 구성을 그린 것이다. 머리 주석만 달았다 —
  그 문서의 "현재 개발 단계는 Stage 1 … 이 그림은 목표 상태다"는 이제 "이 그림이 배포 기준이다"로 읽는다.
- #20 이 말한 운영 문서(docs/09)는 이 저장소에 없다.

---

### D-19. "한 번에 하나만"을 키 둘로 지킨다 — `app:matching` 은 활성 요청 키, `app:room` 은 입장 표시 키를 쓰고 상대 키는 `EXISTS` 로만 본다 (D-11 16번 개정 · D-16 의 해당 대목 개정, 2026-09-19)

> **주어 낡음 — D-33 이 개정(2026-09-26 표시).** 키 약속은 그대로다. "`app:room`" 은 `app:platform`(의 `room` 패키지)으로 읽는다. 입장 표시 키 접두사의 원본은 `../platform/backend/src/main/java/com/queuemate/platform/room/redisKeys/RoomKeys.java` 다.

> **이 결정은 매칭 엔진만의 결정이 아니다.** `app:matching` 과 `app:room` 두 앱에 걸리는 결정이고, 이
> 저장소에 먼저 적어 둔 D-11(16번)과 D-16 의 개정이다. 원본 결정 로그가 있는 queueMate 본 저장소가 이
> 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때 D-11 · D-16 과 함께 올려야 한다. **D-11 · D-13 ·
> D-16 의 본문은 고치지 않았다** (이 파일은 기록이다). 다른 D-항목과 달리 **이 결정은 이 저장소의 코드를
> 바꾼다**(아래 "영향").

**원안.** D-11 16번은 "한 사용자는 자동 매칭 대기와 게시판 방 중 한 번에 하나만"(D-11 9번 · 15번)을
**활성 요청 키 `qm:user:active-request:{userId}` 하나**로 지키게 했다. 방에 들어올 때 방을 맡는 앱(D-16 이
`app:platform` 을 `app:room` 으로 개정했다)이 그 키에 값을 써 넣고 나갈 때 지운다. 그러면
`redis/shared/claim-request.lua` 의 기존 `EXISTS` 검사가 그대로 거절하므로 `app:matching` 에 새 검사 로직이
필요 없다 — 이것이 그 결정의 근거였다.

**결정. 키를 둘로 나눈다.**

| | `app:matching` | `app:room` |
|---|---|---|
| 자기 키 | 활성 요청 키 `qm:user:active-request:{userId}` (HASH) | **입장 표시 키 `qm:user:active-room:{userId}`** (STRING, 값은 `roomId`) |
| 자기 키에 하는 일 | 쓰고, 지우고, 수명을 건다 | 쓰고, 지우고, 수명을 건다 |
| 상대 키에 하는 일 | **`EXISTS` 로 있는지만 본다** | **`EXISTS` 로 있는지만 본다** |

1. **각자 자기 키만 쓰고 지운다. 상대 키는 있는지만 본다** — 값을 읽지 않고, 쓰지도 지우지도 `EXPIRE` 를
   걸지도 않는다.
2. **9번(매칭 대기 중이면 방에 못 들어간다)** — `app:room` 의 입장 Lua 가 활성 요청 키를 `EXISTS` 로 보고,
   있으면 입장을 거절한다.
3. **15번(방에 있으면 매칭을 못 돌린다)** — `claim-request.lua` 가 `KEYS[2]` 로 입장 표시 키를 받아 `EXISTS` 로
   보고, 있으면 **`-1`** 을 돌려준다. `service/MatchRequestService.java#join()` 이 그것을
   `JoinResult.Status.IN_ROOM` 으로 바꾸고 `controller/MatchingController.java` 가 **409 `IN_ROOM`** 으로
   내보낸다. 기존 409 `ALREADY_QUEUED`(반환 `0`)와 구분된다 — 둘 다 409 지만 클라이언트가 할 일이 다르다
   (`ALREADY_QUEUED` 면 상태 조회로 대기 화면을 복구하고, `IN_ROOM` 이면 방에서 나와야 한다 —
   `dto/JoinResult.java` 주석).
4. **입장 표시 키 접두사의 원본은 `app:room` 이다.** `../room/backend/src/main/java/com/queuemate/room/redisKeys/RoomKeys.java`
   의 `ACTIVE_ROOM_PREFIX = "qm:user:active-room:"` 가 원본이고, 이 저장소의 `redisKeys/SharedKeys.java`
   `ACTIVE_ROOM_PREFIX` 가 **따라 적는다.** 활성 요청 키 접두사는 반대다 — 원본이 이 저장소의
   `SharedKeys.ACTIVE_REQUEST_PREFIX` 이고 `app:room` 이 따라 적는다(D-11 16번 그대로).
5. **두 앱의 약속은 입장 표시 키의 "이름"뿐이다.** 자료형 · 값 · 수명은 `app:room` 이 혼자 정한다 —
   `app:matching` 은 `EXISTS` 만 보므로 그것들을 몰라도 된다.
6. **서비스 간 호출은 여전히 없다** (#15). 두 앱은 같은 Redis 를 볼 뿐이다.

**D-11 · D-16 · D-13 과의 관계.**

- **D-11 16번** "9번과 15번은 활성 요청 키 하나로 지킨다 · 방을 맡는 앱이 이 키에 값을 써 넣고 나갈 때
  지운다 · `claim-request.lua` 의 기존 검사가 그대로 거절한다 · `app:matching` 에 새 검사 로직을 넣지 않아도
  된다" → **전부 이 항목으로 바뀐다.** 9번 · 15번의 규칙 자체(한 번에 하나만)는 그대로이고 바뀌는 것은 지키는
  방법이다. 16번의 *(코드 메모)* — 배정 스크립트와 `leave-party.lua` 가 활성 요청 키를 `EXISTS` 로 보는 것은
  뜻이 반대다 — 는 그대로 맞다.
- **D-11 "아직 미정"의 16번 세부** — "HASH 에 무엇을 써 넣는가", "`app:matching` 이 게시판 표시가 든 키를 읽을
  때", "언제 지우는가"는 **물음 자체가 없어진다.** 활성 요청 HASH 에 남이 쓰는 값이 없기 때문이다. "없으면
  쓴다를 원자적으로"와 "안 지워지는 경우"는 `app:room` 이 **자기 키**에 대해 풀 문제로 남는다.
- **D-11 "영향"** 의 "새 검사 로직은 넣지 않는다", "남이 쓴 값을 읽는 쪽(상태 조회 · 취소)은 손봐야 할 수
  있다", "활성 요청 키가 두 앱이 쓰는 키가 됐다" → 셋 다 뒤집힌다. 검사 한 줄이 `claim-request.lua` 에
  들어갔고, 상태 조회 · 취소는 손볼 필요가 없어졌고, 활성 요청 키를 **쓰는** 앱은 다시 `app:matching`
  하나다(`app:room` 은 읽기만, 그것도 `EXISTS` 만 한다).
- **D-16** 의 `app:room` 기능 분담 가운데 "**활성 요청 키 쓰고 지우기**" → **"입장 표시 키 쓰고 지우기 +
  활성 요청 키를 `EXISTS` 로 보기"** 로 읽는다. "D-11 16번의 나머지(키 하나로 양방향을 지킨다 … )는 그대로이고
  주어만 `app:room` 으로 바뀐다"도 이 항목으로 바뀐다. D-16 "영향"의 "이 저장소의 코드는 바뀌지 않는다"도
  이 항목으로 더는 맞지 않는다.
- **D-16 "아직 미정"의 접속 확인** — "같은 신호로 활성 요청 키의 수명도 늘리면"은 **"입장 표시 키의 수명도
  늘리면"** 으로 읽는다. `app:room` 은 활성 요청 키에 `EXPIRE` 를 걸지 않는다(결정 1).
- **D-16 의 "세 번째 공유 약속"(방 키)** 은 이제 네 번째다 — 알림 채널 접두사, 활성 요청 키 접두사, **입장
  표시 키 접두사**, 방 키.
- **D-13 · D-16 이 적은 가능성 "파티(방)가 닫힐 때 방을 맡는 앱이 활성 요청 키를 지워 `status=PARTY` 를
  푼다"는 없어진다.** `app:room` 은 이제 활성 요청 키에 쓰지 않는다(아래 "아직 미정").

**근거.**

1. **활성 요청 키는 자물쇠가 아니라 `app:matching` 의 요청 기록이다.** `SharedKeys.ACTIVE_REQUEST_PREFIX` 의
   주석 그대로 "INV-1 의 선점 자리이자 키를 되조립할 재료"다 — `requestId` · 게임 · 조건 · `partyId` ·
   `status` 가 들어 있다. 상호 배제만 보면 자물쇠 하나를 같이 쓰는 것이 맞아 보이지만, 이 키에 남이 쓰면
   자물쇠를 같이 쓰는 것이 아니라 **남의 장부에 줄을 끼워 넣는 것**이 된다. `app:matching` 에서 이 키를 읽는
   모든 자리가 "남이 쓴 값일 수 있다"를 가려야 한다. 실제로 D-11 "아직 미정"의 16번 세부가 이미 적어 둔
   문제다 — 상태 조회(`service/MatchQueryService.java#find()`)는 방에 있는 사용자를 `QUEUED`(`requestId` 가
   비어 있는)라고 답하고, 취소(`service/MatchCancelService.java#cancel()`)는 `game` 필드가 없어 `valueOf` 에서
   예외로 끝난다. **키를 나누면 방에 있는 사용자에게는 활성 요청이 없으므로 조회는 `IDLE`, 취소는
   `NOT_FOUND` 로 고치지 않아도 옳게 답한다**(두 서비스 모두 HASH 가 비어 있으면 그렇게 답하는 분기가 이미
   있다).
2. **쓰는 주인은 하나여야 한다.** 한 키에 작성자 둘 · 형식 둘이면 앞으로 이 키를 읽는 코드가 늘 때마다 같은
   부담이 생긴다. 나누면 두 앱이 서로에 대해 아는 것이 **"저 키가 있는가" 하나**로 줄어든다 — 같은 Redis 를
   쓰는 두 서비스 사이에서 가능한 가장 약한 결합이다.
3. **사고가 번지는 범위 — 결정적인 이유다.** 키 하나에서는 `app:room` 의 버그가 매칭 엔진의 상태를
   망가뜨린다. 늦게 도착한 나가기가 그 사이 새로 건 매칭 요청을 `DEL` 할 수 있고, 접속 확인의 `EXPIRE` 가
   `app:matching` 이 `PERSIST` 해 둔 키에 수명을 걸 수 있다 — 그러면 파티 HASH 에는 멤버로 남았는데 활성 요청은
   사라진다. *(이 두 시나리오는 코드를 읽고 추론한 것이고 실험으로 확인하지는 않았다.)* 막으려면 `app:room` 의
   **모든** 스크립트가 "이 키가 내 것인가"를 먼저 확인해야 하고, 하나만 빠져도 사고다. **나누면 `app:room`
   버그의 최악은 자기 키가 남는 것이고, 그것은 수명이 풀어 준다.** 방을 떼어 낸 이유(D-16 — 방 쪽 문제로 다른
   것이 죽지 않게)와 같은 방향이다.
4. **수명 정책이 다르다.** `app:matching` 은 claim 때 60초 → 배정에 성공하면 `PERSIST` 다. `app:room` 은 접속
   확인으로 연장하는 임대다(방법 · 주기는 D-16 의 미정). 한 키에 두 정책을 얹지 않는다.
5. **"`app:matching` 을 안 고쳐도 된다"는 옛 근거는 이미 무너져 있었다.** D-11 16번 세부가 "남이 쓴 값을 읽는
   쪽(상태 조회 · 취소)은 손봐야 할 수 있다"고 적었다. 어차피 고친다면 `claim-request.lua` 에 `EXISTS` 하나를
   넣는 쪽이 조회 · 취소 · 상태 enum(`MatchRequestStatus`)을 고치는 쪽보다 작다.
6. **원자성은 그대로다.** Redis 는 스크립트를 하나씩 돌린다. 양쪽이 각각 **"두 키를 확인 → 자기 키만
   쓴다"를 Lua 하나로** 하면 먼저 돈 쪽이 이긴다. 서비스 간 호출도 여전히 없다(#15).

**감수하는 것.**

- **규칙이 이제 양쪽 스크립트가 둘 다 상대 키를 확인해야 성립한다.** 한쪽이 빠뜨리면 조용히 뚫린다. 다만
  뚫렸을 때의 피해는 "방에 있으면서 매칭도 도는 **제품 규칙 위반**"이고, 키 하나에서의 어긋남(**엔진 데이터
  오염**)보다 가볍다.
- **서로의 접두사를 아는 공유 약속이 하나에서 둘이 됐다.** 오타 위험도 두 군데다 — 어느 쪽이 틀려도 컴파일 ·
  테스트는 통과한다. `app:matching` 이 입장 표시 키 접두사를 틀리면 방에 있는 사람의 매칭 요청을 받게 되고,
  `app:room` 이 활성 요청 키 접두사를 틀리면 매칭 대기 중인 사람을 방에 들인다. 이 저장소의 테스트는
  접두사 문자열을 직접 적어 입장 표시 키를 만들므로(`ActiveRequestConcurrencyTest`) **이 저장소 안에서의**
  어긋남(`SharedKeys` 와 Lua 호출부)은 잡지만, `app:room` 과의 어긋남은 잡지 못한다.
- **세 번째 참가자(예: 예약)가 같은 규칙에 걸리면 확인할 키가 또 는다.** 그때는 **중립 자물쇠 키**(예: 사용자별
  슬롯 키 하나에 소유자를 적는 방식)로 옮기는 것을 다시 본다. **지금 그렇게 하지 않는 이유** — `app:matching`
  의 Lua 20개 가운데 활성 요청이 끝나는 모든 출구(취소 · 거절 · 만료, 그리고 아직 없는 확정 해제)가 자물쇠도
  같이 풀어야 하고, 하나만 빠져도 배정 때 `PERSIST` 된 뒤라 그 사용자가 영원히 잠긴다. 돌아가는 엔진에 "항상
  같이 살고 같이 죽어야 하는 키 둘"을 심는 위험이 얻는 것보다 크다. 기존 활성 요청 키가 "`app:matching` 의
  점유 표시" 역할을 이미 정확히 하고 있으므로 그대로 둔다.

**아직 미정 — 임의로 지어내지 않는다.**

- **입장 표시 키의 수명(TTL).** 접속 확인의 방법 · 주기(D-16 의 미정)와 묶인다. `app:room` 이 혼자 정한다.
- **`status=PARTY` 해제**(`HANDOFF.md` §0 ①). D-13 · D-16 이 가능성으로 적은 "방이 닫힐 때 `app:room` 이 이
  키를 지운다"는 **없어졌다.** 푸는 주체는 `app:matching` 이나 `app:platform` 쪽에서 찾아야 한다.
  `HANDOFF.md` §0 ① 의 후보 2("파티 나가기" API) · 3(긴 TTL 안전망)은 그대로 열려 있다.
- **확정된 사용자의 방 입장.** 확정된 사용자는 활성 요청 키가 `status=PARTY` 로 남아 있으므로 **그대로는
  `app:room` 입장도 거절된다**(결정 2 — `app:room` 은 값을 읽지 않고 `EXISTS` 만 본다). "자동 매칭으로 확정된
  파티의 방은 어떻게 생기는가"(D-16 의 미정)를 정할 때 같이 풀어야 한다.
- 그 밖에 D-11 · D-16 의 미정 목록 가운데 이 항목이 없애지 않은 것 전부.

**영향.**

- **이 저장소(`app:matching`)의 코드가 바뀌었다.** 경로는 `backend/src/main/` 아래의 상대 표기다.
  - `resources/redis/shared/claim-request.lua` — `KEYS[2]`(입장 표시 키)를 받는다. 활성 요청 키 `EXISTS`(`0`)
    다음, `HSET` 앞에서 `EXISTS KEYS[2]` 를 보고 있으면 **`-1`** 을 돌려준다. 그 경우 아무것도 쓰지 않는다.
  - `java/com/queuemate/matching/redisKeys/SharedKeys.java` — `ACTIVE_ROOM_PREFIX` 상수와 `activeRoomKey(userId)`.
    주석에 원본이 `room` 의 `RoomKeys.ACTIVE_ROOM_PREFIX` 임을 적었다.
  - `java/com/queuemate/matching/dto/JoinResult.java`(새 파일) — `Status` 가 `ACCEPTED` / `ALREADY_QUEUED` /
    `IN_ROOM` 셋이다. 거절 갈래가 둘이 되어 `Optional<AcceptedRequest>` 로는 모자라게 됐다.
  - `java/com/queuemate/matching/service/MatchRequestService.java` — `join()` 의 반환이
    `Optional<AcceptedRequest>` 에서 `JoinResult` 로 바뀌었고, 스크립트에 키를 둘 넘긴다. `-1` 은 `IN_ROOM`,
    `1` 이 아닌 나머지는 전부 `ALREADY_QUEUED` 로 닫는다(모르는 값을 접수로 읽지 않는다).
  - `java/com/queuemate/matching/controller/MatchingController.java` — `IN_ROOM` 을 **409 `IN_ROOM`** 으로 내보낸다.
  - 테스트 `backend/src/test/java/com/queuemate/matching/concurrency/ActiveRequestConcurrencyTest.java` 에 3건 —
    `secondRequestIsAlreadyQueued`(두 번째 요청은 `ALREADY_QUEUED`) · `userInRoomIsRejected`(입장 표시 키가
    있으면 `IN_ROOM` 이고 활성 요청 키가 생기지 않으며 **입장 표시 키의 값이 그대로다**) ·
    `userWhoLeftRoomCanQueue`(입장 표시 키가 사라지면 다시 접수된다). 동시성 테스트는 24건에서 **27건**이 됐다.
    `join()` 의 반환형이 바뀌어 그것을 부르는 다른 테스트 6개(`NaiveVsLuaComparisonTest` ·
    `PartyJoinConcurrencyTest` · `PubgPartyJoinConcurrencyTest` · `ValorantPartyJoinConcurrencyTest` ·
    `PushNotificationTest` · `ProposalIdempotencyTest`)의 호출부도 한 줄씩 맞췄다.
  - **바뀌지 않은 것** — 상태 조회(`MatchQueryService`) · 취소(`MatchCancelService`) · `MatchRequestStatus` ·
    나머지 Lua 19개. 입장 표시 키를 보는 자리는 `claim-request.lua` 하나다.
- **계약 사본.** `contracts/README.md` 의 **A-10**(원본에 반영할 것 — `POST /match-requests` 의 409 에러 코드가
  `ALREADY_QUEUED` / `IN_ROOM` 둘이 된다)과 에러 코드 표의 409 `IN_ROOM` 줄, `contracts/openapi.yaml` 의 `409`
  설명과 `ErrorResponse.code` enum 의 `IN_ROOM`. A-10 이 가리키는 D-항목이 이 항목이다.
- **`app:room`(`../room/`).** 원본 상수 `RoomKeys.ACTIVE_ROOM_PREFIX` 와 활성 요청 키 접두사의 사본
  (`../room/backend/src/main/java/com/queuemate/room/redisKeys/SharedKeys.java`)이 있다. 입장 Lua 는 이 항목을
  쓰는 시점에 작성 중이다. `../room/` 의 규칙 문서(`CLAUDE.md` §3.2 · §7, `START_HERE.md`, `README.md`,
  `docs/PROJECT_OVERVIEW.md`)는 같은 날 이 결정에 맞췄다. 그 폴더의 사본(`docs/DECISIONS.md` ·
  `docs/MATCHING_REFERENCE.md`)은 이 항목이 커밋된 뒤 다시 떠 간다.
- 알림 서비스 · `app:platform` 은 바뀌지 않는다. `app:platform` 은 D-16 부터 활성 요청 키를 만지지 않았고 입장
  표시 키도 만지지 않는다.
- `CLAUDE.md` §1(게시판 예외 대목) · §3(`SharedKeys` 접두사 목록) · §4(INV-1 · INV-2 행, 회귀 테스트 표, Lua
  스크립트 표), `START_HERE.md`, `HANDOFF.md` §0 ① 을 이 결정에 맞췄다.

---

### D-20. 게시판 목록이 방 안의 사람을 카드로 보여 주고 F5 없이 갱신된다 — 목록은 `app:platform` 이 `app:room` 의 멤버 SET 을 읽어 조립하고, 갱신은 게시판 채널의 "바뀌었다" 신호로 알리며, 차단은 방 안의 누구와든 본다 (D-11 의 목록 구체화 · 14번의 범위 확정, D-16 의 방 키 읽기 범위 개정, 2026-09-20)

> **일부 낡음 — D-31 · D-33 이 개정(2026-09-26 표시).** ③(`filledPositions`)은 D-31 로 없어졌다. "`app:platform` 이 `app:room` 의 멤버 SET 을 읽는다" · 입장권은 D-33 으로 한 앱 안의 호출 · 입장 요청 안의 글 검사가 됐다. ①②④ · 차단 범위 · 신호는 그대로다.

> **이 결정은 매칭 엔진의 결정이 아니다.** 제품 요구(D-11 의 게시판 목록)가 바뀐 것과, 그것을 `app:platform` ·
> `app:room` · 알림 서비스(`app:realtime`, 지금 이름은 `notification`) 세 앱이 어떻게 나눠 맡는지를 정한 것이다.
> 원본 결정 로그가 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본 저장소와 합칠 때 D-11 ·
> D-16 과 함께 올려야 한다. **D-11 · D-12 · D-16 의 본문은 고치지 않았다** (이 파일은 기록이다). **이 결정은 이
> 저장소(`app:matching`)의 코드를 바꾸지 않는다**(아래 "영향").

**원안.** 게시판 목록에 대해 지금까지 적혀 있던 것은 아래가 전부다.

- **D-11 3번** — "다른 사용자는 게시판에서 글을 보고 누르면 그 방 안으로 들어온다." 목록의 한 줄이 무엇을 보여
  주는지는 정하지 않았다. D-11 "아직 미정"은 "모집 글에 담는 것(게임 · 모드 · 원하는 조건 · 한 줄 소개 등),
  목록의 정렬 · 필터"와 "5명이 가득 찼을 때 목록에 어떻게 보이고, 눌렀을 때 어떻게 되는가"를 열어 두었다.
- **D-11 14번** — "차단 관계가 있으면 그 방이 목록에 아예 보이지 않는다. … 어느 쪽이 차단했든 보이지 않는다.
  검사 주체는 `social.blocks` 를 소유한 `app:platform` 자신이다 — 목록을 만들 때 거른다." 그 범위는 D-11 "아직
  미정"에 남아 있었다 — "차단 관계를 **방장과의 사이에서만** 보는가, **방 안에 있는 누구와든** 보는가 — 방에
  이미 들어와 있는 제3자와 차단 관계인 사용자에게 그 방이 보이는가."
- **D-11 "여전히 금지인 것"** — "공개 사용자 탐색 — … 게시판이 보여 주는 것은 **사람 목록이 아니라 모집 글(방)
  목록**이다."
- **D-16 "두 앱을 잇는 방법" 2번** — "`app:platform` 이 방의 Redis 키를 읽기만 한다. 목록을 만들 때 글마다 '방이
  살아 있는가 · 몇 명인가'를 Redis 에서 조회한다(가득 찬 방 표시)." 즉 목록이 방에서 가져오는 것은 **인원수까지**
  였다. D-16 "근거"는 목록과 글을 `app:platform` 에 둔 덕에 "목록을 그릴 때마다 차단 · 프로필을 다른 서비스에서
  가져오기"가 생기지 않는다고 적었다.
- **D-11 · D-16 "영향"** — "알림 서비스는 바뀌지 않는다." 알림 채널은 사용자 한 명의 채널
  `qm:pubsub:push:{userId}` 하나뿐이고, 목록이 바뀐 것을 보고 있는 화면에 알리는 길은 없었다(다시 받아야 보인다).

**결정.**

**제품 — 게시판 목록의 한 줄(= 모집 글 = 방)이 아래 넷을 보여 준다.**

1. **방 안에 몇 명이 있는가.**
2. **방 안에 있는 사람들의 정보를 카드로** — 닉네임 · 티어 · 포지션 등.
3. **글의 "찾는 포지션" 가운데 이미 방 안에 있는 포지션을 밝게 강조한다.**
4. **목록은 F5 없이 갱신된다** — 다른 사람이 글을 올리거나 방의 인원이 바뀌면 보고 있는 화면에 반영된다.

**설계.**

**(가) 포지션의 출처는 프로필의 주 포지션이다.** `app:platform` DB 의 계정 · 게임 계정에 있는 값이다. "이 방에서는
서폿을 하겠다"처럼 **입장할 때 고르는 방식은 하지 않는다.** 3번의 강조는 **"글의 찾는 포지션 ∩ 방 안 사람들의 주
포지션"** 이고 `app:platform`(또는 프런트)에서 계산한다. `app:room` 은 포지션을 들지 않는다.

**(나) 목록의 데이터는 전부 `app:platform` 이 조립한다.** `app:room` 의 Redis 키 — 멤버 SET
`qm:room:{roomId}:members` 의 `SCARD`(몇 명) · `SMEMBERS`(누구, `userId` 목록) — 를 읽고, 자기 DB 에서 그 사람들의
프로필을 붙인다. **서비스 간 호출을 만들지 않고(#15), `app:room` 이 PostgreSQL 에 붙지도 않는다(D-16).** `app:room`
은 `userId` 만 안다.

**(다) 갱신은 게시판 채널에 "바뀌었다"는 신호만 방송하는 것으로 한다.** 지금 알림은 사용자 한 명의 채널로 가는데,
목록을 보는 사람은 **"누구인지 모르는 다수"** 다. 그래서 사용자 채널과 별개인 **주제 채널**을 새로 둔다.

- **채널 이름은 `qm:pubsub:board:{game}` 이다.** `{game}` 은 `LOL` · `VALORANT` · `PUBG` 다.
- **알림의 `type` 은 `BOARD_CHANGED` 다.** 봉투 네 칸(`{type, eventId, occurredAt, payload}`)은 그대로이고
  **`payload` 는 빈 객체 `{}` 다.**
- **`app:platform`** 은 글이 생기거나 사라지거나 상태가 바뀔 때 그 채널에 발행한다.
- **`app:room`** 은 방의 인원이 바뀔 때 발행한다 — 방 만들기 · 입장 · 나가기 · 강퇴 · 접속 확인이 유령을 뺐을 때 ·
  방 닫힘 · 방장 확정.
- **알림 서비스**가 그 채널을 구독해 **"게시판을 보고 있는 연결"** 에 SSE 로 흘려보낸다.
- **클라이언트는 SSE 를 열 때 쿼리 파라미터 `topics=board:LOL` 로 게시판 구독을 알린다**(예:
  `GET /api/v1/events?topics=board:LOL`).
- **이 신호는 "다시 받아라"일 뿐이다.** 받은 프런트가 `app:platform` 의 목록을 `GET` 으로 다시 요청한다(몇 초에 한
  번으로 묶어서). **데이터와 차단 거르기는 그 응답에서 온다.**
- **신호에는 데이터를 싣지 않는다.** 방송에 데이터를 실으면 사람별로 거를 수 없다 — (라)의 차단이 성립하지 않는다.
  `app:room` 이 프로필을 몰라도 되는 것도 이 덕이다. **`roomId` 도 싣지 않는다** — "뭔가 바뀌었다"만 보낸다. 나에게
  숨겨진 방이 바뀌었다는 사실이 새어 나가지 않게 하려는 것이다.
- **알림 서비스의 원칙 "본문을 열어 보지 않고 그대로 흘려보낸다"는 그대로다.** 새로 생기는 것은 **"연결이 사용자
  채널 말고 주제 채널도 구독할 수 있다"** 는 개념 하나다. **정해졌고 구현 전이다.**
- 급하면 **프런트가 몇 초마다 목록을 다시 받는 방식으로 먼저 시작해도 된다** — 나중에 신호를 얹어도 프런트 코드는
  거의 그대로다.

**(라) 차단은 방 안의 누구와든 본다.** 방 안에 나와 차단 관계(**어느 방향이든**)인 사람이 **한 명이라도** 있으면
**그 방 자체를 내 목록에서 뺀다.** 들어가면 음성으로 바로 마주치기 때문이다.

- **입장권을 내줄 때도 같은 규칙을 적용한다** — 목록에서만 숨기고 입장은 되면 의미가 없다. 내가 방에 들어간
  **뒤에** 나와 차단 관계인 사람이 따라 들어오는 것은, **그 사람의 입장권 발급 때** 방 안의 나와 대조해야 막힌다.

**(마) `app:room` 의 방 안 사람 목록 API 는 그대로다.** `GET /api/v1/rooms/{roomId}/members` 는 여전히 **방 안의
사람만** 볼 수 있다(403 `NOT_IN_ROOM` — `../room/contracts/room-api.md` "방 안 사람 목록"). 방 밖에서 방 안을 보는
공개 창구는 `app:platform` 의 목록이다 — 차단을 거르는 곳이 거기라서다. 둘은 모순이 아니다.

**D-11 · D-16 · D-12 · D-19 와의 관계.**

- **D-11 3번 · "아직 미정"의 목록 관련 항목** → 목록의 한 줄이 무엇을 보여 주는지가 위 1~4 로 **구체화됐다.** "모집
  글에 담는 것"에는 3번 때문에 **"찾는 포지션"이 들어간다.** 그 밖의 글 내용 · 정렬 · 필터 · 만석일 때의 표시는
  D-11 의 미정 그대로다.
- **D-11 14번의 범위** → **"방 안에 있는 누구와든"으로 정해졌다**((라)). 14번의 규칙 자체(막는 것이 아니라 보이지
  않게 · 방향을 가리지 않는다 · 검사 주체는 `app:platform`)는 그대로다. D-11 "아직 미정"의 같은 항목 둘째 줄 —
  "목록을 본 뒤에 차단이 생긴 경우, 이미 같은 방에 있는 두 사람 사이에 차단이 생긴 경우" — 는 **그대로 미정**이다.
- **D-11 "여전히 금지인 것"의 공개 사용자 탐색** → **그대로 금지다.** 목록의 단위는 여전히 **모집 글(방)** 이고,
  카드는 그 방에 딸려 나온다. 사람을 검색하거나 방과 무관하게 사람을 둘러보는 기능은 만들지 않는다.
- **D-16 "두 앱을 잇는 방법" 2번** → `app:platform` 이 방 키에서 읽는 것이 "방이 살아 있는가 · 몇 명인가"에서
  **"누가 있는가"(`SMEMBERS`)까지** 늘었다. **읽기만 하고 쓰지 않는다**는 점, 방장 확정 때 같은 키에서 현재 인원을
  읽는다는 점은 그대로다. D-16 "근거"의 "목록을 그릴 때마다 차단 · 프로필을 다른 서비스에서 가져오기가 생기지
  않는다"도 그대로 성립한다 — 다른 서비스에서 가져오는 것은 **`userId` 목록뿐**이고 프로필과 차단은 여전히
  `app:platform` 자기 DB 의 조회다.
- **D-16 입장권(같은 절 1번)** 의 "차단 관계가 아닌지 확인하고" → 확인하는 상대가 **방 안의 전원**이 된다((라)).
  `app:platform` 은 입장권을 내줄 때도 멤버 SET 을 읽는다.
- **D-11 · D-16 "영향"의 "알림 서비스는 바뀌지 않는다"** → **더는 맞지 않는다.** 주제 채널 구독이 생긴다((다)).
  "누가 발행하든 받은 것을 그대로 흘려보낸다"는 그대로다.
- **D-12** → 그대로다. (라)의 대조는 `app:platform` 이 **자기 DB 의 `social.blocks` 를 직접 읽는** 같은 앱 안의
  조회이고(D-12 "영향"이 적은 그대로), 큐도 Redis read model 도 생기지 않는다.
- **D-19** → 바뀌지 않는다. **같은 원칙을 따른다** — 공유하는 Redis 키는 쓰는 주인이 하나다. 방 키는 `app:room` 만
  쓰고 `app:platform` 은 읽기만 한다. D-19 가 센 "공유 약속 넷"(알림 채널 접두사 · 활성 요청 키 접두사 · 입장 표시 키
  접두사 · 방 키)에 **게시판 채널의 이름이 다섯 번째로 더해진다**(원본 상수를 둘 곳은 미정 — 아래).

**근거.**

1. **(가) — 입장할 때 포지션을 고르게 하면 `app:room` 이 포지션을 들고 있어야 한다.** 멤버 SET 을 HASH 로 바꿔야
   하고, 그러면 `app:platform` 과의 방 키 약속(D-16)이 바뀐다. 프로필의 주 포지션을 쓰면 `app:room` 은 `userId` 만
   아는 채로 남는다.
2. **(나) — 검토한 대안 셋과 버린 이유.**
   - ① **`app:platform` 이 `app:room` 의 API 를 호출한다** — 동기 호출이 생겨 한쪽이 죽으면 목록도 죽고 #15 가
     깨진다. 글 N개에 호출 N번이다.
   - ② **`app:room` 이 이벤트를 보내고 `app:platform` 이 사본을 유지한다** — 가장 정석이지만, 놓치면 사본이 영원히
     틀려서 내구성 있는 큐(SQS)와 재처리 · 주기적 맞추기가 필요하다. "방에 누가 있나"처럼 금방 사라지는 정보에는
     과하다.
   - ③ **프런트가 `app:platform` 과 `app:room` 을 따로 불러 조합한다** — 서버끼리는 깔끔하지만 **차단 관계를
     서버에서 거를 수 없다.**
3. **(다) — 목록을 보는 사람이 누구인지 발행하는 쪽이 모른다.** 사용자 채널로는 보낼 수 없다. 그리고 데이터를
   방송에 실으면 사람별로 거를 수 없으므로(차단), 신호는 "다시 받아라"만 말하고 데이터는 거르는 곳(`app:platform`
   의 목록)에서 받게 한다. 알림을 "다시 조회하라는 신호"로 다루는 기존 규약(`contracts/events.md` "순서 보장
   범위")과 같은 방향이다.
4. **(라) — 방에 들어가면 음성으로 바로 마주친다**(D-11 4번). 검토하고 버린 것 둘 — **방장과의 사이에서만 본다**
   (방 안의 제3자와 마주친다) / **방은 보이되 그 사람의 카드만 가린다**(들어가면 마주치는 것은 같다).

**감수하는 것.**

- **(나)는 교과서대로면 "DB 공유로 통합하기"다 — 알고 감수한다.** 읽기 전용 · 키 셋(`../room/contracts/room-api.md`
  "Redis 키"의 표) · 쓰는 주인 하나(`app:room`)로 좁혀 두었다. **나중에 ②로 옮길 길은 열려 있다** — `app:room` 이
  이미 인원이 바뀔 때마다 알림을 발행한다.
- **목록을 그리는 비용이 는다.** 목록을 그릴 때마다 글마다 멤버 SET 을 읽고, 방 안 전원과 요청자의 차단 관계를
  대조하고, 그 사람들의 프로필을 붙여야 한다. 입장권을 내줄 때도 멤버 SET 을 읽는다.
- **이미 없는 사람 때문에 방이 잠깐 숨겨질 수 있다.** 멤버 SET 에는 말없이 사라진 사람이 길어야 수명(600초)만큼
  남을 수 있다(`../room/contracts/room-api.md` "접속 확인"). 그 사람과 차단 관계인 사용자에게는 그동안 그 방이
  보이지 않고, 카드와 인원수에도 그 사람이 남는다.
- **수명이 다해 없어진 방은 신호를 내지 못한다.** 방장이 말없이 사라져 방 키의 수명이 다하는 순간에는 `app:room` 의
  코드가 돌지 않는다(같은 절 — 그래서 그때는 `ROOM_CLOSED` 도 가지 않는다). 그 글은 다음에 누군가 목록을 다시
  받을 때 `app:platform` 이 방장 키가 없는 것을 보고 만료시킨다(D-16 그대로). *(이 항목은 `../room/` 의 계약을
  읽고 추론한 것이고 실험으로 확인하지는 않았다.)*
- **신호는 놓칠 수 있다.** Pub/Sub 이라 SSE 가 끊겨 있던 동안의 신호는 다시 오지 않는다. 다른 알림과 같다 —
  프런트는 SSE 를 (다시) 연결한 직후 목록을 다시 받는다.
- **서비스 경계를 넘는 Redis 이름 약속이 하나 더 는다**(게시판 채널). 알림 채널 접두사와 같은 위험이다 — 어긋나도
  컴파일 · 테스트가 통과한 채로 목록이 조용히 갱신되지 않는다. 발행하는 앱이 둘(`app:platform` · `app:room`), 구독하는
  앱이 하나(알림 서비스)다.
- **알림 서비스에 개념이 하나 는다.** 지금까지 "연결 = 사용자 한 명의 채널"이었는데 "연결이 주제 채널도 구독한다"가
  생긴다.

**아직 미정 — 임의로 지어내지 않는다.**

- **`app:room` 이 어느 게임의 채널에 발행할지를 어떻게 아는가 — 이 결정으로 새로 드러난 미정이다.** **`app:room` 은
  지금 방이 어느 게임의 것인지 모른다** — `app:room` 의 키(방장 키 · 멤버 SET · 입장 표시 키)에도, 방 만들기 · 입장
  요청에도 게임이 없다(`../room/contracts/room-api.md`). 방 만들기 때 받아 방 키에 두는지, 입장권이 말해 주는지
  (입장권도 미정이다 — D-16) 정해지지 않았다. 방 키에 두면 방 키 약속(D-16)에 걸린다.
- **게시판 채널 접두사의 원본 상수를 어느 서비스에 둘지.** 알림 채널 접두사는 이 저장소의
  `redisKeys/SharedKeys.java`(`PUSH_CHANNEL_PREFIX`)가 원본이다 — 같은 방식으로 갈지 정해지지 않았다.
- **한 연결이 여러 주제를 구독할 때의 `topics` 표기**(쉼표로 나열하는지 등).
- **프런트가 재요청을 묶는 간격**("몇 초에 한 번").
- **3번의 강조를 어디서 계산하는가** — `app:platform` 인가 프런트인가.
- **카드에 담는 것**(닉네임 · 티어 · 포지션 "등"의 나머지), 주 포지션이 프로필에 없는 사용자 · 포지션 개념이 다른
  게임(지원 게임은 LoL · VALORANT · PUBG 다 — #8)에서의 표시. 이 항목은 결정에 없던 물음이라 적어만 둔다.
- **(라)에 남는 경쟁.** 입장권을 **받은 뒤 들어오기 전** 사이에, 나와 차단 관계인 사람이 먼저 그 방에 들어온 경우.
- **이미 같은 방에 있는 두 사람 사이에 차단이 생긴 경우** — D-11 의 미정 그대로다.
- 그 밖에 D-11 · D-16 의 미정 목록 가운데 이 항목이 없애지 않은 것 전부.

**영향.**

- **이 저장소(`app:matching`)의 코드는 바뀌지 않는다.** 매칭 Redis 키도, 알림 채널 `qm:pubsub:push:{userId}` 도,
  `notification/PushPublisher.java` 도 그대로다. `app:matching` 은 게시판 채널에 발행하지 않는다. 채널 접두사의 원본을
  이 저장소의 `SharedKeys` 에 두기로 정해지면 그때 상수 하나가 는다 — **지금은 아니다.**
- **`contracts/events.md` 는 고치지 않았다.** 그 파일은 계약 원본의 발췌 사본이고 이 작업은 결정을 남기는 데까지다.
  `BOARD_CHANGED` 와 게시판 채널 · `topics` 파라미터를 그 파일과 `contracts/README.md` 의 "앞서간 변경" 표에 올리는
  일이 남아 있다.
- **`app:platform`(`../platform/`)** — 아직 코드가 없다. 목록 조립(멤버 SET 읽기 → 프로필 붙이기 → 방 안 전원과의
  차단 대조 → 포지션 강조), 입장권 발급 때의 같은 대조, 게시판 채널 발행이 이 앱의 일로 더해졌다.
  `../platform/CLAUDE.md` §2 · §3.2 · §3.3 · §7 · §7.1, `../platform/README.md`, `../platform/docs/ROOM_CONTRACT.md` 의
  머리 절("`platform` 에서 이것이 뜻하는 것")을 같은 날 맞췄다.
- **`app:room`(`../room/`)** — **방 키의 이름 · 구조는 바뀌지 않는다**(멤버 SET 은 SET 그대로다). 방 안 사람 목록
  API 도 그대로다. 더해지는 것은 게시판 채널 신호 발행 하나이고 **구현 전**이다 — 알림 서비스의 주제 구독이 먼저다.
  `../room/CLAUDE.md` §2 · §3.1 · §7, `../room/START_HERE.md`, `../room/contracts/room-api.md` "알림",
  `../room/docs/PROJECT_OVERVIEW.md` 를 같은 날 맞췄고, 그 폴더의 사본 `docs/DECISIONS.md` 에 이 항목을 옮겼다.
- **알림 서비스(`../notification/`)** — 주제 채널 구독이 **정해졌고 구현 전**이다. 본문을 열어 보지 않는다 ·
  sticky session 이 필요 없다는 원칙은 그대로다. `../notification/CLAUDE.md` · `../notification/README.md` 를 같은 날
  맞췄다.
- 이 저장소의 `CLAUDE.md` · `START_HERE.md` · `HANDOFF.md` 는 고치지 않았다 — 이 결정이 매칭 엔진에 걸리는 곳이
  없다.

---

### D-21. `app:room` 의 방 안의 규칙과 계약을 정한다 — D-11 · D-16 이 미정으로 남긴 것들의 답: 방은 "방 만들기" 요청이 만들고, 방의 수명은 방장의 접속 확인만 늘리며, 방장 확정은 `app:room` 이 자기 키에 표시한다 (D-9 · D-11 · D-16 의 미정 확정, D-16 의 "닫힘 표시" 가능성과 D-9 의 구독자 수 제안 기각, 2026-09-20)

> **일부 낡음 — D-33 이 개정(2026-09-26 표시).** 주어 `app:room` 은 `app:platform` 의 `room` 패키지다. 결정 2(방 만들기 요청)는 "글 쓰기가 방을 같이 만든다"로, 결정 6 의 확정 기록은 "한 요청이 Redis 와 DB 를 같이 쓴다"로 바뀌었고, 공통 에러 코드가 한 벌(`VALIDATION_FAILED` · `ROOM_STATE_UNAVAILABLE`)이 됐다.

> **이 결정은 매칭 엔진의 결정이 아니다.** `app:room` 을 구현하면서(2026-09-19~20) D-9 · D-11 · D-16 의 "아직 미정"
> 가운데 방 안의 일에 걸리는 것들을 정한 것이다. 지금까지 D-19(키 둘)와 D-20(게시판 목록)만 이 로그에 있었고 나머지는
> `../room/` 의 문서에만 있었다 — 그것을 여기로 옮겨 적는다. **사실의 원본은 `../room/contracts/room-api.md` 와
> `../room/CLAUDE.md` §7 이다.** 원본 결정 로그가 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본
> 저장소와 합칠 때 D-9 · D-11 · D-16 · D-19 · D-20 과 함께 올려야 한다. **D-9 · D-11 · D-16 · D-19 · D-20 의 본문은 고치지
> 않았다** (이 파일은 기록이다). **이 결정은 이 저장소(`app:matching`)의 코드를 바꾸지 않는다**(아래 "영향").

**원안.** 이번에 답이 난 물음은 D-9 · D-11 · D-16 의 "아직 미정" 절에 아래처럼 적혀 있었다.

- **방 키** — D-16 "아직 미정": "**방 키의 이름 · 구조 · 수명.**" D-16 "감수하는 것"은 "`app:platform` 이 읽는 방 키의 이름 ·
  구조를 `app:room` 이 혼자 바꿀 수 없다"고 적었다.
- **접속 확인** — D-16 "아직 미정": "**접속 확인의 방법과 주기.** 브라우저가 주기적으로 신호를 보내 입장 기록의 수명을 늘리는
  방식이 **가능한 방법**이다 — 같은 신호로 활성 요청 키의 수명도 늘리면 D-11 16번 세부의 '안 지워지는 경우'가 같이 풀린다."
  ("활성 요청 키"는 D-19 로 "입장 표시 키"로 읽는다.) D-19 "아직 미정": "**입장 표시 키의 수명(TTL).** 접속 확인의 방법 ·
  주기(D-16 의 미정)와 묶인다."
- **방장 이탈의 판단** — D-11 "아직 미정": "방장이 나간 것을 **무엇으로 판단하는가** — 명시적 나가기만인가, 연결이 끊긴
  경우도인가, 얼마나 기다리는가."
- **방이 처음 만들어지는 시점** — D-16 "아직 미정": "**방이 처음 만들어지는 시점** — 방장이 글을 쓴 직후 입장권으로 들어올
  때인가."
- **방장 확정** — D-11 "아직 미정"의 13번 세부: "확정하는 대상이 **그 순간 방에 있는 전원**인가, **방장이 고른 사람만**인가.
  고르지 않은 사람은 확정 순간 방에서 나가게 되는가." · "확정 뒤 빈자리가 생기면 다시 모집을 열 수 있는가."
- **확정 순간의 경쟁** — D-16 "두 앱을 잇는 방법" 3번: "방장이 확정을 누르는 찰나에, 미리 받아 둔 입장권으로 누가 들어올 수
  있다. `app:platform` 이 확정 직전에 Redis 에 '닫힘' 표시를 먼저 쓰고 `app:room` 이 입장을 처리할 때 그 표시를 같이 확인하는
  식으로 막을 수 있다 — **가능한 방법일 뿐 정해진 것이 아니다.** 이 방법을 쓰면 `app:platform` 이 `app:room` 의 키 공간에
  **쓰는** 예외가 하나 생긴다."
- **만석일 때의 응답** — D-11 "아직 미정": "5명이 **가득 찼을 때** 목록에 어떻게 보이고, 눌렀을 때 어떻게 되는가." 이번에 답이
  난 것은 뒤쪽(눌렀을 때)뿐이다. D-16 에는 해당 대목이 없다.
- **알림의 종류와 이름** — D-11 "아직 미정": "**어떤 알림 종류를 쓰는가** — 기존 `PARTY_*` 재사용인가 새 `type` 인가. 계약
  원본이 이 컴퓨터에 없어 `PARTY_*` · `FRIEND_*` 7종은 이름조차 이 저장소에 없다." D-16 "아직 미정": "`app:room` 이 발행하는
  알림의 종류와 이름(기존 `PARTY_*` 재사용인가 새 `type` 인가 — D-11 의 미정 그대로)." D-16 "영향": "방 입장 · 퇴장 알림을 어느
  앱이 어떤 `type` 으로 내는지는 미정이다."
- **시그널 `POST`** — D-9 "아직 미정": "`POST` 엔드포인트의 **경로와 요청/응답 스키마.** (후보로
  `POST /api/v1/parties/{partyId}/signals` 를 생각할 수 있으나 **후보일 뿐 정해진 것이 아니다.**)" · "`WEBRTC_SIGNAL` 의
  **`payload` 스키마.** 정해야 할 항목: 보낸 사람 식별자 / 파티 식별자 / 종류(offer · answer · ICE candidate) / SDP 또는
  candidate 본문 / 재협상 시도를 구분할 식별자." D-9 "감수하는 것 / 운영 제약"의 *(제안 사항)*: "`PUBLISH` 반환값(그 순간
  구독자 수)을 `POST` 응답에 실어 주면 클라이언트가 '상대가 지금 연결 안 됨(0)'을 알고 기다리지 않고 재시도할 수 있다."
- **강퇴** — D-11 8번이 규칙("방장은 들어온 사람을 내보낼 수 있다")을, D-16 이 그것이 `app:room` 의 일임을 정했다. 미정으로
  적힌 것은 재입장뿐이다 — D-11 "강퇴당한 사람이 **다시 들어올 수 있는가.**", D-16 "**강퇴당한 사람의 재입장** — 입장권으로
  막을지, `app:room` 이 기억할지." **강퇴의 나머지 세부(방장인지를 무엇으로 확인하는가, 누구에게 알리는가)를 미정으로 적은
  대목은 D-11 · D-16 에 없다.**
- **엔드포인트** — D-11 "아직 미정": "모든 엔드포인트의 경로 · 스키마, 테이블." 방 안 사람 목록 · 내 방 찾기 같은 **조회**와
  **응답의 공통 규칙**을 따로 미정으로 적은 대목은 D-11 · D-16 에 없다(D-9 가 시그널 복구의 기준점으로 "서버에 저장된 파티원
  목록"을 든 것이 전부다).

**결정.** 요청은 아홉 가지다 — 방 만들기 · 입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인 · 방 안 사람 목록 · 내 방 찾기 ·
시그널 보내기. **경로 · 상태 코드 · 에러 코드 · `payload` 의 원본은 `../room/contracts/room-api.md` 이고** 아래는 그 규칙을
옮긴 것이다.

1. **방 키.** 원본 상수는 `app:room` 의 `redisKeys/RoomKeys.java` 다. **`app:platform` 이 읽는다** — 이름과 구조는 두 앱의
   약속이고 `app:room` 이 혼자 바꿀 수 없다(D-16).

   | 키 | 자료형 | 값 | 뜻 |
   |---|---|---|---|
   | `qm:room:{roomId}:host` | STRING | 방장의 `userId` | 방장 키. **이 키가 있다 = 방이 있다** |
   | `qm:room:{roomId}:members` | SET | 방에 있는 사람의 `userId`(**방장 포함**) | `SCARD` 가 현재 인원이다. 정원은 5(D-11 10번). 원소는 `userId` 뿐이다(D-20) |
   | `qm:room:{roomId}:confirmed` | STRING | 그 방의 `roomId` | 확정 표시 키. **이 키가 있다 = 방장이 확정한 방이다** |
   | `qm:user:active-room:{userId}` | STRING | 들어가 있는 방의 `roomId` | 입장 표시 키(D-19). `app:matching` 도 `EXISTS` 로 본다 |

2. **방은 "방 만들기" 요청이 만든다**(`POST /api/v1/rooms/{roomId}`). 부른 사람이 방장이고, 만들면서 곧바로 그 방에 들어와
   있다(정원 5명에 방장이 포함된다). **입장은 방을 만들지 않는다 — 없는 방은 404 `ROOM_NOT_FOUND` 다.** 방장이 나가 사라진
   방에 늦게 도착한 입장이 방장 없는 방을 되살리면 안 되기 때문이다. 같은 사람이 같은 방을 다시 만들거나 다시 입장하면
   200 이고 서버에서 바뀌는 것은 없다(새로고침 · 재시도). 입장의 거절은 409 `ALREADY_QUEUED`(D-19) · 409 `ROOM_FULL`(만석 —
   방장 포함 5명) · 409 `IN_OTHER_ROOM` · 404 `ROOM_NOT_FOUND` · 409 `ROOM_CONFIRMED`(아래 6)이고, **거절되면 서버에는
   아무것도 쓰이지 않는다.**
3. **수명과 접속 확인.** **모든 키에 수명을 둔다** — 기본 600초이고 설정값이다(`ROOM_TTL_SECONDS`). 브라우저가 방에 있는 동안
   **1분마다** 접속 확인(`POST …/heartbeat`)을 보내 수명을 다시 건다. 신호가 끊기면 수명이 다해 **저절로 사라진다** — 나가기를
   못 누르고 탭을 닫아도, `app:room` 이 죽어 있어도 마찬가지다. 1분인 이유는 브라우저가 백그라운드 탭의 타이머를 분당 1회
   정도로 늦추기 때문이다.
   - **방의 수명(방장 키 · 멤버 SET · 확정 표시 키)은 방장의 신호만 늘린다.** 일반 멤버의 신호는 자기 입장 표시 키만 늘린다.
     **그래서 방장의 연결 끊김도 나간 것이고, 방이 저절로 없어진다**(D-11 11번의 "방장이 나가면"에 대한 답이다). 남아 있던
     사람은 자기 다음 신호에서 404 `ROOM_NOT_FOUND` 를 받고, 그 요청이 그 사람의 입장 표시 키를 지운다.
   - **방장의 신호가 유령을 뺀다.** 말없이 사라진 멤버는 입장 표시 키가 만료된 뒤에도 멤버 SET 에 이름이 남는다(SET 의
     원소에는 수명을 걸 수 없다). 방장의 신호가 입장 표시가 이 방을 가리키지 않는 사람을 SET 에서 빼고, 남은 사람들(방장
     포함)에게 그 사람마다 `ROOM_MEMBER_LEFT` 를 보낸다.
   - **`app:room` 은 `app:platform` 에 알리지 않는다.** `app:platform` 이 방장 키를 보고 스스로 글을 만료시킨다(D-16 "두 앱을
     잇는 방법" 2번 그대로).
   - SSE 의 `heartbeat` 이벤트(서버 → 브라우저, 알림 서비스가 보낸다 — D-10)와는 방향도 용도도 다른 것이다.
4. **방장이 나가면 방을 통째로 없앤다** — 방에 다른 사람이 있어도, 확정 뒤에도 마찬가지다. 방장 키 · 멤버 SET · 확정 표시
   키 · **남아 있던 전원의 입장 표시 키**를 **스크립트 하나에서** 지운다. 자바와 나눠 지우면 "방장 키를 지웠다 → 앱이
   죽었다"가 됐을 때 남은 사람들의 입장 표시 키가 남아, 그 사람들이 방 입장도 매칭도 못 하게 된다. 나가기는 방에서 나갔든,
   방장이 나가 방이 없어졌든, 이 방에 없는 사람이 불렀든 **전부 204 다** — 몇 번을 불러도 결과가 같다. 늦게 도착한 나가기가
   그 사이 들어간 다른 방의 입장 표시를 지우지 않는다(이 방의 멤버일 때만, 그리고 입장 표시가 이 방을 가리킬 때만 지운다).
5. **강퇴 — 방장만 할 수 있다**(`DELETE …/members/{targetUserId}`). "부른 사람이 방장인가"는 JWT 의 role 이 아니라 **방장 키의
   값과 비교해 스크립트 안에서** 본다 — 방장은 계정의 권한이 아니라 **방마다 다른 Redis 의 상태**다. 확인 순서는 방이 있는가
   (404 `ROOM_NOT_FOUND`) → 방장인가(403 `NOT_HOST`) → 자기 자신인가(400 `CANNOT_KICK_SELF` — 방장이 나가려면 나가기를 쓴다) →
   대상이 멤버인가(404 `TARGET_NOT_IN_ROOM`)다. 대상의 입장 표시 키는 값이 이 방일 때만 지운다. **강퇴는 수명을 건드리지
   않는다.** **강퇴된 본인에게도 알린다**(아래 8). **강퇴당한 사람의 재입장은 막지 않는다 — 미정이다**(아래 "아직 미정").
6. **방장 확정**(`POST /api/v1/rooms/{roomId}/confirm`).
   - **방장만** 부를 수 있고(방장인지는 5와 같이 스크립트 안에서 본다 — 403 `NOT_HOST`), **방장 포함 2명 이상**이어야 한다
     (409 `NOT_ENOUGH_MEMBERS`). 이미 확정된 방을 다시 확정하면 200 이고 바뀌는 것이 없다.
   - **확정한 그 순간 방에 있던 전원(방장 포함)이 파티원이다.** 방장이 고르지 않는다 — 원치 않는 사람은 그 전에 강퇴한다.
   - **되돌릴 수 없다.** 확정되면 입장이 409 `ROOM_CONFIRMED` 로 거절된다 — 멤버가 나가 자리가 비어도, 나간 파티원이
     되돌아오려 해도 마찬가지다. **이미 들어와 있는 사람의 재입장(새로고침) 200 은 그대로다.** 강퇴 · 나가기 · 접속 확인 ·
     시그널은 확정 전과 똑같고, 방장이 나가면(또는 말없이 사라지면) 확정 전과 똑같이 방이 통째로 없어진다(위 4).
   - `app:room` 이 **확정 표시 키 `qm:room:{roomId}:confirmed`** 를 쓴다. **확정 표시의 수명도 방장의 신호가 늘린다** — 안
     늘리면 수명이 다한 뒤 확정이 풀려 새 사람이 들어온다. 방이 없어질 때 같이 지워진다 — 같은 `roomId` 로 다시 만든 방은
     확정돼 있지 않다.
   - **"`app:platform` 이 '닫힘' 표시를 쓰고 `app:room` 이 확인한다"(D-16 이 가능성으로 적은 방법)는 받지 않았다 — `app:room`
     의 키는 `app:room` 만 쓴다**(D-19 와 같은 이유). **`app:platform` 은 이 요청을 받지 않는다.** 글의 상태를 "확정"으로 바꾸고
     파티원을 DB 에 기록하는 것은 `app:platform` 의 일이고(D-16 그대로), 그때 확정 표시 키와 멤버 SET 을 **읽는다.**
   - **프런트가 할 일** — 되돌릴 수 없으므로 확정 버튼을 누르면 "확정하면 되돌릴 수 없습니다"를 띄우고 **한 번 더 수락을
     눌러야** 이 요청을 보낸다. 서버는 그 확인을 강제할 수 없다.
7. **조회 둘.** 알림은 놓칠 수 있어서(아래 8) 클라이언트가 SSE 를 (다시) 연결한 직후 조회로 맞춘다.
   - **방 안 사람 목록**(`GET /api/v1/rooms/{roomId}/members`) — `{roomId, hostId, members}`. `members` 에는 방장도 들어
     있고 순서에는 뜻이 없다(SET 이다). **방 안의 사람만 본다. 방 밖의 사람에게는 403 `NOT_IN_ROOM` 이다 — 없는 방을 물어도
     404 가 아니라 403 이고, 그 방이 있는지도 알려 주지 않는다**(D-20 (마)가 전제한 그대로다).
   - **내 방 찾기**(`GET /api/v1/rooms/me`) — **`roomId` 를 모르는 클라이언트가 부른다**(방에 들어간 채로 탭을 닫았다가 앱을
     새로 연 경우). 입장 표시 키의 값을 그대로 돌려준다 — `{"roomId": "r1"}` 또는 `{"roomId": null}`(아무 방에도 없는 것은
     에러가 아니라 정상 상태라 404 가 아니라 200 이다). 이 저장소의 `GET /api/v1/match-requests?userId=` 와 같은 자리의
     조회다.
8. **알림의 이름과 받는 사람.** 채널 `qm:pubsub:push:{받는 사람 userId}` 와 봉투 네 칸은 다른 알림과 같다.

   | `type` | 언제 | 받는 사람 | `payload` |
   |---|---|---|---|
   | `ROOM_MEMBER_ENTERED` | 누가 방에 들어왔다(입장 201) | 방에 **이미 있던** 사람들. 들어온 본인은 받지 않는다 | `{roomId, userId}` — 들어온 사람 |
   | `ROOM_MEMBER_LEFT` | 누가 방에서 나갔다 — 나가기를 눌렀거나, 신호가 끊겨 방장의 접속 확인이 뺐다 | 방에 **남은** 사람들. 나간 본인은 받지 않는다 | `{roomId, userId}` — 나간 사람 |
   | `ROOM_CLOSED` | 방장이 나가서 방이 없어졌다 | 방에 **있던** 사람들. 방장 본인은 받지 않는다 | `{roomId}` |
   | `ROOM_MEMBER_KICKED` | 방장이 누군가를 강퇴했다 | 방에 **남은** 사람들(방장 포함) **+ 강퇴된 본인** | `{roomId, userId}` — 강퇴된 사람 |
   | `ROOM_CONFIRMED` | 방장이 파티를 확정했다 | 그 순간 방에 있던 **전원(방장 포함)** | `{roomId, members}` — 이 사람들이 파티원이다 |
   | `WEBRTC_SIGNAL` | 같은 방의 누가 시그널을 보냈다(아래 9) | 받는 사람 한 명 | `{roomId, fromUserId, signal}` |

   - **본인에게는 보내지 않는다** — 본인은 REST 응답으로 이미 안다. **강퇴만 예외다** — 강퇴된 본인은 자기가 부른 요청이
     아니라서 알림이 아니면 알 길이 없다. 확정은 방장의 응답(204)에 파티원이 없어서 방장도 알림으로 받는다.
   - **`PARTY_*` 를 다시 쓰지 않고 새 이름을 지었다** — 그 7종의 이름과 payload 가 이 컴퓨터의 문서에 없어 같은 뜻인지 확인할
     수 없었다. 계약 원본과 합칠 때 맞춘다.
   - **받는 사람 목록은 스크립트가 돌려준다.** 방이 없어지면 멤버 SET 도 지워져 나중에 읽을 수 없으므로 스크립트가 지우기
     전에 읽어 돌려준다.
   - **알림은 놓칠 수 있고, 발행 실패가 입장 · 나가기를 뒤집지 않는다**(서버는 로그만 남긴다 — 이 저장소의 `PushPublisher` 와
     같은 방식이다). 그래서 위 7의 조회가 있다.
9. **시그널 `POST`**(`POST /api/v1/rooms/{roomId}/signals`). 본문은 `{toUserId, signal}`, 성공은 **202**(받는 사람의 채널에
   발행했다는 뜻이고 **도착을 뜻하지 않는다**), 받는 쪽 알림의 `payload` 는 `{roomId, fromUserId, signal}` 이다. 보낸 사람이
   이 방에 없으면 403 `NOT_IN_ROOM`, 받는 사람이 이 방에 없으면 404 `TARGET_NOT_IN_ROOM` 이고 아무것도 발행하지 않는다.
   - **`signal` 의 모양은 클라이언트끼리의 약속이고 서버는 열어 보지 않는다.** JSON 값이기만 하면 받고, 글자 그대로 상대에게
     싣는다. **D-9 가 정하라고 한 종류(offer · answer · candidate) · SDP 나 candidate 본문 · 재협상 시도를 구분할 식별자는 전부
     그 안에 들어간다.** D-9 의 나머지 두 항목 — 보낸 사람 식별자와 파티(방) 식별자 — 는 `payload` 의 `fromUserId` · `roomId`
     다. 권장 모양(`kind` 가 `"description"` / `"candidate"`)은 `../room/contracts/room-api.md` 에 적어 두었으나 서버는 강제하지
     않는다.
   - **`PUBLISH` 반환값(구독자 수)을 응답에 싣자는 D-9 의 제안은 받지 않았다.**
   - 기준은 "파티원"이 아니라 "방에 들어와 있는 사람"이다(D-11 "D-9 와의 관계" 그대로). 확정 뒤에도 같다.
10. **응답의 공통 규칙.** **거절은 예외가 아니라 서비스의 결과 enum 이다**(`domain/*Result`). 서비스는 HTTP 를 모르고, 그
    결과를 상태 코드로 바꾸는 것은 컨트롤러다. Lua 의 반환값과 결과 enum 의 `code` 가 짝이다. 에러 본문은 이 저장소와 같은
    `{code, message, details}` 이고 클라이언트는 `code` 로 갈래를 정한다. 모든 요청의 공통 에러는 **400 `INVALID_REQUEST`**
    (필수 칸 · 쿼리 파라미터가 없다, 본문을 읽을 수 없다)와 **503 `ROOM_UNAVAILABLE`**(Redis 에 닿지 못했다. `Retry-After: 5`.
    방의 상태는 Redis 에만 있어서 확인이 안 되면 통과시키지 않는다)이다.

**D-9 · D-11 · D-16 · D-19 · D-20 과의 관계.**

- **D-9 "아직 미정"** 의 경로 · 요청/응답 스키마 · `payload` 스키마 → **정해졌다**(결정 9). 후보로 적혀 있던
  `POST /api/v1/parties/{partyId}/signals` 는 쓰지 않는다. "시그널 `POST` 의 구현은 아직 없다"도 더는 맞지 않는다. D-9 의
  *(제안 사항)* 은 **받지 않았다.** D-9 "감수하는 것"의 나머지(순서를 보장하지 않는다 · HTTP/2 전제 · 복구는 클라이언트가
  한다)는 그대로이고, 복구의 기준점 "파티원 목록"은 **방 안 사람 목록**(결정 7)이다.
- **D-11 11번** "방장이 나가면 글은 … 만료" → "방장이 나가면"은 **명시적 나가기와 방장의 연결 끊김 둘 다**다(결정 3 · 4).
  "얼마나 기다리는가"는 방 키의 수명(기본 600초)이다. 글을 만료로 바꾸는 쪽이 `app:platform` 이라는 점은 그대로다.
- **D-11 13번과 그 세부** → 대상은 **그 순간 방에 있는 전원**이고, **확정 뒤 빈자리가 생겨도 다시 모집을 열 수 없다**(결정 6).
  13번 세부의 나머지 둘 — 확정된 글이 목록에서 어떻게 보이는가, 확정된 방이 Ready 등을 갖는가 · "최근 함께한 사람"의 기준
  — 은 **그대로 미정**이다. D-11 "D-9 와의 관계"가 "13번의 세부가 미정이라 단정하지 않는다"고 한 대목은 — 확정된 방에서는
  새 사람이 못 들어오므로 방에 있는 사람이 곧 파티원(또는 그 일부)이다. 시그널의 기준은 확정 뒤에도 "방에 들어와 있는
  사람"이다.
- **D-11 "아직 미정"의 나머지** — "5명이 가득 찼을 때 … 눌렀을 때 어떻게 되는가" → 409 `ROOM_FULL`(목록에 어떻게 보이는가는
  `app:platform` 의 미정 그대로다). "어떤 알림 종류를 쓰는가" → 새 `type` 다섯(결정 8). "모든 엔드포인트의 경로 · 스키마" →
  `app:room` 몫의 아홉 가지가 정해졌다(`app:platform` 몫과 테이블은 그대로 미정). "강퇴당한 사람이 다시 들어올 수 있는가" →
  **지금은 들어올 수 있다. 막을지는 미정 그대로다.**
- **D-16 "아직 미정"** 의 방 키 · 접속 확인 · 방이 처음 만들어지는 시점 · 알림의 종류와 이름 → **정해졌다**(결정 1 · 2 · 3 ·
  8). "방장이 글을 쓴 직후 입장권으로 들어올 때인가"에 대해서는 — 지금은 입장권이 없어 **별도의 "방 만들기" 요청**이 만든다.
  입장권이 정해지면 이 요청이 남는지 방장의 첫 입장에 합쳐지는지 다시 정한다(아래 "아직 미정").
- **D-16 "두 앱을 잇는 방법" 3번** → **받지 않았다**(결정 6). 같은 절 2번의 "**쓰지는 않는다**(단 아래 3)"에서 단서가
  없어진다 — `app:platform` 은 `app:room` 의 키에 쓰지 않는다. 2번이 적은 "방장 확정 때도 같은 키에서 현재 인원을 읽어
  파티원으로 기록한다"는 그대로이고, 읽는 키에 **확정 표시 키가 더해진다.** 같은 절 1번(입장권 — 확정되거나 만료된 글에는
  입장권을 내주지 않는다)은 그대로다. 확정 순간의 경쟁은 입장권이 아니라 **확정과 입장이 둘 다 `app:room` 의 스크립트라는
  것**으로 막힌다.
- **D-19** → 바뀌지 않는다. 입장 표시 키의 자료형 · 값 · 수명은 `app:room` 이 혼자 정한다고 한 그대로 정했다(결정 1 · 3).
  D-19 "아직 미정"의 "입장 표시 키의 수명" → 600초이고 그 사람의 접속 확인이 늘린다. 결정 6이 닫힘 표시를 받지 않은 이유가
  D-19 의 원칙(공유하는 Redis 키는 쓰는 주인이 하나다)이다.
- **D-20** → 바뀌지 않는다. D-20 "감수하는 것"이 "읽기 전용 · **키 셋**"이라고 센 `../room/contracts/room-api.md` "Redis 키"의
  표는 확정 표시 키가 더해져 **넷**이 됐다 — 읽기 전용 · 쓰는 주인 하나(`app:room`)는 그대로다. D-20 (다)가 든 게시판 채널
  신호의 발행 시점(방 만들기 · 입장 · 나가기 · 강퇴 · 접속 확인이 유령을 뺐을 때 · 방 닫힘 · 방장 확정)은 이 항목의 요청들과
  같은 자리다. 그 발행은 **여전히 구현 전**이다.

**근거.** 전부 `../room/` 의 계약 · 규칙 문서와 스크립트 주석에 적힌 것이다.

1. **앱이 죽어도 저절로 정리돼야 한다.** 방 키나 입장 표시 키가 남으면 그 사용자는 매칭도(`claim-request.lua` 가 입장 표시
   키를 본다 — D-19) 방 입장도 못 하게 된다. 그래서 모든 키에 수명을 두고, 수명이 다하는 순간에는 `app:room` 의 코드가 돌지
   않아도 되게 했다. 방을 없애는 일을 스크립트 하나에 담은 것(결정 4)도 같은 이유다.
2. **방의 수명을 방장의 신호에만 묶어야 "방장이 사라지면 방이 없어진다"가 정리 코드 없이 성립한다.** 멤버의 신호도 방의
   수명을 늘리면 방장 없는 방이 남는다.
3. **방장은 방마다 다른 상태다.** 계정의 권한(role)으로는 "이 방의 방장"을 가를 수 없다. 그리고 "방장인가"의 확인과 쓰기가
   한 스크립트 안이어야 그 사이에 방이 바뀌지 않는다.
4. **확정 표시를 `app:room` 이 쓰면 확정과 입장이 둘 다 `app:room` 의 Lua 가 된다.** Redis 는 스크립트를 하나씩 돌리므로
   확정하는 순간에 누가 끼어드는 일이 없고, `ROOM_CONFIRMED` 의 `members` 가 곧 확정된 파티원이다. `app:platform` 이 쓰는
   방법은 `app:room` 의 키 공간에 쓰는 주인이 둘이 된다 — D-19 가 활성 요청 키에서 버린 구조다.
5. **서버가 `signal` 을 열어 보지 않는 이유.** SDP 는 글자 하나(줄바꿈 `\r\n` 포함)만 달라져도 깨진다. 보내는 브라우저와
   받는 브라우저가 같은 프런트 코드를 돌리므로 모양은 사실상 그 코드가 정한다. 재협상 번호 같은 칸이 필요해져도 서버는 고칠
   것이 없다.
6. **구독자 수를 싣지 않는 이유.** 발행은 예외도 값도 밖으로 내보내지 않는다 — 발행 실패가 요청을 실패로 뒤집지 않게 하려는
   구조이고, 구독자 수를 돌려주려면 그 구조를 깨야 한다. D-9 도 "0 이 아니어도 브라우저 도착을 보장하지는 않는다"고 적었다.
7. **방 밖의 사람에게 403 을 주는 이유.** 방 밖에서 방 안을 보는 공개 창구는 `app:platform` 의 목록이다 — 차단을 거르는 곳이
   거기라서다(D-20 (마)). 스크립트가 묻는 사람의 입장 표시부터 보고, 방이 없어질 때는 그 방 사람들의 입장 표시도 같이
   지워지므로 없는 방도 403 이 된다.
8. **내 방 찾기가 없으면** 방에 들어간 채로 앱을 새로 연 사용자가 매칭을 눌러 409 `IN_ROOM`, 다른 방을 눌러 409
   `IN_OTHER_ROOM` 을 이유도 모른 채 받는다.
9. **거절을 예외가 아니라 결과로 두는 이유.** 서버 없이 테스트할 수 있고 다른 입구에서도 같은 규칙을 쓸 수 있다. D-16 "다시
   볼 조건"(진입점만 바꿔 Lambda 로 옮길 수 있게 핵심 로직을 Spring 에 묶지 않는다)과 같은 방향이다.

**감수하는 것.**

- **방장이 말없이 사라진 방은 최대 10분(수명 600초) 목록에 살아 있는 것처럼 보인다.** 명시적 나가기는 즉시 닫는다. 줄이려면
  `ROOM_TTL_SECONDS` 를 줄인다.
- **수명이 다해 없어진 방은 `ROOM_CLOSED` 를 보내지 못한다.** 그 순간에는 `app:room` 의 코드가 돌지 않고, 그 뒤에는 누가
  있었는지(멤버 SET)도 남아 있지 않다. 남아 있던 사람은 자기 다음 접속 확인의 404 로 안다. 게시판 채널 신호도 같은 이유로
  나가지 못한다(D-20 "감수하는 것" 그대로).
- **멤버 SET 의 인원은 유령 때문에 부풀 수 있다.** 유령은 길어야 "수명 + 방장의 신호 주기"만큼 목록과 정원에 남는다. 그동안
  `app:platform` 이 읽는 인원수 · 카드 · 차단 대조에도 그 사람이 남는다(D-20 "감수하는 것" 그대로).
- **강퇴된 사람이 입장을 다시 부르면 들어온다**(지금의 동작). 막으려면 강퇴 목록 키가 필요하고, 입장권이 생기면 `app:platform`
  이 입장권을 내주지 않는 것으로 막을 수도 있다 — 미정이다.
- **확정은 되돌릴 수 없고, 서버는 프런트의 "한 번 더 수락"을 강제할 수 없다.** 서버 쪽 안전장치는 두 번 눌러도 아무 일이
  없는 200 하나다.
- **알림과 시그널은 놓칠 수 있고 순서가 보장되지 않는다.** 2026-09-20 에 offer 다음에 보낸 ICE 후보가 받는 쪽 SSE 에 먼저
  찍힌 것을 봤다. 복구는 클라이언트의 일이다(D-9 그대로).
- **`platform` 이 없는 동안의 임시 처리(`TEMP-NO-PLATFORM`).** 사용자는 쿼리 파라미터 `userId` 로 받아 그대로 믿고, 입장권
  검증도 없다 — 지금은 누구나 아무 `roomId` 로 방을 만들어 방장이 되고, 방장의 `userId` 를 적으면 누구나 강퇴할 수 있다. 인증과 입장권이 붙으면 없어진다. 그 자리는 코드에 같은 표식으로 표시돼 있다.
- **`DELETE …/members/me` 는 언제나 나가기다** — 글자 그대로의 경로가 `{targetUserId}` 보다 먼저 잡힌다. `userId` 가 글자
  그대로 `me` 인 사람은 이 주소로 강퇴할 수 없다.
- **`app:platform` 과의 방 키 약속에 키가 하나 늘었다**(확정 표시 키). 어긋나도 컴파일 · 테스트가 통과한다는 성질은 다른 공유
  약속과 같다(D-16 · D-19 · D-20).

**아직 미정 — 임의로 지어내지 않는다.**

- **입장권**의 형식 · 서명 방식 · 수명 · 담는 정보, 서명 키를 두 앱이 나눠 갖는 법(D-16 그대로). 입장권이 정해지면 **"방 만들기"
  요청이 그대로 남는지, 방장의 첫 입장에 합쳐지는지** 다시 정한다 — 원래는 입장권이 "이 방의 방장은 이 사람"이라고 말해 준다.
- **인증** — access 토큰 검증 방법, CSRF 대응(시그널 `POST` · 방 입장이 해당된다), 로컬 개발의 CORS. D-14 의 미정 그대로다.
- **강퇴당한 사람의 재입장** — 막을지, 막는다면 입장권으로 막을지 `app:room` 이 기억할지.
- **자동 매칭으로 확정된 파티의 방은 어떻게 생기는가**(D-16 그대로), 그리고 **`status=PARTY` 해제**(`HANDOFF.md` §0 ①, D-19
  그대로) — 확정된 사용자는 활성 요청 키가 남아 있어 그대로는 방 입장도 거절된다. 둘은 같이 풀어야 한다.
- **방장 확정 뒤에는 입장 표시 키를 언제 지우는가.** 지금은 확정 전과 같다 — 나가기 · 강퇴 · 방 닫힘 · 수명이 지운다.
- **확정된 방이 Ready 등 파티룸 기능을 갖는가**, 확정된 글이 목록에서 어떻게 보이는가, "최근 함께한 사람"의 기준(D-11 13번
  세부 그대로).
- **방장 확정에서 브라우저가 `app:room` 과 `app:platform` 을 어떤 순서로 부르는가.** `app:platform` 을 만들 때 정한다.
- **`app:platform` 이 "아직 안 만들어진 방"과 "사라진 방"을 가리는 법.** 글을 쓴 직후 방 만들기 전에는 방장 키가 없다.
  `app:platform` 이 그 글을 "방이 사라졌다"로 읽고 만료시키지 않게 해야 한다.
- **`PARTY_*` 와의 관계.** 새 `type` 들이 계약 원본의 `PARTY_*` 7종과 같은 뜻인지는 원본과 합칠 때 맞춘다.
- **게시판 채널 신호**에 남은 미정(`app:room` 이 방의 게임을 아는 법 · 채널 접두사의 원본 상수를 둘 곳 등)은 D-20 그대로다.
- 포트(8083 은 여전히 **제안**이다 — D-16 그대로).
- 그 밖에 D-11 · D-16 · D-19 · D-20 의 미정 목록 가운데 이 항목이 없애지 않은 것 전부.

**영향.**

- **이 저장소(`app:matching`)의 코드는 바뀌지 않는다.** `app:room` 이 `app:matching` 과 닿는 곳은 D-19 의 두 키
  (`claim-request.lua` 의 `KEYS[2]` · 409 `IN_ROOM`)와 알림 채널 접두사뿐이고 이 항목은 그것을 바꾸지 않는다. 이 저장소의
  `CLAUDE.md` · `START_HERE.md` · `HANDOFF.md` 도 고치지 않았다.
- **`app:room`(`../room/`)에 전부 구현돼 있다.** 경로는 `../room/backend/src/main/` 아래의 상대 표기다.
  - 스크립트 `resources/lua/` — `create-room.lua` · `enter-room.lua` · `leave-room.lua` · `kick-room.lua` · `confirm-room.lua` ·
    `heartbeat-room.lua` · `members-room.lua` · `signal-room.lua`. 머리 주석에 `KEYS` · `ARGV` · 반환값이 있다.
  - `java/com/queuemate/room/redisKeys/RoomKeys.java`(방 키 · 입장 표시 키의 원본 상수) · `redisKeys/SharedKeys.java`(활성 요청
    키 · 알림 채널 접두사의 사본 — 원본은 이 저장소의 `SharedKeys`).
  - 결과 enum `domain/` — `CreateResult` · `EnterResult` · `LeaveResult` · `KickResult` · `ConfirmResult` · `HeartbeatResult` ·
    `SignalResult`, 그리고 `RoomMembersResult`.
  - `service/RoomService`(방 만들기 · 방장 확정 · 내 방 찾기) · `service/RoomMemberService`(입장 · 나가기 · 강퇴 · 접속 확인 ·
    방 안 사람 목록) · `service/RoomSignalService`, `controller/RoomController` · `RoomMemberController` ·
    `RoomSignalController`.
  - `notification/PushPublisher` · `notification/PushEventType`(알림 여섯), `common/error/GlobalExceptionHandler`(공통 에러),
    `config/RoomProperties`(수명 — `queuemate.room.ttl-seconds` · `ROOM_TTL_SECONDS`).
  - 테스트는 `../room/backend/src/test/` 에 104건이다(`../room/START_HERE.md` §1 에 클래스별 내역). `redisKeys/SharedPrefixTest`
    가 세 접두사(활성 요청 키 · 입장 표시 키 · 알림 채널)를 이 저장소의 원본과 글자까지 비교한다.
- **계약의 원본은 `../room/contracts/room-api.md` 다.** `app:room` 의 엔드포인트는 어느 계약에도 없어서 그 폴더에서 먼저 정하고
  적었다. **본 저장소와 합칠 때 올려야 한다.**
- **`contracts/events.md` · `contracts/README.md` 는 고치지 않았다 — 남은 일이다.** 올릴 것은 새 알림 `type` 다섯
  (`ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED`)과 `WEBRTC_SIGNAL` 의
  `payload`, 그리고 D-20 이 남겨 둔 `BOARD_CHANGED` · 게시판 채널 · `topics` 파라미터다. SSE 가 "15종"이라는 셈이 어떻게
  달라지는지도 그때 정한다(`PARTY_*` 와 같은 뜻인지에 달려 있다). `contracts/openapi.yaml` 에는 `app:room` 의 요청이 없고
  넣지 않는다.
- **`app:platform`(`../platform/`)** — 아직 코드가 없다. 방장 확정에서 이 앱이 할 일이 "확정 표시 키와 멤버 SET 을 **읽어**
  글의 상태를 '확정'으로 바꾸고 파티원을 기록한다"로 정해졌고, `app:room` 의 키에 쓰지 않는다. `../platform/CLAUDE.md` ·
  `../platform/README.md` 의 해당 서술과 `../platform/docs/ROOM_CONTRACT.md`(계약의 발췌 사본 — 강퇴 · 방장 확정 · 확정 표시 키 ·
  `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` · 게시판 채널 신호가 들어갔다)를 같은 날 맞췄다.
- **알림 서비스(`../notification/`)** — 바뀌지 않는다. 새 `type` 들도 열어 보지 않고 그대로 흘려보낸다. 같은 테스트용 Redis 로
  띄워 입장 · 나가기 · 방 닫힘 알림(2026-09-19)과 `WEBRTC_SIGNAL`(2026-09-20)이 SSE 로 도착하는 것을 `../room/` 에서 확인했다
  (`../room/START_HERE.md` §1). `../notification/CLAUDE.md` 에는 저장소 구성 그림과 알림 종류 수에 대한 한 줄만 보탰다.
- `../room/` 의 문서 — `docs/PROJECT_OVERVIEW.md` · `START_HERE.md` · `CLAUDE.md` §7(엔드포인트 행) · §8 · §10 의 낡은 서술을 같은
  날 고쳤고, 그 폴더의 사본 `docs/DECISIONS.md` 에 이 항목을 옮겼다.

---

### D-22. 게시판 채널은 게임을 구분하지 않는 하나다 — `qm:pubsub:board` 에 `{}` 를 발행하고, `topics` 파라미터를 없애 알림 서비스는 모든 연결에 그대로 보내며, 거르기는 클라이언트가 한다 (D-20 (다) 개정, 2026-09-20)

> **주어 낡음 — D-33 이 개정(2026-09-26 표시).** 발행하는 "두 앱"은 `app:platform` 의 두 패키지(`party` · `room`)다. 채널 · `payload` · `topics` 없음 · 클라이언트가 거른다는 그대로다.

> **이 결정은 매칭 엔진의 결정이 아니다.** D-20 (다)가 정한 게시판 채널 신호 가운데 **채널의 이름과 받는 사람을 고르는
> 방법**을 고친 것이다. 프로젝트 소유자가 정했다. 원본 결정 로그가 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저
> 적는다. 본 저장소와 합칠 때 D-20 · D-21 과 함께 올려야 한다. **D-20 · D-21 의 본문은 고치지 않았다** (이 파일은 기록이다).
> **이 결정은 이 저장소(`app:matching`)의 코드를 바꾸지 않는다**(아래 "영향").

**원안.** D-20 (다)는 게시판 채널 신호를 아래처럼 적었다.

- "**채널 이름은 `qm:pubsub:board:{game}` 이다.** `{game}` 은 `LOL` · `VALORANT` · `PUBG` 다."
- "**알림 서비스**가 그 채널을 구독해 **'게시판을 보고 있는 연결'** 에 SSE 로 흘려보낸다."
- "**클라이언트는 SSE 를 열 때 쿼리 파라미터 `topics=board:LOL` 로 게시판 구독을 알린다**(예:
  `GET /api/v1/events?topics=board:LOL`)."
- "새로 생기는 것은 **'연결이 사용자 채널 말고 주제 채널도 구독할 수 있다'** 는 개념 하나다. **정해졌고 구현 전이다.**"
- D-20 "아직 미정": "**`app:room` 이 어느 게임의 채널에 발행할지를 어떻게 아는가 — 이 결정으로 새로 드러난 미정이다.**" ·
  "**한 연결이 여러 주제를 구독할 때의 `topics` 표기**(쉼표로 나열하는지 등)." D-21 "아직 미정"도 앞의 것을 "D-20 그대로"라고
  적었다.

즉 **채널이 게임마다 하나이고, 서버(알림 서비스)가 거른다** — 연결마다 어느 주제를 보는지 기억하고 그 주제를 구독한 연결에만
보낸다. 그리고 발행하는 쪽은 그 방 · 그 글이 어느 게임의 것인지 알아야 한다.

**결정.**

1. **게시판 채널은 게임을 구분하지 않는 하나다 — `qm:pubsub:board`.** D-20 의 `qm:pubsub:board:{game}` 세 채널을 개정한다.
2. **봉투는 그대로 네 칸이고 `type` 은 `BOARD_CHANGED`, `payload` 는 `{}` 다**(D-20 그대로). **`roomId` 도 `game` 도 싣지
   않는다.** `roomId` 를 싣지 않는 이유(차단 때문에 그 방이 숨겨진 사용자에게 "그 방이 바뀌었다"는 사실이 새어 나가지 않게)는
   그대로다.
3. **`topics` 파라미터를 없앤다.** 알림 서비스는 게시판 신호를 살아 있는 **모든** SSE 연결에 그대로 보낸다. 연결마다 주제를
   기억하지 않는다.
4. **거르는 것은 클라이언트다.** 게시판 페이지(어느 게임이든)를 보고 있으면 `app:platform` 의 목록을 다시 요청하고, 게시판
   페이지가 아니면 무시한다. 다른 게임의 방이 바뀐 신호에도 재요청이 나간다 — **프런트가 재요청을 묶어서**(예: 몇 초에 최대
   1번. 간격은 미정) 한 사람의 재요청이 간격당 1번을 넘지 않게 한다.
5. **발행은 D-20 그대로다** — `app:platform` 은 글이 바뀔 때, `app:room` 은 방의 인원이 바뀔 때. **둘 다 같은 채널 하나에 `{}`
   를 발행한다. 발행하는 쪽이 게임을 알 필요가 없다.**

**D-20 · D-21 과의 관계.**

- **D-20 (다)** → 채널 이름(`qm:pubsub:board:{game}` 셋 → **`qm:pubsub:board` 하나**)과 받는 사람(`topics` 로 구독을 알린 연결 →
  **살아 있는 모든 연결**)이 **개정됐다.** "연결이 주제 채널도 구독할 수 있다"는 개념은 **생기지 않는다**(결정 3). **그대로인
  것** — `type` `BOARD_CHANGED`, 봉투 네 칸, `payload` `{}`, 발행하는 앱과 시점, **이 신호는 "다시 받아라"일 뿐이고 데이터와 차단
  거르기는 `app:platform` 의 목록 응답에서 온다**는 점, 신호에 데이터도 `roomId` 도 싣지 않는다는 점, 알림 서비스가 본문을 열어
  보지 않는다는 점, 급하면 프런트가 몇 초마다 목록을 다시 받는 방식으로 먼저 시작해도 된다는 점.
- **D-20 · D-21 "아직 미정"의 "`app:room` 이 어느 게임의 채널에 발행할지를 어떻게 아는가"** → **물음째 없어졌다**(결정 5).
  `app:room` 은 여전히 방이 어느 게임의 것인지 모르고, 몰라도 된다. 방 키의 이름 · 구조도, 방 만들기 · 입장 요청도 바뀌지
  않는다.
- **D-20 "아직 미정"의 "한 연결이 여러 주제를 구독할 때의 `topics` 표기"** → **물음째 없어졌다**(결정 3).
- **D-20 "아직 미정"의 "게시판 채널 접두사의 원본 상수를 어느 서비스에 둘지"** → 접두사가 아니라 **채널 이름**의 원본 상수로
  읽는다. 미정 그대로다(아래).
- **D-20 "감수하는 것"의 "알림 서비스에 개념이 하나 는다 — '연결이 주제 채널도 구독한다'"** → 더는 맞지 않는다. 느는 것은
  "게시판 채널 하나를 구독해 모든 연결에 보낸다" 하나다.
- **D-20 · D-21 "영향"의 "알림 서비스의 주제 구독이 먼저다" · "주제 채널 구독이 정해졌고 구현 전이다"** → **알림 서비스
  쪽은 구현됐다**(아래 "영향"). `app:room` 에는 막는 미정이 남지 않았다.
- **D-20 · D-21 "영향"이 `contracts/events.md` 에 올릴 것으로 든 "`topics` 파라미터"** → 올릴 것이 없어졌다. 올릴 것은
  `BOARD_CHANGED` 와 게시판 채널 `qm:pubsub:board` 다.
- **D-21** → 그 밖에는 바뀌지 않는다. D-21 이 적은 게시판 채널 신호의 발행 시점(방 만들기 · 입장 · 나가기 · 강퇴 · 접속 확인이
  유령을 뺐을 때 · 방 닫힘 · 방장 확정)은 그대로다.

**근거.**

1. **`app:room` 이 방의 게임을 알 필요가 없어진다.** 게임을 알려면 방 키를 하나 더 두고(`qm:room:{roomId}:game` 을 검토했다)
   방 만들기 API 와 만들기 · 수명 연장 · 닫기 · 발행 직전 읽기의 Lua 를 고치고, `app:platform` 과의 방 키 약속에 키를 하나
   더해야 한다 — **그것이 전부 필요 없다.**
2. **알림 서비스는 `topics` 파라미터 · 주제→연결 맵 · 표기 규칙 없이 채널 하나만 구독하면 된다.** 게임 목록(enum)도 필요
   없다.
3. **신호는 어차피 "다시 받아라"일 뿐이고 데이터와 차단 거르기는 `app:platform` 의 응답에서 온다** — 신호가 거칠어도 결과는
   같다.

**검토하고 받지 않은 것.**

- **(가) `payload` 에 `game` 을 싣고 클라이언트가 `payload.game` 으로 거르기.** 같은 날 먼저 이쪽으로 정했다가 물렸다.
  `app:room` 이 게임을 알아야 해서 근거 1의 비용이 든다.
- **(나) D-20 원안 — `topics` 로 서버가 거르기.** 근거 2가 든 것들을 알림 서비스가 가져야 한다.
- **(다) `app:platform` 이 인원 변화를 보고 대신 발행하기.** `app:platform` 은 인원이 바뀌는 순간을 모른다(서비스 간 호출이
  없고 목록 요청이 올 때 Redis 를 읽을 뿐이다) — 알려면 폴링해야 하고 "바뀌면 바로"가 사라진다.

**감수하는 것.**

- **다른 게임의 변화에도 재요청이 나간다.** 묶기로 상한을 둔다(결정 4).
- **게시판을 안 보는 연결에도 작은 이벤트가 간다**(방 안에서 음성 중인 사람 등). 인원이 바뀔 때마다 **(연결 수)만큼 SSE
  쓰기**가 나간다. **MVP 규모에서 받아들인다.**
- **다시 볼 조건.** 재요청이나 SSE 쓰기가 부담이 되면 `payload` 에 `game` 을 싣는다(그러면 `app:room` 이 게임을 알아야 한다 —
  근거 1의 방 키 안을 그때 다시 본다) 또는 `topics` 를 되살린다. `{}` → `{game}` 은 받는 쪽이 모르는 칸을 무시하면 되므로
  넘어가기 쉽고, 반대 방향은 `app:room` 의 키 구조를 되돌려야 해서 번거롭다 — **그래서 단순한 쪽에서 시작한다.**
- D-20 "감수하는 것"의 나머지(신호는 놓칠 수 있다 · 수명이 다해 없어진 방은 신호를 내지 못한다 · 서비스 경계를 넘는 Redis
  이름 약속이 하나 더 는다 등)는 그대로다.

**아직 미정 — 임의로 지어내지 않는다.**

- **게시판 채널 이름의 원본 상수를 어느 서비스에 둘지** — D-20 의 미정 그대로다. 알림 서비스는 지금 자기
  `redisKeys/BoardChannels.java` 에 채널 이름을 적어 두었다 — 그것이 원본인지는 정해지지 않았다.
- **프런트가 재요청을 묶는 간격** — D-20 의 미정 그대로다.
- 그 밖에 D-20 의 미정 목록 가운데 이 항목이 없애지 않은 것 전부(없앤 것은 "`app:room` 이 방의 게임을 어떻게 아는가"와
  "`topics` 표기" 둘이다).

**영향.**

- **이 저장소(`app:matching`)의 코드는 바뀌지 않는다.** `app:matching` 은 게시판 채널에 발행하지 않는다(D-20 그대로). 이
  저장소의 `CLAUDE.md` · `START_HERE.md` · `HANDOFF.md` 도 고치지 않았다.
- **`contracts/events.md` · `contracts/README.md` 는 고치지 않았다 — 남은 일이다**(D-20 · D-21 그대로). 올릴 것에서 `topics`
  파라미터가 빠지고 게시판 채널이 `qm:pubsub:board` 하나가 된다.
- **알림 서비스(`../notification/`)** — **게시판 채널 구독과 전체 연결로의 전달은 구현됐다**(2026-09-20).
  `BoardChannelSubscriber` 가 기동 때 `qm:pubsub:board` **하나**를 구독하고 풀지 않는다. 받은 메시지는 `PushBoardListener` →
  `SseConnections.sendAll` 로 살아 있는 모든 연결에 보낸다. 채널 이름 상수는 `redisKeys/BoardChannels.java` 에 있다. 게임
  enum 은 없다. `topics` 파라미터도 없다. 본문을 열어 보지 않는다는 원칙은 그대로다. `../notification/CLAUDE.md` ·
  `../notification/README.md` 를 같은 날 맞췄다.
- **`app:room`(`../room/`)** — 게시판 채널 신호 발행은 **아직 없다.** 알림 서비스 쪽은 됐고 **막는 미정이 없어 바로 구현할 수
  있다.** 방 키도 요청도 바뀌지 않는다. `../room/CLAUDE.md` §2 · §3.1 · §7 · §10 · §11, `../room/START_HERE.md`,
  `../room/README.md`, `../room/contracts/room-api.md` "게시판 채널 신호", `../room/docs/PROJECT_OVERVIEW.md` 를 같은 날 맞췄고,
  그 폴더의 사본 `docs/DECISIONS.md` 에 이 항목을 옮겼다.
- **`app:platform`(`../platform/`)** — 아직 코드가 없다. 글이 바뀔 때 발행하는 곳이 `qm:pubsub:board` 하나가 됐고 `payload` 는
  `{}` 그대로다. `../platform/CLAUDE.md` · `../platform/README.md` 와 `../platform/docs/ROOM_CONTRACT.md`(계약의 발췌 사본)를
  같은 날 맞췄다.
- **프런트** — SSE 를 열 때 `topics` 를 붙이지 않는다. `BOARD_CHANGED` 를 받으면 게시판 페이지를 보고 있을 때만, 묶어서 목록을
  다시 요청한다(결정 4).

---

### D-23. 확정한 방은 방장이 나가도 없어지지 않는다 — 방장 자리를 남은 멤버에게 넘긴다. 그리고 `app:room` 의 게시판 채널 신호 발행이 구현됐고, 접속 확인이 "방이 없어졌다"를 돌려줄 때도 신호를 보낸다 (D-21 결정 3 · 4 · 6 개정, D-20 "감수하는 것" 일부 개정, 2026-09-21)

> **주어 낡음 — D-33 이 개정(2026-09-26 표시).** `app:room` 은 `app:platform` 의 `room` 패키지다. "아직 미정"의 "확정된 글에서 방장 키가 없을 때"는 D-33 으로 정해졌다 — 확정된 글은 만료시키지 않는다.

> **이 결정은 매칭 엔진의 결정이 아니다.** 둘을 적는다 — **(가)** D-21 이 정한 방 안의 규칙 가운데 **확정한 방에서 방장이 나갔을
> 때**를 고친 것, **(나)** D-20 · D-22 가 정한 게시판 채널 신호의 `app:room` 쪽 발행이 **구현된 것**과 그 발행 시점이 하나 는 것.
> 프로젝트 소유자가 정했다. **사실의 원본은 `../room/` 의 스크립트(`leave-room.lua` · `heartbeat-room.lua`)와
> `../room/contracts/room-api.md` 다.** 원본 결정 로그가 있는 queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다. 본
> 저장소와 합칠 때 D-20 · D-21 · D-22 와 함께 올려야 한다. **D-20 · D-21 · D-22 의 본문은 고치지 않았다** (이 파일은 기록이다).
> **이 결정은 이 저장소(`app:matching`)의 코드를 바꾸지 않는다**(아래 "영향").

**원안.** D-20 · D-21 · D-22 는 아래처럼 적었다.

- **D-21 결정 4** — "**방장이 나가면 방을 통째로 없앤다** — 방에 다른 사람이 있어도, 확정 뒤에도 마찬가지다. 방장 키 · 멤버 SET ·
  확정 표시 키 · **남아 있던 전원의 입장 표시 키**를 **스크립트 하나에서** 지운다."
- **D-21 결정 6** — "강퇴 · 나가기 · 접속 확인 · 시그널은 확정 전과 똑같고, 방장이 나가면(또는 말없이 사라지면) 확정 전과 똑같이
  방이 통째로 없어진다(위 4)."
- **D-21 결정 3** — "**방의 수명(방장 키 · 멤버 SET · 확정 표시 키)은 방장의 신호만 늘린다.** 일반 멤버의 신호는 자기 입장 표시
  키만 늘린다. **그래서 방장의 연결 끊김도 나간 것이고, 방이 저절로 없어진다**". D-21 "근거" 2번은 "멤버의 신호도 방의 수명을
  늘리면 방장 없는 방이 남는다"고 적었다.
- **D-20 "감수하는 것"** — "**수명이 다해 없어진 방은 신호를 내지 못한다.** 방장이 말없이 사라져 방 키의 수명이 다하는 순간에는
  `app:room` 의 코드가 돌지 않는다". D-21 "감수하는 것"도 "게시판 채널 신호도 같은 이유로 나가지 못한다"고, D-22 "감수하는 것"도
  그 항목을 "그대로다"라고 적었다.
- **`app:room` 의 게시판 채널 신호 발행** — D-20 "영향": "더해지는 것은 게시판 채널 신호 발행 하나이고 **구현 전**이다". D-21:
  "그 발행은 **여전히 구현 전**이다." D-22 "영향": "게시판 채널 신호 발행은 **아직 없다.** 알림 서비스 쪽은 됐고 **막는 미정이 없어
  바로 구현할 수 있다.**"

즉 **확정했든 안 했든 방장이 나가면(명시적 나가기든 연결 끊김이든) 방이 통째로 없어지고**, 방의 수명은 방장의 접속 확인만 늘렸다.
그리고 `app:room` 은 게시판 채널에 아직 발행하지 않았다.

**결정.**

**(가) 확정한 방은 방장이 나가도 없어지지 않는다 — 방장 자리를 넘긴다.** D-21 의 "방장이 나가면 확정 전과 똑같이 방을 없앤다"를
개정한다.

1. **확정하지 않은 방은 그대로다.** 방장이 나가면(명시적 나가기든 연결 끊김이든) 방이 통째로 없어진다(D-21 결정 3 · 4 그대로).
2. **확정한 방에서 방장이 나가기를 부르면** — 방장은 멤버 SET 에서 빠지고 자기 입장 표시가 지워지며, **남은 멤버 가운데 한 명이
   방장을 넘겨받는다.**
   - 넘겨받는 사람은 **입장 표시 키가 이 방을 가리키는(살아 있는) 멤버**여야 한다 — 멤버 SET 에 이름만 남은 유령에게 넘기면 방장
     없는 방이 된다. **누가 넘겨받는지는 정해져 있지 않다**(그 조건을 만족하는 아무나 — 아래 "아직 미정").
   - **방장 키의 수명은 그대로 둔다**(`SET … KEEPTTL`). 나가기 스크립트는 수명 값을 받지 않는다 — 새 방장의 접속 확인이 곧 늘린다.
   - **넘겨받을 사람이 없으면 방을 없앤다** — 멤버 SET · 방장 키 · 확정 표시 키를 지운다.
3. **확정한 방에서 방장이 말없이 사라지면** — 확정한 방에 한해 **일반 멤버의 접속 확인도 멤버 SET 과 확정 표시 키의 수명을
   늘린다.** **방장 키는 늘리지 않는다** — 방장 키가 만료돼야 다음 사람이 넘겨받는다. 방장 키가 만료된 뒤 **처음 접속 확인을 보낸
   멤버가 방장이 된다**(`SET 방장 키 EX 수명`). 그 신호는 그대로 방장의 접속 확인 분기로 들어간다 — 방의 수명을 늘리고, 입장 표시가
   만료된 옛 방장을 유령으로 빼서 남은 사람들에게 `ROOM_MEMBER_LEFT` 를 보낸다(D-21 결정 3 의 "방장의 신호가 유령을 뺀다" 그대로).
4. **새 반환값 · 새 알림 · 새 `payload` 필드는 없다.** 확정한 방의 방장이 나간 것은 나가기의 기존 결과 "나갔다"다 — 204 이고, 남은
   사람들에게 `ROOM_MEMBER_LEFT` `{roomId, userId}` 가 간다. 넘길 사람이 없어 방이 없어진 것은 기존 "방이 없어졌다"(`ROOM_CLOSED`)다.
   **새 방장이 누구인지는 알림에 싣지 않는다** — 클라이언트는 `ROOM_MEMBER_LEFT` 의 `userId` 가 자기가 알던 방장이면 방 안 사람
   목록(`GET …/members`)을 다시 조회하고, 응답의 `hostId` 가 새 방장이다.
5. **"방장 키가 있다 = 방이 있다"는 그대로 성립한다.** 방장 키의 이름 · 자료형(STRING) · 값의 뜻(방장의 `userId`)도 그대로다.
   달라진 것은 **확정한 방에서는 그 값이 바뀔 수 있다**는 것 하나다. `app:platform` 이 알아야 하는 것도 이것뿐이다 — 방 키 약속의
   형식은 바뀌지 않는다. 강퇴 · 방 안 사람 목록 · 유령 빼기는 방장 키가 늘 있으므로 그대로 동작한다.

**(나) 게시판 채널 신호 — `app:room` 의 발행이 구현됐고, 발행 시점이 하나 늘었다.**

6. **`app:room` 의 발행이 구현됐다(2026-09-21).** 채널 `qm:pubsub:board` · `type` `BOARD_CHANGED` · `payload` `{}` 는 D-20 · D-22
   그대로다. 보내는 때는 아래다.
   - 방 만들기(만들어졌을 때) · 입장(들어왔을 때) · 나가기("나갔다"와 "방이 없어졌다" 둘 다) · 강퇴(강퇴했을 때) · 방장
     확정(확정했을 때) · 접속 확인이 유령을 뺐을 때(몇 명을 뺐든 한 번) · **접속 확인이 "방이 없어졌다"(404 `ROOM_NOT_FOUND`)를
     돌려줄 때.**
   - **아무것도 안 바뀐 경우에는 보내지 않는다** — 이미 들어와 있는 방의 재입장, 거절.
   - 채널 이름 상수는 `app:room` 의 `redisKeys/SharedKeys.BOARD_CHANNEL`(`qm:pubsub:board`)이다 — 알림 서비스의
     `redisKeys/BoardChannels.BOARD_CHANNEL` 과 **같은 값이어야 한다**(원본을 어느 서비스에 둘지는 여전히 미정이다). `type` 상수는
     `notification/PushEventType.BOARD_CHANGED` 다.
   - **발행 실패가 입장 · 퇴장을 뒤집지 않는 것은 그대로다.**
7. **마지막 시점이 새것이다 — D-20 "감수하는 것"의 "수명이 다해 없어진 방은 신호를 내지 못한다"를 줄인다.** 방장이 말없이 사라져
   수명이 다한 (확정하지 않은) 방은 **없어지는 순간에는 여전히 신호를 못 낸다**(그 순간 `app:room` 의 코드가 돌지 않는다). 그러나
   **그 방에 남아 있던 멤버의 다음 접속 확인**(늦어도 1분 뒤)이 "방이 없어졌다"를 받을 때 `app:room` 이 그것을 처음 알게 되고,
   **그때 게시판 신호를 보낸다.** 남은 사람이 여럿이면 각자 한 번씩 보내게 되지만 해가 없다 — "다시 받아라"일 뿐이고 프런트가
   재요청을 묶는다(D-22 결정 4).
   - **남는 한계 — 방장 혼자 있던 방.** 접속 확인을 보낼 사람이 없어 여전히 신호를 못 낸다. 다른 방의 신호나 새로고침으로 목록을
     다시 받을 때 `app:platform` 이 방장 키가 없는 것을 보고 그 글을 뺀다.

**D-20 · D-21 · D-22 와의 관계.**

- **D-21 결정 4** "방장이 나가면 방을 통째로 없앤다 — … 확정 뒤에도 마찬가지다" → **확정하지 않은 방에만 맞다.** 확정한 방은
  방장을 넘기고(결정 2), 넘길 사람이 없을 때만 없앤다. "방을 없애는 일은 스크립트 하나에서 한다" · 나가기는 전부 204 다 · 늦게
  도착한 나가기가 다른 방의 입장 표시를 지우지 않는다는 점은 그대로다.
- **D-21 결정 6** "방장이 나가면(또는 말없이 사라지면) 확정 전과 똑같이 방이 통째로 없어진다" → **개정됐다**(결정 2 · 3). 결정 6 의
  나머지 — 방장만 · 2명 이상 · 그 순간의 전원이 파티원 · 되돌릴 수 없다 · 입장 409 `ROOM_CONFIRMED` · 확정 표시 키를 `app:room` 이
  쓴다 · `app:platform` 은 읽기만 한다 · 프런트의 "한 번 더 수락" — 은 그대로다. "**확정 표시의 수명도 방장의 신호가 늘린다**"에는
  **확정한 방의 일반 멤버의 신호도** 더해진다(결정 3).
- **D-21 결정 3** "방의 수명은 방장의 신호만 늘린다" → **확정한 방에 예외가 생겼다**(결정 3). 확정하지 않은 방은 그대로이고,
  **방장 키의 수명은 어느 방에서든 방장의 신호만 늘린다.** "그래서 방장의 연결 끊김도 나간 것이고, 방이 저절로 없어진다"도
  확정하지 않은 방에만 맞다. 수명 600초 · 1분 주기 · 방장의 신호가 유령을 뺀다 · `app:platform` 에 알리지 않는다는 점은 그대로다.
- **D-21 "근거" 2번** "멤버의 신호도 방의 수명을 늘리면 방장 없는 방이 남는다" → 확정한 방에서는 방장 키가 만료된 뒤 처음 신호를
  보낸 멤버가 방장이 되므로(결정 3) 방장 없는 방으로 남지 않는다.
- **D-21 결정 1 의 방 키 표** → 이름 · 자료형 · 값의 뜻은 그대로다. 방장 키의 값이 **확정한 방에서는 바뀔 수 있다**(결정 5).
- **D-21 결정 8 의 알림 표** → 그대로다. 새 `type` 도 새 `payload` 필드도 없다(결정 4). `ROOM_MEMBER_LEFT` 가 가는 경우에 "확정한 방의
  방장이 나갔다"가, `ROOM_CLOSED` 가 가는 경우에 "확정한 방인데 넘길 사람이 없었다"가 더해질 뿐이다.
- **D-21 "감수하는 것"의 "방장이 말없이 사라진 방은 최대 10분 목록에 살아 있는 것처럼 보인다" · "수명이 다해 없어진 방은
  `ROOM_CLOSED` 를 보내지 못한다"** → 확정하지 않은 방에 대해서는 그대로다. 같은 항목의 "게시판 채널 신호도 같은 이유로 나가지
  못한다"는 결정 7 로 걸러 읽는다.
- **D-20 "감수하는 것"의 "수명이 다해 없어진 방은 신호를 내지 못한다"**(D-22 "감수하는 것"이 "그대로다"라고 받은 것 포함) →
  **줄었다**(결정 7). 남는 것은 방장 혼자 있던 방이다.
- **D-20 (다) · D-21 · D-22 가 든 `app:room` 의 발행 시점**(방 만들기 · 입장 · 나가기 · 강퇴 · 접속 확인이 유령을 뺐을 때 · 방 닫힘 ·
  방장 확정) → 그대로이고 **하나가 늘었다**(결정 6 의 마지막).
- **D-20 · D-21 · D-22 가 `app:room` 의 발행을 "구현 전" · "아직 없다"고 적은 곳** → **구현됐다**(결정 6). `app:platform` 의 발행은
  아직 없다(코드가 없다).
- **D-22** → 그 밖에는 바뀌지 않는다. 채널이 하나라는 점 · `payload` `{}` · `topics` 가 없다는 점 · 거르는 것은 클라이언트라는 점은
  그대로다.
- **D-19** → 바뀌지 않는다. 입장 표시 키는 여전히 그 사람의 접속 확인만 늘리고, 나가는 방장의 입장 표시는 값이 이 방일 때만
  지운다.

**근거.**

1. **확정은 "이 사람들로 파티가 성립했다"는 뜻이다.** 그 뒤에 방장 한 명이 나갔다고(또는 연결이 끊겼다고) 나머지 전원을 내보내는
   것은 지나치다.
2. **확정 전에는 방 = 모집 글이고 글의 주인이 방장이다.** 그래서 확정 전에는 방장이 나가면 방도 끝나는 것이 맞다 — 결정 1 이 그대로인
   이유다.
3. **확정한 방에서 멤버의 신호가 수명을 늘려야 하는 이유.** 세 키(방장 키 · 멤버 SET · 확정 표시 키)가 방장의 마지막 신호에서 같은
   수명을 받는다. 멤버의 신호가 늘리지 않으면 방장 키가 만료되는 순간 멤버 SET 과 확정 표시 키도 같이 사라져 **이어 갈 방이 남지
   않는다.**
4. **방장 키는 늘리지 않는 이유.** 방장이 사라졌으면 방장 키가 만료돼야 다음 사람이 넘겨받는다(결정 3).
5. **살아 있는 멤버에게만 넘기는 이유.** 멤버 SET 에 이름만 남은 유령에게 넘기면 방장 없는 방이 된다(결정 2).
6. **접속 확인이 "방이 없어졌다"를 받는 순간이 `app:room` 이 그 방이 없어진 것을 아는 첫 순간이다**(결정 7). 수명이 다하는 순간에는
   `app:room` 의 코드가 돌지 않는다.

**검토하고 받지 않은 것.**

- **(가) 방장 없이 방을 이어 가기**(방장 키를 지우고 멤버만 남긴다). "방장 키가 있다 = 방이 있다"가 깨져 `app:platform` 과의 약속이
  바뀌고, 방 안 사람 목록(404 가 된다) · 강퇴 · 유령 빼기(방장의 신호만 한다)가 전부 깨진다. **같은 날 먼저 이쪽으로 짰다가 방장을
  넘기는 쪽으로 바꿨다.**
- **(나) `ROOM_MEMBER_LEFT` 의 `payload` 에 `newHostId` 를 싣기.** 계약의 필드를 늘리지 않는다 — 클라이언트가 방 안 사람 목록을 다시
  조회하면 된다(결정 4).
- **(다) Redis 키 만료 이벤트로 수명이 다한 방의 신호를 내기.** `app:room` 이 구독을 해야 하고, 인스턴스가 여럿이면 중복 발행이
  되고, 앱이 죽어 있던 동안의 만료는 놓친다.

**감수하는 것.**

- **누가 방장을 넘겨받는지 정해져 있지 않다**(결정 2 · 3). 명시적 나가기에서는 살아 있는 멤버 가운데 아무나, 연결 끊김에서는 방장
  키가 만료된 뒤 처음 접속 확인을 보낸 멤버다.
- **새 방장이 누구인지는 알림만으로는 알 수 없다**(결정 4). 클라이언트가 방 안 사람 목록을 한 번 더 조회해야 한다.
- **수명이 다한 방의 게시판 신호는 늦고(늦어도 1분 뒤), 남은 사람 수만큼 여러 번 나갈 수 있다**(결정 7). 방장 혼자 있던 방은
  여전히 신호를 못 낸다.
- **같은 값을 두 앱이 따로 적는다.** 게시판 채널 이름이 `app:room` 의 `SharedKeys` 와 알림 서비스의 `BoardChannels` 두 곳에 있고
  원본이 어느 쪽인지 정해지지 않았다 — 어긋나도 컴파일 · 테스트가 통과한 채로 목록이 조용히 갱신되지 않는다(D-20 "감수하는 것"
  그대로).
- **확정한 방의 방장이 말없이 사라지면 방장 키가 잠깐 없는 구간이 생긴다.** 방장 키가 만료된 때부터 다음 멤버의 접속 확인까지(늦어도
  1분) 멤버 SET 과 확정 표시 키는 있는데 방장 키가 없다 — 그 사이 방 안 사람 목록 · 강퇴 · 확정은 "없는 방"으로 답하고,
  `app:platform` 이 그 순간 방장 키를 읽으면 방이 없다고 본다. 그리고 방장 키가 만료되기 **전까지**(길게는 방의 수명만큼)는 방장
  키의 값이 이미 사라진 방장이라, 그동안 강퇴와 유령 빼기를 할 사람이 없다. *(스크립트를 읽고 판단한 것이다 — 실험으로 확인하지는
  않았다.)* 그래서 `app:platform` 은 **확정된 글을 "방장 키가 없다" 하나로 곧바로 만료시키면 안 된다** — 어떻게 가를지는 미정이다(아래).
- D-20 · D-21 · D-22 "감수하는 것"의 나머지는 그대로다.

**아직 미정 — 임의로 지어내지 않는다.**

- **확정된 글에서 방장 키가 없을 때 `app:platform` 이 어떻게 다루는가** — 잠깐 없는 구간(위 "감수하는 것")과 방이 정말 없어진 것을
  어떻게 가르는가(멤버 SET · 확정 표시 키도 같이 보는지 등). `app:platform` 을 만들 때 정한다.
- **방장을 넘겨받는 사람을 고르는 기준을 정할 것인가 — 이 결정으로 새로 드러난 미정이다.** 지금은 아무나다(결정 2 · 3).
- **방장 확정 뒤에는 입장 표시 키를 언제 지우는가**, **확정된 방이 Ready 등 파티룸 기능을 갖는가**, **강퇴당한 사람의 재입장** —
  D-21 의 미정 그대로다.
- **게시판 채널 이름의 원본 상수를 어느 서비스에 둘지**, **프런트가 재요청을 묶는 간격** — D-20 · D-22 의 미정 그대로다.
- 그 밖에 D-20 · D-21 · D-22 의 미정 목록 전부. 이 항목이 없앤 미정은 없다.

**영향.**

- **이 저장소(`app:matching`)의 코드는 바뀌지 않는다.** `app:matching` 은 방 키도 게시판 채널도 만지지 않는다. 이 저장소의
  `CLAUDE.md` · `START_HERE.md` · `HANDOFF.md` 도 고치지 않았다.
- **`contracts/events.md` · `contracts/README.md` 는 고치지 않았다 — 남은 일은 D-20 · D-21 · D-22 그대로다.** 이 결정으로 올릴 것이
  늘지 않는다(새 `type` 도 새 `payload` 필드도 없다).
- **`app:room`(`../room/`)에 구현돼 있다.** 경로는 `../room/backend/src/main/` 아래의 상대 표기다.
  - `resources/lua/leave-room.lua`(확정한 방의 방장 넘기기 · 넘길 사람이 없으면 방을 없앤다) · `resources/lua/heartbeat-room.lua`
    (확정한 방은 멤버의 신호도 멤버 SET · 확정 표시 키의 수명을 늘린다 · 방장 키가 만료된 뒤 처음 신호를 보낸 멤버가 방장이
    된다).
  - `java/com/queuemate/room/service/RoomService`(방 만들기 · 방장 확정의 게시판 신호) · `service/RoomMemberService`(입장 · 나가기 ·
    강퇴 · 접속 확인의 게시판 신호), `notification/PushEventType.BOARD_CHANGED`, `redisKeys/SharedKeys.BOARD_CHANNEL`.
  - `../room/contracts/room-api.md` "나가기" · "방장 확정" · "접속 확인" · "알림" · "게시판 채널 신호" · "Redis 키",
    `../room/CLAUDE.md` §1 · §2 · §3.1 · §7 · §8 · §10, `../room/START_HERE.md`, `../room/README.md`,
    `../room/docs/PROJECT_OVERVIEW.md` 를 같은 날 맞췄고, 그 폴더의 사본 `docs/DECISIONS.md` 에 이 항목을 옮겼다.
- **`app:platform`(`../platform/`)** — 아직 코드가 없다. **방 키 약속의 형식은 바뀌지 않는다.** 알아야 하는 것은 **확정한 방에서는
  방장 키의 값이 바뀔 수 있다**는 것이고, 그래서 확정된 글을 "방장이 나갔으니 만료"로 다루면 안 된다 — **방장 키가 없어졌을 때**가
  방이 없어진 것이다. `../platform/CLAUDE.md` · `../platform/README.md` 와 `../platform/docs/ROOM_CONTRACT.md`(계약의 발췌 사본)를
  같은 날 맞췄다.
- **알림 서비스(`../notification/`)** — 바뀌지 않는다. `../notification/CLAUDE.md` · `../notification/README.md` 의 "`room` 의 발행은
  아직 없다"는 서술만 고쳤다.
- **프런트** — `ROOM_MEMBER_LEFT` 의 `userId` 가 자기가 알던 방장이면 방 안 사람 목록을 다시 조회해 `hostId` 를 새로 받는다(결정 4).

---

### D-24. **(일부 낡음 — 로그인 실패 제한은 D-35 로 물음째 없어졌다)** 인증 세부를 정한다 — RS256(서명은 `app:platform` 만) · 공개 키는 환경변수(JWKS 없음) · CSRF 는 `SameSite=Lax` + `Origin` 검사 · **access denylist 를 두지 않는다** · `token_use` 클레임 (#16 개정 · D-14 "아직 미정"의 대부분 확정, 2026-09-21)

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템 전체에 걸리는 인증의 세부이고 **프로젝트 소유자가 정했다**(2026-09-21).
> **사실의 원본은 `../platform/CLAUDE.md` §5.1 과 `../platform/contracts/platform-api.md` "공통" · "access 토큰" · P-2 다.** 원본 결정 로그가 있는
> queueMate 본 저장소가 이 컴퓨터에 없어 여기에 먼저 적는다(`app:platform` 쪽이 "docs/11 에 올려야 한다"고 남겨 둔 것을 2026-09-26 에 옮겼다).
> **#16 · D-14 의 본문은 고치지 않았다** (이 파일은 기록이다). access · refresh 의 **수명**과 refresh 의 **Redis 키**는 D-26 이다.

**원안.** #16 은 "정지/삭제 계정 즉시 차단은 stateless 로 불가능하므로 **Redis denylist 를 두고, denylist 조회 실패는 fail-closed**"라고 적었다.
D-14 는 access = 쿠키로 주고받는 JWT, refresh = Redis 의 불투명 UUID 까지 정하고 **쿠키 속성 · CSRF · 서명 방식과 키 나눠 갖기 · 클레임 · 수명 · denylist 여부 ·
SSE 와 토큰 만료 · 개발 환경의 CORS** 를 미정으로 남겼다.

**결정.** (전제 — 운영은 CloudFront 한 도메인 아래에서 경로로 나뉘어 **브라우저가 보기에 모든 서비스가 같은 출처다**. `docs/AWS_ARCHITECTURE.md` 의 연결 표.)

1. **서명은 RS256 이고 개인 키는 `app:platform` 만 갖는다.** `app:matching` · `notification` 은 **공개 키로 검증만** 한다. 키는 RSA 2048,
   환경변수 `JWT_PRIVATE_KEY`(PKCS#8 PEM — `app:platform` 만) · `JWT_PUBLIC_KEY`(X.509 PEM — 옆 서비스도 받는다), 헤더의 `kid` 는 `JWT_KEY_ID`.
2. **공개 키는 환경변수로 나눠 준다(운영은 Secrets Manager). JWKS 엔드포인트를 두지 않는다** — 두면 서비스 간 동기 호출이 생긴다(#15 "경계를 넘는 동기 호출을 새로 만들지 않는다").
3. **라이브러리는 Spring Security `oauth2-resource-server`(Nimbus)다.** 서명은 `NimbusJwtEncoder`, 검증은 `NimbusJwtDecoder.withPublicKey()` + **쿠키에서 토큰을 꺼내는 `BearerTokenResolver`**. jjwt 등을 들이지 않는다.
4. **클레임은 `sub` · `iss`(`queuemate-platform`) · `iat` · `exp` · `jti` · `token_use` 뿐이다.** 닉네임처럼 바뀌는 값은 싣지 않는다.
   **`sub` 는 사용자 번호의 십진 문자열이다**(D-25 — 정할 때는 로그인 아이디였다). 검증하는 쪽은 `sub` 가 `^[0-9]{1,19}$` 인지도 본다.
5. **`token_use` 클레임으로 쓰임새를 가른다** — 값은 `access`(access 토큰) · `social_signup`(소셜 가입 대기 토큰). **같은 키로 서명하므로 검증하는 쪽은 서명 · `iss` · `exp`
   에 더해 `token_use` 가 기대한 값인지 반드시 본다.** JOSE 헤더의 `typ` 을 쓰지 않은 것은 Spring Security 의 기본 디코더가 `typ` 이 `JWT` 가 아니면 거절해서다.
   (방 입장권의 값 `room_ticket` 도 있었으나 D-33 으로 없어졌다.)
6. **쿠키** — 이름 `qm_access` · `HttpOnly` · `SameSite=Lax`(`Strict` 면 외부 링크로 들어온 첫 화면이 로그아웃 상태로 보인다) · `Path=/` · **`Domain` 없음**(host-only) ·
   `Max-Age` = 토큰 수명 · `Secure` 는 환경변수 `COOKIE_SECURE`(운영은 켠다).
7. **CSRF 는 `SameSite=Lax` + `Origin` 헤더 검사다. CSRF 토큰을 쓰지 않는다.** POST/PUT/PATCH/DELETE 에 `Origin` 이 있고 허용 목록(`ALLOWED_ORIGINS`)에 없으면 403 `ORIGIN_NOT_ALLOWED`.
   `Origin` 이 없는 요청(curl · 서버 사이)은 통과한다. **전제 — 상태를 바꾸는 GET 을 만들지 않는다**(예외는 OAuth 가 강제하는 소셜 로그인 콜백 하나 — `state` 검증이 지킨다).
8. **access denylist 를 두지 않는다 — #16 의 "Redis denylist, 조회 실패 시 fail-closed" 를 개정한다.** 로그아웃은 refresh 삭제 + 쿠키 제거이고, **남는 access 수명만큼은 감수한다**(D-26 으로 15분).
9. **SSE 와 토큰 만료** — `notification` 은 **연결할 때만** 검증하고 열린 연결은 토큰이 만료돼도 끊지 않는다. 재접속이 401 로 멈추면 프런트가 `onerror` 에서
   `readyState === CLOSED` 를 보고 **재발급한 뒤 `EventSource` 를 새로 만든다.** 서버 쪽 장치는 두지 않는다.
10. **로컬 CORS — 서비스에 CORS 설정을 넣지 않는다.** 프런트 개발 서버의 프록시가 경로별로 나눠 보낸다(운영이 같은 출처라서다).
11. **옆 서비스의 전환** — 임시 식별(`?userId=`)에서 쿠키로의 전환은 `app:platform` 의 로그인이 도는 것을 본 뒤 서비스별로 따로 한다. 순서는 **`notification` → `app:matching`**
    (맨 앞이던 `app:room` 은 D-33 으로 `app:platform` 에 합치며 끝났다). 쿠키가 없으면 `userId` 파라미터를 받는 개발용 스위치를 잠깐 남겨도 된다 — 임시 처리로 표시하고 운영에서는 끈다.

**#16 · D-14 와의 관계.**

- **#16** 의 "Redis denylist 를 두고 denylist 조회 실패는 fail-closed" → **개정됐다**(결정 8). "refresh rotation 필수" · "access TTL 은 짧게"는 그대로다(D-26).
- **D-14 "아직 미정"** 의 쿠키 속성 · CSRF · 서명 방식과 키 나눠 갖기 · 클레임 구성 · denylist 여부 · SSE 와 토큰 만료 · 개발 환경 → **정해졌다**(위).
  수명 · refresh 의 Redis 키 · 기기 수 · rotation 때 옛 값 재사용 → D-26. D-14 가 적은 "`sub` = 로그인 아이디" → D-25 로 사용자 번호가 됐다.
- **D-14 "영향"의 "`securitySchemes` 는 cookie 방식(`apiKey` · `in: cookie`)으로 채워야 한다. 쿠키 이름은 미정이다"** → 쿠키 이름이 `qm_access` 로 정해졌다. 원본 `openapi.yaml` 에 올리는 것은 그대로 남은 일이다.

**근거.** (`../platform/CLAUDE.md` §5.1)

1. **HS256 이면 비밀 키를 여러 서비스가 다 갖는다** — 하나만 뚫려도 아무 사용자의 토큰을 만들 수 있다.
2. **denylist 를 두면 모든 서비스의 모든 요청이 Redis 를 조회하고**, fail-closed 라 Redis 가 죽으면 전부 401 이 되며, JWT 를 스스로 검증하는 이점이 사라진다.
3. **`SameSite` 만으로는 모자라다** — 출처가 아니라 사이트 단위라 서브도메인을 못 막는다. **CSRF 토큰은** stateless 인 서비스들이 각자 발급 · 검증해야 하고 프런트도 매번 실어야 한다.
   `Origin` 검사는 필터 하나라 서비스마다 똑같이 들어간다.

**감수하는 것.**

- **로그아웃해도 access 는 수명이 다할 때까지 유효하다**(D-26 으로 최대 15분). 정지 · 삭제 계정을 즉시 끊는 길이 access 쪽에는 없다.
- `token_use` 를 안 보는 검증 코드는 소셜 가입 대기 토큰을 access 로 받아 준다 — 검증하는 서비스마다 같은 검사를 붙여야 한다.

**아직 미정 — 임의로 지어내지 않는다.**

- `notification` · `app:matching` 의 전환 시점과 `Origin` 검사를 그 둘에 언제 넣는가(각 폴더의 일 — 둘 다 아직이다).
- 프런트의 재발급 흐름(프런트가 이 컴퓨터에 없어 맞춰 본 적이 없다).

**영향.**

- **이 저장소(`app:matching`)의 코드는 아직 바뀌지 않았다** — 여전히 요청의 `userId` 를 그대로 믿는다(`contracts/README.md` 불일치 표의 임시 조치). 전환할 때
  공개 키 검증 · 쿠키 `BearerTokenResolver` · `token_use` 검사 · `sub` 형식 검사 · `Origin` 필터가 붙는다.
- 구현은 `app:platform` 에 있다(`../platform/backend/…/common/security/` · `common/web/`). 개발용 키는 `../platform/backend/.dev-keys/public.pem` — 옆 서비스는 그것으로 검증한다.

---

### D-25. **(절반 낡음 — `loginId` 는 D-35 로 없어졌다. 사용자 번호는 그대로)** 모든 테이블의 PK 를 `bigint GENERATED ALWAYS AS IDENTITY` 로 하고, 사용자의 식별자를 **사용자 번호(`userId`)** 와 **로그인 아이디(`loginId`)** 로 가른다 (D-4 개정 · D-14 결정 3 개정, 2026-09-22)

> **이 결정은 매칭 엔진의 결정이 아니다.** 그러나 **이 저장소의 코드를 지금 깨뜨리고 있다**(아래 "영향"). 프로젝트 소유자가 정했다(2026-09-22).
> **사실의 원본은 `../platform/contracts/platform-api.md` "공통" · P-11 과 `../platform/CLAUDE.md` §3.5, 테이블은 `../platform/backend/src/main/resources/db/migration/V1__schema.sql` 이다.**
> **D-4 · D-14 의 본문은 고치지 않았다** (이 파일은 기록이다).

**원안.** 2026-09-19 에 `app:platform` 쪽에서 "**사용자 id 는 가입할 때 정한 로그인 아이디(문자열)다**"로 정했고(D-14 결정 3 이 그것을 옮겨 적었다), D-4 는
`Block` 의 `blockerId` / `blockedId` 를 `String` 으로 두며 "**사용자 id 타입을 `String` 으로 통일한다** — 요청의 `userId` 가 `String` 이라 변환 지점이 생기지 않는다"를 근거로 들었다.

**결정.**

1. **모든 테이블의 PK 는 `bigint GENERATED ALWAYS AS IDENTITY` 다.** 채번은 DB 가 한다.
2. **`users.id`(bigint)가 `userId`(사용자 번호)다** — JWT 의 `sub`(**숫자를 십진 문자열로** — `"42"`), 알림 채널 `qm:pubsub:push:{userId}`, 방 키 · 멤버 SET 의 `{userId}`,
   URL 의 `{userId}`, 요청 · 응답 본문의 `userId`, 다른 테이블의 `*_id` 컬럼이 전부 이것이다.
3. **로그인 아이디는 `users.login_id` · `loginId` 로 따로 둔다**(`varchar(20)` UNIQUE · `^[a-z0-9_]{4,20}$`). 가입 · 로그인 본문과 `users/me` 응답에만 나온다 — 그것으로 사람을 가리키지 않는다.
   (예외 하나 — 로그인 실패 제한의 Redis 키 `qm:auth:login-fail:{loginId}` · `qm:auth:login-lock:{loginId}` 는 사용자를 찾기 **전에** 세야 해서 로그인 아이디 기준이다.)
4. **그래서 `blocks.blocker_id` · `blocked_id` 가 `varchar(20)` 에서 `bigint` 가 됐다.**

**D-4 · D-14 와의 관계.**

- **D-4 의 "사용자 id 는 `String`"과 그 근거("변환 지점이 생기지 않는다")** → **뒤집혔다.** `Block.java` 의 두 칸을 `Long` 으로 바꿔야 하고 **이제는 변환 지점이 생긴다**
  (`findBlockedUserIds` 의 결과를 파티 HASH 의 문자열 `member:{userId}` 와 대조하는 자리 — `HANDOFF.md` §0-4 (가)). D-4 의 나머지(일련번호 PK · `@IdClass` 를 없앤 것 ·
  `@GeneratedValue` 를 붙이지 않은 것 · UNIQUE 는 `app:platform` 이 건다)는 그대로다. D-4 "주의"의 "`WHY_POSTGRESQL` 은 `uuid` 로 서술한다"는 여전히 어긋난다 — 이제는 bigint 다.
- **D-14 결정 3** "사용자 식별자는 가입할 때 정한 로그인 아이디 문자열이다" → **사용자 번호다.** D-14 "아직 미정"의 "`sub` = 로그인 아이디"도 같다(D-24 결정 4).

**근거.** (`../platform/CLAUDE.md` §3.5)

- **로그인 아이디를 바꿀 수 있는 값이 된다** — 채널 이름 · URL · 토큰에 박혀 있지 않으므로. 옛 결정의 "바꿀 수 없는 값으로 다룬다"가 풀린 것이 이 결정의 이득이다(바꾸는 API 는 아직 없다).
- 식별자를 한 가지 모양(숫자)으로 통일한다.

**이 저장소는 통째로 `Long` 으로 바꾸지 않아도 된다.** Redis 키 · 요청 파라미터 · DTO · 파티 HASH 의 `member:{userId}` 는 **문자열 그대로 둬도 된다** — `app:platform` 이
`sub` 와 본문에 숫자를 십진 문자열로 찍기 때문이다. **`block` 패키지만이 DB 의 bigint 와 만난다.** 어느 쪽으로 맞출지(엔티티만 `Long` + 부르는 자리에서 문자열로 되돌리기 /
`userId` 를 다루는 자리까지 `Long`)는 **코드를 만질 때 고른다 — 정하지 않았다.**

**감수하는 것.**

- `ScriptSupport#blockedWith` 가 문자열 멤버 id 와 차단 목록을 `contains` 로 대조한다 — **한쪽만 `Long` 으로 올리면 컴파일도 테스트도 통과한 채 차단이 조용히 안 걸린다.**
- 테스트 H2 의 `schema.sql` 이 `varchar(255)` 면 이 변경이 깨져도 테스트가 못 잡는다 — 같이 `bigint` 로 바꿔야 한다.

**아직 미정.** 자동 매칭 파티(`parties.source = 'MATCH'`)가 이 앱의 UUID `partyId` 를 어디에 두는가 — `parties.id` 가 bigint 가 됐다. `app:platform` 의 6단계(SQS)에서 정한다.

**영향.**

- **이 저장소 — 코드를 고쳐야 한다. 아직 안 고쳤다**(`HANDOFF.md` §0-4 (가) 의 표). `block/Block.java` 의 두 칸 · `BlockRepository` 의 세 메서드 · `rule/*/…CandidateRule` 셋 ·
  `rule/ScriptSupport#blockedWith` 와 그 호출 여섯 · `backend/src/test/resources/schema.sql`. **안 고치면 운영 DB 에 붙는 순간 차단 조회가 깨져 INV-6 선필터가 죽는다**
  (배정은 `@Async` 안이라 요청은 201 로 나가고 배정만 조용히 실패한다). 테이블 이름도 D-34 로 바뀌었다 — 같이 고친다.
- `notification` 은 `sub` 를 문자열로 다뤄 코드 변경이 없다.

---

### D-26. refresh 토큰을 붙인다 — access `PT15M` · refresh `P7D` · 불투명 UUID 를 Redis `qm:auth:refresh:{uuid}` 에 두고 `GETDEL` 한 번으로 rotation 한다 (#16 구체화 · D-14 "아직 미정"의 수명 · refresh 키 확정, 2026-09-23)

> **이 결정은 매칭 엔진의 결정이 아니다.** 프로젝트 소유자가 정했다(2026-09-23). D-24 와 같은 묶음이다(#16 의 개정 · 구체화).
> **사실의 원본은 `../platform/contracts/platform-api.md` "refresh 토큰" · P-15 와 `../platform/CLAUDE.md` §5.1 (라) · (마) 다.** **#16 · D-14 의 본문은 고치지 않았다.**

**원안.** D-14 는 refresh 의 **형태**(Redis 의 불투명 UUID)만 정하고 수명 · Redis 키 · 기기 수 · 옛 값 재사용을 미정으로 두었다. `app:platform` 은 한동안 refresh 없이
access 만으로 돌았다(개발 기본값 24시간 · 표식 `TEMP-NO-REFRESH`).

**결정.**

1. **access 수명은 `ACCESS_TOKEN_TTL` 기본값 `PT15M`, refresh 수명은 `REFRESH_TOKEN_TTL` 기본값 `P7D`(7일)다.** 24시간이던 개발 기본값과 `TEMP-NO-REFRESH` 가 없어졌다.
2. **refresh 는 Redis 키 `qm:auth:refresh:{uuid}` → 값 사용자 번호다.** 접두사 `qm:auth:*` 는 매칭의 `qm:user:*` 등과 겹치지 않는다. 기기 수는 제한하지 않는다(토큰마다 키 하나).
3. **rotation 은 `GETDEL` 한 번으로 원자적으로 한다**(`조회 → 판단 → 삭제` 가 아니다). 옛 값을 다시 쓰면 **그냥 401** 이다 — 탈취 감지(토큰 계보 추적)는 넣지 않는다.
4. **재발급은 `POST /api/v1/auth/refresh`** — 본문 없이 쿠키 `qm_refresh`(`Path=/api/v1/auth/refresh` · `Max-Age` = 7일)로만 받고, 성공하면 로그인과 같은 본문에 새 쿠키 둘을 싣는다.
   **실패는 전부 같은 401 `INVALID_REFRESH_TOKEN`**(글자까지 같다 — 어느 쪽인지 알려 주면 그 값이 살아 있는지가 새어 나간다)이고 실패에도 refresh 쿠키를 지운다.
5. **로그아웃은 Redis 의 그 줄과 쿠키 둘을 지운다.** 쿠키가 없거나 Redis 가 죽어 있어도 204 다.
6. **Redis 가 죽었을 때** — 로그인은 성공하고 access 만 나간다 · 재발급은 401(fail-closed) · 로그아웃은 204.

**#16 · D-14 와의 관계.** #16 의 "refresh rotation 필수" · "access TTL 은 짧게"를 구체화한다. D-14 "아직 미정"의 수명 · refresh 의 Redis 키 이름 · 값 · TTL · 기기 수 ·
rotation 때 옛 값 재사용 → **정해졌다**(위). **D-14 "영향"의 "재발급은 Redis 가 있어야 한다"는 그대로 맞다**(결정 6).

**근거.** access denylist 를 두지 않으므로(D-24 결정 8) **서버가 무효화할 수 있는 것은 refresh 하나다** — access 를 짧게 하고 refresh 로 이어 주면 로그아웃 · 탈취 뒤에
access 가 살아 있는 창이 24시간에서 15분으로 준다. (경로 이름 · 실패를 401 하나로 합친 것 · 실패에도 쿠키를 지우는 것 · Redis 장애의 갈림은 Claude 가 정한 세부다 — P-15.)

**감수하는 것.** **한 사용자의 refresh 를 한꺼번에 끊는 길이 없다** — 사용자별 토큰 집합을 두지 않았고 `KEYS`/`SCAN` 을 쓰지 않는다. 비밀번호를 바꾸거나 계정이 털렸을 때
모든 기기를 로그아웃시킬 수 없다.

**아직 미정.** 모든 기기 로그아웃을 둘지 · 프런트의 재발급 흐름(access 만료 전에 프런트가 재발급을 불러야 하고 서버 쪽 장치는 없다).

**영향.** **옆 서비스에는 걸리지 않는다** — 서명 · 검증이 달라지지 않고 access 의 수명만 짧아진다. 이 저장소의 코드는 바뀌지 않는다.

---

### D-27. **(일부 낡음 — "커밋 뒤 비동기" 와 LoL 의 자기신고는 D-37 이 개정했다)** LoL 전적을 Riot API 에서 긁는다 — 비동기이고 실패해도 본 요청은 성공한다. **긁는 시점은 처음 둘이었다가 하루 뒤 모집 글 쪽을 되물렸다** (새 결정 · 2026-09-23, 2026-09-24 개정)

> **이 결정은 매칭 엔진의 결정이 아니다.** 프로젝트 소유자가 정했다(2026-09-23 · 2026-09-24). **사실의 원본은 `../platform/contracts/platform-api.md` "전적을 긁는 것" · P-13 과
> `../platform/CLAUDE.md` §7 "게임 계정 연동" 이다.** `app:platform` 은 2026-09-24 의 개정에 새 P-번호를 두지 않고 P-13 을 고쳤다 — 여기서도 한 항목에 둘 다 적는다.
> 사용자가 누르는 "전적 갱신" 요청은 D-30 이다.

**원안.** 게임 계정(게임 닉네임 · 티어 · 주 포지션 · PUBG 의 서버)은 사용자의 **자기신고**였고, 밖에 보여 주는 게임 프로필에 읽기 전용 `verified` · `stats`(전적 스냅숏)의
자리만 있었다(`stats` 는 늘 `null`). **전적 스냅숏은 세 게임이 한 테이블 `game_account_stats` 를 쓰고 판 수 `games` 만 공통 컬럼이다**(2026-09-22 소유자 결정 · P-12 —
승/패 · 연승 · 어시스트는 게임에 따라 비는 칸, 게임마다 다른 지표는 `detail` jsonb).

**결정.**

1. **(2026-09-23) LoL 의 전적을 Riot API 에서 긁는다.** 긁는 시점은 **둘** — 게임 계정을 연결 · 수정할 때(`PUT /api/v1/users/me/game-accounts/{game}`)와 **모집 글을 쓸 때** — 이고,
   `synced_at` 이 **30분**(`platform.riot.freshness`) 안이면 건너뛴다. **커밋된 뒤에 비동기로** 돌고(전용 풀) **실패해도 본 요청은 성공**이며 기존 `stats` 줄을 지우지 않는다.
   `external_id` 에 `puuid` 를 적지만 **`verified` 는 켜지 않는다**(식별자를 알아낸 것은 본인 확인이 아니다). **평점은 넣지 않는다**(Riot API 에 없다 — 2026-09-23 재확인).
   키(`RIOT_API_KEY`)가 없으면 긁는 일 자체를 하지 않는다.
2. **(2026-09-24 — 1 의 절반을 되물렸다) 모집 글을 쓸 때는 긁지 않는다.** 그 시점만 보던 **신선도 30분도 같이 없어졌다.** 긁는 시점은 게임 계정을 연결 · 수정할 때 **하나**가 됐다
   (같은 날 D-30 의 "전적 갱신" 요청이 붙어 **다시 둘**이 됐다 — 이 결정을 뒤집지 않는다).

**근거.**

- 1 — 목록의 카드가 OP.GG 듀오 찾기처럼 전적을 보여 준다(2026-09-21 소유자 지시). 비동기로 둔 것은 알림 발행과 같은 원칙이다 — 곁일의 실패가 본 작업을 뒤집지 않는다.
- 2 — 긁는 것이 비동기라 **그 글쓰기 응답에 반영되지도 않는데** 대가가 **Riot 호출 21번**(puuid · 소환사 · 리그 · 경기 id · 경기 20)이고 개발용 키의 한도가 2분에 100회다 — 수지가 안 맞는다.
  신선도를 보는 곳이 그 시점 하나뿐이라 죽은 코드가 됐다(그 판단은 Claude 가 했다).

**감수하는 것.** **저절로 갱신되지 않는다** — 오래전에 연결하고 안 건드린 사람의 `stats` 는 낡은 채로 남는다(언제 긁은 것인지는 `syncedAt`). VALORANT · PUBG 의 `stats` 는 늘 `null` 이다.

**아직 미정.** 전적을 주기적으로(사용자가 누르지 않아도) 갱신할지와 그 주기 · VALORANT(Riot 의 별도 승인) · PUBG(다른 API)의 전적 · Riot(RSO) 인증으로 `verified` 를 켜는 법
(**당분간 자기신고를 믿는다** — 2026-09-25 소유자 결정) · 운영의 API 키.

**영향.** `app:platform` 안에서 끝난다 — **이 저장소가 고칠 코드는 없다.** Redis 락 키 `qm:riot:sync:{gameAccountId}` 가 늘었다(`qm:riot:*` 는 `app:platform` 의 접두사다).
이 저장소의 티어도 여전히 자기신고다(`CLAUDE.md` §2).

---

### D-28. 게시판 목록의 모양 — 커서 페이지 나누기(2026-09-23) · 정렬과 커서를 `id` 하나로(2026-09-24) · 커서를 감싸지 않는다 · 보존 기간을 없앤다 · `game` 은 필수다(2026-09-25) (D-11 · D-20 의 목록 구체화, 새 결정)

> **이 결정은 매칭 엔진의 결정이 아니다.** 게시판 목록(`GET /api/v1/posts`)의 모양에 대해 프로젝트 소유자가 사흘에 걸쳐 정한 것을 한 항목에 모은다.
> **사실의 원본은 `../platform/contracts/platform-api.md` "목록의 정렬" · "목록의 페이지 나누기" · "목록의 `game` 은 필수다" · P-14 · P-20 · P-21 과 `../platform/CLAUDE.md` §7.1 이다.**
> `app:platform` 은 09-24 · 09-25 의 커서 개정에 새 번호를 두지 않고 P-14 를 고쳤고, 보존 기간(P-20)과 `game` 필수(P-21)에는 새 번호를 두었다.
> **D-11 · D-20 에 목록의 페이지 · 정렬 · 필터 이야기가 없어 개정하는 D-항목은 없다.**

**원안.** 목록은 전부를 한 번에 내려 주었다. 정렬은 "모집 중인 글이 먼저, 그 안에서는 새 글이 먼저"였고, **만료 · 확정된 글은 끝난 뒤 10분 동안만** 목록에 남았다
(`platform.board.closed-retention` — Claude 가 정한 P-5). `?game=` 은 없으면 세 게임 전부였다.

**결정.**

1. **(2026-09-23) 커서 방식으로 페이지를 나눈다.** `limit` 은 없으면 20 · 최대 100 이고 **벗어나면 400**(상한으로 조용히 잘라 주지 않는다). 응답의 `nextCursor` 로 이어 받고
   더 없으면 `null`. **`BOARD_CHANGED` 신호(D-20 · D-22)를 받은 프런트는 커서를 쓰지 않고 펼친 만큼을 `limit` 으로 맨 위부터 다시 받는다** — 커서는 "더 보기"에만 쓴다.
2. **(2026-09-24) 정렬은 `id` 내림차순 하나(= 최신순)이고 커서는 글 번호 하나다.** 글의 상태도 `created_at` 도 정렬 · 커서에 쓰지 않는다(`createdAt` 은 응답에 그대로 있다).
3. **(2026-09-25) 커서를 base64url 로 감싸지 않는다** — `cursor` · `nextCursor` 가 **숫자**다. 숫자가 아니면 400 `VALIDATION_FAILED`, 0 · 음수 · 맨 끝을 넘은 번호는 빈 페이지다.
4. **(2026-09-25) 보존 기간을 없앤다 — 목록은 글을 상태로 가리지 않는다.** 모집 중 · 확정 · 만료가 전부 `id` 내림차순으로 나오고 **끝난 글도 계속 남는다**(`status` 로 구분해 그린다).
   설정 `closed-retention` 이 없어졌다. **`status` 필터는 두지 않는다. 앱에 끝난 글을 지우는 정리 작업을 만들지 않는다 — 오래된 글은 운영에서 소유자가 직접 지운다.**
5. **(2026-09-25) 목록의 `game` 은 필수다.** 안 보내면(빈 값 포함) 400 `VALIDATION_FAILED`, 모르는 이름 · 소문자도 400. **목록 하나에만 걸린다**(단건 · 입장 · 확정 · 쓰기 · 고치기 · 지우기는 그대로다).

**근거.**

1. **`offset` 이 아닌 이유** — 보는 동안 글이 올라와 줄이 밀리면 같은 글이 두 번 나오거나 사이의 글이 빠진다. 전부 내려 주면 `BOARD_CHANGED` 마다 큰 응답이 되풀이된다.
2. **정렬 키가 변하면 커서가 중복을 낸다.** "모집 중인가"는 변하고 **그것도 목록 조회 자신이 바꾼다**(방장 키가 사라진 글을 그 자리에서 만료로 옮겨 적는다) — 1쪽에 나간 글이 2쪽에 또 나왔다.
   `id` 가 identity 라 **순증가 · 유일 · 불변**을 혼자 만족한다.
3. **감싸도 얻는 것이 없었다** — 서명하지 않아 보안 값이 0 이고, 글 번호는 응답의 `postId` 로 이미 다 나간다.
4. **보존 기간** — ① 옛 조건이 `status = RECRUITING or confirmed_at > ? or expired_at > ?` 라는 세 컬럼에 걸친 `OR` 이라 `(game, id DESC)` 인덱스를 깨끗하게 타지 못했다
   ② 끝난 글이 10분 만에 사라지면 **"모집이 얼마나 활발한가"를 보여 주지 못한다.**
5. **게시판은 게임별로 나뉜 페이지이고 "세 게임 전부" 화면이 없다** — 쓰지 않는 갈래가 게임 없이 훑는 쿼리 둘을 더 있게 만들었다. 남은 쿼리는 `game` 이 늘 등호라 인덱스를 그대로 탄다.

**감수하는 것.**

- **만료 · 확정된 글이 목록 위쪽에 섞여 나온다**(제자리다). 다만 글은 모집 중으로 태어나고 정렬이 최신순이라 1쪽은 대개 모집 중인 글이다.
- **행이 DB 에 영원히 쌓인다**(앱에 정리 작업이 없다).
- **만료 · 확정 옮겨 적기가 읽은 글에만 걸린다** — 목록 깊은 곳의 글은 누가 그 페이지를 볼 때 만료된다. 입장은 방의 Lua 가 방 키를 직접 보므로 "죽은 방에 들어가기"는 생기지 않는다.

**아직 미정.** 목록의 필터를 더 둘지(`status` 필터는 두지 않기로 했다) · 프런트가 재요청을 묶는 간격(D-20 · D-22 그대로).

**영향.** `app:platform` 안에서 끝난다 — 이 저장소가 고칠 코드는 없다. 세부(차단으로 숨겨진 글 때문에 모자라면 최대 3번 더 읽어 채우는 것 · `nextCursor` 는 마지막으로
"읽은" 줄 · 옛 커서와의 호환을 두지 않는 것 · 에러 글귀)는 Claude 가 정했다 — `../platform/contracts/platform-api.md` 참조.

---

### D-29. `app:platform` 이 `qm:gameconfig:*` 를 **읽어** 모집 글의 `mode` 와 게임 계정의 `tier` 를 검증한다 — 읽는 키는 둘, 쓰지 않는다, Redis 를 못 읽으면 통과시킨다 (#15 개정, 2026-09-24)

> **이 결정은 이 저장소에 직접 걸린다** — 값의 원본이 이 저장소의 `seed/gameconfig.redis` 이고 키 접두사의 원본이 `redisKeys/SharedKeys.GAMECONFIG_PREFIX` 다.
> 프로젝트 소유자가 정했다(2026-09-24). **사실의 원본은 `../platform/CLAUDE.md` §3.6 · `../platform/contracts/platform-api.md` "gameconfig 를 읽는 것" · P-16 이다.**
> **#15 의 본문은 고치지 않았다.**

**원안.** #15 는 gameconfig 를 **`app:matching` 의 모듈**로 두었고(`app:matching` — matching / gameconfig / common), `app:platform` 의 규칙은 "매칭 Redis 키(`qm:party:*` ·
`qm:user:*` · `qm:proposal:*` · `qm:lock:*` · `qm:gameconfig:*`) 접근 — **예외가 없다**"였다. 모집 글의 `mode` 는 30자까지의 자유 문자열(없어도 된다), 게임 계정의 `tier` 도 자유 입력이었다.

**결정.**

1. **`app:platform` 이 읽는 키는 둘뿐이다** — `qm:gameconfig:{GAME}:{MODE}`(모드별 설정 HASH — **`EXISTS` 만.** 내용은 읽지 않는다)와 `qm:gameconfig:{GAME}:tier`(티어 사다리 ZSET — **`ZSCORE`** 만).
   **`:tier-range:{MODE}` 는 읽지 않는다**(매칭의 판정 규칙이다). **쓰지 않고, seed 를 심지 않고, 이 앱을 HTTP 로 부르지 않는다.**
2. **모집 글의 `mode` 가 필수가 됐고** 그 게임의 gameconfig 에 있는 모드여야 한다(`PATCH` 에서 빈 문자열로 비우는 길도 없어졌다). **`tier` 는 값이 있을 때만** 그 게임의 사다리에 있어야 한다.
   거절은 400 `VALIDATION_FAILED`.
3. **Redis 를 못 읽으면 검증만 건너뛰고 통과시킨다(fail-open)** — WARN 한 줄. gameconfig 가 아예 안 심긴 Redis 도 통과시킨다(티어 사다리 키가 있는가로 가른다 — Claude 가 정한 세부).
4. **값의 목록을 `app:platform` 에 상수로 베껴 두지 않는다.**
5. **나머지 넷(`qm:party:*` · `qm:user:*` · `qm:proposal:*` · `qm:lock:*`)의 "예외가 없다"는 그대로다.**

**#15 와의 관계.** "gameconfig 는 `app:matching` 의 모듈이다" → **모듈(모드 설정을 정하고 · 심고 · 해석하는 것)은 그대로 `app:matching` 의 것이다.** 달라진 것은 **값이 있는지를
다른 앱도 읽는다**는 것 하나다.

**근거.**

1. **gameconfig 는 `app:matching` 이 쓰는 상태가 아니다.** seed 파일의 머리가 "이 파일이 MVP 의 사실상 원본(source of truth)이다 · 앱은 부팅 시 설정을 밀어넣지 않고 Redis 에서
   읽기만 한다"고 적었다 — **쓰는 앱이 없고 이 앱도 읽는 쪽이다.** 운영자가 배포 때 심는 공유 설정이라 여러 서비스가 읽어도 된다. **가르는 기준은 "바뀌는 계기가 사용자의 행동인가,
   운영자의 배포인가"다**(방 키 · 활성 요청 키는 앞쪽이라 여전히 남의 키다).
2. **자동 매칭이 게시판 방에 합류하는 길**(2026-09-23 — 방향만 정해졌다, 아래 "아직 미정")이 성립하려면 **글의 `mode` 와 매칭 요청의 `mode` 를 같은 이름으로 맞춰 봐야 한다.** 자유 문자열이면 판정할 수 없다.
3. fail-open 은 목록 조회가 이미 fail-open 인 것과 결을 맞춘 것이다.

**감수하는 것.**

- **Redis 가 죽은 동안에는 이상한 모드가 들어올 수 있다.**
- **이 저장소가 조심할 것이 생겼다.** `SharedKeys.GAMECONFIG_PREFIX` 나 seed 의 키 모양을 바꾸면 `app:platform` 의 검증이 **조용히 꺼진다**(fail-open 이라 에러도 안 난다 — 알림 채널 접두사보다
  더 조용하다). **바꿀 때는 `app:platform`(`common/gameconfig/GameConfigKeys`)과 같이 바꾼다.** seed 에 모드를 더하거나 지우는 것도 영향이 있다 — 없는 모드로는 모집 글 쓰기가 400 이다.

**아직 미정.** `recruit_posts.mode` 를 `NOT NULL` 로 조일지와 옛 글의 빈 `mode`(`app:platform` 의 일) · **자동 매칭이 조건 맞는 게시판 방에 먼저 합류하는 길**(2026-09-23 소유자 결정 —
**방향만이다.** 어느 앱의 어느 경로인가 · "조건이 맞는다"의 판정 · 방이 여럿일 때 · 활성 요청 키를 어떻게 다루는가(D-19) · ①이 실패했을 때 누가 대기열로 넘기는가가 전부 미정이다.
`HANDOFF.md` §0-4 (다) · `../platform/CLAUDE.md` §7).

**영향.** 이 저장소의 코드는 바뀌지 않는다. `CLAUDE.md` · `docs/GAME_CONFIG.md` 에 "`app:platform` 도 읽는다"를 적었다.

---

### D-30. "전적 갱신" 요청을 둔다 — 사용자가 누르는 **동기** 요청 · 같은 게임 계정은 2분에 한 번 · 상한 30초 (새 결정 · D-27 을 뒤집지 않는다, 2026-09-24)

> **이 결정은 매칭 엔진의 결정이 아니다.** 프로젝트 소유자가 정했다(2026-09-24). **사실의 원본은 `../platform/contracts/platform-api.md` "전적 갱신" · P-17 이다.**

**원안.** D-27 결정 2 로 전적을 긁는 시점이 "게임 계정을 연결 · 수정할 때" 하나가 되어, 그 뒤로는 전적이 낡은 채로 남았다.

**결정.**

1. **`POST /api/v1/users/me/game-accounts/{game}/refresh`**(로그인한 본인 것만)를 둔다. **동기다** — 다 긁을 때까지 기다렸다가 **200 + 갱신된 게임 프로필**을 준다.
2. **같은 게임 계정은 2분에 한 번**이다 — 429 + `Retry-After`. **이미 긁고 있을 때도 같은 429**다(D-27 의 락을 그대로 쓴다). **쿨타임은 긁기를 시작할 때 찍고 실패해도 소모된다.**
3. **상한은 30초** — 넘으면 요청만 503 으로 끊고 뒤에서 돌던 갱신은 그대로 둔다. 라이엇 실패 · 시간 초과 · 키 없음은 503 이고 **전적 줄을 건드리지 않는다**(옛 값이 남는다).
4. **신선도 장치(D-27 에서 없어진 30분)는 되살리지 않는다** — "최근에 긁었어도 사용자가 원하면 긁는다"가 요점이고 남용은 쿨타임이 막는다.

**D-27 과의 관계.** **D-27 을 뒤집지 않는다** — 모집 글을 쓸 때는 여전히 긁지 않는다. 긁는 시점이 **하나에서 둘**(게임 계정 저장 · 이 요청)로 늘었다.

**근거.** D-27 결정 2 로 낡은 채 남게 된 전적을 **사용자가 직접** 갱신하는 길이다. 실패만 무제한으로 다시 할 수 있으면 Riot 한도를 그대로 태운다(쿨타임이 실패에도 소모되는 이유).

**감수하는 것.** 저절로 갱신되지는 않는다 — 이 요청은 사용자가 누르는 것이라 D-27 의 "주기적 갱신" 미정을 닫지 않는다.

**영향.** `app:platform` 안에서 끝난다. 에러 코드 넷(`TOO_MANY_STATS_REFRESHES` · `GAME_ACCOUNT_NOT_FOUND` · `GAME_STATS_NOT_SUPPORTED`(VALORANT · PUBG 는 409) · `GAME_STATS_UNAVAILABLE`)과
쿨타임 키 `qm:riot:refresh:{gameAccountId}` · 30초를 재는 법은 Claude 가 정한 세부다(P-17).

---

### D-31. 게시판 글 한 줄에서 `filledPositions`(찾는 포지션 가운데 이미 방 안에 있는 것의 강조)를 없앤다 (D-20 ③ 개정, 2026-09-24)

> **이 결정은 매칭 엔진의 결정이 아니다.** 프로젝트 소유자가 정했다(2026-09-24). **사실의 원본은 `../platform/contracts/platform-api.md` "글 한 줄" · P-18 과 `../platform/CLAUDE.md` §7.1 이다.**
> **D-20 의 본문은 고치지 않았다.**

**원안.** D-20 은 목록의 한 줄이 넷을 보여 준다고 정했다 — ① 방 안에 몇 명인가 ② 방 안 사람들의 카드 ③ **글의 "찾는 포지션" 가운데 이미 방 안에 있는 포지션을 밝게 강조한다**
④ F5 없이 갱신된다. ③ 은 `wantedPositions` ∩ 방 안 사람들의 **주 포지션**으로 계산했다(D-20 (가) "포지션의 출처는 프로필의 주 포지션이다").

**결정.** **③ 을 없앤다 — 응답에서 `filledPositions` 칸을 뺀다.** **①②④ 는 그대로 유효하다.** 글의 `wantedPositions`(쓸 때 고르는 "찾는 포지션")와 그 검증, 카드의 주 포지션
(`profile.mainPosition`), 테이블은 그대로다(마이그레이션 없음).

**근거.** **주 포지션은 "내가 주로 하는 것"이지 "이 방에서 할 것"이 아니다.** 주 포지션이 정글인 사람이 미드를 구하는 방에 미드로 들어와도 계산은 미드가 **비었다고** 표시했다 —
**틀린 정보를 자신 있게 보여 주는 것**이라 없앴다.

**D-20 과의 관계.** D-20 결정 ③ 과 그에 딸린 설계 (가) 의 "강조"를 위한 대목 → **개정됐다.** (가) 의 "포지션의 출처는 프로필의 주 포지션이다 · 입장할 때 고르지 않는다"는 카드에
주 포지션을 보여 주는 근거로 그대로 남는다 — 없앤 것은 그것으로 자리가 찼는지를 **판단하는 것**이다.

**아직 미정.** **다시 둘 것인가** — 소유자가 "일단" 없앴다. **입장할 때 포지션을 고르게 하는 방식은 하지 않기로 이미 정해져 있다**(D-20 근거 1 — 방 키의 모양이 바뀐다). 다른 방식을 지어내지 않는다.

**영향.** `app:platform` 안에서 끝난다. 이 저장소가 고칠 코드는 없다.

---

### D-32. 방에 방장 말고 누가 있으면 모집 글을 고칠 수 없다 — 409 `ROOM_HAS_OTHER_MEMBERS` (새 결정, 2026-09-24)

> **이 결정은 매칭 엔진의 결정이 아니다.** 프로젝트 소유자가 정했다(2026-09-24). **사실의 원본은 `../platform/contracts/platform-api.md` "모집 글 · 목록" 의 `PATCH` · P-19 와 `../platform/CLAUDE.md` §7.1 이다.**
> **개정하는 D-항목은 없다** — D-11 · D-20 에 글 고치기 이야기가 없다.

**결정.**

1. **방에 방장 말고 누가 있으면 `PATCH /api/v1/posts/{postId}` 는 409 `ROOM_HAS_OTHER_MEMBERS` 다** — 칸을 가리지 않는다(`title` 만 고치는 것도 막는다). 방장 혼자면 고칠 수 있다.
2. **방 안을 못 읽으면 막는다** — 503 `ROOM_STATE_UNAVAILABLE`(fail-closed).
3. **막는 것은 `PATCH` 하나다** — 지우기 · 입장 · 방장 확정 · 조회는 그대로다.

**근거.** 고칠 수 있는 칸에 `mode` · `voice` · `purpose` · `conditions` 가 있다 — `NO_VOICE` 를 보고 들어와 앉아 있는 사람 앞에서 `REQUIRED` 로 바꿀 수 있는데
**방 안 사람에게 바뀌었다고 알려 줄 길이 없다**(게시판 신호는 목록을 보는 사람에게 가고 `ROOM_*` 알림에 "글이 바뀌었다"가 없다).

**감수하는 것.** 방 키를 글의 줄을 잠그는 트랜잭션 **밖에서** 읽으므로 "읽은 뒤 저장하기 전"에 누가 들어오는 경쟁이 남는다(창이 짧다). 거르는 순서(400 → 403 → 409 `POST_NOT_RECRUITING` → 이 검사)는 Claude 가 정했다.

**아직 미정.** 방 안 사람에게 "글이 바뀌었다 · 지워졌다"를 알릴지 — 알림의 이름과 `payload` 가 없다(`PARTY_*` 가 통째로 미정이다).

**영향.** `app:platform` 안에서 끝난다.

---

### D-33. `app:room` 을 `app:platform` 에 합친다 — 방 안의 일이 `app:platform` 의 `room` 패키지가 되고, 글 쓰기가 방을 같이 만들며, 방장 확정은 한 요청이 Redis 와 DB 를 같이 쓰고, 확정 전에는 방과 글이 같이 산다 (D-16 되돌림 · D-19 ~ D-23 의 주어 개정 · D-21 결정 2 · 6 개정 · D-9 · D-11 6번 원래대로, 2026-09-25)

> **이 결정은 매칭 엔진의 결정이 아니다.** 시스템의 배포 단위 구성(#15 · D-16)을 다시 바꾼다. 프로젝트 소유자가 정했다(2026-09-25).
> **사실의 원본은 `../platform/contracts/platform-api.md` "방" · "모집 글 · 목록" · P-22 와 `../platform/CLAUDE.md` 머리 블록 · §3.3 이다.** 옛 `app:room` 의 폴더 `../room/`(`room` 브랜치)은
> **합치기 전의 기록**이라 읽기만 한다 — D-19 ~ D-23 이 가리키는 `../room/…` 경로는 그 기록이다. 되돌리기용 태그는 `pre-room-merge` 다.
> **D-9 · D-11 · D-16 · D-19 · D-20 · D-21 · D-22 · D-23 의 본문은 고치지 않았다** (이 파일은 기록이다).

**원안.** D-16 은 방을 별도 서비스 `app:room`(Redis 만, 포트 8083)으로 떼고, 두 앱을 **입장권**(`app:platform` 이 서명하고 `app:room` 이 검증)과 **`app:platform` 이 방 키를 읽기만
하는 것**으로 이었다. D-19 는 입장 표시 키를 `app:room` 이 쓰게 했고, D-20 은 목록을 `app:platform` 이 `app:room` 의 멤버 SET 을 읽어 조립하게 했다. D-21 은 방을 **"방 만들기" 요청**
(`POST /api/v1/rooms/{roomId}`)이 만들고, 방장 확정은 `app:room` 이 확정 표시 키를 쓰고 `app:platform` 은 그것을 읽어 기록하게 했다(브라우저가 확정 뒤 `POST /api/v1/posts/{postId}/confirm`
을 부르는 길 ① + 목록이 확정 표시 키를 보면 기록하는 길 ②). D-22 · D-23 은 두 앱이 각각 게시판 신호를 발행하는 것을 전제로 적혔다.

**결정.**

**(가) 합친다 — 1단계(옮겨서 돌게 하기).**

1. **방 안의 일(입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인과 방장 이탈 감지 · 방장 승계 · 시그널 `POST` 와 `WEBRTC_SIGNAL` · `ROOM_*` 알림 · 방 안 사람 목록 · 내 방 찾기)이
   `app:platform` 의 `room` 패키지**(`com.queuemate.platform.room`)가 된다. DB 가 없고 상태는 여전히 Redis 에만 있다. Lua 스크립트도 같이 왔다.
2. **경로는 그대로다**(`/api/v1/rooms/**`). **포트 8083 이 없어졌다**(전부 8082). **`?userId=` 가 없어지고 `qm_access` 쿠키의 사용자가 "나"다**(D-24).
3. **배포 단위는 다시 넷이다** — `app:matching` / `app:platform` / `app:realtime`(지금 이름은 `notification`) / `app:reservation`(Lambda, D-15). **`notification`(SSE 연결 보유)과
   `app:matching`(매칭 엔진)은 그대로 따로 둔다.**

**(나) 두 앱을 전제로 만든 경계 장치를 걷어낸다 — 2단계. 소유자 결정 셋.**

4. **① 입장권을 없앤다. 입장 경로(`POST /api/v1/rooms/{roomId}/members`)는 그대로 두고 그 안에서 글을 검사한다** — 글이 없거나 차단으로 숨겨진 글이면 404 `POST_NOT_FOUND`(상태보다 먼저),
   모집 중이 아니면 409 `POST_NOT_RECRUITING`, 그다음 방의 Lua. **차단 대조는 방장 + 그 순간 방 안 전원이다**(D-20 (라) 그대로).
5. **C 글 쓰기가 방을 같이 만든다.** `POST /api/v1/posts` 한 요청이 트랜잭션 안에서 글을 넣고(그 번호가 `roomId`) 커밋 전에 방 만들기 Lua 를 부른다. **Lua 가 거절하면 글도 되돌린다**
   (409 `ALREADY_QUEUED` · `IN_OTHER_ROOM` 등, Redis 에 못 닿으면 503). **방 만들기 요청(`POST /api/v1/rooms/{roomId}`)은 없어졌다.**
6. **방과 글은 같이 산다.** **확정 전에는** 방장이 나가면 글도 그 자리에서 만료되고, 방장이 글을 지우면 방도 닫힌다(`ROOM_CLOSED` · 남아 있던 전원의 입장 표시 키가 지워진다).
   **확정 뒤에는** 글은 `CONFIRMED` 로 고정이고 방은 방장이 나가도 승계된다(D-23 그대로).
7. 그래서 — **방장 확정은 `POST /api/v1/rooms/{roomId}/confirm` 한 요청이** 확정 Lua 와 글의 확정 · 파티원 기록(DB)을 같이 한다. **`POST /api/v1/posts/{postId}/confirm` 이 없어졌다.**
   커밋이 실패해 남은 "확정된 방인데 글은 모집 중"은 목록 · 단건이 확정 표시 키를 보면 그 자리에서 기록한다(옛 길 ② 가 **자가 치유**로 남았다).
8. **모집 중인 글에 방장 키가 없으면 무조건 만료다**("아직 안 만들어진 방"이 없어져 그것을 가르던 칸 `room_seen_at` 과 유예 설정이 없어졌다). 확정된 글은 방장 키가 없어도 만료시키지 않는다(D-23).
9. 게시판은 방 키를 Redis 로 직접 읽지 않고 같은 앱 안의 서비스(`RoomService#states`)를 부른다. 방 키의 원본 상수는 `../platform/…/room/redisKeys/RoomKeys.java` 하나다.
   에러 코드가 한 벌이 됐다(`INVALID_REQUEST` → `VALIDATION_FAILED`, `ROOM_UNAVAILABLE` → `ROOM_STATE_UNAVAILABLE`).

**(다) 이 저장소와의 약속(D-19)은 그대로다 — 주어만 바뀐다.**

10. **활성 요청 키 `qm:user:active-request:{userId}` 는 `app:matching` 이 쓰고 지우며 `app:platform` 은 `EXISTS` 만 한다**(글 쓰기 · 입장의 Lua 가 409 `ALREADY_QUEUED`).
    **입장 표시 키 `qm:user:active-room:{userId}` 는 `app:platform`(의 `room` 패키지)이 쓰고 지우며 `app:matching` 은 `EXISTS` 만 한다**(매칭 요청이 409 `IN_ROOM`).
    키 이름 · 자료형 · 값 · 수명 규칙은 바뀌지 않았다. 입장 표시 키 접두사의 원본 자리만 `../room/…/RoomKeys.java` 에서 **`../platform/backend/src/main/java/com/queuemate/platform/room/redisKeys/RoomKeys.java`** 로 옮겨 갔다.

**옛 항목과의 관계.**

- **D-16** — **분리 결정 자체가 되돌려졌다.** "`app:room` 은 Redis 만" · "배포 단위는 다섯" · "두 앱을 잇는 방법"(입장권 · 방 키 읽기 · 확정 순간의 경쟁) · 입장권 서명 키를 두 앱이 나눠
  갖는 것 · 포트 8083 제안 · "`app:room` 을 Lambda 로 시작하지 않는다" · "두 앱이 Redis 키 형식을 약속해야 한다(세 번째 공유 약속)"가 전부 낡았다. D-16 이 #26 을 두고 한 분석
  ("파티의 오래 남는 부분은 `app:platform`, 사라지는 부분만 뗀다")도 합치며 뜻을 잃었다. **남는 것은 "오래 남는 것은 PostgreSQL, 금방 사라지는 것은 Redis"라는 저장소 구분뿐이다 — 이제 한 앱 안의 패키지 구분이다.**
- **D-9** "시그널 `POST` 는 `app:platform` 이 받는다" · **D-11 6번** "이 기능은 `app:platform`(파티 모듈)에 들어간다" → D-16 이 `app:room` 으로 읽으라고 했던 것이 **다시 원문 그대로 맞다**(`app:platform` 의 `room` 패키지).
- **D-19** → 규칙은 그대로이고 `app:room` 은 `app:platform` 으로 읽는다(결정 10). 결정 4 의 원본 경로가 바뀌었다. 결정 2 의 "`app:room` 의 입장 Lua" 는 `app:platform` 의 입장 · **글 쓰기** Lua 다.
- **D-20** → 목록의 모양 · 차단 범위 · 게시판 신호는 그대로다. "목록은 `app:platform` 이 `app:room` 의 멤버 SET 을 읽어 조립한다" · "(나) 검토한 대안 셋(API 호출 · 이벤트로 사본 유지 · 프런트 조합)"은
  물음째 없어졌다(같은 앱 안의 호출이다). "입장권을 내줄 때도 같은 차단 규칙" → 입장 요청 안의 글 검사다. **③ 은 D-31 로 따로 없어졌다.**
- **D-21** → 결정 2(방은 "방 만들기" 요청이 만든다) → **글 쓰기가 만든다.** 결정 6(방장 확정 — `app:room` 이 확정 표시 키를 쓰고 `app:platform` 은 읽어 기록) → **한 요청이 둘 다 한다.**
  결정 1 의 "원본 상수는 `app:room` 의 `RoomKeys` · `app:platform` 이 읽는다 · 두 앱의 약속" → 한 앱 안의 상수다. 결정 10 의 "거절은 서비스의 결과 enum" · 공통 에러(`INVALID_REQUEST` 등) → 에러 코드 한 벌.
  방 키의 이름 · 수명 600초 · 방장의 접속 확인 · 강퇴 · 조회 둘 · 알림의 이름과 `payload` · 시그널은 그대로다.
- **D-22** → 채널 하나 · `{}` · `topics` 없음 · 클라이언트가 거른다는 그대로다. 발행하는 "두 앱"이 한 앱의 두 패키지(`party` · `room`)가 됐고, `app:room` 의 채널 상수 사본(`SharedKeys.BOARD_CHANNEL`)은
  없어졌다 — 같은 값을 적어 둔 곳은 이제 `app:platform`(`party/board/BoardChannels`)과 `notification` 둘이다(원본을 어디 둘지는 여전히 미정).
- **D-23** → 방장 승계 · 발행 시점은 그대로다. "`app:platform` 은 확정된 글을 '방장 키가 없다' 하나로 곧바로 만료시키면 안 된다 — 어떻게 가를지는 미정"은
  **정해졌다**(확정된 글은 방장 키가 없어도 만료시키지 않는다 · 확정 표시 키가 있으면 확정으로 기록한다 — 결정 7 · 8).
- **D-13** "`PartyClosed.fifo` 의 소비자는 `app:platform` 하나" → 그대로다.

**근거.** (`../platform/CLAUDE.md` §3.3 "회고" · P-22)

1. **목록을 그릴 때마다 게시판이 방의 Redis 를 읽어야 했다** — 서로 chatty 하면 같은 서비스라는 기준.
2. **확정 · 방 키 · 입장권을 두 앱이 같이 바꿔야 했다**(design-time coupling).
3. **팀이 한 사람이다.** 먼저 한 덩어리로 만들고 경계가 드러나면 가른다.
4. **입장권(Valet Key)은 나눠 놨을 때의 표준이었지 나눠야 할 이유는 아니었다.**

**감수하는 것.**

- D-16 이 든 분리의 이득(부하 모양이 다른 방을 따로 스케일 · 방 쪽 장애가 로그인 · 게시판으로 번지지 않는 것)을 내려놓는다.
- **"트랜잭션 안에서 Redis 를 기다리지 않는다"의 예외가 둘 생겼다** — 글 쓰기와 방장 확정(Lua 한 번 · 밀리초). 글 쓰기의 Lua 성공 뒤 커밋이 실패하면 고아 방이 수명(600초)으로 죽는 것을 감수한다.
- 입장 요청 안의 글 검사와 방의 Lua 사이는 원자적이지 않다 — 그 사이에 나와 차단 관계인 사람이 먼저 들어오는 경쟁(창은 밀리초).

**아직 미정 — 임의로 지어내지 않는다.**

- **자동 매칭으로 확정된 파티의 방을 어떻게 만드는가** — 방 만들기 요청이 없어졌고 입장이 글을 검사하므로 **새로 정해야 한다**(글이 없는 파티방은 지금 들어갈 길이 없다).
- **`status=PARTY` 해제**(`HANDOFF.md` §0-1 ①) — 그대로 미정이다. 확정된 사용자는 활성 요청 키가 남아 방 입장이 409 `ALREADY_QUEUED` 로 거절된다.
- 강퇴당한 사람의 재입장 · 게시판 채널 이름의 원본을 둘 곳 — 그대로다.
- 이 합치기의 세부(창구 이름 · 검사 순서 · 자가 치유 · 에러 코드 통일 · 트랜잭션 안 Lua 예외)는 **Claude 가 정했고 소유자가 항목별로 검토하지 않았다**(P-22).

**영향.**

- **이 저장소(`app:matching`)의 코드는 바뀌지 않는다.** `claim-request.lua` 는 입장 표시 키를 여전히 `EXISTS` 로 본다(키 이름이 그대로다). `SharedKeys.ACTIVE_ROOM_PREFIX` 의 짝(원본)이
  `../platform/…/room/redisKeys/RoomKeys.ACTIVE_ROOM_PREFIX` 가 됐다 — 바꿀 때는 `app:platform` 과 같이 바꾼다.
- `WEBRTC_SIGNAL` · `ROOM_*` 의 발행 주체가 `app:platform` 이 됐다(`contracts/events.md` · `contracts/README.md` 를 맞췄다).
- 이 저장소의 `CLAUDE.md` · `START_HERE.md` · `docs/AWS_ARCHITECTURE.md` · `docs/CONCURRENCY_TESTS.md` 의 "`app:room`" 서술에 이 항목을 가리키는 말을 달았다. `HANDOFF.md` §0 의 옛 서술(§0-1 ① 의 인용 등)은 그날의 기록으로 두었다.

---

### D-34. DB 스키마를 `public` 하나로 합치고 테이블 사이의 JOIN · FK 를 허용한다. 스키마별 DB 롤은 없다 — `app:matching` 이 읽는 테이블은 `blocks` 하나 그대로다 (#17 개정 · D-1 개정, 2026-09-22 · 2026-09-26)

> **이 결정은 이 저장소에 직접 걸린다** — `block/Block.java` 가 읽는 테이블의 이름이 바뀌었다. 프로젝트 소유자가 정했다.
> **사실의 원본은 `../platform/CLAUDE.md` §3.5 · `../platform/contracts/platform-api.md` P-23 과 `../platform/backend/src/main/resources/db/migration/V1__schema.sql` 이다.**
> 2026-09-22 의 "스키마별 DB 롤을 두지 않는다"(소유자 결정)를 이 항목에 접었다 — 2026-09-26 에 스키마가 하나가 되며 그 물음이 여기에 흡수됐다(`app:platform` 도 "P-23 하나로 올리면 된다"고 적었다).
> **#17 · D-1 · D-3 의 본문은 고치지 않았다** (이 파일은 기록이다).

**원안.** #17 은 PostgreSQL 인스턴스 1개에 **schema-per-service**(`account` `gameconfig` `matching` `reservation` `party` `social` `shared_read`)를 두고 **크로스 스키마 FK · JOIN 금지 ·
스키마별 DB 롤로 권한 격리 · `db/migration/<schema>/` 로 마이그레이션 분리**를 정했다. D-1 은 뷰 대신 **`matching` 롤에 `social.blocks` 의 SELECT 권한을 직접 준다 — 스키마 분리와
크로스 스키마 금지는 유지하며 예외는 이 테이블 하나뿐**이라고 정했다.

**결정.**

1. **(2026-09-22) 스키마별 DB 롤을 두지 않는다.** 앱 하나가 DB 계정 하나로 붙는다. `qm_matching` 롤도 `GRANT` 도 없다 — `app:matching` 은 별도 롤 없이 `blocks` 를 읽는다.
   (그날은 "스키마 분리 · 크로스 스키마 FK/JOIN 금지는 그대로"였다 — 아래 2 로 낡았다.)
2. **(2026-09-26) `app:platform` 의 스키마 셋(`account` · `social` · `party`)을 `public` 하나로 합친다.** 테이블 이름은 그대로다(14개 — `users` · … · `blocks` · … · `party_members`).
3. **테이블 사이의 JOIN · FK 를 허용한다.** 사용자 번호를 담는 칸 전부에 **`users(id)` FK `ON DELETE CASCADE`**, `recent_players.last_party_id` → `parties(id)` 는 `ON DELETE SET NULL`.
   없는 사용자는 FK 위반으로 404 `USER_NOT_FOUND` 가 된다.
4. **마이그레이션은 `V1__schema.sql` 하나로 다시 썼다**(옛 V1 ~ V8 의 최종 모양 + 위 둘). 운영 DB 가 없고 로컬 · 테스트 DB 가 `--rm` 컨테이너라 매번 빈 채로 뜨기 때문이다.
   **"이미 적용된 마이그레이션은 고치지 않는다"는 운영 DB 가 생긴 뒤부터 다시 걸린다.**
5. **`app:matching` 이 읽는 테이블은 `blocks` 하나라는 약속은 그대로다** — 이름이 `social.blocks` 에서 **`public.blocks`** 로 바뀌었을 뿐이고 컬럼(`id` · `blocker_id` · `blocked_id`)은 그대로다
   (두 칸이 bigint 인 것은 D-25). 권한이 아니라 약속으로 지킨다. 뷰(`shared_read.blocked_pairs`)는 여전히 만들지 않는다.
6. **패키지 나누기(`account` · `social` · `party` · `room` · `common`)는 그대로다** — 바뀐 것은 DB 쪽이다.

**#17 · D-1 · D-3 과의 관계.**

- **#17** — schema-per-service · 크로스 스키마 FK/JOIN 금지 · 스키마별 롤 · `db/migration/<schema>/` → **전부 낡았다**(`app:platform` 에 대해). 인스턴스 1개를 여러 앱이 같이 쓰는
  절충(database-per-service 가 아니다)만 남는다. `reservation` 의 테이블은 `app:platform` 의 것이 아니다(D-15) — 그 마이그레이션을 누가 실행하는지는 여전히 미정이다.
- **D-1** — "`matching` 롤에 SELECT 권한을 준다" · "스키마 분리와 크로스 스키마 금지는 유지" → **낡았다.** "뷰를 만들지 않고 테이블을 직접 양방향으로 조회한다"는 그대로다.
- **D-3** — "H2 로는 스키마별 롤 · GRANT 격리를 재현할 수 없다 · 권한 경계는 PostgreSQL 에서만 검증된다" → 격리할 롤이 없어 물음째 없어졌다.
- **`docs/WHY_POSTGRESQL.md` §3 의 스키마 배치** → 낡았다(그 문서 머리에 표시했다).

**근거.** (`../platform/CLAUDE.md` §3.5) **DB 를 보는 앱이 사실상 `app:platform` 하나다**(`app:matching` 은 `blocks` 를 읽는 것 하나뿐). 그런데 스키마를 나누고 JOIN · FK 를 막은 탓에
코드가 쓸데없이 복잡했다 — 닉네임을 따로 읽어 자바에서 정렬하고, 사용자가 있는지를 앱이 조회로 확인하고(FK 를 못 걸어서), 도메인 사이에 "읽는 창구"를 두었다.

**감수하는 것.** 나중에 DB 를 물리적으로 나눌 때 뽑아낼 경계가 스키마로 표시돼 있지 않다(패키지로만 남는다). `blocks` 의 모양을 바꾸면 이 저장소가 런타임에 깨진다는 결합(D-1 "영향")은 그대로다.

**아직 미정.** 운영에서 앱이 붙는 DB 계정의 이름과 권한 · 마이그레이션 계정과 앱 계정을 나눌지(`app:matching` 의 계정도 같다) · outbox 테이블을 몇 개 둘지(`app:platform` 6단계).

**영향.**

- **이 저장소 — 코드를 고쳐야 한다. 아직 안 고쳤다**(`HANDOFF.md` §0-4 (가)). **`block/Block.java` 의 `@Table(schema = "social", name = "blocks")` 에서 `schema` 를 뺀다** —
  안 빼면 D-25 의 `Long` 으로 고쳐도 "relation social.blocks does not exist" 로 똑같이 깨진다. javadoc 의 "이 테이블만 `matching` 롤에 SELECT 권한을 준다(D-1)"도 낡았다.
  테스트의 `backend/src/test/resources/schema.sql` 도 스키마 이름을 따라 맞춘다.
- 이 저장소의 `CLAUDE.md` · `docs/WHY_POSTGRESQL.md` · `START_HERE.md` · `docs/AWS_ARCHITECTURE.md` 등의 `social.blocks` · 스키마별 롤 서술에 이 항목을 가리키는 말을 달았다. `HANDOFF.md` §0 의 옛 서술은 그날의 기록으로 두었다.

### D-35. 가입 · 로그인은 소셜(카카오 · 디스코드)뿐이다 — 직접 가입 · 비밀번호 로그인 · 로그인 아이디(`loginId`)를 없앤다 (D-25 의 절반 개정 · D-24 의 로그인 실패 제한 물음째 없어짐, 2026-09-26)

> **프로젝트 소유자가 정했다.** 사실의 원본은 `../platform/CLAUDE.md` §2 "계정" · §5.1 과 `../platform/contracts/platform-api.md` "계정" · "소셜 로그인" · P-24 다.
> **이 저장소에는 코드로 걸리지 않는다** — JWT 의 `sub` 는 여전히 사용자 번호(숫자 문자열)이고 서명 · 검증은 D-24 그대로다.

**원안.** `docs/00_PRODUCT_SPEC.md` 의 계정은 "회원가입 / 로그인" 이었고, `app:platform` 은 로그인 아이디 + 비밀번호(bcrypt · `credentials` 테이블)로 가입 · 로그인하는 길과 소셜 로그인의 길을 둘 다 두었다.
D-25 는 사용자의 식별자를 **사용자 번호(`userId`)와 로그인 아이디(`loginId`)** 둘로 갈랐고, D-24 는 로그인 실패 제한(15분 5회 · 두 배 잠금 · Redis `qm:auth:login-fail:*`)을 정했다.

**결정.**

1. **가입 · 로그인은 소셜로만 한다.** `POST /api/v1/auth/signup` · `POST /api/v1/auth/login` · `credentials` 테이블 · 비밀번호가 없어졌다.
   **왜** — 비밀번호 보관 · 이메일 인증 · 로그인 실패 제한 같은 부담을 우리가 지지 않는다. 그 몫은 카카오 · 디스코드가 진다.
2. **`loginId` 를 없앤다.** 비밀번호 로그인이 없으면 쓰는 데가 없다 — 친구 요청도 사용자 번호로 한다. `users.login_id` 컬럼 · 응답의 `loginId` · 409 `LOGIN_ID_TAKEN` 이 없어졌다.
   **식별자는 사용자 번호 하나, 보여 주는 이름은 닉네임 하나다.** D-25 의 "사용자 번호는 bigint identity" 는 그대로이고 "로그인 아이디를 따로 둔다" 절반만 낡았다.
3. **소셜로 처음 온 사람은 닉네임만 정한다.** 가입 대기 한 단계(`social_signup` 토큰 · `/social/pending` → `POST /social/signup`)는 그대로이고 본문이 `{nickname}` 하나다(닉네임이 UNIQUE 라 제공자 이름을 그대로 못 쓴다).
4. **로그인 실패 제한은 물음째 없어졌다** — 비밀번호 로그인에만 걸리던 것이다. D-24 의 그 대목과 Redis 키 둘이 낡았다. 나머지(RS256 · `Origin` 검사 · denylist 없음 · `token_use` · refresh 토큰 D-26)는 그대로다.

**감수하는 것.** 카카오 · 디스코드의 앱 등록 · 키 · Redirect URI 등록이 **필수**가 됐다 — 그것 없이는 아무도 로그인할 수 없다(소유자가 해야 한다). 제공자가 죽으면 로그인이 같이 죽는다.

**아직 미정.** 이미 가입한 계정에 소셜 계정을 나중에 잇기 · 끊기(한 사용자에 제공자 둘). 회원 탈퇴(유예).

**영향.** `docs/00_PRODUCT_SPEC.md` 의 "회원가입" 계정 정의가 "소셜 로그인" 으로 읽힌다. 이 저장소의 코드 · 키 약속은 바뀌지 않는다.

### D-36. 게시판 파티는 확정된 방이 없어질 때 닫힌다 — 그 순간 최근 함께한 사람을 적는다. `PartyClosed.fifo` 는 게시판 파티에 필요 없어졌다 (D-13 의 절반 개정 · #21 의 `PartyClosed.fifo` 용도 좁힘, 2026-09-26)

> **프로젝트 소유자가 정했다.** 사실의 원본은 `../platform/CLAUDE.md` §3.3 "파티 닫힘" · `../platform/contracts/platform-api.md` "파티 닫힘" · P-25 다.
> **이 저장소에는 코드로 걸리지 않는다** — 자동 매칭 파티(`ProposalConfirmed.fifo` → `app:platform`)는 여전히 6단계의 일이고, 그 파티의 닫힘도 그때 정한다.

**원안.** #21 은 파티가 닫히면 `app:platform` 이 `PartyClosed.fifo` 를 발행하고, D-13 은 그 큐의 소비자를 `app:platform` 하나로 두어 닫힌 파티의 멤버로 `social.recent_players` 를 만든다고 정했다.
그런데 **"파티가 닫혔다" 를 무엇으로 판단하는가가 어느 문서에도 없어** `parties.status` 가 `ACTIVE` 로 박힌 채 아무도 바꾸지 않았고, `recent_players` 를 채우는 주체가 없어 최근 함께한 사람이 늘 빈 목록이었다.

**결정.**

1. **게시판 파티는 확정된 방(`qm:room:{roomId}:confirmed` 가 있는 방)이 없어질 때 닫힌다** — `parties.status = 'CLOSED'` · `closed_at`. 확정 전에 방이 없어지면 파티가 없으니 글만 만료된다(D-33 그대로).
2. **닫히는 그 순간 `party_members` 의 사람끼리 서로를 `recent_players` 에 적는다**(방향마다 한 줄 · 다시 만나면 `last_party_id` · `last_played_at` 만 갱신). 파티원이 1명이면 적을 쌍이 없다.
3. **길이 둘이다.** ① 마지막 사람이 나가기 · 접속 확인으로 방 키 셋이 지워질 때 그 자리에서 ② 전원이 말없이 사라져 키가 수명(600초)으로 없어진 경우는 목록 · 단건이 방 키를 읽다가 **방장 키 · 멤버 SET · 확정 키가 전부 없는 것**을 보고(방장 키만 없는 것은 D-23 의 승계 중이다). 조건부 UPDATE 라 두 길이 겹쳐도 한 번이다 — `app:platform` 의 관례("불변식은 DB 가 강제한다").
4. **`PartyClosed.fifo` 는 게시판 파티에 쓰지 않는다** — 방 · 글 · 파티 · 최근 함께한 사람이 전부 `app:platform` 한 앱 안에 있어(D-33 · D-34) 큐를 거칠 이유가 없다. **자동 매칭 파티의 닫힘과 그 큐는 6단계에서 다시 본다.**
5. 글은 `CONFIRMED` 그대로이고 응답에 새 칸은 없다. `PARTY_*` 알림은 여전히 미정이고 내지 않는다.

**D-13 · #21 과의 관계.** D-13 의 "소비자는 `app:platform` 하나" 는 자동 매칭 파티에 대해서만 남는다. #21 의 큐 셋 가운데 `BlockChanged.fifo` 는 D-12 로, `PartyClosed.fifo` 는 이 항목으로 용도가 좁혀졌다 — 남은 것은 `ProposalConfirmed.fifo` 하나다.

**감수하는 것.** 확정한 방에서 방장이 말없이 사라지고 남은 사람이 승계 전에 모두 나가기를 누른 경우는 확정 키가 남아 곧바로 닫히지 않는다 — 수명이 다한 뒤 길 ② 가 닫는다(Lua 를 고치지 않으려는 선택). 차단 관계인 사람도 `recent_players` 에는 적힌다 — 읽을 때 거른다.

**아직 미정.** 자동 매칭 파티의 닫힘(6단계) · 확정된 방의 기능(Ready 등).

### D-37. LoL 게임 계정은 이름#태그만 받고 티어 · 주 포지션을 Riot 에서 채운다 — 저장 전에 동기로 긁는다 (D-27 의 "커밋 뒤 비동기" 개정, 2026-09-27)

> **프로젝트 소유자가 정했다.** 사실의 원본은 `../platform/contracts/platform-api.md` "게임 프로필" · "전적을 긁는 것" · P-26 이다.
> **이 저장소에는 코드로 걸리지 않는다** — 이 앱이 보는 티어는 여전히 매칭 요청에 실려 오는 값이고(`matching/CLAUDE.md` §2 "자기신고"), 그 값의 출처가 `app:platform` 쪽에서 LoL 만 Riot 으로 바뀌었다.

**원안.** D-27 은 게임 계정을 **자기신고**(게임 닉네임 · 티어 · 주 포지션 · PUBG 의 서버)로 받고, 저장이 커밋된 **뒤에 비동기로** Riot 에서 전적(`stats`)만 긁는다고 정했다. 그런데 `LolStatsProvider` 가 `league-v4` 의 티어와 `match-v5` 의 `teamPosition` 을 이미 받아 오면서 **저장은 하지 않았다** — 계정(09-21)이 Riot 연동(09-23)보다 먼저 만들어져 요청 모양을 안 고친 것이다.

**결정.**

1. **LoL 의 `PUT /api/v1/users/me/game-accounts/LOL` 본문은 `{gameNickname}`(이름#태그) 하나다.** `tier` · `mainPosition` · `server` 를 보내면 400.
2. **저장 전에 동기로 긁는다**(상한 30초 — 전적 갱신과 같은 길). `tier` 는 `league-v4` 솔로랭크에서(gameconfig 사다리의 이름으로 옮긴다 · 언랭이면 `null`), `mainPosition` 은 최근 경기에서 가장 많이 간 포지션에서. 응답에 `tier` · `mainPosition` · `stats` 가 바로 들어 있다.
3. 이름#태그가 Riot 에 없으면 **404 `RIOT_ID_NOT_FOUND`**, Riot 이 죽었다 · 시간 초과 · 키 없음이면 503 — 둘 다 저장하지 않는다(연동이 안 된 것이다).
4. **`POST …/LOL/refresh`(전적 갱신)도 티어 · 포지션을 같이 갱신한다.**
5. **VALORANT · PUBG 는 API 가 없어 자기신고 그대로다.** 게임 계정 저장 뒤의 비동기 긁기는 없어졌다(LoL 은 동기, 다른 둘은 긁을 것이 없다).
6. D-20 의 "포지션의 출처는 프로필의 주 포지션" 은 그대로다 — LoL 의 그 값이 자기신고에서 Riot 으로 바뀌었을 뿐이다.

**감수하는 것.** LoL 계정 연결이 Riot 의 응답 시간(수 초)을 기다린다 · Riot 이 죽어 있으면 LoL 계정을 연결할 수 없다 · `verified` 는 여전히 켜지 않는다(식별자를 알아낸 것은 본인 확인이 아니다).

**아직 미정.** VALORANT · PUBG 의 API · 주기적 갱신 · RSO 로 `verified` 를 켜는 법(D-27 그대로).

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

### Q-4. VALORANT 가 끝까지 들어왔다 — Q-2 의 "없는 것" 이 해소됐다

Q-2 가 "아직 없는 것" 으로 적은 다섯 가지(티어를 안 보는 모드의 Lua, 취소 Lua, `rule/valorant`,
`config/redis/valorant`, 시드의 VALORANT 항목)가 전부 들어왔다. `redis/valorant/` 는 5개
(배정 4 + 취소 1), `rule/valorant/` 는 `Valorant*` 6개, `config/redis/valorant/ValorantRedisConfig`,
시드는 모드 4 / 티어 사다리 26 / tier-range 표 2다. 동시성 테스트도
`concurrency/ValorantPartyJoinConcurrencyTest` 하나가 생겼다 — D-7 의 대가("게임마다 불변식
테스트가 있어야 한다")가 이제 **PUBG 에만** 밀려 있다.

Q-3 의 표 두 줄도 낡았다. `qm:party:needs-roles:{partyId}` 는 취소가 되돌리고
(`valorant/leave-party.lua` 가 빠진 사람의 역할군을 `SADD` 한다), 파티 HASH 의
`minTier`/`maxTier` 는 **읽는 곳이 생겼다** — `join-party-tiered.lua` 가 읽어 범위를 좁히고,
취소가 남은 사람 기준으로 다시 적는다.

### Q-5. 제안 만료가 구현됐다 — INV-5 의 expired 갈래가 막혔다

O-4 / P-5 가 "만료 sweeper 가 없다" 로 적은 것이 해소됐다. `expiresAt` 을 읽는 주체가 생겼다.

| 무엇 | 어디 |
|---|---|
| 진행 중인 제안 목록 | **새 키 `qm:proposal:pending` ZSET.** member = partyId, score = `expiresAt` |
| 넣는 자리 | 합류 스크립트 6개(`{lol,pubg,valorant}/join-party.lua` · `join-party-tiered.lua`)의 `HSETNX status 'PENDING'` **성공 분기 안**. 분기 밖에 두면 재시도가 score 를 미래로 밀어 파티 HASH 의 `expiresAt` 과 어긋난다 |
| 빼는 자리 | 제안이 끝나는 **모든** 곳 — `accept-proposal.lua`(확정 분기) · `decline-proposal.lua` · `{lol,pubg,valorant}/leave-party.lua` · `proposal/expiry-proposal.lua` |
| 꺼내는 쪽 | `service/ProposalSweeper`(`@Scheduled(fixedDelay = queuemate.sweep.interval-ms)`, 한 회차 100건, 파티별 try/catch, Redis 장애 로그는 30초 억제). `MatchingApplication` 에 `@EnableScheduling` 을 붙였고 별도 설정 클래스는 두지 않았다 |
| 실제 정리 | `redis/proposal/expiry-proposal.lua` — `status ~= 'PENDING'` 이면 pending 에서만 빼고 **빈 목록**(그사이 확정된 제안을 만료가 뒤집지 못한다). PENDING 이면 `status`/`expiresAt` HDEL + 수락자 SET DEL + ZREM 하고 `{무응답자 {userId, requestId} 쌍, 수락자 userId}` 를 돌려준다 |

**정책(B안).** 만료되면 **수락하지 않은 사람만** 큐에서 뺀다(`MatchCancelService#cancel`).
수락한 사람은 파티에 남아 다시 기다리고, 옛 수락 기록이 지워지므로 빈자리가 채워져 제안이
새로 열리면 **다시 눌러야 한다** — 그 사람은 새 멤버를 본 적이 없기 때문이다.
`MATCH_PROPOSAL_EXPIRED` 는 무응답자와 수락자를 **가리지 않고** 그 제안에 있던 전원에게 나간다
(수락자가 못 받으면 제안 화면에 갇힌다).

**남는 창이 하나 있다.** `accept-proposal.lua` 는 여전히 `expiresAt` 을 보지 않는다. 시한이
지나고 스위퍼가 그 파티를 꺼내기 전(주기 기본 1초)에 도착한 수락은 그대로 확정된다. 스크립트
안에서 시한을 보게 하려면 분기를 하나 더 넣어야 한다.

**취소 갈래(INV-5 ④)도 같이 막혔다.** `{lol,pubg,valorant}/leave-party.lua` 가 멤버를 빼기
**전에** `status`/`expiresAt` HDEL + 수락자 SET DEL + pending ZREM 을 한다. 전에는 `PENDING`
상태와 취소자의 옛 수락이 남아, 새로 합류한 사람의 수락 하나로 `SCARD` 가 `target` 에 닿을 수
있었다.

### Q-6. 확정 후속 처리의 절반이 붙었다 — 활성 요청을 **지우지 않고** 표시한다

O-4 / P-5 가 "확정 뒤가 없다" 로 적은 것 중 Redis 쪽 뒷정리와 알림이 들어왔다.
`service/ProposalService#accept()` 가 확정 직후 새 스크립트
`redis/proposal/cleanup-confirmed.lua` 를 한 번 부른다.

- **활성 요청을 지우지 않는다.** 지우면 그 순간 새 매칭을 걸 수 있어 한 사람이 두 파티에 속한다
  (INV-2). 그래서 지우는 대신 `status = 'PARTY'` 를 찍어 "파티 중"으로 표시하고 INV-1 선점을
  유지한다. **이 상태를 푸는 것은 이 앱이 아니다** — 파티를 닫는 `app:platform` 이 `PartyClosed`
  를 발행하면 그때 풀어야 하고, 그 소비는 **아직 없다.**
- 파티 HASH 는 남긴다(상태 조회와 수락 재전송이 읽는다). 수락자 SET 에만 TTL
  (`queuemate.proposal.confirmed-retention-seconds`, 기본 60초)을 건다.
- 확정을 찍는 `accept-proposal.lua` 와 **합치지 않았다.** 그 스크립트는 수락자 집합을 다시 세는
  것으로 멱등성을 얻는데, 거기서 같이 지우면 재시도가 셀 근거를 잃는다. 정리 스크립트는 스스로
  `status == 'CONFIRMED'` 를 확인하므로 몇 번 불려도 결과가 같다.
- `MATCH_CONFIRMED`(payload `{partyId}`)는 그 스크립트가 돌려준 파티원 전원에게 나간다.
  **확정을 만든 그 한 번의 호출에서만** 나가는 것은 `accept-proposal.lua` 가 이미 확정된
  제안의 재수락에 `CONFIRMED` 가 아니라 `ALREADY_RESPONDED` 를 돌려주기 때문이다. 컨트롤러는
  수락 분기에서 그 값도 **204** 로 받는다 — 같은 명령을 두 번 보내 결과가 같으면 실패가 아니다
  (거절 분기의 `ALREADY_RESPONDED` 는 409 그대로다).

**아직 없는 것**: `matching.outbox` 기록과 `ProposalConfirmed.fifo` 발행. 그래서 파티가 DB 에
만들어지지 않는다(#21 / #27 이 정한 경로 그대로 비어 있다). `PartyClosed` 소비도 없고,
`GET /api/v1/match-requests/{requestId}` 는 여전히 **501** 이다(P-5 의 마지막 줄은 유효하다).

### 점검 방법

`backend/src/main/resources/redis/` 의 Lua 15개와 `seed/gameconfig.redis` 를 직접 읽고
문서와 대조했다. **코드는 건드리지 않았고 빌드·테스트도 돌리지 않았다** — 고친 것은 문서뿐이다.

(Q-4~Q-6 은 그 뒤 같은 날 다시 대조한 것이다. 그때는 Lua 가 **20개**였다 —
`shared` 1 / `lol` 5 / `pubg` 5 / `valorant` 5 / `proposal` 4. 새로 생긴 `proposal/` 의 둘은
`expiry-proposal.lua` 와 `cleanup-confirmed.lua` 이고, 뒤엣것은 아직 커밋되지 않은 작업 트리에
있다. 이번에도 코드는 건드리지 않았다.)

---

## 문서 정합성 점검 기록 (2026-09-17)

Q-1~Q-6 뒤에 코드가 또 바뀌었다. **Q 는 기록이라 지우지 않고 여기에 갱신분만 적는다.**
겹치는 항목은 이쪽이 우선한다.

### R-1. 상태 조회가 구현됐다 — 그런데 **계약과 경로가 다르다**

P-5 / Q-6 의 마지막 줄("`GET /api/v1/match-requests/{requestId}` 는 여전히 501")이 해소됐다.
`controller/MatchingController#getMatchRequest` + `service/MatchQueryService` 가 들어왔고
501 스텁은 없어졌다(`grep -rn NOT_IMPLEMENTED backend/src/` 0건).

**다만 경로가 원본 계약과 다르다.**

| | 계약 | 구현 |
|---|---|---|
| 경로 | `GET /match-requests/{requestId}` | `GET /match-requests?userId=...` — **경로 변수가 없다** |

**왜 그렇게 정했나.** 둘이다.

1. **활성 요청이 애초에 사용자 단위로 저장된다.** `qm:user:active-request:{userId}` 이고
   `requestId` 는 그 HASH 안에 든 값이다(INV-1, #27 이 `match_requests` 테이블을 금지한 결과이기도
   하다). `requestId` 로 찾으려면 `requestId → userId` 역색인을 새로 만들어야 하는데, 그 키의
   수명을 관리할 자리가 또 생긴다.
2. **이 조회가 가장 필요한 순간에 클라이언트는 `requestId` 를 잃은 상태다.** 쓰임새의 대부분이
   "페이지를 새로 열었을 때 내가 지금 큐에 있나"인데, 그때 요구하면 정작 필요할 때 못 쓰는 API 가
   된다. 취소(`DELETE`)가 `requestId` 를 받는 것은 **쓰기**라서다 — 늦게 도착한 취소가 그 사이
   새로 만든 요청을 지우면 안 되기 때문이고(compare-and-delete), 조회에는 그 위험이 없다.

**그래서 이것은 queueMate 본 저장소의 contract 변경이 필요한 사안이다** (CLAUDE.md §5).
그때까지의 불일치는 `contracts/README.md` 표 #5 에 적어 두었다. JWT 가 붙으면 `userId` 쿼리
파라미터가 사라지고 경로는 `/match-requests/me` 가 된다.

**응답도 커졌다.** 계약의 `MatchRequestView` 는 `{id, status, queuedAt, proposalId}` 4필드인데
`dto/MatchRequestResponse` 는 record 8필드다 — `{status, requestId, queuedAt, partyId, target,
memberCount, expiresAt, isAccepted}`. `@JsonInclude(NON_NULL)` 이라 그 갈래에서 뜻이 없는 칸은
응답에서 통째로 빠진다. 늘어난 넷(`target`/`memberCount`/`expiresAt`/`isAccepted`)은 클라이언트가
대기 화면의 "3/5명"과 제안 화면의 남은 시간·내가 눌렀는지를 그리는 데 쓴다.

**답할 수 없는 것이 하나 있다.** 갈래는 `IDLE` / `QUEUED` / `PROPOSED` / `MATCHED` 넷이고,
`MatchRequestStatus` 에 있는 `CANCELLED` / `EXPIRED` 는 **조회가 절대 돌려주지 않는다** —
취소도 만료도 활성 요청 키를 지우므로 서버에 근거가 남지 않아 `IDLE` 과 구분되지 않는다.
"왜 큐에서 빠졌는지"를 알려면 알림을 받았어야 하는데 Pub/Sub 은 at-most-once 다.
이유를 남기려면 근거가 될 키를 따로 두어야 하고, 그것은 #27(요청 이력을 남기지 않는다)과
부딪힌다 — **지금은 남기지 않는 쪽을 택했다.**

### R-2. `accept-proposal.lua` 가 시한을 직접 본다 — Q-5 의 "남는 창"이 닫혔다

Q-5 가 "`accept-proposal.lua` 는 여전히 `expiresAt` 을 보지 않는다. 시한이 지나고 스위퍼가 그
파티를 꺼내기 전(주기 기본 1초)에 도착한 수락은 그대로 확정된다"고 적은 것이 해소됐다.

스크립트가 `ARGV[3] = now` 를 받아 `expiresAt <= now` 면 수락을 기록하지 않고 **`NOT_FOUND`** 를
돌려준다. `NOT_FOUND` 인 이유는 클라이언트가 갈 곳이 스위퍼가 이미 걷어간 뒤와 같아서다(대기
화면 복귀) — 상태 값을 하나 더 만들면 클라이언트가 같은 상황을 두 갈래로 다뤄야 한다.

**흔적을 지우는 것은 여전히 스위퍼 몫이다.** 여기서 지우면 이 스크립트가 "수락 집계" 말고 다른
일까지 하게 되고, **만료 알림(`MATCH_PROPOSAL_EXPIRED`)도 못 나간다** — 그 알림을 보내는 자리는
`ProposalExpiryService` 이기 때문이다. 그래서 확인만 하고 정리는 넘긴다.

INV-5 의 네 갈래가 이것으로 **전부** 막혔다. `CLAUDE.md` §4 INV-5 행의 상태를
"구현됨 (만료는 스위퍼 주기만큼 늦다)" → **"구현·테스트됨"** 으로 고쳤다.

### R-3. PUBG 동시성 테스트가 생겼다 — 세 게임 모두 테스트가 있다

D-7 이 스크립트를 게임별로 나누면서 "나눈 대가로 게임마다 테스트가 있어야 한다"고 적었고,
Q-4 시점까지 PUBG 만 비어 있었다. `concurrency/PubgPartyJoinConcurrencyTest` 9건이 들어와
그 구멍이 메워졌다. 이제 **동시성 24건**(LoL 7 + VALORANT 8 + PUBG 9) + 제안 멱등성 11 +
알림 6 = **41건**이다.

PUBG 테스트가 다른 둘과 다르게 보는 것은 **핵심 조건이 겹쳐도 된다는 것**이다 —
플랫폼은 LoL 포지션·VALORANT 역할군과 달리 한 파티에 여럿이 같아도 되므로, "같은 플랫폼만으로도
파티가 정원까지 찬다"를 단언한다. 반대로 스팀과 카카오는 색인 자체가 갈려 섞이지 않는다는 것
(INV-8 구조적 분리)도 같이 본다.

`CLAUDE.md` §4 INV-8 의 상태 열을 "LoL만 구현·테스트됨" → **"세 게임 모두 구현·테스트됨"** 으로,
같은 문서 D-7 절의 "PUBG 스크립트를 도는 테스트는 아직 하나도 없다"를 고쳤다.

### R-4. 조건 값 enum 이 `domain/condition/` 으로 모였다

`KeyConditionType` · `VoicePreference` · `PlayPurpose` 와 게임별 조건 값
(`condition/lol/LolPosition` · `condition/valorant/ValorantRole` ·
`condition/pubg/`(빈 디렉터리))이 그 아래로 옮겨졌다.

**무엇이 남았는가가 이 구분의 뜻이다.** `GameKey` 는 `domain/` 에 남았다 — 게임은 사용자가 고르는
**조건**이 아니라 그 위의 **갈래**이고, 실제로 `game` 은 후보 풀 키의 맨 앞에 붙어 조건들을
담는 그릇 노릇을 한다. `ActiveRequest` · `CancelResult` · `MatchRequestStatus` ·
`ProposalResult` 도 조건이 아니라 상태·결과라 남았다.

`condition/pubg/` 가 빈 것은 사고가 아니다 — **PUBG 핵심 조건은 플랫폼 문자열이라 enum 이 없고**
`PubgConditionValidator` 가 `STEAM`/`KAKAO` 를 직접 본다. LoL 티어가 자바에 없는 것(D-8)과는
이유가 다르다. 저쪽은 값이 자주 바뀌어 Redis 로 뺀 것이고, 이쪽은 값이 둘뿐이라 enum 을 만들
값이 없었다.

### R-5. Redis 키 문자열이 `redisKeys/SharedKeys` 하나로 모였다

**왜 모았나.** 같은 접두사를 여러 클래스가 각자 적고 있으면 **한쪽만 고쳐도 컴파일이 통과한다.**
그때부터 서로 다른 키를 만들고, 아무도 못 찾는 데이터가 조용히 쌓인다. 실제로
`ProposalService` / `ProposalExpiryService` 는 게임과 무관한데도 `LolPartyKeys.PARTY_PREFIX` 를
빌려 쓰거나 같은 문자열을 따로 적고 있었다.

**게임별 `*PartyKeys` 를 없애지는 않았다.** 게임을 가리는 것은 needs 색인과 gameconfig 뿐이고
(조건이 키 이름에 들어가기 때문이다 — INV-8), 그 둘은 게임마다 격자 모양이 다르다.
그래서 **조각만 `SharedKeys` 에 두고 게임 이름과 조건을 엮는 조립은 게임별 클래스**가 한다.

**한계가 하나 있다. 같은 문자열이 Lua 안에도 리터럴로 있다.** 컴파일러가 맞춰 주지 않는 짝이라,
값을 고치면 어느 스크립트를 같이 고쳐야 하는지가 `SharedKeys` 클래스 주석에 목록으로 있다.
`PARTY_PREFIX` 만 예외다 — 배정 스크립트가 ARGV 로 받아 가므로 자바 쪽만 고치면 된다.

### R-6. 활성 요청에 `queuedAt` 이 붙었다

줄 선 시각(epoch millis). **Lua 가 아니라 `MatchRequestService#requestFields()` 가 필드로
넘긴다** — `claim-request.lua` 는 받은 필드를 그대로 `HSET` 하므로 한 줄이면 됐다.

둘 곳이 여기뿐이었다. 조회가 "얼마나 기다렸나"를 답하려면 요청이 살아 있는 동안 남는 자리가
필요한데, **`match_requests` 테이블을 만들지 않으므로**(#27) 활성 요청 HASH 말고는 없다.

`docs/07_REDIS_DESIGN.md` 는 원문 verbatim 이라 이 필드가 반영되지 않았다(그 문서는 활성 요청을
아직 `STRING requestId` 로 적는다). **실제 키 구조는 `START_HERE.md` 의 Redis 키 표가 맞다.**

### 점검 방법

`backend/src/main` 의 자바와 Lua 를 직접 읽고 문서와 대조했다. 특히
`MatchingController` · `MatchQueryService` · `MatchRequestResponse` · `MatchRequestStatus` ·
`SharedKeys` · `MatchRequestService` · `redis/proposal/accept-proposal.lua` ·
`PubgPartyJoinConcurrencyTest` 를 읽었고, `build.gradle` 에 Flyway·AWS SDK 가 없는 것과
`MeterRegistry` 가 0건인 것을 grep 으로 확인했다.
**코드는 건드리지 않았고 빌드·테스트도 돌리지 않았다** — 고친 것은 문서뿐이다.
이번에 확인한 "아직 없는 것" 전체 목록과 우선순위는 `HANDOFF.md` §0 에 있다.
