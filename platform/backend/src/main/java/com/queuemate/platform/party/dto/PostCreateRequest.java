package com.queuemate.platform.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * @param wantedPositions 그 게임의 포지션 이름. 없으면 빈 배열로 친다. 겹치는 값은 하나로 친다. <b>포지션이 있는 모드면 하나 이상 필수 ·
 *                        포지션이 없는 모드 · PUBG 는 빈 배열만</b>이다(2026-09-30 소유자 결정 — P-38. gameconfig 를 못 읽으면 보지 않는다)
 * @param hostPosition    <b>방장 자신의 포지션</b>(2026-09-30 소유자 결정 — P-38). 그 게임의 포지션 이름 하나. <b>포지션이 있는 모드면 필수</b>이고
 *                        (gameconfig 모드 HASH 의 {@code positionUniqueness} 가 {@code true}), 포지션이 없는 모드 · PUBG 면 보내면 400 이다.
 *                        {@code wantedPositions} 에 들어 있으면 400 이다. 검증은 서비스가 한다({@code PostValidation#hostPosition})
 * @param allowAutoJoin   <b>빠른매치로 들어오는 것을 허용하는가</b>(2026-10-02 소유자 결정 — P-50). <b>필수다</b> — 프런트는 기본값 없이 둘 중 하나를 골라야 글을 쓸 수 있다.
 *                        없거나 {@code null} 이면 400 {@code "allowAutoJoin: 필요합니다"}. {@code false} 면 게시판 방 먼저 합류({@code AutoJoinService})가 이 방에 넣지 않는다 —
 *                        직접 입장은 그대로 된다. 모집 중에는 방 설정에서 변경할 수 있다
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

        List<String> wantedPositions,

        String hostPosition,

        @NotNull(message = "필요합니다") Boolean allowAutoJoin
) {
}
