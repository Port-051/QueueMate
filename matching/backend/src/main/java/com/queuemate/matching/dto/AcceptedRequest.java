package com.queuemate.matching.dto;

/**
 * 활성 요청 선점에 성공했을 때 {@code MatchRequestService#join} 이 돌려주는 값.
 *
 * <p><b>{@code queuedAt} 을 같이 돌려주는 이유.</b> 그 시각은 선점 스크립트가 Redis 에 쓴 값과
 * 같아야 한다. 컨트롤러가 응답을 만들면서 {@code System.currentTimeMillis()} 를 다시 부르면
 * 저장된 값과 몇 밀리초 어긋나, 접수 응답의 {@code queuedAt} 과 나중에 조회로 받는 값이
 * 달라진다. 같은 뜻의 값은 한 곳에서만 만든다.
 */
public record AcceptedRequest(String requestId, long queuedAt) {
}
