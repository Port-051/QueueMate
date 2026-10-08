package com.queuemate.platform.party.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 게시판의 설정값. {@code application.yaml} 의 {@code platform.board.*}. 값의 원본은 {@code contracts/platform-api.md} "모집 글 · 목록" 이다.
 *
 * <p>2026-09-25 2단계로 둘이 빠졌다 — 입장권의 수명({@code room-ticket-ttl}, 입장권이 없어졌다)과 방을 기다려 주는 시간({@code room-grace},
 * 글 쓰기가 방을 같이 만들어 "아직 안 만들어진 방" 이 없어졌다).
 *
 * @param maxRefills 차단으로 숨겨진 글 때문에 한 페이지가 {@code limit} 에 모자랄 때 <b>그 뒤를 더 읽어 채우는 횟수의 상한</b>
 *                   (2026-09-23 소유자 결정). 채우기 한 번이 목록 조립 한 벌(글 쿼리 · Redis 파이프라인 · 프로필 · 차단)이라
 *                   무한정 할 수 없다 — 다 써도 모자라면 있는 만큼 내려 준다
 * @param autoJoinScan 게시판 방 먼저 합류({@code POST /api/v1/posts/auto-join} — 2026-09-28 · P-28)가 <b>한 번에 보는 후보 글의 상한</b>.
 *                     그 게임 · 그 모드의 모집 중인 글을 오래된 순으로 이만큼만 읽고 그 안에서 고른다 — 다 돌아도 맞는 방이 없으면 404 다(그 뒤는 보지 않는다).
 *                     후보 하나가 방 키 읽기 한 자리(파이프라인)와 입장 스크립트 한 번이라 상한이 없으면 한 요청이 게시판을 끝까지 훑는다. 이름과 기본값(50)은 Claude 가 정했다
 */
@ConfigurationProperties(prefix = "platform.board")
public record BoardProperties(
        @DefaultValue("3") int maxRefills,
        @DefaultValue("50") int autoJoinScan
) {
    /** 목록의 기본 페이지 크기 — {@code limit} 을 주지 않았을 때다 (2026-09-23 소유자 결정) */
    public static final int DEFAULT_PAGE_LIMIT = 20;

    /** 목록의 페이지 크기 상한. 넘으면 400 {@code VALIDATION_FAILED} 다 — 잘라 주지 않는다(클라이언트가 받은 줄 알면 안 된다) */
    public static final int MAX_PAGE_LIMIT = 100;
}
