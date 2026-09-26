package com.queuemate.platform.account.domain;

/**
 * 게임 계정과 그 전적을 <b>한 쿼리로</b> 같이 읽어 온 한 줄. 전적이 없으면 {@code stats} 가 {@code null} 이다(LEFT JOIN).
 */
public record GameAccountWithStats(GameAccount account, GameAccountStats stats) {
}
