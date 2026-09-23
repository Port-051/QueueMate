/**
 * QueueMate 의 API 서버 {@code app:platform} — 계정 · 파티 · 소셜(친구/차단/신고)과 파티 모집 게시판의 글 쪽.
 *
 * <p><b>계정과 인증 · 게임 프로필 · 소셜 로그인 · 차단 · 파티 모집 게시판(글 · 목록 · 입장권 · 방장 확정의 기록) · 친구 · 신고 ·
 * 최근 함께한 사람(읽기) · 개인 알림 발행이 있다.</b> 무엇을 어떤 순서로 만드는지는 폴더 루트의 {@code START_HERE.md} §3,
 * 규칙은 {@code CLAUDE.md}, 이 앱이 정한 계약은 {@code contracts/platform-api.md} 에 있다.
 *
 * <p><b>패키지는 도메인(= DB 스키마)을 먼저 나눈다.</b> 이 앱이 소유하는 스키마는 {@code account} · {@code party} · {@code social}
 * 셋이고(CLAUDE.md §3.5 · docs/11 #17) 크로스 스키마 FK · JOIN 을 만들지 않는다 — 패키지도 같은 금으로 가른다.
 * <ul>
 *   <li>{@code common} — 도메인에 속하지 않는 것. {@code error}(에러 본문 · 예외 처리) · {@code web}({@code Origin} 검사) ·
 *       {@code security}(토큰 서명과 검증 · 보안 설정 · 현재 사용자) · {@code push}(개인 알림 발행과 봉투 — CLAUDE.md §3.2.
 *       채널 접두사는 {@code push.PushChannels} 한 곳에만 있고 원본은 {@code matching} 의 {@code SharedKeys} 다)</li>
 *   <li>{@code account} — 가입 · 로그인 · 프로필 · 게임 계정(게임 프로필) · 소셜 로그인. 안에서 {@code controller} · {@code service} ·
 *       {@code domain} · {@code repository} · {@code dto} 로 나누고, 제공자와 주고받는 것은 {@code oauth} 에,
 *       <b>게임사 API 에서 전적을 긁는 것은 {@code stats}</b> 에 둔다(2026-09-23 — LoL 만. 비동기이고 실패가 본 작업을 뒤집지 않는다)</li>
 *   <li>{@code social} — 차단 · 친구 요청과 친구 · 신고 · 최근 함께한 사람(읽는 쪽만 — 채우는 것은 SQS 가 정해진 뒤다).
 *       {@code account} 와 같은 모양이다. <b>사람을 검색하는 요청은 없다</b>(CLAUDE.md §1)</li>
 *   <li>{@code party} — 파티 모집 게시판의 글 쪽과 확정된 파티의 기록. 같은 모양에 둘이 더 있다 — {@code room}({@code room} 의 방 키를
 *       <b>읽는</b> 곳. 쓰지 않는다 — CLAUDE.md §3.3) · {@code board}(게시판 채널 신호 발행 — §3.2)</li>
 * </ul>
 *
 * <p><b>도메인 사이는 "읽는 창구"로만 잇는다</b> — 남의 리포지토리를 직접 쓰거나 남의 스키마의 테이블을 JOIN 하지 않는다.
 * <ul>
 *   <li>{@code account.service.UserReader}(있는 사용자인가 · 닉네임) · {@code account.service.GameProfileReader}(여러 사용자의 게임 프로필 — 쿼리 한 번) ·
 *       {@code account.stats.GameStatsSync}(전적을 긁으라고 거는 창구 — 글을 쓸 때 {@code party} 가 부른다)</li>
 *   <li>{@code social.service.BlockReader}(나와 어느 방향으로든 차단 관계인 사람 — 쿼리 한 번)</li>
 * </ul>
 *
 * <p>여기 만들지 않는 것 — 매칭 로직, 방 안의 일(입장 · 강퇴 · 시그널), SSE 연결 보유, 예약(CLAUDE.md §2 · §11).
 */
package com.queuemate.platform;
