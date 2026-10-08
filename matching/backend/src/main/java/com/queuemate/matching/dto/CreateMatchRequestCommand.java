package com.queuemate.matching.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.condition.KeyConditionType;
import com.queuemate.matching.domain.condition.PlayPurpose;
import com.queuemate.matching.domain.condition.VoicePreference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMatchRequestCommand {
    /**
     * 요청한 사용자의 번호(문자열 — {@code "42"}). <b>요청 본문에서 받지 않는다</b> — 컨트롤러가 access 토큰의 {@code sub} 로 채운다
     * (2026-09-27. 그 전에는 본문의 필수 필드였다 — JWT 도입 전 임시). 본문에 {@code userId} 가 와도 무시된다({@link JsonIgnore}).
     * 엔진 안에서는 전처럼 이 필드를 읽는다.
     */
    @JsonIgnore
    private String userId;
    @NotNull
    private GameKey game;
    @NotBlank
    private String modeKey;

    /**
     * 랭크 티어. 게임마다 값 집합이 달라서 String이다
     * (LoL IRON~CHALLENGER / VALORANT IRON~RADIANT / PUBG BRONZE~MASTER).
     * keyCondition.value 를 String으로 둔 것과 같은 이유다.
     *
     * <p><b>어느 사다리의 티어인지는 modeKey가 정한다.</b> RANKED_SOLO면 개인/2인 랭크 티어,
     * RANKED_FLEX_*면 자유 랭크 티어다. 둘은 완전히 별개의 사다리라 같은 사람도 값이 다르다
     * (솔랭 챌린저인데 자랭 실버가 가능하다). 그래서 필드는 하나이고, 무엇을 담을지는
     * 모드가 결정한다.
     *
     * <p><b>@NotNull이 아닌 이유</b> — 티어를 보지 않는 모드가 있다. 일반(NORMAL_*)과
     * 칼바람(ARAM_*)은 게임 자체에 티어 제한이 없어서 받을 이유가 없다. 어느 모드에
     * 필요한지는 gameconfig가 알고 있으므로, 필요한데 빠졌는지는 게임별 validator가
     * 판단한다. 모드 이름으로 가르지 마라.
     *
     * <p><b>임시 필드다.</b> 지금은 사용자 자기신고라 검증할 방법이 없다. 라이엇 계정
     * 연동이 붙으면 userId와 함께 요청에서 사라진다 — 받는 값이 아니라 연동 계정에서
     * 가져오는 값이 되기 때문이다 (docs/02 §6 derived conditions).
     */
    private String tier;

    /**
     * 게임별 핵심 조건. 필수 여부는 게임이 정한다.
     *
     * <p>@NotNull을 빼둔 이유는 tier와 같다 — 이 조건이 없는 게임이 있을 수 있고,
     * 어느 게임에 필요한지는 어노테이션이 아니라 게임별 validator가 판단한다.
     * LoL/VALORANT/PUBG에서는 여전히 필수이며 validator가 거른다.
     */
    @Valid
    private KeyCondition keyCondition;
    @NotNull
    private VoicePreference voicePreference;
    @NotNull
    private PlayPurpose playPurpose;

    @Getter
    @Setter
    public static class KeyCondition {
        @NotNull  private KeyConditionType type;
        @NotBlank private String value;   // "TOP" — 게임별로 달라서 String
    }
}
