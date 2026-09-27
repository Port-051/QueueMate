package com.queuemate.platform.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 모집 글 쓰기 ({@code contracts/platform-api.md} "모집 글 · 목록").
 *
 * <p>애너테이션은 길이만 본다. <b>이름의 목록에 드는지는 서비스가 검증한다</b>({@code PostService}) — {@code game} · {@code voice} 를
 * enum 으로 받으면 모르는 값이 "본문을 읽을 수 없다"로 떨어져 어느 필드가 틀렸는지 말해 줄 수 없다. 포지션과 {@code conditions} 는 게임마다 다르다.
 *
 * @param mode            그 게임의 모드. <b>필수다</b>(2026-09-24 소유자 결정) — 목록의 원본은 {@code matching} 의 gameconfig(Redis)라
 *                        <b>있는 값인지는 서비스가 읽어서 본다</b>({@code GameConfigReader}). 30자 제한은 seed 의 모드 이름이 그보다 짧기 때문이다
 * @param conditions      게임별 조건. PUBG 는 {@code {"perspective": "TPP" | "FPP"}}(필수), 다른 게임은 {@code {}}. 모르는 키는 400 이다
 * @param wantedPositions 그 게임의 포지션 이름. 없으면 빈 배열로 친다. 겹치는 값은 하나로 친다
 */
public record PostCreateRequest(
        @NotBlank(message = "필요합니다") String game,

        @NotBlank(message = "필요합니다")
        @Size(max = 30, message = "30자를 넘을 수 없습니다")
        String mode,

        @NotBlank(message = "필요합니다")
        @Size(max = 60, message = "60자를 넘을 수 없습니다")
        String title,

        @Size(max = 300, message = "300자를 넘을 수 없습니다") String description,

        @NotBlank(message = "필요합니다") String voice,

        Map<String, Object> conditions,

        List<String> wantedPositions
) {
}
