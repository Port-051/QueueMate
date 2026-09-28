package com.queuemate.platform.common.gameconfig;

/**
 * 모드별 설정 HASH 에서 이 앱이 읽는 두 필드({@link GameConfigReader#modeConfig}). 값의 뜻은 {@code matching/seed/gameconfig.redis} 의 것이다.
 *
 * @param tierRule        그 모드가 티어를 보는가 — {@code NONE}(안 본다) · {@code EXIST}(본다 — 어떻게 보는지는 tier-range 표가 정한다). 필드가 없으면 {@code null}
 * @param targetPartySize 그 모드의 파티 정원. 필드가 없거나 숫자가 아니면 {@code null}
 */
public record ModeConfig(String tierRule, Integer targetPartySize) {

    public static final String TIER_RULE_NONE = "NONE";
    public static final String TIER_RULE_EXIST = "EXIST";

    public boolean seesTier()
    {
        return TIER_RULE_EXIST.equals(tierRule);
    }
}
