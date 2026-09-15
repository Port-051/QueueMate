package com.queuemate.user.riot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 Riot 응답 모양에 맞춘 스텁 서버로 확인한다.
 *
 * <p>여기서 고정하려는 것은 파싱보다 <b>경로</b>다. Riot이 2025년 6월에 SummonerID 기반
 * 엔드포인트를 지웠고, 인터넷에 남은 예제 상당수가 아직 그 경로를 가리킨다.
 * 누가 "원래 이렇게 쓰던데" 하고 되돌리면 이 테스트가 먼저 깨진다.
 */
class RiotClientTest {

    private HttpServer server;
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private final List<String> sentTokens = new CopyOnWriteArrayList<>();
    /** 경로별 응답을 미리 꽂아 둔다. {경로 접두사, 상태, 본문} */
    private final List<String[]> canned = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestedPaths.add(exchange.getRequestURI().getRawPath());
        sentTokens.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Riot-Token")));
        String[] match = canned.stream()
                .filter(c -> exchange.getRequestURI().getRawPath().startsWith(c[0]))
                .findFirst()
                .orElse(new String[] {"", "404", "{}"});
        byte[] body = match[2].getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(Integer.parseInt(match[1]), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void stub(String pathPrefix, int status, String body) {
        canned.add(new String[] {pathPrefix, String.valueOf(status), body});
    }

    private RiotClient client() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new RiotClient(properties(baseUrl), RestClient.builder());
    }

    private static RiotProperties properties(String baseUrl) {
        return new RiotProperties("RGAPI-test-key", baseUrl, baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(3),
                Duration.ofHours(6), Duration.ofHours(24));
    }

    @Test
    @DisplayName("Riot ID는 account-v1의 by-riot-id 경로로 puuid가 된다")
    void resolvesPuuidThroughAccountV1() {
        stub("/riot/account/v1/accounts/by-riot-id/", 200,
                "{\"puuid\":\"PUUID-1\",\"gameName\":\"Hide on bush\",\"tagLine\":\"KR1\"}");

        Optional<String> puuid = client().findPuuid("Hide on bush", "KR1");

        assertEquals(Optional.of("PUUID-1"), puuid);
        // 공백은 인코딩되어 나가야 한다. 그대로 실으면 요청 줄이 깨진다.
        assertEquals("/riot/account/v1/accounts/by-riot-id/Hide%20on%20bush/KR1", requestedPaths.get(0));
        assertEquals("RGAPI-test-key", sentTokens.get(0));
    }

    @Test
    @DisplayName("한 응답에서 솔로와 자유를 함께 읽는다")
    void readsBothQueuesThroughLeagueV4ByPuuid() {
        // 순서는 Riot이 정한다. 자유가 먼저 와도 각자 제자리에 들어가야 한다.
        stub("/lol/league/v4/entries/by-puuid/", 200, """
                [{"queueType":"RANKED_FLEX_SR","tier":"PLATINUM","rank":"I","leaguePoints":12},
                 {"queueType":"RANKED_SOLO_5x5","tier":"GOLD","rank":"II","leaguePoints":45}]
                """);

        RiotRanks ranks = client().findRanks("PUUID-1");

        // by-summoner가 아니다. 그 경로는 2025년 6월에 없어졌다.
        assertEquals("/lol/league/v4/entries/by-puuid/PUUID-1", requestedPaths.get(0));
        assertEquals("GOLD_2", ranks.soloRankCode());
        assertEquals("PLATINUM_1", ranks.flexRankCode());
        assertEquals(45, ranks.solo().leaguePoints());
        assertEquals(12, ranks.flex().leaguePoints());
    }

    @Test
    @DisplayName("언랭은 빈 목록으로 온다. 오류가 아니다")
    void unrankedIsAnEmptyList() {
        stub("/lol/league/v4/entries/by-puuid/", 200, "[]");

        RiotRanks ranks = client().findRanks("PUUID-1");

        assertNull(ranks.soloRankCode());
        assertNull(ranks.flexRankCode());
    }

    @Test
    @DisplayName("한쪽 큐만 한 계정은 그쪽만 채워진다")
    void oneQueueOnly() {
        stub("/lol/league/v4/entries/by-puuid/", 200,
                "[{\"queueType\":\"RANKED_FLEX_SR\",\"tier\":\"PLATINUM\",\"rank\":\"I\",\"leaguePoints\":12}]");

        RiotRanks ranks = client().findRanks("PUUID-1");

        assertNull(ranks.soloRankCode());
        assertEquals("PLATINUM_1", ranks.flexRankCode());
    }

    @Test
    @DisplayName("모르는 큐 타입은 무시한다")
    void ignoresUnknownQueues() {
        // Riot이 큐를 추가해도 화면이 깨지면 안 된다.
        stub("/lol/league/v4/entries/by-puuid/", 200,
                "[{\"queueType\":\"CHERRY\",\"tier\":\"GOLD\",\"rank\":\"I\",\"leaguePoints\":1}]");

        RiotRanks ranks = client().findRanks("PUUID-1");

        assertNull(ranks.soloRankCode());
        assertNull(ranks.flexRankCode());
    }

    @Test
    @DisplayName("없는 Riot ID는 빈 값이다. 예외가 아니다")
    void missingRiotIdIsEmptyNotAnError() {
        stub("/riot/account/v1/accounts/by-riot-id/", 404,
                "{\"status\":{\"message\":\"Data not found\",\"status_code\":404}}");

        assertEquals(Optional.empty(), client().findPuuid("없는사람", "KR1"));
    }

    @Test
    @DisplayName("키가 만료되면 '없음'이 아니라 장애다")
    void expiredKeyIsUnavailableNotEmpty() {
        // 개발자 키는 24시간마다 만료된다. 이것을 '랭크 없음'으로 기록하면
        // 사용자의 티어가 통째로 지워진 것처럼 보인다.
        stub("/riot/account/v1/accounts/by-riot-id/", 401,
                "{\"status\":{\"message\":\"Unknown apikey\",\"status_code\":401}}");

        assertThrows(RiotUnavailableException.class, () -> client().findPuuid("아무개", "KR1"));
    }

    @Test
    @DisplayName("한도 초과와 서버 장애도 장애로 올라온다")
    void rateLimitAndOutageAreUnavailable() {
        stub("/lol/league/v4/entries/by-puuid/", 429, "{\"status\":{\"status_code\":429}}");

        assertThrows(RiotUnavailableException.class, () -> client().findRanks("PUUID-1"));
    }
}
