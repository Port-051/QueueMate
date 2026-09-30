package com.queuemate.platform.common.gameconfig;

import com.queuemate.platform.account.domain.Game;

/**
 * 이 앱이 <b>읽는</b> gameconfig(게임 모드 설정)의 Redis 키. <b>접두사의 원본은 {@code matching} 의 {@code redisKeys/SharedKeys.java}</b>
 * ({@code GAMECONFIG_PREFIX} · {@code TIER_SUFFIX} · {@code TIER_RANGE_INFIX})이고 <b>값의 원본은 {@code matching/seed/gameconfig.redis}</b> 다 —
 * 여기는 그 접두사를 따라 적은 것이다(계약은 {@code contracts/platform-api.md} "gameconfig 를 읽는 것").
 *
 * <p><b>이름은 두 앱의 약속이다.</b> 어긋나도 컴파일 · 테스트는 통과하고 검증만 조용히 틀린다 — {@code mode} · {@code tier} 검증은 <b>fail-open</b> 이라
 * 접두사를 한 글자 틀리면 "gameconfig 가 안 심겼다"로 읽혀 검증이 통째로 꺼진다({@link GameConfigReader}).
 * 그래서 접두사를 이 한 곳에만 둔다. 바꿀 때는 {@code matching} 과 같이 바꾼다.
 *
 * <p><b>이 앱은 이 키들에 절대 쓰지 않는다</b> — 심는 것은 운영자(배포 때 seed 를 붓는다)이고 {@code matching} 도 읽는 쪽이다.
 * 모드별 설정 HASH 의 내용 가운데 <b>{@code tierRule} · {@code targetPartySize} 둘(2026-09-29 부터 {@code tierLadder} 까지 셋 — 필드 이름은
 * {@link GameConfigReader} 한 곳에 있다)과 티어별 허용 범위({@code qm:gameconfig:{GAME}:tier-range:{MODE}})는 2026-09-28 부터 읽는다</b> — 자동 매칭이 게시판 방에 먼저 합류하는 길(P-28 · docs/11 D-40)이 "내 티어가 그 모드의 허용 범위 안인가" 를
 * 봐야 해서다({@code party.service.AutoJoinService}). <b>2026-09-30 부터 {@code positionUniqueness} 도 읽는다</b> — 모집 글의 방장 포지션이
 * 그 모드에 포지션이 있는지를 봐야 해서다({@link GameConfigReader#modePositions} · P-38). <b>같은 날부터 {@code targetPartySize} 는 모집 글을 쓸 때 · 모드를 고칠 때 읽는다</b> —
 * 게시판 방의 정원이 그 모드의 인원이 됐다({@link GameConfigReader#partySize} · P-41. 게시판 방 먼저 합류는 글에 적힌 정원을 본다). 그 밖의 필드는 여전히 읽지 않는다 — 이 앱은 그 뜻을 모른다.
 */
public final class GameConfigKeys {

    private static final String PREFIX = "qm:gameconfig:";

    private GameConfigKeys()
    {
    }

    /**
     * 모드별 설정 HASH. <b>이 키가 있다 = 그 게임에 그 모드가 있다</b> — 모드 목록 SET 은 원본 seed 가 일부러 없앴다
     * (목록을 따로 두면 모드를 하나 고칠 때 두 곳이 어긋난다). 그래서 모드가 있는지는 이 키의 {@code EXISTS} 가 답한다.
     * 필드 가운데 {@code tierRule}({@code NONE} · {@code EXIST}) · {@code tierLadder}(2026-09-29 — 그 모드가 보는 티어 사다리) 는
     * 게시판 방 먼저 합류가 읽고({@link GameConfigReader#modeConfig}), {@code targetPartySize} 는 모집 글이 정원으로 읽는다({@link GameConfigReader#partySize} — 2026-09-30 · P-41).
     */
    public static String mode(Game game, String modeKey)
    {
        return PREFIX + game.name() + ":" + modeKey;
    }

    /**
     * 티어 사다리 ZSET(원소 = 티어 이름, score = 사다리의 단계 번호). {@code mode} · {@code tier} 검증은 <b>있는 이름인지만</b> 본다 — {@code ZSCORE} 가 {@code null} 이면 없는 티어다.
     * 게시판 방 먼저 합류는 score 도 읽는다 — 허용 범위 {@code MIN:MAX} 의 안인지를 단계 번호로 비교한다({@link GameConfigReader#tierScores}).
     *
     * <p><b>이 키는 "그 게임의 gameconfig 가 심겼는가"도 겸한다</b> — 세 게임 모두 사다리가 있고(seed), 모드는 목록 키가 없어 "하나도 없다"를 물을 데가 없다.
     */
    public static String tierLadder(Game game)
    {
        return PREFIX + game.name() + ":tier";
    }

    /**
     * 티어별 허용 범위 HASH(필드 = 티어 이름, 값 = {@code MIN:MAX} 또는 {@code SOLO_ONLY}). 원본은 {@code matching} 의 {@code SharedKeys#tierRangeKey} —
     * {@code qm:gameconfig:LOL:tier-range:RANKED_SOLO}. 2026-09-28 부터 읽는다(P-28) — 파티를 만든 사람(게시판이면 방장)의 줄이 그 방의 허용 범위다.
     */
    public static String tierRange(Game game, String modeKey)
    {
        return PREFIX + game.name() + ":tier-range:" + modeKey;
    }
}
