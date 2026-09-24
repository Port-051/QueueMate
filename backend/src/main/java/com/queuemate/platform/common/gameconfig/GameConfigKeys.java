package com.queuemate.platform.common.gameconfig;

import com.queuemate.platform.account.domain.Game;

/**
 * 이 앱이 <b>읽는</b> gameconfig(게임 모드 설정)의 Redis 키. <b>접두사의 원본은 {@code matching} 의 {@code redisKeys/SharedKeys.java}</b>
 * ({@code GAMECONFIG_PREFIX} · {@code TIER_SUFFIX})이고 <b>값의 원본은 {@code matching/seed/gameconfig.redis}</b> 다 —
 * 여기는 그 접두사를 따라 적은 것이다(계약은 {@code contracts/platform-api.md} "gameconfig 를 읽는 것").
 *
 * <p><b>이름은 두 앱의 약속이다.</b> 어긋나도 컴파일 · 테스트는 통과하고 검증만 조용히 틀린다 — 이 앱은 <b>fail-open</b> 이라
 * 접두사를 한 글자 틀리면 "gameconfig 가 안 심겼다"로 읽혀 {@code mode} · {@code tier} 검증이 통째로 꺼진다({@link GameConfigReader}).
 * 그래서 접두사를 이 한 곳에만 둔다. 바꿀 때는 {@code matching} 과 같이 바꾼다.
 *
 * <p><b>이 앱은 이 키들에 절대 쓰지 않는다</b> — 심는 것은 운영자(배포 때 seed 를 붓는다)이고 {@code matching} 도 읽는 쪽이다.
 * 모드별 설정 HASH 의 <b>내용({@code targetPartySize} · {@code tierRule} 등)은 읽지 않는다</b> — 이 앱은 그 뜻을 모르고 알 필요도 없다.
 * 티어별 허용 범위({@code qm:gameconfig:{GAME}:tier-range:{MODE}})는 <b>매칭의 판정 규칙</b>이라 이 앱과 무관하다 — 그래서 여기에 그 이름이 없다.
 */
public final class GameConfigKeys {

    private static final String PREFIX = "qm:gameconfig:";

    private GameConfigKeys()
    {
    }

    /**
     * 모드별 설정 HASH. <b>이 키가 있다 = 그 게임에 그 모드가 있다</b> — 모드 목록 SET 은 원본 seed 가 일부러 없앴다
     * (목록을 따로 두면 모드를 하나 고칠 때 두 곳이 어긋난다). 그래서 모드가 있는지는 이 키의 {@code EXISTS} 가 답한다.
     */
    public static String mode(Game game, String modeKey)
    {
        return PREFIX + game.name() + ":" + modeKey;
    }

    /**
     * 티어 사다리 ZSET(원소 = 티어 이름, score = 사다리의 단계 번호). 이 앱은 <b>있는 이름인지만</b> 본다 — {@code ZSCORE} 가 {@code null} 이면 없는 티어다.
     * score 의 뜻(몇 단계 차이인가)은 매칭의 것이라 읽지 않는다.
     *
     * <p><b>이 키는 "그 게임의 gameconfig 가 심겼는가"도 겸한다</b> — 세 게임 모두 사다리가 있고(seed), 모드는 목록 키가 없어 "하나도 없다"를 물을 데가 없다.
     */
    public static String tierLadder(Game game)
    {
        return PREFIX + game.name() + ":tier";
    }
}
