package com.queuemate.platform.account.dto;

/**
 * 사용자 한 명의 닉네임과 어느 한 게임의 게임 프로필 — {@code account} 밖(목록의 방 안 사람 카드)에 내주는 모양이다
 * ({@code GameProfileReader}).
 *
 * @param profile 그 게임에 연결한 게임 계정이 없으면 {@code null} 이다(계약의 {@code "profile": null})
 */
public record UserGameProfile(Long userId, String nickname, GameProfileResponse profile) {
}
