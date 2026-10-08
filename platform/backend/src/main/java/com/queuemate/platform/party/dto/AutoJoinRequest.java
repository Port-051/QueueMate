package com.queuemate.platform.party.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시판 방 먼저 합류 — {@code POST /api/v1/posts/auto-join} 의 본문(2026-09-28 소유자 결정 · {@code contracts/platform-api.md} "자동 매칭이 게시판 방에 먼저 합류하는 길" · P-28).
 *
 * <p><b>{@code matching} 의 {@code CreateMatchRequestCommand} 와 필드 이름이 글자까지 같다</b> — 프런트가 객체 하나를 만들어 먼저 이 요청에, 404 면 그대로 {@code matching} 의
 * {@code POST /api/v1/match-requests} 에 보낸다. {@code userId} 는 본문에 없다 — 토큰의 사용자다.
 *
 * <p>애너테이션은 있는지 · 길이만 본다. <b>이름의 목록에 드는지는 서비스가 검증한다</b>({@code AutoJoinService}) — {@code PostCreateRequest} 와 같은 이유로 enum 으로 받지 않는다
 * (모르는 값이 "본문을 읽을 수 없다"로 떨어져 어느 필드가 틀렸는지 말해 줄 수 없다).
 *
 * @param game            {@code LOL} · {@code VALORANT} · {@code PUBG}
 * @param modeKey         그 게임의 모드 — gameconfig 에 있어야 한다(400)
 * @param tier            자기신고 티어. 티어를 보는 모드({@code tierRule=EXIST})면 필수, 안 보는 모드({@code NONE})면 있으면 400 — {@code matching} 과 같다.
 *                        <b>DB 의 게임 계정을 읽지 않는다</b> — 본문의 값이 "내 티어" 다
 * @param keyCondition    게임별 핵심 조건. 없어도 된다(그러면 포지션이 없는 것으로 본다 — {@code wantedPositions} 가 빈 글만 맞는다)
 * @param voicePreference {@code REQUIRED} · {@code NO_VOICE} — 글의 {@code voice} 와 같은 이름
 * @param playPurpose     받되 <b>무시한다</b> — 글에서 {@code purpose} 가 없어졌다(P-29). 있어도 없어도 된다
 */
public record AutoJoinRequest(
        @NotBlank(message = "필요합니다") String game,

        @NotBlank(message = "필요합니다")
        @Size(max = 30, message = "30자를 넘을 수 없습니다")
        String modeKey,

        @Size(max = 20, message = "20자를 넘을 수 없습니다") String tier,

        @Valid KeyCondition keyCondition,

        @NotBlank(message = "필요합니다") String voicePreference,

        String playPurpose
) {
    /**
     * @param type  {@code POSITION}(LoL) · {@code ROLE}(VALORANT) · {@code PLATFORM}(PUBG) — 게임과 맞아야 한다(400)
     * @param value 그 종류의 값 — 포지션 · 역할군 이름 또는 {@code NONE}, PUBG 는 {@code STEAM} · {@code KAKAO}
     */
    public record KeyCondition(
            @NotBlank(message = "필요합니다") String type,
            @NotBlank(message = "필요합니다") String value
    ) {
    }
}
