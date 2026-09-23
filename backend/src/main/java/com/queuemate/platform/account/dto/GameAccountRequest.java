package com.queuemate.platform.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 게임 계정 연결 요청. 어느 게임인지는 경로에 있다.
 *
 * @param gameNickname 그 게임 안에서의 이름. 40자까지
 * @param tier         자기신고 티어. 없어도 된다. <b>값의 목록은 검증하지 않는다</b> — 원본이 {@code matching} 의 gameconfig 다
 * @param mainPosition 주 포지션. 없어도 된다. 게임마다 목록이 달라 서비스에서 검증한다({@code Game#allowsPosition})
 * @param server       PUBG 만({@code STEAM} · {@code KAKAO}). 없어도 된다. 다른 게임은 {@code null} 만 받는다 — 서비스에서 검증한다({@code Game#allowsServer})
 *
 * <p><b>{@code verified} · {@code externalId} · {@code stats} 칸이 없다</b> — 읽기 전용이라 요청으로 바꿀 수 없다.
 * 본문에 그런 이름이 들어 있어도 무시된다(모르는 칸은 읽지 않는다).
 */
public record GameAccountRequest(
        @NotBlank(message = "필요합니다")
        @Size(max = 40, message = "40자를 넘을 수 없습니다")
        String gameNickname,

        @Pattern(regexp = "^[A-Z0-9_]{1,20}$", message = "대문자 · 숫자 · 밑줄로 1~20자여야 합니다")
        String tier,

        String mainPosition,

        String server
) {
}
