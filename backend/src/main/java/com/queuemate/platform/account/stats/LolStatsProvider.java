package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * LoL 의 전적을 Riot API 에서 긁는다 — <b>응답의 JSON 칸을 읽는 곳은 이 클래스 하나다</b>(부르는 곳은 {@link RiotApiClient}).
 * Riot 이 칸 이름을 바꾸면 여기만 고친다.
 *
 * <p>순서는 다섯 걸음이다.
 * <ol>
 *   <li>게임 닉네임을 {@code 이름#태그} 로 가른다 — <b>태그가 없으면 긁지 않는다</b>({@code null} 을 돌려준다)</li>
 *   <li>{@code account-v1} → {@code puuid}</li>
 *   <li>{@code summoner-v4} → 소환사 {@code id} → {@code league-v4} 에서 <b>솔로랭크 줄</b>의 승/패(= 시즌 누적)</li>
 *   <li>{@code match-v5} → 최근 경기 id 목록(새 경기가 먼저)</li>
 *   <li>경기마다 참가자 가운데 <b>그 {@code puuid} 인 사람</b>의 챔피언 · K/D/A · 승패</li>
 * </ol>
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
    private static final String FIELD_WINS = "wins";
    private static final String FIELD_LOSSES = "losses";
    private static final String FIELD_INFO = "info";
    private static final String FIELD_PARTICIPANTS = "participants";
    private static final String FIELD_CHAMPION_NAME = "championName";
    private static final String FIELD_KILLS = "kills";
    private static final String FIELD_DEATHS = "deaths";
    private static final String FIELD_ASSISTS = "assists";
    private static final String FIELD_WIN = "win";
    private static final String FIELD_TEAM_POSITION = "teamPosition";

    /** 솔로랭크 줄을 가르는 값. 자유랭크({@code RANKED_FLEX_SR})의 승/패를 섞지 않는다 */
    private static final String SOLO_QUEUE = "RANKED_SOLO_5x5";

    /** {@code detail} 의 모스트 챔피언은 셋까지다 ({@code contracts/platform-api.md} "게임 프로필") */
    private static final int MOST_CHAMPIONS = 3;

    private final RiotApiClient riot;
    private final RiotProperties properties;
    private final ObjectMapper objectMapper;

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
            // 닉네임은 사용자가 적는 자유 문자열이다 — 형식이 아니면 긁을 수 없다. 본 작업(계정 연결 · 글 쓰기)은 이미 성공했다
            log.warn("LoL 게임 닉네임이 '이름#태그' 가 아니라 전적을 긁지 않는다");
            return null;
        }

        String puuid = text(riot.account(riotId[0], riotId[1]).path(FIELD_PUUID));
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
                detail(played));
    }

    // ---- 3걸음: 솔로랭크의 시즌 누적 승/패 ----

    /** 솔로랭크 줄. 언랭이거나 응답의 모양이 달라 못 읽으면 {@code null} 이다 — 그러면 {@code wins} · {@code losses} 가 둘 다 비어 나간다 */
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
            // 한쪽만 읽히면 둘 다 버린다 — DB 의 CHECK 가 "같이 있거나 같이 없다"를 건다
            return (wins == null || losses == null) ? null : new SoloRank(wins, losses);
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
                    intOrZero(participant.path(FIELD_KILLS)),
                    intOrZero(participant.path(FIELD_DEATHS)),
                    intOrZero(participant.path(FIELD_ASSISTS)),
                    participant.path(FIELD_WIN).isBoolean() && participant.path(FIELD_WIN).booleanValue(),
                    text(participant.path(FIELD_TEAM_POSITION)));
        }
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
     * {@code {"mostChampions": [{"championId", "games", "winRate"}]}} — 판 수 많은 순 → 같으면 승률 높은 순 → 같으면 이름순으로 셋까지.
     * {@code championId} 의 값은 Riot 의 {@code championName}({@code "Samira"})이고 {@code winRate} 는 정수 퍼센트다.
     * 경기를 하나도 못 읽었으면 빈 배열이다 — {@code detail} 은 {@code null} 이 될 수 없다(컬럼이 {@code NOT NULL}).
     */
    private String detail(List<Played> played)
    {
        Map<String, int[]> byChampion = new LinkedHashMap<>();
        for(Played one : played)
        {
            if(one.championName() == null)
            {
                continue;
            }
            int[] count = byChampion.computeIfAbsent(one.championName(), name -> new int[2]);
            count[0]++;
            if(one.win())
            {
                count[1]++;
            }
        }
        List<Map.Entry<String, int[]>> ranked = new ArrayList<>(byChampion.entrySet());
        ranked.sort(Comparator
                .<Map.Entry<String, int[]>>comparingInt(entry -> -entry.getValue()[0])
                .thenComparingInt(entry -> -percent(entry.getValue()[1], entry.getValue()[0]))
                .thenComparing(Map.Entry::getKey));

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode champions = root.putArray("mostChampions");
        for(Map.Entry<String, int[]> entry : ranked.subList(0, Math.min(MOST_CHAMPIONS, ranked.size())))
        {
            ObjectNode champion = champions.addObject();
            champion.put("championId", entry.getKey());
            champion.put("games", entry.getValue()[0]);
            champion.put("winRate", percent(entry.getValue()[1], entry.getValue()[0]));
        }
        return root.toString();
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

    private static int intOrZero(JsonNode node)
    {
        Integer value = integer(node);
        return value == null ? 0 : value;
    }

    private record SoloRank(int wins, int losses) {
    }

    /**
     * 경기 하나에서 이 사람의 기록.
     *
     * @param teamPosition Riot 이 주는 포지션. <b>읽기만 하고 저장하지 않는다</b> — 이 앱에서 포지션의 출처는
     *                     프로필의 <b>주 포지션</b>(자기신고)이다(CLAUDE.md §7.1). Riot 이 주는 칸을 한곳에 모아 두려고 남겨 둔다
     */
    private record Played(String championName, int kills, int deaths, int assists, boolean win, String teamPosition) {
    }
}
