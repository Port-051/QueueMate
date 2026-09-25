/**
 * QueueMate 의 API 서버 {@code app:platform} — 계정 · 파티 · 소셜(친구/차단/신고) · 파티 모집 게시판 · 방 안의 일.
 *
 * <p><b>계정과 인증 · 게임 프로필 · 소셜 로그인 · 차단 · 파티 모집 게시판(글 · 목록 · 방장 확정의 기록) · 친구 · 신고 ·
 * 최근 함께한 사람(읽기) · 개인 알림 발행 · 방 안의 일(2026-09-25 에 {@code room} 앱을 합쳤다)이 있다.</b> 무엇을 어떤 순서로 만드는지는 폴더 루트의 {@code START_HERE.md} §3,
 * 규칙은 {@code CLAUDE.md}, 이 앱이 정한 계약은 {@code contracts/platform-api.md} 에 있다.
 *
 * <p><b>패키지는 도메인(= DB 스키마)을 먼저 나눈다.</b> 이 앱이 소유하는 스키마는 {@code account} · {@code party} · {@code social}
 * 셋이고(CLAUDE.md §3.5 · docs/11 #17) 크로스 스키마 FK · JOIN 을 만들지 않는다 — 패키지도 같은 금으로 가른다. DB 가 없는 도메인이 하나 더 있다 — {@code room}.
 * <ul>
 *   <li>{@code common} — 도메인에 속하지 않는 것. {@code error}(에러 본문 · 예외 처리) · {@code web}({@code Origin} 검사) ·
 *       {@code security}(토큰 서명과 검증 · 보안 설정 · 현재 사용자) · {@code push}(개인 알림 발행과 봉투 — CLAUDE.md §3.2.
 *       채널 접두사는 {@code push.PushChannels} 한 곳에만 있고 원본은 {@code matching} 의 {@code SharedKeys} 다) ·
 *       {@code gameconfig}(운영자가 심는 공유 설정을 <b>읽는</b> 곳 — {@code mode} · {@code tier} 가 있는 값인지 본다. 2026-09-24 소유자 결정.
 *       도메인 둘({@code party} 의 모드 · {@code account} 의 티어)이 같이 쓰므로 여기 있다. <b>쓰지 않는다</b>)</li>
 *   <li>{@code account} — 가입 · 로그인 · 프로필 · 게임 계정(게임 프로필) · 소셜 로그인. 안에서 {@code controller} · {@code service} ·
 *       {@code domain} · {@code repository} · {@code dto} 로 나누고, 제공자와 주고받는 것은 {@code oauth} 에,
 *       <b>게임사 API 에서 전적을 긁는 것은 {@code stats}</b> 에 둔다(2026-09-23 — LoL 만. 비동기이고 실패가 본 작업을 뒤집지 않는다.
 *       <b>거는 곳은 게임 계정을 저장하는 자리 하나뿐이라 {@code account} 안에서 끝난다</b> — 2026-09-24 소유자 결정으로 글 쓰기가 거는 길이 없어졌다)</li>
 *   <li>{@code social} — 차단 · 친구 요청과 친구 · 신고 · 최근 함께한 사람(읽는 쪽만 — 채우는 것은 SQS 가 정해진 뒤다).
 *       {@code account} 와 같은 모양이다. <b>사람을 검색하는 요청은 없다</b>(CLAUDE.md §1)</li>
 *   <li>{@code party} — 파티 모집 게시판의 글 쪽과 확정된 파티의 기록. 같은 모양에 하나가 더 있다 — {@code board}(게시판 채널 신호 발행 — §3.2).
 *       방 키를 직접 읽던 {@code party.room} 은 2026-09-25 2단계로 없어졌다 — 방의 상태는 {@code room} 의 서비스로 읽는다(아래)</li>
 *   <li>{@code room} — <b>방 안의 일</b>: 방 만들기 · 입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인 · 방 안 사람 목록 · 내 방 찾기 · WebRTC 시그널 전달 ·
 *       방의 상태 읽기. 2026-09-25 에 {@code room} 앱(8083)을 합쳤다(소유자 결정 — {@code contracts/platform-api.md} P-22). 계약은 {@code contracts/platform-api.md} 의
 *       방의 절이고 상태는 Redis 에만 있다(DB 없음). 안은 {@code controller} · {@code service} · {@code domain} · {@code dto} · {@code redisKeys} 이고,
 *       두 패키지가 같이 쓰는 거절은 {@code room.RoomErrors} 에 있다.
 *       <b>방을 바꾸려면 Lua 스크립트를 부르는 서비스({@code RoomService} · {@code RoomMemberService})를 거친다</b> — 정원 검사 · 입장 표시 키 ·
 *       활성 요청 키의 {@code EXISTS} 가 한 스크립트 안에 있어서, 맨손으로 {@code SADD} 하면 그 불변식이 깨진다</li>
 * </ul>
 *
 * <p><b>도메인 사이는 "창구"로만 잇는다</b> — 남의 리포지토리를 직접 쓰거나 남의 스키마의 테이블을 JOIN 하지 않는다.
 * <ul>
 *   <li>{@code account.service.UserReader}(있는 사용자인가 · 닉네임) · {@code account.service.GameProfileReader}(여러 사용자의 게임 프로필 — 쿼리 한 번)</li>
 *   <li>{@code social.service.BlockReader}(나와 어느 방향으로든 차단 관계인 사람 — 쿼리 한 번)</li>
 *   <li><b>{@code room} ↔ {@code party}</b>(2026-09-25 2단계 — 두 앱이던 때는 입장권과 방 키 읽기로 이었다. 지금은 서로의 서비스를 부른다)
 *     <ul>
 *       <li>{@code party} → {@code room}: {@code room.service.RoomService} 의 {@code create}(글 쓰기가 방을 만든다 — 글의 트랜잭션 안) ·
 *           {@code confirm}(방장 확정이 방을 확정하고 같은 트랜잭션에서 파티를 적는다 — {@code party.service.PostStore#confirmRoom}) ·
 *           {@code states(Collection<Long>)}(목록 · 단건 · 고치기가 방 안을 읽는다 — 파이프라인 한 번. 못 읽으면 {@code RoomStateUnavailableException} 이고
 *           fail-open 인지 fail-closed 인지는 부르는 쪽이 정한다)</li>
 *       <li>{@code room} → {@code party}: {@code room.service.RoomMemberService#enter} 가 스크립트 전에 {@code party.service.PostEntryGate#check}
 *           (없는 글 · 차단으로 숨겨진 글 404 · 끝난 글 409). 방장 확정의 HTTP 요청은 {@code room.controller.RoomController} 가
 *           {@code party.service.PostService#confirmRoom} 으로 넘긴다</li>
 *       <li><b>빈 순환을 만들지 않는다</b> — {@code PostService} · {@code PostStore} 가 {@code RoomService} 를 물으므로 {@code RoomService} 는 {@code party} 를
 *           물지 않는다. 입장의 창구 {@code PostEntryGate} 는 {@code PostService} 를 물지 않는 따로 선 빈이다({@code PostStore} · {@code RoomService} ·
 *           {@code BlockReader} 만). 그래서 고리가 없다: {@code RoomMemberService → PostEntryGate → PostStore → RoomService}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>여기 만들지 않는 것 — 매칭 로직, SSE 연결 보유, 예약(CLAUDE.md §2 · §11).
 */
package com.queuemate.platform;
