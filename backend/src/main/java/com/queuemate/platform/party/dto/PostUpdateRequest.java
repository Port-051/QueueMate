package com.queuemate.platform.party.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 모집 글 고치기 — <b>준 것만 바꾼다.</b> 칸이 없거나 {@code null} 이면 그대로 둔다. {@code game} 은 바꿀 수 없다(칸이 없다).
 *
 * <p>{@code null} 이 "그대로"라서 {@code description} 을 {@code null} 로 비울 수 없다 — <b>빈 문자열을 주면 비운다.</b>
 * {@code title} 은 비울 수 없다(빈 문자열은 400). {@code wantedPositions} 는 빈 배열을 주면 비운다.
 *
 * <p><b>{@code mode} 를 비우는 길은 없어졌다</b>(2026-09-24 소유자 결정 — 모든 글이 gameconfig 에 있는 모드 하나를 갖는다). {@code null} 은 그대로 두고
 * <b>빈 문자열은 400</b> 이다. 모드를 바꾸려면 그 게임의 다른 모드 이름을 준다.
 */
public record PostUpdateRequest(
        @Size(max = 30, message = "30자를 넘을 수 없습니다") String mode,

        @Size(max = 60, message = "60자를 넘을 수 없습니다") String title,

        @Size(max = 300, message = "300자를 넘을 수 없습니다") String description,

        String voice,

        String purpose,

        Map<String, Object> conditions,

        List<String> wantedPositions
) {
}
