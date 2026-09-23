package com.queuemate.platform.party.dto;

import java.time.Instant;

/**
 * 입장권 ({@code contracts/platform-api.md} "입장권"). 브라우저는 {@code ticket} 을 들고 {@code room} 에 간다 — 이 앱은 {@code room} 을 부르지 않는다.
 *
 * @param ticket    서명된 JWT. 쿠키가 아니라 본문으로 준다 — 받는 곳이 다른 앱이다
 * @param roomId    글의 id(숫자). 토큰 안의 {@code room_id} 는 이 숫자의 문자열이다 — {@code room} 의 주소에 그대로 넣는다
 * @param hostId    글을 쓴 사람의 사용자 번호. 토큰 안의 {@code host_id} 는 이 숫자의 문자열이다
 * @param expiresAt 입장권의 {@code exp}. 받은 즉시 쓰는 것이라 짧다(기본 60초)
 */
public record TicketResponse(String ticket, Long roomId, Long hostId, Instant expiresAt) {
}
