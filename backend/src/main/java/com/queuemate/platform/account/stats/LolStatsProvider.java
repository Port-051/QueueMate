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
 * <p>순서는 여섯 걸음이다 — Riot 호출은 경기 20판이면 <b>25번</b>(계정 · 소환사 · 리그 · 경기 id · 경기 20 · 숙련도).
 * <ol>
 *   <li>게임 닉네임을 {@code 이름#태그} 로 가른다 — <b>태그가 없으면 긁지 않는다</b>({@code null} 을 돌려준다)</li>
 *   <li>{@code account-v1} → {@code puuid}</li>
 *   <li>{@code summoner-v4} → 소환사 {@code id} → {@code league-v4} 에서 <b>솔로랭크 줄</b>의 승/패(= 시즌 누적)와 <b>티어</b></li>
 *   <li>{@code match-v5} → 최근 경기 id 목록(새 경기가 먼저)</li>
 *   <li>경기마다 참가자 가운데 <b>그 {@code puuid} 인 사람</b>의 챔피언 · K/D/A · 승패</li>
 *   <li>{@code champion-mastery-v4} → 모스트 챔피언 각각의 <b>숙련도</b>(레벨 · 점수) — 한 번에 전부 받아 고른다.
 *       <b>이 걸음만은 실패해도 전적을 살린다</b>(WARN 하고 숙련도를 비운다 — 전적의 본체가 아니다). 모스트 챔피언이 없으면 부르지 않는다</li>
 * </ol>
 *
 * <p><b>티어도 여기서 만든다</b>(2026-09-27 소유자 결정 — LoL 은 요청에서 {@code tier} 를 빼고 Riot 에서 채운다).
 * 솔로랭크 줄의 {@code tier} + {@code rank} 를 gameconfig 사다리의 이름으로({@link #ladderName}). 줄이 없으면(언랭) {@code null},
 * 만든 이름이 사다리에 없으면(Riot 이 티어를 새로 만들었다 등) WARN 하고 {@code null}. Redis 를 못 읽으면 {@link GameConfigReader} 의 fail-open 대로 그 이름을 그대로 둔다.
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
    /** 소환사의 encrypted summoner id — {@code league-v4} 를 부르는 열쇠다 */
    private static final String FIELD_SUMMONER_ID = "id";
    private static final String FIELD_QUEUE_TYPE = "queueType";
    private static final String FIELD_TIER = "tier";
    private static final String FIELD_RANK = "rank";
    private static final String FIELD_WINS = "wins";
    private static final String FIELD_LOSSES = "losses";
    private static final String FIELD_INFO = "info";
    private static final String FIELD_PARTICIPANTS = "participants";
    private static final String FIELD_CHAMPION_NAME = "championName";
    private static final String FIELD_KILLS = "kills";
    private static final String FIELD_DEATHS = "deaths";
    private static final String FIELD_ASSISTS = "assists";
    private static final String FIELD_WIN = "win";
    /** 경기 참가자의 챔피언 번호(숫자). 숙련도 응답과 맞추는 열쇠다 — 숙련도에는 {@code championName} 이 없다 */
    private static final String FIELD_CHAMPION_KEY = "championId";
    private static final String FIELD_CHAMPION_LEVEL = "championLevel";
    private static final String FIELD_CHAMPION_POINTS = "championPoints";

    /** 솔로랭크 줄을 가르는 값. 자유랭크({@code RANKED_FLEX_SR})의 승/패를 섞지 않는다 */
    private static final String SOLO_QUEUE = "RANKED_SOLO_5x5";

    /** {@code detail} 의 모스트 챔피언은 셋까지다 ({@code contracts/platform-api.md} "게임 프로필") */
    private static final int MOST_CHAMPIONS = 3;

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

    @Override
    public Game game()
    {
        return Game.LOL;
    }

    @Override
    public StatsSnapshot fetch(String gameNickname)
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

        SoloRank rank = soloRank(puuid);
        List<Played> played = recentMatches(puuid);

        int games = played.size();
        return new StatsSnapshot(puuid, games,
                average(played, Played::kills, games),
                average(played, Played::deaths, games),
                average(played, Played::assists, games),
                rank == null ? null : rank.wins(),
                rank == null ? null : rank.losses(),
                games == 0 ? null : winStreak(played),
                detail(puuid, played),
                rank == null ? null : onLadder(rank.tier()));
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

    // ---- 3걸음: 솔로랭크의 시즌 누적 승/패와 티어 ----

    /**
     * 솔로랭크 줄. 언랭이거나 응답의 모양이 달라 못 읽으면 {@code null} 이다 — 그러면 {@code wins} · {@code losses} · 티어가 전부 비어 나간다.
     * 줄은 있는데 승/패 한쪽을 못 읽으면 승/패만 비우고 티어는 살린다
     */
    private SoloRank soloRank(String puuid)
    {
        String summonerId = text(riot.summoner(puuid).path(FIELD_SUMMONER_ID));
        if(summonerId == null)
        {
            log.warn("Riot 의 소환사 응답에 {} 가 없다 — 승/패를 비운 채 간다", FIELD_SUMMONER_ID);
            return null;
        }
        JsonNode entries = riot.leagueEntries(summonerId);
        if(!entries.isArray())
        {
            return null;
        }
        for(JsonNode entry : entries)
        {
            if(!SOLO_QUEUE.equals(text(entry.path(FIELD_QUEUE_TYPE))))
            {
                continue;
            }
            Integer wins = integer(entry.path(FIELD_WINS));
            Integer losses = integer(entry.path(FIELD_LOSSES));
            String tier = ladderName(text(entry.path(FIELD_TIER)), text(entry.path(FIELD_RANK)));
            // 한쪽만 읽히면 둘 다 버린다 — DB 의 CHECK 가 "같이 있거나 같이 없다"를 건다
            boolean both = wins != null && losses != null;
            return new SoloRank(both ? wins : null, both ? losses : null, tier);
        }
        return null;
    }

    // ---- 4 · 5걸음: 최근 경기 ----

    /** 새 경기가 먼저인 순서를 지킨다 — 연승을 그 순서로 센다. 읽지 못한 경기는 빠진다 */
    private List<Played> recentMatches(String puuid)
    {
        JsonNode matchIds = riot.matchIds(puuid, properties.matchCount());
        List<Played> played = new ArrayList<>();
        if(!matchIds.isArray())
        {
            log.warn("Riot 의 경기 id 응답이 배열이 아니다 — 경기를 읽지 않는다");
            return played;
        }
        int calls = 0;
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
            Played one = participation(riot.match(matchId), puuid);
            if(one != null)
            {
                played.add(one);
            }
        }
        return played;
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
            return new Played(text(participant.path(FIELD_CHAMPION_NAME)),
                longValue(participant.path(FIELD_CHAMPION_KEY)),
                    intOrZero(participant.path(FIELD_KILLS)),
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
     * {@code {"mostChampions": [{"championId", "games", "winRate", "masteryLevel", "masteryPoints"}]}} — 판 수 많은 순 → 같으면 승률 높은 순 → 같으면 이름순으로 셋까지.
     * {@code championId} 의 값은 Riot 의 {@code championName}({@code "Samira"})이고 {@code winRate} 는 정수 퍼센트다.
     * {@code masteryLevel} · {@code masteryPoints} 는 그 챔피언의 숙련도이고(2026-09-27 소유자 지시 — "숙련도만"), 숙련도 목록에 없거나
     * 숙련도를 못 받았으면 둘 다 {@code null} 이다. 경기를 하나도 못 읽었으면 빈 배열이다 — {@code detail} 은 {@code null} 이 될 수 없다(컬럼이 {@code NOT NULL}).
     */
    private String detail(String puuid, List<Played> played)
    {
        Map<String, Champion> byChampion = new LinkedHashMap<>();
        for(Played one : played)
        {
            if(one.championName() == null)
            {
                continue;
            }
            Champion champion = byChampion.computeIfAbsent(one.championName(), name -> new Champion());
            champion.games++;
            if(one.win())
            {
                champion.wins++;
            }
            if(champion.key == null)
            {
                champion.key = one.championKey();
            }
        }
        List<Map.Entry<String, Champion>> ranked = new ArrayList<>(byChampion.entrySet());
        ranked.sort(Comparator
                .<Map.Entry<String, Champion>>comparingInt(entry -> -entry.getValue().games)
                .thenComparingInt(entry -> -percent(entry.getValue().wins, entry.getValue().games))
                .thenComparing(Map.Entry::getKey));
        List<Map.Entry<String, Champion>> most = ranked.subList(0, Math.min(MOST_CHAMPIONS, ranked.size()));
        Map<Long, JsonNode> masteries = most.isEmpty() ? Map.of() : masteries(puuid);

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode champions = root.putArray("mostChampions");
        for(Map.Entry<String, Champion> entry : most)
        {
            Champion counted = entry.getValue();
            ObjectNode champion = champions.addObject();
            champion.put("championId", entry.getKey());
            champion.put("games", counted.games);
            champion.put("winRate", percent(counted.wins, counted.games));
            JsonNode mastery = counted.key == null ? null : masteries.get(counted.key);
            putIntOrNull(champion, "masteryLevel", mastery == null ? null : integer(mastery.path(FIELD_CHAMPION_LEVEL)));
            putIntOrNull(champion, "masteryPoints", mastery == null ? null : integer(mastery.path(FIELD_CHAMPION_POINTS)));
        }
        return root.toString();
    }

    /**
     * 챔피언 번호 → 숙련도 한 줄. <b>실패해도(4xx · 5xx · 타임아웃 · JSON 이 아닌 응답) 던지지 않는다</b> — WARN 하고 빈 맵이다(숙련도만 비고 전적은 산다).
     * 모양이 다른 줄(번호가 없는 줄)은 건너뛴다
     */
    private Map<Long, JsonNode> masteries(String puuid)
    {
        JsonNode list;
        try
        {
            list = riot.championMasteries(puuid);
        }
        catch(RiotApiException e)
        {
            log.warn("Riot 의 챔피언 숙련도를 받지 못했다 — 숙련도를 비운 채 간다 status={} cause={}", e.status(), e.getMessage());
            return Map.of();
        }
        if(!list.isArray())
        {
            log.warn("Riot 의 챔피언 숙련도 응답이 배열이 아니다 — 숙련도를 비운 채 간다");
            return Map.of();
        }
        Map<Long, JsonNode> byKey = new HashMap<>();
        for(JsonNode mastery : list)
        {
            Long key = longValue(mastery.path(FIELD_CHAMPION_KEY));
            if(key != null)
            {
                byKey.putIfAbsent(key, mastery);
            }
        }
        return byKey;
    }

    private static void putIntOrNull(ObjectNode node, String field, Integer value)
    {
        if(value == null)
        {
            node.putNull(field);
        }
        else
        {
            node.put(field, value);
        }
    }

    private static int percent(int wins, int games)
    {
        return games == 0 ? 0 : (int) Math.round(wins * 100.0 / games);
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

    /** 경기 하나에서 이 사람의 기록 */
    record Played(String championName, Long championKey, int kills, int deaths, int assists, boolean win) {
    }

    /** 모스트 챔피언을 셀 때 한 챔피언의 판 수 · 승 수와 숙련도를 찾을 번호(처음 읽힌 경기의 것) */
    private static final class Champion {
        int games;
        int wins;
        Long key;
    }
}
