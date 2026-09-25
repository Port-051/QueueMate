package com.queuemate.platform.room.dto;

/**
 * "내가 지금 어느 방에 있나"의 응답 본문. 방에 없으면 {@code roomId} 가 {@code null} 이다 — 칸 자체는 언제나 있다.
 */
public record MyRoomResponse(String roomId) {
}
