package com.queuemate.platform.account.domain;

/**
 * 사용자 한 명과, 어느 한 게임에 연결한 게임 계정 · 전적을 <b>한 쿼리로</b> 읽어 온 한 줄 ({@code UserRepository#findGameProfileRows}).
 * 그 게임에 연결한 계정이 없으면 {@code account} · {@code stats} 가 {@code null} 이다.
 */
public record UserGameProfileRow(Long userId, String nickname, GameAccount account, GameAccountStats stats) {
}
