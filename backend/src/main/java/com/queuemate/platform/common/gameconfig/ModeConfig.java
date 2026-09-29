package com.queuemate.platform.common.gameconfig;

/**
 * 모드별 설정 HASH 에서 이 앱이 읽는 세 필드({@link GameConfigReader#modeConfig}). 값의 뜻은 {@code matching/seed/gameconfig.redis} 의 것이다.
 *
 * @param tierRule        그 모드가 티어를 보는가 — {@code NONE}(안 본다) · {@code EXIST}(본다 — 어떻게 보는지는 tier-range 표가 정한다). 필드가 없으면 {@code null}
 * @param targetPartySize 그 모드의 파티 정원. 필드가 없거나 숫자가 아니면 {@code null}
 * @param tierLadder      그 모드가 <b>어느 티어 사다리</b>를 보는가(2026-09-29 — P-36 · docs/11 D-48) — 게임 계정의 {@code tiers} 의 키
 *                        ({@code RANKED_SOLO} → {@code SOLO} · {@code RANKED_FLEX_*} → {@code FLEX} · VALORANT 경쟁전 → {@code COMPETITIVE} ·
 *                        PUBG 랭크 → {@code RANKED}). {@code tierRule NONE} 모드에는 없다. 옛 seed 에도 없다 — 그러면 {@code null}.
 *                        <b>이 앱은 모드 이름으로 사다리를 가르지 않는다</b> — 이 필드만 본다
 */
public record ModeConfig(String tierRule, Integer targetPartySize, String tierLadder) {

    public static final String TIER_RULE_NONE = "NONE";
    public static final String TIER_RULE_EXIST = "EXIST";

    public boolean seesTier()
    {
        return TIER_RULE_EXIST.equals(tierRule);
    }
}
