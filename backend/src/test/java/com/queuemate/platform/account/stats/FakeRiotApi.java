package com.queuemate.platform.account.stats;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트용 <b>가짜 Riot API</b> — {@code account-v1} · {@code summoner-v4} · {@code league-v4} · {@code match-v5} 의 다섯 주소를 흉내 낸다.
 * JDK 의 {@link HttpServer} 를 임의 포트로 띄운다({@code FakeOAuthProvider} 와 같은 방식 — WireMock 같은 새 의존성을 들이지 않는다).
 *
 * <p><b>대륙 주소와 플랫폼 주소를 한 서버가 같이 받는다</b> — 경로가 겹치지 않으므로 테스트가 두 설정을 같은 주소로 돌려도 된다.
 *
 * <p>약속 —
 * <ul>
 *   <li>키 헤더({@code X-Riot-Token})가 없거나 다르면 <b>403</b> 이다. 실제 Riot 과 같은 자리에서 걸러진다</li>
 *   <li>{@link #failWith(int)} 로 모든 주소가 그 상태를 주게 한다(500 · 429). {@link #respondAfter(Duration)} 로 늦게 답한다(타임아웃)</li>
 *   <li>{@link #calls()} 는 <b>받은 요청의 수</b>다 — "신선하면 아예 부르지 않는지" 를 이것으로 본다</li>
 *   <li>넣어 두지 않은 Riot ID · 소환사 · 경기는 <b>404</b> 다</li>
 * </ul>
 */
final class FakeRiotApi {

    /** 테스트가 설정에 넣는 키. 가짜 서버는 이 값만 받는다 */
    static final String API_KEY = "fake-riot-key";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final AtomicInteger calls = new AtomicInteger();

    /** {@code "이름#태그"} → {@code puuid} */
    private final Map<String, String> accounts = new ConcurrentHashMap<>();
    /** {@code puuid} → 소환사 응답의 JSON */
    private final Map<String, String> summoners = new ConcurrentHashMap<>();
    /** 소환사 id → 리그 목록의 JSON(배열) */
    private final Map<String, String> leagues = new ConcurrentHashMap<>();
    /** {@code puuid} → 경기 id 목록(새 경기가 먼저) */
    private final Map<String, List<String>> matchIds = new ConcurrentHashMap<>();
    /** 경기 id → 경기 응답의 JSON */
    private final Map<String, String> matches = new ConcurrentHashMap<>();

    private final AtomicInteger matchSequence = new AtomicInteger();
    private volatile int failStatus;
    private volatile Duration delay = Duration.ZERO;

    FakeRiotApi()
    {
        try
        {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException(e);
        }
        server.createContext("/riot/account/v1/accounts/by-riot-id/", this::account);
        server.createContext("/lol/summoner/v4/summoners/by-puuid/", this::summoner);
        server.createContext("/lol/league/v4/entries/by-summoner/", this::league);
        // 더 긴 접두사가 이긴다 — by-puuid 의 요청이 경기 하나를 주는 핸들러로 가지 않는다
        server.createContext("/lol/match/v5/matches/by-puuid/", this::matchIdList);
        server.createContext("/lol/match/v5/matches/", this::match);
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

    /** 게임 닉네임({@code 이름#태그}) → {@code puuid} */
    void stubAccount(String riotId, String puuid)
    {
        accounts.put(riotId, puuid);
    }

    /** {@code puuid} → 소환사. {@code summonerId} 가 {@code null} 이면 {@code id} 칸이 없는 응답이다(칸이 사라진 경우를 본다) */
    void stubSummoner(String puuid, String summonerId)
    {
        summoners.put(puuid, summonerId == null
                ? "{\"puuid\":\"" + puuid + "\",\"profileIconId\":1234,\"summonerLevel\":300}"
                : "{\"id\":\"" + summonerId + "\",\"puuid\":\"" + puuid + "\",\"profileIconId\":1234,\"summonerLevel\":300}");
    }

    /** 솔로랭크 줄이 있는 리그 목록. 자유랭크 줄을 같이 넣는다 — 그쪽 승/패를 읽지 않는지 본다 */
    void stubSoloRank(String summonerId, int wins, int losses)
    {
        leagues.put(summonerId, "["
                + entry("RANKED_FLEX_SR", "GOLD", "II", 99, 99) + ","
                + entry("RANKED_SOLO_5x5", "EMERALD", "IV", wins, losses)
                + "]");
    }

    /** 솔로랭크 줄이 없는 리그 목록(언랭 또는 자유랭크만) */
    void stubNoSoloRank(String summonerId)
    {
        leagues.put(summonerId, "[" + entry("RANKED_FLEX_SR", "GOLD", "II", 7, 3) + "]");
    }

    private static String entry(String queueType, String tier, String rank, int wins, int losses)
    {
        return "{\"queueType\":\"" + queueType + "\",\"tier\":\"" + tier + "\",\"rank\":\"" + rank
                + "\",\"leaguePoints\":42,\"wins\":" + wins + ",\"losses\":" + losses + "}";
    }

    /** 최근 경기. <b>적은 순서가 곧 새 경기부터의 순서다</b> — 연승을 그 순서로 센다 */
    void stubMatches(String puuid, List<Play> plays)
    {
        List<String> ids = new ArrayList<>();
        for(Play play : plays)
        {
            String matchId = "KR_" + matchSequence.incrementAndGet();
            ids.add(matchId);
            matches.put(matchId, matchJson(matchId, puuid, play));
        }
        matchIds.put(puuid, ids);
    }

    /** 경기 하나에서 그 사람의 기록. 엉뚱한 참가자를 하나 같이 넣는다 — {@code puuid} 로 골라 읽는지 본다 */
    record Play(String champion, int kills, int deaths, int assists, boolean win, String position) {
    }

    private static String matchJson(String matchId, String puuid, Play play)
    {
        return "{\"metadata\":{\"matchId\":\"" + matchId + "\",\"participants\":[\"" + puuid + "\",\"other-puuid\"]},"
                + "\"info\":{\"gameId\":1,\"queueId\":420,\"participants\":["
                + participant("other-puuid", new Play("Teemo", 99, 0, 99, !play.win(), "TOP")) + ","
                + participant(puuid, play)
                + "]}}";
    }

    private static String participant(String puuid, Play play)
    {
        return "{\"puuid\":\"" + puuid + "\",\"championName\":\"" + play.champion() + "\",\"championId\":1,"
                + "\"kills\":" + play.kills() + ",\"deaths\":" + play.deaths() + ",\"assists\":" + play.assists()
                + ",\"win\":" + play.win() + ",\"teamPosition\":\"" + play.position() + "\"}";
    }

    // ---- 흔들어 보는 것 ----

    /** 0 이면 정상. 그 밖의 값이면 모든 주소가 그 상태를 준다 */
    void failWith(int status)
    {
        failStatus = status;
    }

    /** 이만큼 늦게 답한다 — 읽기 타임아웃을 본다 */
    void respondAfter(Duration delay)
    {
        this.delay = delay;
    }

    int calls()
    {
        return calls.get();
    }

    void reset()
    {
        failStatus = 0;
        delay = Duration.ZERO;
        calls.set(0);
    }

    // ---- 주소 ----

    private void account(HttpExchange exchange) throws IOException
    {
        if(!accept(exchange))
        {
            return;
        }
        // …/by-riot-id/{gameName}/{tagLine} — URI#getPath 가 퍼센트 인코딩을 풀어 준다(한글 · 공백)
        String[] segments = tail(exchange, "/riot/account/v1/accounts/by-riot-id/").split("/");
        if(segments.length != 2)
        {
            respond(exchange, 400, "{\"status\":{\"status_code\":400}}");
            return;
        }
        String puuid = accounts.get(segments[0] + "#" + segments[1]);
        if(puuid == null)
        {
            respond(exchange, 404, "{\"status\":{\"status_code\":404,\"message\":\"Data not found\"}}");
            return;
        }
        respond(exchange, 200, "{\"puuid\":\"" + puuid + "\",\"gameName\":\"" + segments[0]
                + "\",\"tagLine\":\"" + segments[1] + "\"}");
    }

    private void summoner(HttpExchange exchange) throws IOException
    {
        if(!accept(exchange))
        {
            return;
        }
        respondOr404(exchange, summoners.get(tail(exchange, "/lol/summoner/v4/summoners/by-puuid/")));
    }

    private void league(HttpExchange exchange) throws IOException
    {
        if(!accept(exchange))
        {
            return;
        }
        String entries = leagues.get(tail(exchange, "/lol/league/v4/entries/by-summoner/"));
        // 리그 목록은 없어도 404 가 아니라 빈 배열이다(언랭)
        respond(exchange, 200, entries == null ? "[]" : entries);
    }

    private void matchIdList(HttpExchange exchange) throws IOException
    {
        if(!accept(exchange))
        {
            return;
        }
        // …/by-puuid/{puuid}/ids
        String tail = tail(exchange, "/lol/match/v5/matches/by-puuid/");
        String puuid = tail.endsWith("/ids") ? tail.substring(0, tail.length() - "/ids".length()) : tail;
        List<String> ids = matchIds.get(puuid);
        if(ids == null)
        {
            respond(exchange, 404, "{\"status\":{\"status_code\":404}}");
            return;
        }
        List<String> quoted = new ArrayList<>();
        for(String id : ids)
        {
            quoted.add('"' + id + '"');
        }
        respond(exchange, 200, "[" + String.join(",", quoted) + "]");
    }

    private void match(HttpExchange exchange) throws IOException
    {
        if(!accept(exchange))
        {
            return;
        }
        respondOr404(exchange, matches.get(tail(exchange, "/lol/match/v5/matches/")));
    }

    // ---- 공통 ----

    /** 세고 · 키를 보고 · 흔들어 보는 설정을 적용한다. {@code false} 면 이미 답한 것이다 */
    private boolean accept(HttpExchange exchange) throws IOException
    {
        calls.incrementAndGet();
        if(!API_KEY.equals(exchange.getRequestHeaders().getFirst("X-Riot-Token")))
        {
            respond(exchange, 403, "{\"status\":{\"status_code\":403,\"message\":\"Forbidden\"}}");
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
            respond(exchange, status, "{\"status\":{\"status_code\":" + status + "}}");
            return false;
        }
        return true;
    }

    private static String tail(HttpExchange exchange, String prefix)
    {
        String path = exchange.getRequestURI().getPath();
        return path.length() <= prefix.length() ? "" : path.substring(prefix.length());
    }

    private static void respondOr404(HttpExchange exchange, String json) throws IOException
    {
        if(json == null)
        {
            respond(exchange, 404, "{\"status\":{\"status_code\":404}}");
            return;
        }
        respond(exchange, 200, json);
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException
    {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try(var out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }
}
