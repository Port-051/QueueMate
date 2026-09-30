package com.queuemate.platform.common.gameconfig;

/**
 * 그 모드에 <b>포지션이 있는가</b> — 모드별 설정 HASH 의 {@code positionUniqueness} 로 가른다({@link GameConfigReader#modePositions}).
 * 모집 글의 방장 포지션({@code hostPosition})이 필수인지 · 받으면 안 되는지를 정한다(2026-09-30 소유자 결정 — {@code contracts/platform-api.md} P-38).
 *
 * <p>값의 원본은 {@code matching/seed/gameconfig.redis} 다 — LoL {@code RANKED_*} · {@code NORMAL_*} 와 VALORANT 네 모드가 {@code true},
 * LoL {@code ARAM_*} 가 {@code false}, PUBG 모드에는 그 필드가 없다. <b>이 앱은 모드 이름으로 가르지 않는다</b> — 이 필드만 본다.
 */
public enum ModePositions {

    /** {@code positionUniqueness} 가 {@code "true"} 다 — 포지션이 있는 모드 */
    YES,
    /** 모드 HASH 는 있는데 {@code positionUniqueness} 가 {@code "true"} 가 아니다(없거나 {@code "false"}) — 포지션이 없는 모드 */
    NO,
    /** 모른다 — Redis 를 못 읽었거나 gameconfig 가 안 심겼다(fail-open — 부르는 쪽은 요구하지도 거절하지도 않는다) */
    UNKNOWN
}
