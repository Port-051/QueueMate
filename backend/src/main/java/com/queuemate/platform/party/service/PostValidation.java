package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import com.queuemate.platform.common.gameconfig.ModePositions;
import com.queuemate.platform.party.domain.VoicePreference;
import com.queuemate.platform.party.dto.PostUpdateRequest;

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
     * <b>모드에 따른 규칙</b>(있는 모드는 하나 이상 · 없는 모드는 빈 배열만 — 2026-09-30)은 이 다음에 {@link #wantedPositionsForMode} 가 본다.
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
     * 그 모드에 포지션이 있는가 — 방장 포지션({@link #hostPosition})의 규칙을 정한다(2026-09-30 소유자 결정 — P-38).
     * <b>포지션이 없는 게임(PUBG)은 gameconfig 를 읽지 않고 {@link ModePositions#NO}</b> 다. 모드가 비어 있는 옛 글은 물을 데가 없어 {@link ModePositions#UNKNOWN} 이다.
     *
     * <p>{@link #mode} 처럼 <b>Redis 를 읽으므로 트랜잭션 밖에서 부른다</b>({@code PostService}). 못 읽으면 {@link ModePositions#UNKNOWN} 이다(fail-open — {@link GameConfigReader#modePositions}).
     */
    static ModePositions modePositions(GameConfigReader gameConfig, Game game, String mode)
    {
        if(game.positions().isEmpty())
        {
            return ModePositions.NO;
        }
        return (mode == null || mode.isBlank()) ? ModePositions.UNKNOWN : gameConfig.modePositions(game, mode);
    }

    /** 포지션이 있는 게임의, 포지션이 있다고 <b>확인된</b> 모드다 — 모르면({@link ModePositions#UNKNOWN}) 아니다 */
    private static boolean hasPositions(Game game, ModePositions modePositions)
    {
        return !game.positions().isEmpty() && modePositions == ModePositions.YES;
    }

    /** 포지션이 없는 게임(PUBG)이거나, 포지션이 없다고 <b>확인된</b> 모드다 — 모르면({@link ModePositions#UNKNOWN}) 아니다 */
    private static boolean noPositions(Game game, ModePositions modePositions)
    {
        return game.positions().isEmpty() || modePositions == ModePositions.NO;
    }

    /**
     * 찾는 포지션의 <b>모드에 따른 규칙</b>(2026-09-30 소유자 결정 — P-38). 포지션이 있는 모드면 <b>하나 이상 필수</b>다 — "찾는 포지션이 빈 글"(누구든)을 없앴다.
     * 방장 포지션과 겹칠 수 없으므로 게시판 방 먼저 합류가 방장과 같은 포지션인 사람을 저절로 들이지 않는다({@code AutoJoinService} 는 그대로다).
     * 포지션이 없는 모드(ARAM 등)는 <b>빈 배열만</b> 된다 — 값이 있으면 400 이다(조용히 버리지 않는다). PUBG 는 {@link #wantedPositions} 가 이미 거절했다.
     * <b>모르면({@link ModePositions#UNKNOWN}) 보지 않는다</b>(fail-open — 이름은 {@link #wantedPositions} 가 봤다).
     *
     * @param wanted 이름을 검증한 찾는 포지션({@link #wantedPositions})
     */
    static Set<String> wantedPositionsForMode(Game game, ModePositions modePositions, Set<String> wanted)
    {
        if(hasPositions(game, modePositions) && wanted.isEmpty())
        {
            throw ApiException.validationFailed("wantedPositions", "하나 이상 필요합니다");
        }
        if(modePositions == ModePositions.NO && !wanted.isEmpty())
        {
            throw ApiException.validationFailed("wantedPositions", "포지션이 없는 모드입니다");
        }
        return wanted;
    }

    /**
     * 고치기({@code PATCH})의 찾는 포지션 — <b>고친 뒤의 모양</b>을 {@link #wantedPositionsForMode} 로 본다(방장 포지션의 {@link #editedHostPosition} 과 같은 짜임이다).
     * {@code mode} · {@code hostPosition} · {@code wantedPositions} 를 하나도 주지 않았으면 보지 않고, {@code wantedPositions} 를 주지 않았는데 고친 뒤의 모드에
     * 포지션이 없으면 <b>적혀 있던 것을 비운다</b>(포지션이 있는 모드 → 없는 모드).
     *
     * @param wanted 고친 뒤의 찾는 포지션 — 준 값(이름을 검증했다) 또는 적혀 있던 값
     */
    static Set<String> editedWantedPositions(Game game, ModePositions modePositions, PostUpdateRequest request, Set<String> wanted)
    {
        if(!touchesPositions(request))
        {
            return wanted;
        }
        if(request.wantedPositions() == null && noPositions(game, modePositions))
        {
            return new LinkedHashSet<>();
        }
        return wantedPositionsForMode(game, modePositions, wanted);
    }

    /** 포지션에 닿는 고치기인가 — {@code mode} · {@code hostPosition} · {@code wantedPositions} 가운데 하나라도 줬다 */
    private static boolean touchesPositions(PostUpdateRequest request)
    {
        return request.mode() != null || request.hostPosition() != null || request.wantedPositions() != null;
    }

    /**
     * <b>방장 자신의 포지션</b>(2026-09-30 소유자 결정 — P-38). 거르는 순서는 ① 포지션이 있는 모드인데 없다 → {@code "필요합니다"}
     * ② 포지션이 없는 게임 · 모드인데 있다 → 거절(조용히 버리지 않는다 — P-35 의 {@code mainPosition} 과 같은 뜻이다) ③ 그 게임의 포지션 이름이 아니다
     * ④ 찾는 포지션({@code wantedPositions})에 들어 있다. 전부 400 {@code VALIDATION_FAILED} 이고 {@code details} 는 {@code "hostPosition: …"} 한 줄이다.
     *
     * <p><b>모르면({@link ModePositions#UNKNOWN}) 요구하지도 거절하지도 않는다</b> — gameconfig 를 못 읽었거나 안 심겼을 때다({@link #mode} 의 fail-open 과 같다).
     * 그때도 ③ ④ 는 본다 — 이름의 목록({@link Game#positions()})과 찾는 포지션은 Redis 없이 알 수 있다.
     *
     * @param wanted 이미 검증한 찾는 포지션({@link #wantedPositions})
     */
    static String hostPosition(Game game, ModePositions modePositions, String hostPosition, Set<String> wanted)
    {
        if(hostPosition == null)
        {
            if(hasPositions(game, modePositions))
            {
                throw ApiException.validationFailed("hostPosition", "필요합니다");
            }
            return null;
        }
        if(noPositions(game, modePositions))
        {
            throw ApiException.validationFailed("hostPosition", game.positions().isEmpty()
                    ? game.name() + " 에는 포지션이 없습니다"
                    : "포지션이 없는 모드입니다");
        }
        if(!game.positions().contains(hostPosition))
        {
            throw ApiException.validationFailed("hostPosition", game.name() + " 의 포지션이 아닙니다");
        }
        if(wanted.contains(hostPosition))
        {
            throw ApiException.validationFailed("hostPosition", "찾는 포지션(wantedPositions)과 겹칠 수 없습니다");
        }
        return hostPosition;
    }

    /**
     * 고치기({@code PATCH})의 방장 포지션 — <b>고친 뒤의 모양</b>을 {@link #hostPosition} 의 규칙으로 본다(Claude 가 정한 세부 — P-38).
     * <ul>
     *   <li>{@code mode} · {@code hostPosition} · {@code wantedPositions} 를 <b>하나도 주지 않았으면 보지 않는다</b> — 제목 · 소개 · 음성만 고치는 요청이
     *       그 전에 쓴 글(방장 포지션이 없다)에서도 된다</li>
     *   <li>{@code hostPosition} 을 주지 않았는데 고친 뒤의 모드에 포지션이 없으면 <b>적혀 있던 값을 비운다</b>(포지션이 있는 모드 → 없는 모드)</li>
     *   <li>그 밖에는 준 값(없으면 적혀 있던 값)을 고친 뒤의 모드 · 찾는 포지션으로 검증한다 — 포지션이 있는 모드로 바꾸며 방장 포지션을 안 주면
     *       (적혀 있던 것도 없으면) 400 {@code "hostPosition: 필요합니다"} 다</li>
     * </ul>
     * 비우는 길은 따로 없다({@code null} 은 "그대로" 다 — 다른 칸과 같다). 빈 문자열은 포지션 이름이 아니라 400 이다.
     *
     * @param modePositions 고친 뒤의 모드에 포지션이 있는가({@link #modePositions})
     * @param stored        지금 글에 적힌 방장 포지션
     * @param wanted        고친 뒤의 찾는 포지션
     */
    static String editedHostPosition(Game game, ModePositions modePositions, PostUpdateRequest request, String stored,
                                     Set<String> wanted)
    {
        if(!touchesPositions(request))
        {
            return stored;
        }
        if(request.hostPosition() == null && noPositions(game, modePositions))
        {
            return null;
        }
        return hostPosition(game, modePositions, request.hostPosition() != null ? request.hostPosition() : stored, wanted);
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
