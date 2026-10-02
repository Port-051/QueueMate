package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * LoL 의 전적을 Riot API 에서 긁는다 — <b>응답의 JSON 칸을 읽는 곳은 이 클래스 하나다</b>(부르는 곳은 {@link RiotApiClient}).
 * Riot 이 칸 이름을 바꾸면 여기만 고친다.
 *
 * <p>순서는 여섯 걸음이다 — Riot 호출은 경기 20판(기본 — {@code match-count}, 2026-10-03 멤버 상세 요청)이면
 * <b>24번</b>(계정 · 리그 · 경기 id · 경기 20 · 숙련도 — 대륙 주소 22 · 플랫폼 주소 2). 경기가 하나도 없으면 4번이다(숙련도는 경기와 무관하게 부른다).
 * 평균 K/D/A · 연승 · 최근 경기의 승 · 패 줄({@code detail.recentResults} — 2026-09-30, P-43)은 그 경기들로 내고, 승/패는 경기 수와 무관한 솔로랭크 시즌 누적,
 * 모스트 챔피언은 경기와 무관한 통산 숙련도 상위 셋이다.
 * <ol>
 *   <li>게임 닉네임을 {@code 이름#태그} 로 가른다 — <b>태그가 없으면 긁지 않는다</b>({@code null} 을 돌려준다)</li>
 *   <li>{@code account-v1} → {@code puuid}</li>
 *   <li>{@code league-v4}({@code entries/by-puuid}) 에서 <b>솔로랭크 줄</b>의 승/패(= 시즌 누적)와 티어, <b>자유랭크 줄</b>의 티어.
 *       (2026-09-29 까지는 {@code summoner-v4} 로 소환사 {@code id} 를 받아 {@code entries/by-summoner} 를 불렀다 — 실제 소환사 응답에 {@code id} 가 없어 늘 비었다.
 *       그 호출을 없애 25번이 24번이 됐다 — 경기 20판일 때의 수다)</li>
 *   <li>{@code match-v5} → 최근 경기 id 목록(새 경기가 먼저)</li>
 *   <li>경기마다 참가자 가운데 <b>그 {@code puuid} 인 사람</b>의 K/D/A · 승패. 참가자 전원의 챔피언 번호 → 이름도 모아 둔다(6걸음의 이름표에 없는 새 챔피언을 메운다).
 *       승패는 경기마다 {@code detail.recentResults} 에도 한 칸씩 싣는다 — <b>Riot 을 더 부르지 않는다</b>(이미 읽은 경기다 · {@link #detail}).
 *       <b>커스텀 게임은 통째로 뺀다</b>(2026-10-02 소유자 지시 — Riot 정책이 커스텀의 전적을 공개로 보여 주지 못하게 한다 · {@link #customOrUnknown}).
 *       대신 더 받지 않는다 — 최근 20판 가운데 커스텀이 있으면 그만큼 적게 나온다</li>
 *   <li>{@code champion-mastery-v4} {@code top?count=3} → <b>모스트 챔피언 = 숙련도 점수 상위 셋</b>(2026-09-30 소유자 결정 — P-39).
 *       칸은 챔피언 · 레벨 · 점수 셋뿐이다 — 최근 경기의 판 수 · 승률을 섞지 않는다({@link #detail}).
 *       <b>이 걸음만은 실패해도 전적을 살린다</b>(WARN 하고 모스트 챔피언을 빈 배열로 — 전적의 본체가 아니다)</li>
 * </ol>
 * (2026-09-30 까지는 모스트 챔피언이 <b>최근 경기에서 많이 한 셋</b>이었고 판 수 · 승률 옆에 그 챔피언의 통산 숙련도를 붙였다 — 최근 10판의 수와 통산 숙련도가
 * 한 줄에 섞여 소유자가 "숙련도만, 숙련도가 가장 높은 챔피언으로" 바꿨다. 숙련도는 그때도 한 번에 받았으므로 호출 수는 그대로다.)
 *
 * <p><b>티어도 여기서 만든다</b>(2026-09-27 소유자 결정 — LoL 은 요청에서 {@code tier} 를 빼고 Riot 에서 채운다).
 * <b>사다리가 둘이다</b>(2026-09-29 소유자 결정 "모드별 티어를 무조건 저장한다" — P-36) — 솔로랭크 줄({@code RANKED_SOLO_5x5})이 {@code SOLO},
 * 자유랭크 줄({@code RANKED_FLEX_SR})이 {@code FLEX} 다. 그 전에는 자유랭크 줄을 버려 자유랭크 매칭이 솔로랭크 티어로 돌았다.
 * 줄의 {@code tier} + {@code rank} 를 gameconfig 사다리의 이름으로({@link #ladderName}) — 두 큐가 같은 이름 목록({@code qm:gameconfig:LOL:tier})을 쓴다.
 * 줄이 없으면(그 큐 언랭) 그 사다리는 {@code null}, 만든 이름이 사다리에 없으면(Riot 이 티어를 새로 만들었다 등) WARN 하고 {@code null}.
 * Redis 를 못 읽으면 {@link GameConfigReader} 의 fail-open 대로 그 이름을 그대로 둔다. <b>승/패 · 연승 등 {@code stats} 는 지금대로 솔로랭크 줄이다</b> — 자유랭크의 승/패를 섞지 않는다.
 *
 * <p><b>주 포지션은 만들지 않는다</b>(같은 날 소유자 결정) — 최근 경기의 {@code teamPosition} 은 "지금까지 한 것"이라 뜻이 다르다. 그래서 그 칸을 읽지도 않는다.
 * (2026-09-29 부터는 게임 계정에 주 포지션 칸 자체가 없다 — P-35.)
 *
 * <p><b>Riot ID 가 없으면({@code account-v1} 404) {@link RiotIdNotFoundException}</b> 이다 — 게임 계정 연결이 404 {@code RIOT_ID_NOT_FOUND} 로 옮긴다.
 *
 * <p><b>없는 칸에 터지지 않는다</b> — 못 읽은 칸은 {@code null} 이거나 그 경기를 건너뛴다({@code JsonText} 의 방식).
 * 지식이 낡아 실제 응답이 다를 수 있으므로, 칸 하나가 사라져도 나머지로 스냅숏이 만들어진다. 다만 {@code puuid} 를 못 읽으면 아무것도 할 수 없어 끝낸다.
 *
 * <p><b>평점(OP.GG 의 5.57 같은 값)과 MVP · Ace 배지는 만들지 않는다</b> — Riot API 에 없고 OP.GG 가 스스로 계산한 값이다
 * (소유자가 2026-09-23 에 다시 확인했다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LolStatsProvider implements GameStatsProvider {

    // ---- Riot 응답의 칸 이름. 이 목록이 이 클래스에 있는 이유는 위 주석에 있다 ----
    private static final String FIELD_PUUID = "puuid";
    private static final String FIELD_QUEUE_TYPE = "queueType";
    private static final String FIELD_TIER = "tier";
    private static final String FIELD_RANK = "rank";
    private static final String FIELD_WINS = "wins";
    private static final String FIELD_LOSSES = "losses";
    private static final String FIELD_INFO = "info";
    private static final String FIELD_QUEUE_ID = "queueId";
    private static final String FIELD_GAME_TYPE = "gameType";
    private static final String FIELD_PARTICIPANTS = "participants";
    private static final String FIELD_CHAMPION_NAME = "championName";
    private static final String FIELD_KILLS = "kills";
    private static final String FIELD_DEATHS = "deaths";
    private static final String FIELD_ASSISTS = "assists";
    private static final String FIELD_WIN = "win";
    /** 챔피언 번호(숫자) — 숙련도 응답과 경기 참가자에 같은 이름으로 있다. <b>숙련도에는 {@code championName} 이 없다</b> — 이름은 {@link LolChampionNames} 에서 찾는다 */
    private static final String FIELD_CHAMPION_KEY = "championId";
    private static final String FIELD_CHAMPION_LEVEL = "championLevel";
    private static final String FIELD_CHAMPION_POINTS = "championPoints";

    /** 솔로랭크 줄을 가르는 값. 승/패(시즌 누적)는 이 줄에서만 읽는다 — 자유랭크의 승/패를 섞지 않는다 */
    private static final String SOLO_QUEUE = "RANKED_SOLO_5x5";
    /** 자유랭크 줄을 가르는 값. <b>티어만</b> 읽는다(2026-09-29 — P-36) */
    private static final String FLEX_QUEUE = "RANKED_FLEX_SR";

    /**
     * 커스텀 게임을 가르는 두 값 — 경기의 {@code info.queueId} · {@code info.gameType}. 2026-10-02 실제 키로 본 커스텀 경기 여섯이 전부
     * {@code queueId 0} · {@code gameType "CUSTOM_GAME"} 이었다(랭크 · 일반은 {@code "MATCHED_GAME"}). 어느 한쪽만 맞아도 커스텀으로 본다
     */
    private static final int CUSTOM_QUEUE_ID = 0;
    private static final String CUSTOM_GAME_TYPE = "CUSTOM_GAME";

    /** 사다리 키 — {@code Game.LOL.tierLadders()} 의 둘이다. 솔로랭크 줄 → {@code SOLO}, 자유랭크 줄 → {@code FLEX} */
    static final String LADDER_SOLO = "SOLO";
    static final String LADDER_FLEX = "FLEX";

    /** {@code detail} 의 모스트 챔피언은 셋까지다 ({@code contracts/platform-api.md} "게임 프로필") — 숙련도 {@code top?count=} 에 그대로 싣는다 */
    static final int MOST_CHAMPIONS = 3;

    /**
     * {@code detail} 의 최근 경기 승 · 패 줄의 칸 이름과 두 값(2026-09-30 소유자 요청 — duo.gg 식 "15승 5패 (20 게임)" + 승/패 칸 줄 · P-43).
     * 칸 이름 · 글자 둘 · 순서(새 경기가 먼저)는 Claude 가 정한 세부다({@link #detail})
     */
    static final String RECENT_RESULTS = "recentResults";
    static final String RESULT_WIN = "W";
    static final String RESULT_LOSS = "L";

    /**
     * 모스트 챔피언의 순서 — 점수 내림차순 → 같으면 레벨 내림차순 → 같으면 {@code championId}(이름의 글자) 오름차순(Claude 가 정한 세부 — P-39).
     * Riot 이 이미 점수 순으로 주지만 같은 점수의 순서는 말하지 않아 다시 줄 세운다. 셋째와 넷째가 같은 점수면 누가 셋에 드는지는 Riot 이 고른다(넷째를 받지 않는다)
     */
    static final Comparator<MostChampion> MOST_CHAMPION_ORDER = Comparator
            .comparingLong(MostChampion::masteryPoints).reversed()
            .thenComparing(Comparator.comparingInt(MostChampion::masteryLevel).reversed())
            .thenComparing(MostChampion::championId);

    /**
     * 단이 없는 티어 — 사다리 이름에 {@code _단} 을 붙이지 않는다({@code MASTER}). Riot 은 이 셋에도 {@code rank: "I"} 를 준다.
     * 사다리의 원본은 {@code matching/seed/gameconfig.redis} 의 {@code qm:gameconfig:LOL:tier} 다
     */
    private static final List<String> APEX_TIERS = List.of("MASTER", "GRANDMASTER", "CHALLENGER");

    /** Riot 의 단(로마 숫자) → 사다리 이름의 숫자. 사다리는 {@code GOLD_4} … {@code GOLD_1} 이다 */
    private static final Map<String, String> DIVISIONS = Map.of("I", "1", "II", "2", "III", "3", "IV", "4");

    private final RiotApiClient riot;
    private final RiotProperties properties;
    private final ObjectMapper objectMapper;
    private final GameConfigReader gameConfig;
    private final LolChampionNames championNames;

    @Override
    public Game game()
    {
        return Game.LOL;
    }

    @Override
    public boolean configured()
    {
        return properties.configured();
    }

    /** {@code server} 는 보지 않는다 — LoL 에는 서버가 없다(Riot 의 지역은 설정의 주소다) */
    @Override
    public StatsSnapshot fetch(String gameNickname, String server)
    {
        String[] riotId = splitRiotId(gameNickname);
        if(riotId == null)
        {
            // 연결은 이 형식을 먼저 400 으로 거른다({@link #isRiotId}). 여기 오는 것은 그 검사 전에 저장된 옛 줄뿐이다
            log.warn("LoL 게임 닉네임이 '이름#태그' 가 아니라 전적을 긁지 않는다");
            return null;
        }

        String puuid = text(account(riotId[0], riotId[1]).path(FIELD_PUUID));
        if(puuid == null)
        {
            log.warn("Riot 의 계정 응답에 {} 가 없다 — 전적을 긁지 않는다", FIELD_PUUID);
            return null;
        }

        Ranks ranks = ranks(puuid);
        Matches matches = recentMatches(puuid);
        List<Played> played = matches.played();

        int games = played.size();
        Map<String, String> tiers = new LinkedHashMap<>();
        tiers.put(LADDER_SOLO, ranks.solo() == null ? null : onLadder(ranks.solo().tier()));
        tiers.put(LADDER_FLEX, onLadder(ranks.flexTier()));
        return new StatsSnapshot(puuid, games,
                average(played, Played::kills, games),
                average(played, Played::deaths, games),
                average(played, Played::assists, games),
                ranks.solo() == null ? null : ranks.solo().wins(),
                ranks.solo() == null ? null : ranks.solo().losses(),
                games == 0 ? null : winStreak(played),
                detail(puuid, matches.championNames(), played),
                tiers);
    }

    /**
     * {@code 이름#태그} 꼴인가 — 게임 계정 연결이 <b>Riot 을 부르기 전에</b> 400 으로 거르는 데 쓴다({@code account.service.UserService}).
     * 가르는 규칙은 {@link #fetch} 와 같다(한 곳에 둔다).
     */
    public static boolean isRiotId(String gameNickname)
    {
        return splitRiotId(gameNickname) != null;
    }

    /** {@code account-v1}. <b>404 만 따로 가른다</b> — 이름#태그가 Riot 에 없다는 뜻이고 사용자가 고칠 수 있다 */
    private JsonNode account(String gameName, String tagLine)
    {
        try
        {
            return riot.account(gameName, tagLine);
        }
        catch(RiotApiException e)
        {
            if(e.status() == 404)
            {
                throw new RiotIdNotFoundException(e);
            }
            throw e;
        }
    }

    // ---- 3걸음: 솔로랭크의 시즌 누적 승/패와 티어 · 자유랭크의 티어 ----

    /**
     * {@code puuid} 의 리그 목록에서 솔로랭크 줄과 자유랭크 줄의 티어. 목록이 배열이 아니면 둘 다 비어 있다 —
     * 그러면 {@code wins} · {@code losses} · 두 사다리가 전부 비어 나간다. 솔로랭크 줄은 있는데 승/패 한쪽을 못 읽으면 승/패만 비우고 티어는 살린다.
     * 같은 큐의 줄이 둘 오면(정상이면 없다) 앞의 것을 쓴다
     */
    private Ranks ranks(String puuid)
    {
        JsonNode entries = riot.leagueEntries(puuid);
        if(!entries.isArray())
        {
            log.warn("Riot 의 리그 목록 응답이 배열이 아니다 — 승/패 · 티어를 비운 채 간다");
            return Ranks.NONE;
        }
        SoloRank solo = null;
        String flexTier = null;
        for(JsonNode entry : entries)
        {
            String queue = text(entry.path(FIELD_QUEUE_TYPE));
            if(SOLO_QUEUE.equals(queue) && solo == null)
            {
                Integer wins = integer(entry.path(FIELD_WINS));
                Integer losses = integer(entry.path(FIELD_LOSSES));
                String tier = ladderName(text(entry.path(FIELD_TIER)), text(entry.path(FIELD_RANK)));
                // 한쪽만 읽히면 둘 다 버린다 — DB 의 CHECK 가 "같이 있거나 같이 없다"를 건다
                boolean both = wins != null && losses != null;
                solo = new SoloRank(both ? wins : null, both ? losses : null, tier);
            }
            else if(FLEX_QUEUE.equals(queue) && flexTier == null)
            {
                flexTier = ladderName(text(entry.path(FIELD_TIER)), text(entry.path(FIELD_RANK)));
            }
        }
        return new Ranks(solo, flexTier);
    }

    // ---- 4 · 5걸음: 최근 경기 ----

    /**
     * 새 경기가 먼저인 순서를 지킨다 — 연승을 그 순서로 센다. 읽지 못한 경기와 <b>커스텀 게임</b>({@link #customOrUnknown})은 빠진다.
     * 커스텀을 빼고 모자란 만큼 더 받지 않는다 — 경기 상세를 부르는 수는 늘 {@code match-count} 까지다(P-43 의 "호출을 늘리지 않는다")
     */
    private Matches recentMatches(String puuid)
    {
        JsonNode matchIds = riot.matchIds(puuid, properties.matchCount());
        List<Played> played = new ArrayList<>();
        Map<Long, String> championNames = new HashMap<>();
        if(!matchIds.isArray())
        {
            log.warn("Riot 의 경기 id 응답이 배열이 아니다 — 경기를 읽지 않는다");
            return new Matches(played, championNames);
        }
        int calls = 0;
        int customs = 0;
        for(JsonNode matchIdNode : matchIds)
        {
            if(calls >= properties.matchCount())
            {
                break;
            }
            String matchId = text(matchIdNode);
            if(matchId == null)
            {
                continue;
            }
            calls++;
            JsonNode match = riot.match(matchId);
            if(customOrUnknown(match))
            {
                // 그 경기에서는 아무것도 읽지 않는다 — 챔피언 이름표를 메우는 데도 쓰지 않는다
                customs++;
                continue;
            }
            collectChampionNames(match, championNames);
            Played one = participation(match, puuid);
            if(one != null)
            {
                played.add(one);
            }
        }
        if(customs > 0)
        {
            log.info("최근 경기 {}판 가운데 커스텀(또는 큐를 못 읽은) {}판을 뺐다 — Riot 정책상 공개로 보여 주지 않는다", calls, customs);
        }
        return new Matches(played, championNames);
    }

    /**
     * 커스텀 게임이거나 커스텀이 아님을 알 수 없는 경기 — <b>전적에 넣지 않는다</b>(2026-10-02 소유자 지시).
     * Riot 의 LoL 개발자 정책이 "선수가 따로 동의하지 않으면 커스텀 큐의 전적을 공개로 보여 주지 마라"고 하고, 게시판 카드는 로그인한 누구에게나 보인다.
     *
     * <p>{@code info.queueId == 0} 이거나 {@code info.gameType == "CUSTOM_GAME"} 이면 커스텀이다. API 키로 받는 커스텀은 <b>토너먼트 코드로 연 경기</b>뿐이었다
     * (2026-10-02 실제 키로 확인 — 큐를 지정하지 않은 경기 id 목록에 그 경기가 섞여 나왔고, 상세는 {@code queueId 0} · {@code "CUSTOM_GAME"} · {@code tournamentCode} 있음.
     * 그냥 만든 커스텀 방은 Riot 이 RSO 로만 준다). 토너먼트 경기도 커스텀 큐라 뺀다.
     *
     * <p>{@code queueId} 를 정수로 못 읽은 경기도 뺀다 — 커스텀이 아님을 확인할 수 없고, 공개로 보여 주면 안 되는 것이라 모를 때는 빼는 쪽이다(Claude 가 정한 세부).
     * 실제 경기 상세에는 늘 있는 칸이라 모양이 바뀐 경기를 건너뛰는 것과 같다. 음수도 그대로 읽는다({@link #integer} 처럼 0 으로 깎으면 커스텀으로 잘못 본다)
     */
    static boolean customOrUnknown(JsonNode match)
    {
        JsonNode info = match.path(FIELD_INFO);
        Long queueId = longValue(info.path(FIELD_QUEUE_ID));
        if(queueId == null)
        {
            log.warn("Riot 의 경기 응답에 {} 가 없다 — 커스텀이 아님을 알 수 없어 그 경기를 뺀다", FIELD_QUEUE_ID);
            return true;
        }
        return queueId == CUSTOM_QUEUE_ID || CUSTOM_GAME_TYPE.equals(text(info.path(FIELD_GAME_TYPE)));
    }

    /** 그 경기 참가자 전원의 챔피언 번호 → {@code championName}. 이름표({@link LolChampionNames})에 없는 새 챔피언을 메우는 데만 쓴다. 모양이 다르면 건너뛴다 */
    private static void collectChampionNames(JsonNode match, Map<Long, String> championNames)
    {
        JsonNode participants = match.path(FIELD_INFO).path(FIELD_PARTICIPANTS);
        if(!participants.isArray())
        {
            return;
        }
        for(JsonNode participant : participants)
        {
            Long key = longValue(participant.path(FIELD_CHAMPION_KEY));
            String name = text(participant.path(FIELD_CHAMPION_NAME));
            if(key != null && name != null)
            {
                championNames.putIfAbsent(key, name);
            }
        }
    }

    /** 그 경기에서 이 사람의 기록. 참가자 목록에 없거나 모양이 다르면 {@code null} — 그 경기를 건너뛴다 */
    private static Played participation(JsonNode match, String puuid)
    {
        JsonNode participants = match.path(FIELD_INFO).path(FIELD_PARTICIPANTS);
        if(!participants.isArray())
        {
            return null;
        }
        for(JsonNode participant : participants)
        {
            if(!puuid.equals(text(participant.path(FIELD_PUUID))))
            {
                continue;
            }
            return new Played(intOrZero(participant.path(FIELD_KILLS)),
                    intOrZero(participant.path(FIELD_DEATHS)),
                    intOrZero(participant.path(FIELD_ASSISTS)),
                    participant.path(FIELD_WIN).isBoolean() && participant.path(FIELD_WIN).booleanValue());
        }
        return null;
    }

    // ---- 티어 ----

    /**
     * Riot 의 {@code tier} + {@code rank} → gameconfig 사다리의 이름. {@code GOLD} + {@code II} → {@code GOLD_2},
     * {@code MASTER} + {@code I} → {@code MASTER}(단이 없는 셋). 못 만들면 {@code null} — 사다리 검사는 {@link #onLadder} 가 한다
     */
    static String ladderName(String tier, String rank)
    {
        if(tier == null)
        {
            return null;
        }
        if(APEX_TIERS.contains(tier))
        {
            return tier;
        }
        String division = (rank == null) ? null : DIVISIONS.get(rank);
        if(division == null)
        {
            log.warn("Riot 의 단을 읽지 못했다 tier={} rank={} — 티어를 비운다", tier, rank);
            return null;
        }
        return tier + "_" + division;
    }

    /** 사다리에 없는 이름이면 WARN 하고 {@code null}. Redis 를 못 읽으면 {@link GameConfigReader} 가 통과시킨다(fail-open) — 그 이름을 그대로 둔다 */
    private String onLadder(String ladderName)
    {
        if(ladderName == null)
        {
            return null;
        }
        if(gameConfig.hasTier(Game.LOL, ladderName))
        {
            return ladderName;
        }
        log.warn("Riot 이 준 티어가 gameconfig 사다리에 없다 tier={} — 티어를 비운다(seed 를 확인하라)", ladderName);
        return null;
    }

    // ---- 모아서 계산하는 것 ----

    /** 경기가 없으면 {@code null} 이다 — 0.0 으로 적으면 "전적이 없다"와 "평균이 0 이다"를 가를 수 없다 */
    private static BigDecimal average(List<Played> played, ToIntFunction<Played> field, int games)
    {
        if(games == 0)
        {
            return null;
        }
        long sum = 0;
        for(Played one : played)
        {
            sum += field.applyAsInt(one);
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(games), 1, RoundingMode.HALF_UP);
    }

    /** 가장 최근 경기부터 이어지는 연승. <b>최근 경기가 패면 0 이다</b> */
    private static int winStreak(List<Played> played)
    {
        int streak = 0;
        for(Played one : played)
        {
            if(!one.win())
            {
                break;
            }
            streak++;
        }
        return streak;
    }

    /**
     * {@code {"mostChampions": [{"championId", "masteryLevel", "masteryPoints"}], "recentResults": ["W", "L", …]}}.
     * {@code mostChampions} 는 <b>숙련도 점수 상위 셋</b>(2026-09-30 소유자 결정 — P-39).
     * 순서는 {@link #MOST_CHAMPION_ORDER}. <b>최근 경기의 판 수 · 승률은 싣지 않는다</b>(그 둘이 통산 숙련도와 한 줄에 섞였던 것을 걷어냈다).
     *
     * <p>{@code championId} 의 값은 챔피언 <b>이름</b>({@code "Kaisa"} — Data Dragon ID)이다. 숙련도 응답에는 숫자만 있어 {@link LolChampionNames} 로 옮긴다.
     * 그 표에 없는 새 챔피언은 최근 경기 참가자의 {@code championName} 으로 메우고, 거기에도 없으면 <b>번호를 글자로</b>({@code "999"} 꼴) 쓰고 WARN 한다 —
     * 셋의 자리를 비우거나 넷째로 채우면 "숙련도 상위 셋"이 틀려진다(Claude 가 정한 세부).
     *
     * <p>{@code masteryLevel} · {@code masteryPoints} 는 늘 값이 있다 — 번호 · 레벨 · 점수 가운데 하나라도 못 읽은 줄은 건너뛴다.
     * 숙련도를 못 받았거나 하나도 없으면 빈 배열이다 — {@code detail} 은 {@code null} 이 될 수 없다(컬럼이 {@code NOT NULL}).
     *
     * <p><b>{@code recentResults}</b>(2026-09-30 — P-43) — 읽은 최근 경기의 승 · 패를 <b>새 경기가 먼저</b>인 순서로 {@code "W"} · {@code "L"} 한 칸씩.
     * {@code games} · 평균 K/D/A · 연승과 <b>같은 경기 목록</b>이다 — 그래서 길이가 늘 {@code games} 이고, 참가자를 못 찾은 경기 · 커스텀 게임(2026-10-02 —
     * {@link #customOrUnknown})은 여기서도 빠진다.
     * 경기가 하나도 없으면 빈 배열이다(칸을 빼지 않는다 — {@code mostChampions} 와 같다). 승패를 못 읽은 경기({@code win} 이 불린이 아니다)는
     * 연승과 같이 {@code "L"} 이다. 다시하기(remake)를 따로 가르지 않는다 — 연승 · 평균도 가르지 않는다.
     */
    private String detail(String puuid, Map<Long, String> namesFromMatches, List<Played> played)
    {
        List<MostChampion> most = new ArrayList<>();
        for(JsonNode mastery : topMasteries(puuid))
        {
            Long key = longValue(mastery.path(FIELD_CHAMPION_KEY));
            Integer level = integer(mastery.path(FIELD_CHAMPION_LEVEL));
            Long points = longValue(mastery.path(FIELD_CHAMPION_POINTS));
            if(key == null || level == null || points == null)
            {
                log.warn("Riot 의 숙련도 줄에 {} · {} · {} 가운데 못 읽은 칸이 있다 — 그 줄을 건너뛴다",
                        FIELD_CHAMPION_KEY, FIELD_CHAMPION_LEVEL, FIELD_CHAMPION_POINTS);
                continue;
            }
            most.add(new MostChampion(championName(key, namesFromMatches), level, Math.max(0, points)));
        }
        most.sort(MOST_CHAMPION_ORDER);

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode champions = root.putArray("mostChampions");
        for(MostChampion one : most.subList(0, Math.min(MOST_CHAMPIONS, most.size())))
        {
            ObjectNode champion = champions.addObject();
            champion.put("championId", one.championId());
            champion.put("masteryLevel", one.masteryLevel());
            champion.put("masteryPoints", one.masteryPoints());
        }
        ArrayNode results = root.putArray(RECENT_RESULTS);
        for(Played one : played)
        {
            results.add(one.win() ? RESULT_WIN : RESULT_LOSS);
        }
        return root.toString();
    }

    /** 이름표 → 최근 경기 참가자 → 번호의 글자 순이다(위 {@link #detail}) */
    private String championName(long key, Map<Long, String> namesFromMatches)
    {
        String name = championNames.name(key);
        if(name == null)
        {
            name = namesFromMatches.get(key);
        }
        if(name == null)
        {
            log.warn("이름을 모르는 챔피언이다 championId={} — 번호를 그대로 쓴다(resources/{} 를 새 Data Dragon 버전으로 다시 떠라)",
                    key, LolChampionNames.RESOURCE);
            return String.valueOf(key);
        }
        return name;
    }

    /**
     * 숙련도 점수 상위 셋의 줄들. <b>실패해도(4xx · 5xx · 타임아웃 · JSON 이 아닌 응답) 던지지 않는다</b> — WARN 하고 빈 목록이다(모스트 챔피언만 비고 전적은 산다)
     */
    private List<JsonNode> topMasteries(String puuid)
    {
        JsonNode list;
        try
        {
            list = riot.topChampionMasteries(puuid, MOST_CHAMPIONS);
        }
        catch(RiotApiException e)
        {
            log.warn("Riot 의 챔피언 숙련도를 받지 못했다 — 모스트 챔피언을 비운 채 간다 status={} cause={}", e.status(), e.getMessage());
            return List.of();
        }
        if(!list.isArray())
        {
            log.warn("Riot 의 챔피언 숙련도 응답이 배열이 아니다 — 모스트 챔피언을 비운 채 간다");
            return List.of();
        }
        List<JsonNode> rows = new ArrayList<>();
        list.forEach(rows::add);
        return rows;
    }

    // ---- 값 꺼내기 ----

    /**
     * {@code 이름#태그} 를 가른다. 태그가 없거나 한쪽이 비어 있으면 {@code null} 이다.
     * 이름 안에 {@code #} 이 들어갈 수는 없지만 마지막 것을 기준으로 가른다 — 잘못 적었을 때 태그를 살리는 쪽이 낫다.
     */
    private static String[] splitRiotId(String gameNickname)
    {
        if(gameNickname == null)
        {
            return null;
        }
        int hash = gameNickname.lastIndexOf('#');
        if(hash < 0)
        {
            return null;
        }
        String name = gameNickname.substring(0, hash).trim();
        String tag = gameNickname.substring(hash + 1).trim();
        return (name.isEmpty() || tag.isEmpty()) ? null : new String[]{name, tag};
    }

    /** 없거나 · {@code null} 이거나 · 문자열이 아니거나 · 비어 있으면 전부 {@code null} 이다 ({@code JsonText} 와 같은 규칙) */
    private static String text(JsonNode node)
    {
        if(node == null || !node.isString())
        {
            return null;
        }
        String value = node.stringValue();
        return (value == null || value.isBlank()) ? null : value;
    }

    /** 정수가 아니면 {@code null}. 음수는 DB 의 CHECK 가 막으므로 0 으로 깎는다 */
    private static Integer integer(JsonNode node)
    {
        if(node == null || !node.isIntegralNumber())
        {
            return null;
        }
        return Math.max(0, node.intValue());
    }

    /** 정수가 아니면 {@code null}. 챔피언 번호처럼 음수가 뜻이 없는 것도 그대로 읽는다(맞춰 보는 열쇠일 뿐이다) */
    private static Long longValue(JsonNode node)
    {
        if(node == null || !node.isIntegralNumber())
        {
            return null;
        }
        return node.longValue();
    }

    private static int intOrZero(JsonNode node)
    {
        Integer value = integer(node);
        return value == null ? 0 : value;
    }

    /** 솔로랭크 줄. {@code wins} · {@code losses} 는 같이 있거나 같이 없다. {@code tier} 는 사다리 이름(검사 전)이다 */
    private record SoloRank(Integer wins, Integer losses, String tier) {
    }

    /** 리그 목록에서 읽은 두 줄 — 솔로랭크 줄(없으면 {@code null})과 자유랭크 줄의 티어(사다리 이름 · 검사 전. 없으면 {@code null}) */
    private record Ranks(SoloRank solo, String flexTier) {

        static final Ranks NONE = new Ranks(null, null);
    }

    /** 경기 하나에서 이 사람의 기록. 챔피언은 담지 않는다 — 모스트 챔피언은 경기가 아니라 숙련도에서 온다(2026-09-30 — P-39). {@code win} 은 연승과 {@code recentResults}(P-43)가 쓴다 */
    record Played(int kills, int deaths, int assists, boolean win) {
    }

    /** 읽은 최근 경기 — 이 사람의 기록(새 경기가 먼저)과 참가자 전원의 챔피언 번호 → 이름 */
    private record Matches(List<Played> played, Map<Long, String> championNames) {
    }

    /** 모스트 챔피언 한 줄 — {@code detail} 에 나가는 세 칸 그대로다 */
    record MostChampion(String championId, int masteryLevel, long masteryPoints) {
    }
}
