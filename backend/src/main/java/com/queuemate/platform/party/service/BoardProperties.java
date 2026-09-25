package com.queuemate.platform.party.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 게시판의 설정값. {@code application.yaml} 의 {@code platform.board.*}. 값의 원본은 {@code contracts/platform-api.md} "모집 글 · 목록 · 입장권" 이다.
 *
 * @param roomTicketTtl   입장권의 수명(환경변수 {@code ROOM_TICKET_TTL}). 받은 즉시 쓰는 것이라 짧다 — 입장권을 받은 뒤 들어오기 전의
 *                        차단 경쟁(D-20 미정)의 창도 이만큼이다
 * @param roomGrace       방 만들기를 아직 안 부른 글({@code room_seen_at} 이 없다)을 만료시키지 않고 기다려 주는 시간. {@code room} 의 방 수명
 *                        ({@code ROOM_TTL_SECONDS} 600초)과 같은 값이다
 * @param maxRefills      차단으로 숨겨진 글 때문에 한 페이지가 {@code limit} 에 모자랄 때 <b>그 뒤를 더 읽어 채우는 횟수의 상한</b>
 *                        (2026-09-23 소유자 결정). 채우기 한 번이 목록 조립 한 벌(글 쿼리 · Redis 파이프라인 · 프로필 · 차단)이라
 *                        무한정 할 수 없다 — 다 써도 모자라면 있는 만큼 내려 준다
 */
@ConfigurationProperties(prefix = "platform.board")
public record BoardProperties(
        @DefaultValue("PT60S") Duration roomTicketTtl,
        @DefaultValue("PT10M") Duration roomGrace,
        @DefaultValue("3") int maxRefills
) {
    /** 목록의 기본 페이지 크기 — {@code limit} 을 주지 않았을 때다 (2026-09-23 소유자 결정) */
    public static final int DEFAULT_PAGE_LIMIT = 20;

    /** 목록의 페이지 크기 상한. 넘으면 400 {@code VALIDATION_FAILED} 다 — 잘라 주지 않는다(클라이언트가 받은 줄 알면 안 된다) */
    public static final int MAX_PAGE_LIMIT = 100;

    /** 정원 — 방장 포함 5명. {@code room} 이 지키는 값이고(D-11 10번) 이 앱은 {@code capacity} · {@code full} 을 그릴 때만 쓴다 */
    public static final int ROOM_CAPACITY = 5;
}
