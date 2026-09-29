package com.queuemate.platform.account.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 지원 게임 셋(docs/11 #8)과 게임마다의 포지션 목록. 원본은 {@code contracts/platform-api.md} "계정" 이다.
 *
 * <p>포지션의 이름은 {@code matching} 의 {@code LolPosition} · {@code ValorantRole} 과 같다 — 모집 글의 "찾는 포지션"({@code wantedPositions})과
 * 게시판 방 먼저 합류의 {@code keyCondition} 이 이 목록으로 검증된다(CLAUDE.md §7.1). <b>PUBG 는 포지션이 없다</b>.
 * <b>게임 계정의 주 포지션은 없다</b>(2026-09-29 소유자 결정 — P-35. 그 칸만 검증하던 {@code allowsPosition} 도 같이 없앴다).
 * DB 의 CHECK({@code game_accounts_game_check})도 같은 세 이름을 건다.
 */
public enum Game {

    LOL(List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT"), Set.of()),
    VALORANT(List.of("DUELIST", "INITIATOR", "CONTROLLER", "SENTINEL"), Set.of()),
    PUBG(List.of(), Set.of("STEAM", "KAKAO"));

    /** 적은 순서를 지킨다 — 모집 글의 "찾는 포지션"을 늘 같은 순서로 내려 주려는 것이다(DB 의 줄에는 순서가 없다) */
    private final Set<String> positions;
    /** 서버(플랫폼)를 가르는 게임은 PUBG 뿐이다. DB 의 CHECK({@code game_accounts_server_check})도 같은 값을 건다 */
    private final Set<String> servers;

    Game(List<String> positions, Set<String> servers)
    {
        this.positions = Collections.unmodifiableSet(new LinkedHashSet<>(positions));
        this.servers = servers;
    }

    public Set<String> servers()
    {
        return servers;
    }

    /** {@code null} 은 "서버를 정하지 않았다"라서 어느 게임이든 된다. 서버가 없는 게임은 {@code null} 만 된다 */
    public boolean allowsServer(String server)
    {
        return server == null || servers.contains(server);
    }

    public Set<String> positions()
    {
        return positions;
    }

    /** 경로 변수로 들어온 이름을 찾는다. 대소문자를 봐주지 않는다 — 계약의 이름은 대문자다 */
    public static Optional<Game> fromName(String name)
    {
        for(Game game : values())
        {
            if(game.name().equals(name))
            {
                return Optional.of(game);
            }
        }
        return Optional.empty();
    }
}
