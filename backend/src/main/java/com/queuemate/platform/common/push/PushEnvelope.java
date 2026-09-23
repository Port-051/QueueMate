package com.queuemate.platform.common.push;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * 계약이 정한 봉투 — <b>네 칸이 고정이다</b>(contracts/events.md "Envelope" · CLAUDE.md §3.2). 칸의 순서도 {@code matching} · {@code room} 과 같다.
 * 개인 알림({@link PushPublisher})과 게시판 채널 신호({@code party.board.BoardSignalPublisher})가 <b>이 한 벌을 같이 쓴다</b> —
 * {@code notification} 은 열어 보지 않고 SSE {@code data:} 에 그대로 싣고, 프런트는 두 가지를 같은 모양으로 읽는다.
 *
 * @param eventId    매번 새 UUID. SSE 의 {@code id:} 가 되고 클라이언트가 중복을 거르는 데 쓴다
 * @param occurredAt ISO-8601 UTC, 밀리초까지. {@code Instant} 는 UTC 다 — 서버 타임존에 흔들리지 않는다
 * @param payload    객체. 담을 것이 없어도 {@code null} 이 아니라 {@code {}} 다 — {@code null} 이 나가면 받는 쪽이 칸을 읽다 터진다
 */
public record PushEnvelope(String type, String eventId, String occurredAt, Map<String, Object> payload) {

    /** 지금 일어난 일의 봉투를 만든다. {@code type} 은 enum 의 {@code name()} 을 넘긴다 — 글자를 직접 적지 마라 */
    public static PushEnvelope now(String type, Map<String, Object> payload)
    {
        return new PushEnvelope(type, UUID.randomUUID().toString(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS).toString(),
                payload == null ? Map.of() : payload);
    }
}
