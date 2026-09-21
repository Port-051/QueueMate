/**
 * QueueMate 의 API 서버 {@code app:platform} — 계정 · 파티 · 소셜(친구/차단/신고)과 파티 모집 게시판의 글 쪽.
 *
 * <p><b>지금은 뼈대뿐이다.</b> 도메인 코드(엔티티 · 컨트롤러 · 서비스)가 하나도 없다. 무엇을 어떤 순서로 만드는지는
 * 폴더 루트의 {@code START_HERE.md} §3, 규칙은 {@code CLAUDE.md} 에 있다.
 *
 * <p>이 앱이 소유하는 DB 스키마는 {@code account} · {@code party} · {@code social} 셋이다(CLAUDE.md §3.5 · docs/11 #17).
 * 크로스 스키마 FK · JOIN 을 만들지 않는다. <b>패키지를 어떻게 나눌지는 정해지지 않았다</b> — 옆 서비스
 * ({@code matching} · {@code room})는 {@code controller/} · {@code service/} · {@code domain/} · {@code redisKeys/} 처럼
 * 계층으로 나눴다. 첫 기능(1단계 계정)을 만들 때 정한다.
 *
 * <p>여기 만들지 않는 것 — 매칭 로직, 방 안의 일(입장 · 강퇴 · 시그널), SSE 연결 보유, 예약(CLAUDE.md §2 · §11).
 */
package com.queuemate.platform;
