package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * PUBG 의 전적 · 티어를 PUBG API 에서 긁는다(2026-09-29 소유자 결정 "PUBG 티어는 API 로 채우고 LoL 처럼 동기로" — {@code contracts/platform-api.md} P-36).
 * <b>응답의 JSON 칸을 읽는 곳은 이 클래스 하나다</b>(부르는 곳은 {@link PubgApiClient}). {@link LolStatsProvider} 를 본떴다.
 *
 * <p>순서는 넷이고 PUBG 호출은 <b>2 ~ 4번</b>이다 — 플레이어 1 · 시즌 목록 1(캐시가 있으면 0) · 랭크 전적 1 · (랭크 판이 0 이면) 일반 전적 1.
 * 한도는 키 하나에 <b>분당 10회</b>이고 <b>없는 닉네임의 404 도 한도를 쓴다</b>(확인됨 — 2026-09-29).
 * <ol>
 *   <li>게임 계정의 {@code server}({@code STEAM} · {@code KAKAO}) → shard({@code steam} · {@code kakao}). 서버가 없으면 긁지 않는다({@code null})</li>
 *   <li>닉네임 → 계정 id. <b>이름은 대소문자까지 같아야 한다</b> — 404 거나 목록에 같은 이름이 없으면 {@link PubgPlayerNotFoundException}</li>
 *   <li>현재 시즌 id — Redis 캐시({@link PubgSeasonCache} · 하루)에 없을 때만 시즌 목록을 부른다({@code isCurrentSeason})</li>
 *   <li>이번 시즌 <b>랭크</b> 전적({@code rankedGameModeStats}) — 판이 있는 모드를 전부 합산한다. <b>랭크 판이 0 이면</b> 이번 시즌 <b>일반</b> 전적({@code gameModeStats})을
 *       부르고 그 모드들을 전부 합산한다</li>
 * </ol>
 *
 * <p><b>티어는 사다리 하나({@code RANKED})다</b> — 시즌 36(2025-06-05)부터 PUBG 의 티어 · RP 가 듀오 · 스쿼드와 TPP · FPP 에 걸쳐 통합됐다(소유자가 2026-09-14 에
 * 공식 패치노트로 확인 — {@code matching/WORKLOG_2026-09-14.md} §1). 랭크 판이 있는 모드들의 {@code currentTier} 를 본다.
 * <b>확인됨(2026-09-29 실제 응답)</b> — {@code rankedGameModeStats} 의 키는 실제로 {@code duo} · {@code squad} 가 왔고(한 사람은 {@code squad} 하나만 — 공개 샘플에는
 * {@code squad-fpp} 도 있다) <b>키마다 {@code currentTier} · {@code currentRankPoint} 가 완전히 같았다</b>(통합 확인). 그래서 <b>키 이름을 고정하지 않고 온 키를 전부 훑는다</b>.
 * 그래도 <b>다르면 {@code currentRankPoint} 가 가장 큰 모드의 것</b>을 쓰고 WARN 한 줄(원문 값 — 이 갈래는 실제로 본 적이 없다). 랭크 판이 없으면 {@code rankedGameModeStats} 가
 * {@code {}} 로 와서(200) 티어는 {@code null} 이다.
 * {@code tier} + {@code subTier} → gameconfig 사다리 이름({@link #ladderName}). <b>확인됨(2026-09-29)</b> — 티어는 첫 글자만 대문자({@code "Gold"} · {@code "Diamond"} ·
 * {@code "Master"} · {@code "Survivor"} 관측, 공개 샘플에 {@code "Crystal"}), {@code subTier} 는 문자열 {@code "1"} ~ {@code "4"}, <b>{@code Master} · {@code Survivor} 도 {@code subTier "1"} 로 온다</b> —
 * 그 둘은 단을 무시하고 {@code MASTER} · {@code SURVIVOR}, 나머지는 {@code 대문자_단}({@code GOLD_3} · {@code DIAMOND_3} — seed 사다리의 이름과 맞는다).
 * 만든 이름이 사다리({@code qm:gameconfig:PUBG:tier})에 없으면 WARN(원문 값) 하고 {@code null}. {@code Unranked} 는 {@code null}(이 이름은 관측하지 못했다 — 방어다).
 * <b>응답의 {@code kda} 칸은 늘 0 으로 온다(확인됨) — 쓰지 않는다.</b>
 *
 * <p><b>전적 스냅숏은 P-12 의 PUBG 칸 규칙 그대로다</b> — {@code wins} · {@code losses} · {@code winStreak} · {@code avgAssists} 는 비는 칸이다(100명 중 순위 싸움이라
 * "승"이 치킨이다). {@code games} = 합산한 판 수, {@code avgKills} · {@code avgDeaths} = 판당 평균(소수 첫째 자리). {@code detail} 은 계약 "게임 프로필" 의 PUBG 모양
 * {@code {"seasonMode", "avgDamage", "kd", "top1Rate"}} 이다 — {@code seasonMode} 는 합산의 출처({@code RANKED} · {@code NORMAL}), {@code avgDamage} 는 판당 평균 딜량(소수 첫째 자리),
 * {@code kd} 는 킬 / 데스(소수 둘째 자리 · 데스가 0 이면 {@code null}), {@code top1Rate} 는 치킨 판 / 판 × 100(퍼센트 숫자 · 소수 첫째 자리). 판이 0 이면 셋 다 {@code null} 이다.
 * <b>일반 전적에는 데스 칸이 없다</b> — {@code losses}(진 판 = 죽은 판)를 데스로 쓴다(Claude 가 정한 세부).
 *
 * <p><b>없는 칸에 터지지 않는다</b> — 못 읽은 숫자는 0 이다. 랭크 · 일반 전적 주소가 404 를 주면 그 전적이 없는 것으로 보고(WARN) 넘어간다(Claude 가 정한 세부 — 플레이어와 시즌은
 * 방금 찾았으니 그 조합의 전적이 없다는 뜻으로 읽는다). 그 밖의 실패는 {@link PubgApiException} 그대로다.
 * <b>{@code verified} 는 켜지 않는다</b> — 닉네임만으로 찾으니 본인 확인이 아니다(LoL 과 같은 이유).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubgStatsProvider implements GameStatsProvider {

    /** 사다리 키 — {@code Game.PUBG.tierLadders()} 의 하나뿐이다 */
    static final String LADDER_RANKED = "RANKED";

    /** {@code detail.seasonMode} 의 값 — 합산의 출처. 계약에 값 목록이 없어 Claude 가 정했다(프런트가 이 둘을 안다) */
    static final String SEASON_MODE_RANKED = "RANKED";
    static final String SEASON_MODE_NORMAL = "NORMAL";

    /** 나누기(단)가 없는 티어 — 사다리 이름에 {@code _단} 을 붙이지 않는다. 원본은 {@code matching/seed/gameconfig.redis} 의 {@code qm:gameconfig:PUBG:tier} */
    private static final List<String> NO_DIVISION = List.of("MASTER", "SURVIVOR");
    private static final String UNRANKED = "UNRANKED";
    /** 단이 로마 숫자로 올 때(미확인) — 사다리는 {@code GOLD_4} … {@code GOLD_1} 이다 */
    private static final Map<String, String> ROMAN = Map.of("I", "1", "II", "2", "III", "3", "IV", "4", "V", "5");

    // ---- PUBG 응답의 칸 이름 ----
    private static final String FIELD_DATA = "data";
    private static final String FIELD_ID = "id";
    private static final String FIELD_ATTRIBUTES = "attributes";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_IS_CURRENT_SEASON = "isCurrentSeason";
    private static final String FIELD_RANKED_MODES = "rankedGameModeStats";
    private static final String FIELD_NORMAL_MODES = "gameModeStats";
    private static final String FIELD_ROUNDS = "roundsPlayed";
    private static final String FIELD_WINS = "wins";
    private static final String FIELD_KILLS = "kills";
    private static final String FIELD_DEATHS = "deaths";
    private static final String FIELD_LOSSES = "losses";
    private static final String FIELD_DAMAGE = "damageDealt";
    private static final String FIELD_CURRENT_TIER = "currentTier";
    private static final String FIELD_TIER = "tier";
    private static final String FIELD_SUB_TIER = "subTier";
    private static final String FIELD_RANK_POINT = "currentRankPoint";

    private final PubgApiClient pubg;
    private final PubgProperties properties;
    private final PubgSeasonCache seasonCache;
    private final ObjectMapper objectMapper;
    private final GameConfigReader gameConfig;

    @Override
    public Game game()
    {
        return Game.PUBG;
    }

    @Override
    public boolean configured()
    {
        return properties.configured();
    }

    @Override
    public StatsSnapshot fetch(String gameNickname, String server)
    {
        String shard = shardOf(server);
        if(shard == null)
        {
            // 연결은 서버를 먼저 400 으로 거른다. 여기 오는 것은 자기신고이던 때 서버 없이 저장된 옛 줄뿐이다
            log.warn("PUBG 게임 계정에 서버가 없어 전적을 긁지 않는다 server={}", server);
            return null;
        }
        if(gameNickname == null || gameNickname.isBlank())
        {
            return null;
        }
        String accountId = accountId(shard, gameNickname);
        String seasonId = currentSeason(shard);

        Totals ranked = new Totals();
        List<RankedTier> tiers = new ArrayList<>();
        JsonNode rankedModes = orEmpty(() -> pubg.rankedStats(shard, accountId, seasonId), "랭크")
                .path(FIELD_DATA).path(FIELD_ATTRIBUTES).path(FIELD_RANKED_MODES);
        for(Map.Entry<String, JsonNode> mode : entries(rankedModes))
        {
            JsonNode stats = mode.getValue();
            long rounds = whole(stats.path(FIELD_ROUNDS));
            if(rounds <= 0)
            {
                continue;
            }
            ranked.add(rounds, whole(stats.path(FIELD_WINS)), whole(stats.path(FIELD_KILLS)), whole(stats.path(FIELD_DEATHS)),
                    amount(stats.path(FIELD_DAMAGE)));
            JsonNode current = stats.path(FIELD_CURRENT_TIER);
            tiers.add(new RankedTier(mode.getKey(), text(current.path(FIELD_TIER)), text(current.path(FIELD_SUB_TIER)),
                    whole(stats.path(FIELD_RANK_POINT))));
        }

        Totals totals = ranked;
        String seasonMode = SEASON_MODE_RANKED;
        if(ranked.rounds == 0)
        {
            // 이번 시즌 랭크를 안 했다 — 일반 전적으로 대신한다(티어는 없다)
            totals = new Totals();
            seasonMode = SEASON_MODE_NORMAL;
            JsonNode normalModes = orEmpty(() -> pubg.seasonStats(shard, accountId, seasonId), "일반")
                    .path(FIELD_DATA).path(FIELD_ATTRIBUTES).path(FIELD_NORMAL_MODES);
            for(Map.Entry<String, JsonNode> mode : entries(normalModes))
            {
                JsonNode stats = mode.getValue();
                long rounds = whole(stats.path(FIELD_ROUNDS));
                if(rounds <= 0)
                {
                    continue;
                }
                // 일반 전적에는 데스가 없다 — 진 판(= 죽은 판)을 데스로 쓴다
                totals.add(rounds, whole(stats.path(FIELD_WINS)), whole(stats.path(FIELD_KILLS)), whole(stats.path(FIELD_LOSSES)),
                        amount(stats.path(FIELD_DAMAGE)));
            }
        }

        Map<String, String> ladderTiers = new LinkedHashMap<>();
        ladderTiers.put(LADDER_RANKED, tier(tiers));
        return new StatsSnapshot(accountId, (int) Math.min(Integer.MAX_VALUE, totals.rounds),
                perRound(totals.kills, totals.rounds),
                perRound(totals.deaths, totals.rounds),
                null, null, null, null,
                detail(seasonMode, totals),
                ladderTiers);
    }

    // ---- 1 · 2걸음: shard · 계정 id ----

    /** {@code STEAM} → {@code steam}. 모르는 값 · 없음은 {@code null} */
    static String shardOf(String server)
    {
        if(server == null || !Game.PUBG.servers().contains(server))
        {
            return null;
        }
        return server.toLowerCase(Locale.ROOT);
    }

    /** 404 거나 · 목록이 비었거나 · 글자 그대로 같은 이름이 없으면 {@link PubgPlayerNotFoundException}. 응답에 이름 칸이 없으면 첫 줄을 쓴다 */
    private String accountId(String shard, String playerName)
    {
        JsonNode players;
        try
        {
            players = pubg.players(shard, playerName);
        }
        catch(PubgApiException e)
        {
            if(e.status() == 404)
            {
                throw new PubgPlayerNotFoundException();
            }
            throw e;
        }
        JsonNode data = players.path(FIELD_DATA);
        if(data.isArray())
        {
            for(JsonNode player : data)
            {
                String id = text(player.path(FIELD_ID));
                String name = text(player.path(FIELD_ATTRIBUTES).path(FIELD_NAME));
                if(id != null && (name == null || name.equals(playerName)))
                {
                    return id;
                }
            }
        }
        throw new PubgPlayerNotFoundException();
    }

    // ---- 3걸음: 현재 시즌 ----

    private String currentSeason(String shard)
    {
        Optional<String> cached = seasonCache.get(shard);
        if(cached.isPresent())
        {
            return cached.get();
        }
        JsonNode seasons = pubg.seasons(shard).path(FIELD_DATA);
        if(seasons.isArray())
        {
            for(JsonNode season : seasons)
            {
                JsonNode current = season.path(FIELD_ATTRIBUTES).path(FIELD_IS_CURRENT_SEASON);
                String id = text(season.path(FIELD_ID));
                if(id != null && current.isBoolean() && current.booleanValue())
                {
                    seasonCache.put(shard, id);
                    return id;
                }
            }
        }
        throw new PubgApiException("PUBG 의 시즌 목록에 현재 시즌이 없다 shard=" + shard, 0);
    }

    // ---- 티어 ----

    /**
     * 랭크 판이 있는 모드들의 티어 → 사다리 이름 하나. 모드마다 다르면 RP 가 가장 큰 모드의 것 + WARN(원문 값). 판이 있는 모드가 없으면 {@code null}.
     * 만든 이름이 사다리에 없으면 WARN(원문 값) 하고 {@code null} — <b>미확인 규칙이다</b>(클래스 주석)
     */
    private String tier(List<RankedTier> tiers)
    {
        if(tiers.isEmpty())
        {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        for(RankedTier one : tiers)
        {
            names.add(String.valueOf(ladderName(one.tier(), one.subTier())));
        }
        RankedTier chosen = tiers.stream().max(Comparator.comparingLong(RankedTier::rankPoint)).orElseThrow();
        if(names.size() > 1)
        {
            log.warn("PUBG 의 모드마다 티어가 다르다 — RP 가 가장 큰 {} 의 것을 쓴다 (원문 {}) — 통합 티어라 같아야 한다, 첫 실제 응답으로 확인하라",
                    chosen.mode(), tiers);
        }
        String name = ladderName(chosen.tier(), chosen.subTier());
        if(name == null)
        {
            if(chosen.tier() != null && !UNRANKED.equals(chosen.tier().trim().toUpperCase(Locale.ROOT)))
            {
                log.warn("PUBG 의 티어를 사다리 이름으로 옮기지 못했다 tier={} subTier={} — 티어를 비운다", chosen.tier(), chosen.subTier());
            }
            return null;
        }
        if(gameConfig.hasTier(Game.PUBG, name))
        {
            return name;
        }
        log.warn("PUBG 가 준 티어가 gameconfig 사다리에 없다 tier={} (원문 tier={} subTier={}) — 티어를 비운다(seed 를 확인하라)",
                name, chosen.tier(), chosen.subTier());
        return null;
    }

    /**
     * PUBG 의 {@code tier} + {@code subTier} → gameconfig 사다리의 이름. {@code Gold} + {@code 2} → {@code GOLD_2}, {@code Master} + {@code 1} → {@code MASTER}(단을 무시한다),
     * {@code Unranked} · 없음 → {@code null}. 단은 숫자({@code "2"} — 확인됨)를 받고, 로마 숫자({@code "II"})도 방어로 받는다. 못 읽은 단은 {@code null} — 사다리 검사는 부르는 쪽이 한다.
     */
    static String ladderName(String tier, String subTier)
    {
        if(tier == null || tier.isBlank())
        {
            return null;
        }
        String upper = tier.trim().toUpperCase(Locale.ROOT);
        if(UNRANKED.equals(upper))
        {
            return null;
        }
        if(NO_DIVISION.contains(upper))
        {
            return upper;
        }
        String division = (subTier == null) ? null : subTier.trim().toUpperCase(Locale.ROOT);
        if(division != null && ROMAN.containsKey(division))
        {
            division = ROMAN.get(division);
        }
        if(division == null || !division.matches("[1-9]"))
        {
            return null;
        }
        return upper + "_" + division;
    }

    // ---- 모아서 계산하는 것 ----

    /** 계약 "게임 프로필" 의 PUBG {@code detail} — {@code {"seasonMode", "avgDamage", "kd", "top1Rate"}}. 판이 0 이면 숫자 셋은 {@code null} */
    private String detail(String seasonMode, Totals totals)
    {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("seasonMode", seasonMode);
        putOrNull(root, "avgDamage", totals.rounds == 0 ? null
                : BigDecimal.valueOf(totals.damage).divide(BigDecimal.valueOf(totals.rounds), 1, RoundingMode.HALF_UP));
        putOrNull(root, "kd", totals.deaths == 0 ? null
                : BigDecimal.valueOf(totals.kills).divide(BigDecimal.valueOf(totals.deaths), 2, RoundingMode.HALF_UP));
        putOrNull(root, "top1Rate", totals.rounds == 0 ? null
                : BigDecimal.valueOf(totals.wins * 100).divide(BigDecimal.valueOf(totals.rounds), 1, RoundingMode.HALF_UP));
        return root.toString();
    }

    /** 판이 없으면 {@code null} 이다 — 0.0 으로 적으면 "전적이 없다" 와 "평균이 0 이다" 를 가를 수 없다(LoL 과 같다) */
    private static BigDecimal perRound(long sum, long rounds)
    {
        if(rounds == 0)
        {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(rounds), 1, RoundingMode.HALF_UP);
    }

    private static void putOrNull(ObjectNode node, String field, BigDecimal value)
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

    /**
     * 랭크 · 일반 전적 주소를 부른다 — <b>404 는 "그 전적이 없다"</b> 로 읽고 빈 객체를 준다(WARN). 그 밖의 실패는 그대로 던진다
     */
    private JsonNode orEmpty(Supplier<JsonNode> call, String what)
    {
        try
        {
            return call.get();
        }
        catch(PubgApiException e)
        {
            if(e.status() == 404)
            {
                log.warn("PUBG 의 {} 전적 주소가 404 다 — 전적이 없는 것으로 본다", what);
                return objectMapper.createObjectNode();
            }
            throw e;
        }
    }

    // ---- 값 꺼내기 ----

    /** 객체의 칸들. 객체가 아니면 비어 있다 */
    private static Set<Map.Entry<String, JsonNode>> entries(JsonNode node)
    {
        return (node != null && node.isObject()) ? node.properties() : Set.of();
    }

    /** 없거나 · {@code null} 이거나 · 문자열이 아니거나 · 비어 있으면 전부 {@code null} 이다 */
    private static String text(JsonNode node)
    {
        if(node == null || !node.isString())
        {
            return null;
        }
        String value = node.stringValue();
        return (value == null || value.isBlank()) ? null : value;
    }

    /** 숫자가 아니면 0. 음수는 0 으로 깎는다(DB 의 CHECK 가 음수를 막는다). 소수로 오면 버린다 */
    private static long whole(JsonNode node)
    {
        if(node == null || !node.isNumber())
        {
            return 0;
        }
        return Math.max(0L, (long) node.doubleValue());
    }

    private static double amount(JsonNode node)
    {
        if(node == null || !node.isNumber())
        {
            return 0;
        }
        return Math.max(0d, node.doubleValue());
    }

    /** 판이 있는 랭크 모드 하나의 티어(원문 그대로)와 RP */
    record RankedTier(String mode, String tier, String subTier, long rankPoint) {
    }

    /** 여러 모드를 합산한 것 */
    private static final class Totals {
        long rounds;
        long wins;
        long kills;
        long deaths;
        double damage;

        void add(long rounds, long wins, long kills, long deaths, double damage)
        {
            this.rounds += rounds;
            this.wins += wins;
            this.kills += kills;
            this.deaths += deaths;
            this.damage += damage;
        }
    }
}
