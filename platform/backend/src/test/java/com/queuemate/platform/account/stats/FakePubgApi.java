package com.queuemate.platform.account.stats;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트용 <b>가짜 PUBG API</b> — 플레이어 · 시즌 목록 · 랭크 전적 · 일반 시즌 전적의 넷을 흉내 낸다(2026-09-29 — P-36).
 * JDK 의 {@link HttpServer} 를 임의 포트로 띄운다({@link FakeRiotApi} 와 같은 방식 — 새 의존성을 들이지 않는다).
 *
 * <p><b>응답의 모양은 실제 응답에서 왔다</b> — {@code src/test/resources/pubg/*.json} 은 2026-09-29 에 실제 키로 받은 응답에서 닉네임 · 계정 id 를
 * 자리표시({@code {{name}}} · {@code {{accountId}}} · {@code {{shard}}})로 바꾼 것이다(숫자는 그대로). 일반 시즌 전적은 실제로 받아 보지 못해
 * 공식 문서(swagger 의 {@code gameModeStats})의 칸으로 만든다({@link #normalMode}).
 *
 * <p>약속 —
 * <ul>
 *   <li>{@code Authorization: Bearer fake-pubg-key} 가 아니면 <b>401</b>, {@code Accept} 에 {@code application/vnd.api+json} 이 없으면 <b>415</b> 다(문서의 약속)</li>
 *   <li>넣어 두지 않은 플레이어는 <b>404</b> 다 — 이름은 <b>대소문자를 가린다</b>(실제 PUBG 와 같다). 넣어 두지 않은 랭크 · 일반 전적도 404 다</li>
 *   <li>{@link #failWith(int)} 로 모든 주소가 그 상태를 준다. 429 에는 {@link #rateLimitReset(Long)} 로 {@code X-RateLimit-Reset}(UNIX 초)을 싣는다</li>
 *   <li>{@link #calls()} 는 받은 요청의 수 · {@link #calls(String)} 은 종류({@code players} · {@code seasons} · {@code ranked} · {@code season})별 수다</li>
 *   <li>{@link #rawQueries()} 는 플레이어 조회의 날것의 쿼리다 — 대괄호가 퍼센트 인코딩돼 오는지 본다</li>
 * </ul>
 */
final class FakePubgApi {

    /** 테스트가 설정에 넣는 키. 가짜 서버는 이 값만 받는다 */
    static final String API_KEY = "fake-pubg-key";
    /** 실제 응답의 현재 시즌 id(공개 값이다) */
    static final String CURRENT_SEASON = "division.bro.official.pc-2018-43";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final AtomicInteger calls = new AtomicInteger();
    private final Map<String, AtomicInteger> callsByKind = new ConcurrentHashMap<>();
    private final List<String> rawQueries = new CopyOnWriteArrayList<>();

    /** {@code shard + "|" + 이름} → 계정 id */
    private final Map<String, String> players = new ConcurrentHashMap<>();
    /** {@code shard + "|" + 계정 id} → 랭크 전적 응답 */
    private final Map<String, String> ranked = new ConcurrentHashMap<>();
    /** {@code shard + "|" + 계정 id} → 일반 시즌 전적 응답 */
    private final Map<String, String> seasonStats = new ConcurrentHashMap<>();

    private volatile int failStatus;
    private volatile Long rateLimitReset;
    private volatile Duration delay = Duration.ZERO;

    FakePubgApi()
    {
        try
        {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException(e);
        }
        server.createContext("/shards/", this::route);
        server.setExecutor(executor);
        server.start();
    }

    String baseUrl()
    {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void stop()
    {
        server.stop(0);
        executor.shutdownNow();
    }

    // ---- 넣어 두는 것 ----

    void stubPlayer(String shard, String name, String accountId)
    {
        players.put(shard + "|" + name, accountId);
    }

    /** 실제 응답에서 만든 랭크 전적 파일 하나({@code ranked-survivor-duo-squad.json} 등)를 그 계정의 랭크 전적으로 */
    void stubRankedFixture(String shard, String accountId, String fixture)
    {
        ranked.put(shard + "|" + accountId, resource(fixture).replace("{{accountId}}", accountId));
    }

    /** 랭크 전적 — {@code rankedGameModeStats} 의 모드들을 골라 넣는다(키 → {@link #rankedMode} 의 글자) */
    void stubRanked(String shard, String accountId, Map<String, String> modes)
    {
        ranked.put(shard + "|" + accountId, "{\"data\":{\"type\":\"rankedplayerstats\",\"attributes\":{\"rankedGameModeStats\":{"
                + joinModes(modes) + "}},\"relationships\":{\"player\":{\"data\":{\"type\":\"player\",\"id\":\"" + accountId + "\"}},"
                + "\"season\":{\"data\":{\"type\":\"season\",\"id\":\"" + CURRENT_SEASON + "\"}}}},\"links\":{},\"meta\":{}}");
    }

    /** 일반 시즌 전적 — {@code gameModeStats} 의 모드들(키 → {@link #normalMode} 의 글자) */
    void stubSeasonStats(String shard, String accountId, Map<String, String> modes)
    {
        seasonStats.put(shard + "|" + accountId, "{\"data\":{\"type\":\"playerSeason\",\"attributes\":{\"gameModeStats\":{"
                + joinModes(modes) + "},\"bestRankPoint\":0},\"relationships\":{\"player\":{\"data\":{\"type\":\"player\",\"id\":\""
                + accountId + "\"}}}},\"links\":{},\"meta\":{}}");
    }

    /** 랭크 한 모드 — 실제 응답의 칸 모양 그대로다({@code kda} 는 실제처럼 늘 0 이다 — 쓰면 안 된다) */
    static String rankedMode(String tier, String subTier, int rankPoint, int rounds, int wins, int kills, int deaths, int assists,
                             double damage)
    {
        return "{\"currentTier\":{\"tier\":\"" + tier + "\",\"subTier\":\"" + subTier + "\"},\"currentRankPoint\":" + rankPoint
                + ",\"bestTier\":{\"tier\":\"" + tier + "\",\"subTier\":\"" + subTier + "\"},\"bestRankPoint\":" + rankPoint
                + ",\"roundsPlayed\":" + rounds + ",\"avgRank\":10.5,\"top10Ratio\":0.5,\"winRatio\":0.1,\"assists\":" + assists
                + ",\"wins\":" + wins + ",\"kda\":0,\"kdr\":0,\"kills\":" + kills + ",\"deaths\":" + deaths
                + ",\"damageDealt\":" + damage + ",\"dBNOs\":0,\"revives\":0}";
    }

    /** 일반 시즌 한 모드 — 문서의 {@code gameModeStats} 칸. <b>데스 칸이 없다</b> — {@code losses} 가 있다 */
    static String normalMode(int rounds, int wins, int kills, int losses, double damage)
    {
        return "{\"assists\":3,\"boosts\":0,\"dBNOs\":0,\"damageDealt\":" + damage + ",\"headshotKills\":0,\"kills\":" + kills
                + ",\"losses\":" + losses + ",\"roundsPlayed\":" + rounds + ",\"top10s\":1,\"wins\":" + wins + ",\"timeSurvived\":0}";
    }

    private static String joinModes(Map<String, String> modes)
    {
        List<String> parts = new ArrayList<>();
        modes.forEach((mode, json) -> parts.add("\"" + mode + "\":" + json));
        return String.join(",", parts);
    }

    // ---- 흔들어 보는 것 ----

    void failWith(int status)
    {
        failStatus = status;
    }

    /** 429 에 실을 {@code X-RateLimit-Reset}(UNIX 초). {@code null} 이면 싣지 않는다 */
    void rateLimitReset(Long epochSeconds)
    {
        rateLimitReset = epochSeconds;
    }

    void respondAfter(Duration delay)
    {
        this.delay = delay;
    }

    int calls()
    {
        return calls.get();
    }

    int calls(String kind)
    {
        AtomicInteger count = callsByKind.get(kind);
        return count == null ? 0 : count.get();
    }

    List<String> rawQueries()
    {
        return rawQueries;
    }

    void reset()
    {
        failStatus = 0;
        rateLimitReset = null;
        delay = Duration.ZERO;
        calls.set(0);
        callsByKind.clear();
        rawQueries.clear();
    }

    // ---- 주소 ----

    /** {@code /shards/{shard}/…} 를 종류별로 가른다 */
    private void route(HttpExchange exchange) throws IOException
    {
        String[] parts = exchange.getRequestURI().getPath().substring("/shards/".length()).split("/");
        String shard = parts[0];
        String kind;
        if(parts.length == 2 && parts[1].equals("players"))
        {
            kind = "players";
        }
        else if(parts.length == 2 && parts[1].equals("seasons"))
        {
            kind = "seasons";
        }
        else if(parts.length == 6 && parts[1].equals("players") && parts[3].equals("seasons") && parts[5].equals("ranked"))
        {
            kind = "ranked";
        }
        else if(parts.length == 5 && parts[1].equals("players") && parts[3].equals("seasons"))
        {
            kind = "season";
        }
        else
        {
            respond(exchange, 404, "{\"errors\":[{\"title\":\"Not Found\"}]}");
            return;
        }
        calls.incrementAndGet();
        callsByKind.computeIfAbsent(kind, k -> new AtomicInteger()).incrementAndGet();
        if(!accept(exchange))
        {
            return;
        }
        switch(kind)
        {
            case "players" -> players(exchange, shard);
            case "seasons" -> respond(exchange, 200, resource("seasons.json"));
            case "ranked" -> respondOr404(exchange, ranked.get(shard + "|" + parts[2]));
            default -> respondOr404(exchange, seasonStats.get(shard + "|" + parts[2]));
        }
    }

    private void players(HttpExchange exchange, String shard) throws IOException
    {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        rawQueries.add(rawQuery);
        String name = null;
        for(String pair : rawQuery == null ? new String[0] : rawQuery.split("&"))
        {
            int equals = pair.indexOf('=');
            if(equals > 0 && URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8).equals("filter[playerNames]"))
            {
                name = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            }
        }
        String accountId = (name == null) ? null : players.get(shard + "|" + name);
        if(accountId == null)
        {
            // 실제 PUBG 와 같다 — 그 이름이 없으면 404 다(한도는 쓴다)
            respond(exchange, 404, "{\"errors\":[{\"title\":\"Not Found\",\"detail\":\"No Players Found Matching Criteria\"}]}");
            return;
        }
        respond(exchange, 200, resource("players-by-name.json")
                .replace("{{accountId}}", accountId).replace("{{name}}", name).replace("{{shard}}", shard));
    }

    // ---- 공통 ----

    /** 키 · Accept 를 보고 흔들어 보는 설정을 적용한다. {@code false} 면 이미 답한 것이다 */
    private boolean accept(HttpExchange exchange) throws IOException
    {
        if(!("Bearer " + API_KEY).equals(exchange.getRequestHeaders().getFirst("Authorization")))
        {
            respond(exchange, 401, "{\"errors\":[{\"title\":\"Unauthorized\"}]}");
            return false;
        }
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if(accept == null || !accept.contains("application/vnd.api+json"))
        {
            respond(exchange, 415, "{\"errors\":[{\"title\":\"Unsupported Media Type\"}]}");
            return false;
        }
        Duration waiting = delay;
        if(!waiting.isZero())
        {
            try
            {
                Thread.sleep(waiting.toMillis());
            }
            catch(InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
        int status = failStatus;
        if(status != 0)
        {
            Long reset = rateLimitReset;
            if(status == 429 && reset != null)
            {
                exchange.getResponseHeaders().add("x-ratelimit-reset", String.valueOf(reset));
            }
            respond(exchange, status, "{\"errors\":[{\"title\":\"status " + status + "\"}]}");
            return false;
        }
        return true;
    }

    private static String resource(String name)
    {
        try(InputStream in = FakePubgApi.class.getResourceAsStream("/pubg/" + name))
        {
            if(in == null)
            {
                throw new IllegalStateException("테스트 자원이 없다: /pubg/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static void respondOr404(HttpExchange exchange, String json) throws IOException
    {
        if(json == null)
        {
            respond(exchange, 404, "{\"errors\":[{\"title\":\"Not Found\"}]}");
            return;
        }
        respond(exchange, 200, json);
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException
    {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/vnd.api+json");
        exchange.sendResponseHeaders(status, bytes.length);
        try(var out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }
}
