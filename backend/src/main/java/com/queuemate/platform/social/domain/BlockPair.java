package com.queuemate.platform.social.domain;

/**
 * {@code blocks} 한 줄의 두 사용자 번호 — 차단 관계 사본의 재구성이 표 전체를 이 모양으로 읽는다({@code BlockRepository#findAllPairs}).
 * 방향이 있다 — {@code blockerId} 가 {@code blockedId} 를 차단했다. 사본은 방향을 지우고 양쪽에 적는다.
 */
public record BlockPair(Long blockerId, Long blockedId) {
}
