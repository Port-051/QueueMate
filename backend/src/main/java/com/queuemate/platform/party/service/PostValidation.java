package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import com.queuemate.platform.party.domain.PlayPurpose;
import com.queuemate.platform.party.domain.VoicePreference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 애너테이션으로 못 거르는 글의 검증 — 이름의 목록에 드는지, 게임마다 다른 포지션과 {@code conditions}.
 * 실패는 전부 400 {@code VALIDATION_FAILED} 이고 {@code details} 에 {@code "필드: 사유"} 한 줄이 실린다.
 * 값의 원본은 {@code contracts/platform-api.md} "글 한 줄" 이다.
 */
final class PostValidation {

    /** PUBG 의 {@code conditions} 가 갖는 단 하나의 키 */
    private static final String PERSPECTIVE = "perspective";
    private static final Set<String> PERSPECTIVES = Set.of("TPP", "FPP");
    private static final String EMPTY_CONDITIONS = "{}";

    private PostValidation()
    {
    }

    /**
     * 목록의 페이지 크기 — 없으면 {@value BoardProperties#DEFAULT_PAGE_LIMIT}, 1 미만이거나 {@value BoardProperties#MAX_PAGE_LIMIT} 초과면 400 이다
     * (2026-09-23 소유자 결정). <b>상한으로 잘라 주지 않는다</b> — 200개를 달라고 했는데 조용히 100개를 주면 클라이언트가 "다 받았다"고 읽는다.
     * 숫자가 아닌 값은 여기 오지 않는다 — 컨트롤러의 형 변환이 먼저 400 이다({@code GlobalExceptionHandler#handleTypeMismatch}).
     */
    static int limit(Integer limit)
    {
        if(limit == null)
        {
            return BoardProperties.DEFAULT_PAGE_LIMIT;
        }
        if(limit < 1 || limit > BoardProperties.MAX_PAGE_LIMIT)
        {
            throw ApiException.validationFailed("limit", "1 ~ " + BoardProperties.MAX_PAGE_LIMIT + " 이어야 합니다");
        }
        return limit;
    }

    static Game game(String name)
    {
        return Game.fromName(name).orElseThrow(
                () -> ApiException.validationFailed("game", "LOL · VALORANT · PUBG 가운데 하나여야 합니다"));
    }

    static VoicePreference voice(String name)
    {
        return VoicePreference.fromName(name).orElseThrow(
                () -> ApiException.validationFailed("voice", "REQUIRED · NO_VOICE 가운데 하나여야 합니다"));
    }

    static PlayPurpose purpose(String name)
    {
        return PlayPurpose.fromName(name).orElseThrow(
                () -> ApiException.validationFailed("purpose", "RANK_UP · NORMAL · FUN 가운데 하나여야 합니다"));
    }

    /** 비어 있지 않아야 한다. 길이는 애너테이션이 본다 */
    static String title(String title)
    {
        if(title == null || title.isBlank())
        {
            throw ApiException.validationFailed("title", "필요합니다");
        }
        return title;
    }

    /**
     * <b>gameconfig 에 있는 모드여야 한다</b>(2026-09-24 소유자 결정 — {@code contracts/platform-api.md} "gameconfig 를 읽는 것").
     * 이제 모든 글이 모드 하나를 갖는다 — 빈 문자열은 400 이고, 고치기에서 모드를 비우는 길은 없어졌다.
     *
     * <p><b>Redis 를 읽으므로 트랜잭션 밖에서 부른다</b>({@code PostService}) — {@code PostStore} 의 짧은 트랜잭션이 Redis 를 기다리며 DB 커넥션을 붙잡지 않게 한다.
     * {@code game} 은 글이 정해진 뒤 바뀌지 않으므로 잠금 밖에서 읽은 게임으로 검증해도 안전하다. 값의 목록이 Redis 에 있어 <b>DB 로는 강제할 수 없는 종류</b>다(CLAUDE.md §5).
     */
    static String mode(GameConfigReader gameConfig, Game game, String mode)
    {
        if(mode == null || mode.isBlank())
        {
            throw ApiException.validationFailed("mode", "필요합니다");
        }
        if(!gameConfig.hasMode(game, mode))
        {
            throw ApiException.validationFailed("mode", game.name() + " 에 없는 모드입니다");
        }
        return mode;
    }

    /** 빈 문자열은 "없다"로 친다 — 고치기에서 {@code description} 을 비우는 길이다({@code PostUpdateRequest}) */
    static String blankToNull(String value)
    {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * 그 게임의 포지션 이름이어야 한다. 겹치는 값은 하나로 친다. {@code null} 은 빈 배열이다. PUBG 는 포지션이 없어 빈 배열만 된다.
     * 돌려주는 집합의 순서는 뜻이 없다 — DB 의 줄에 순서가 없다. 내려 줄 때 게임의 포지션 순서로 세운다.
     */
    static Set<String> wantedPositions(Game game, List<String> positions)
    {
        Set<String> wanted = new LinkedHashSet<>();
        if(positions == null)
        {
            return wanted;
        }
        for(String position : positions)
        {
            if(position == null || !game.positions().contains(position))
            {
                throw ApiException.validationFailed("wantedPositions", game.positions().isEmpty()
                        ? game.name() + " 에는 포지션이 없습니다"
                        : game.name() + " 의 포지션이 아닙니다");
            }
            wanted.add(position);
        }
        return wanted;
    }

    /**
     * {@code conditions} 를 검증하고 저장할 JSON 으로 만든다. PUBG 는 {@code {"perspective": "TPP" | "FPP"}} 가 필수이고 다른 키는 없다.
     * LOL · VALORANT 는 {@code {}} 뿐이다({@code null} 도 {@code {}} 로 친다). <b>모르는 키는 400 이다</b> — 조용히 버리면 클라이언트가 저장된 줄 안다.
     *
     * <p>JSON 을 손으로 짓는다 — 나올 수 있는 값이 셋({@code {}} · TPP · FPP)뿐이고 전부 검증을 지난 상수다.
     */
    static String conditions(Game game, Map<String, Object> conditions)
    {
        Map<String, Object> given = (conditions == null) ? Map.of() : conditions;
        if(game != Game.PUBG)
        {
            if(!given.isEmpty())
            {
                throw ApiException.validationFailed("conditions", game.name() + " 에는 조건이 없습니다 — {} 여야 합니다");
            }
            return EMPTY_CONDITIONS;
        }
        for(String key : given.keySet())
        {
            if(!PERSPECTIVE.equals(key))
            {
                throw ApiException.validationFailed("conditions", "모르는 키입니다");
            }
        }
        Object perspective = given.get(PERSPECTIVE);
        if(!(perspective instanceof String name) || !PERSPECTIVES.contains(name))
        {
            throw ApiException.validationFailed("conditions", "perspective 는 TPP · FPP 가운데 하나여야 합니다");
        }
        return "{\"" + PERSPECTIVE + "\":\"" + name + "\"}";
    }
}
