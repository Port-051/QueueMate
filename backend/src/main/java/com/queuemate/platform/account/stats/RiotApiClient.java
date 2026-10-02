package com.queuemate.platform.account.stats;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

/**
 * Riot API 를 부르는 <b>전송 쪽</b> — 주소 · 경로 · 키 헤더 · 타임아웃만 안다. 응답의 JSON 을 해석하지 않고 {@link JsonNode} 를 그대로 돌려준다
 * (칸을 읽는 곳은 {@link LolStatsProvider} 한 곳이다). {@link com.queuemate.platform.account.oauth.OAuthClient} 와 같은 방식으로 짰다.
 *
 * <p><b>경로가 이 한 곳에 모여 있다</b> — Riot 이 경로를 바꾸면 여기만 고친다. 지금 부르는 것은 다섯이다.
 * <ol>
 *   <li>{@code account-v1} — Riot ID({@code 이름#태그}) → {@code puuid} <b>(대륙 주소)</b></li>
 *   <li>{@code league-v4} — <b>{@code puuid} 로</b> 리그 목록(솔로랭크의 승/패 · 티어, 자유랭크의 티어) <b>(플랫폼 주소)</b>.
 *       (2026-09-29 까지는 {@code summoner-v4} 로 소환사 {@code id} 를 받아 {@code entries/by-summoner} 를 불렀다 — <b>실제 키로 불러 보니 소환사 응답에 {@code id} 가 없었다</b>
 *       ({@code profileIconId} · {@code puuid} · {@code revisionDate} · {@code summonerLevel} 넷뿐). {@code entries/by-puuid} 는 200 으로 동작해 그리로 바꾸고 소환사 호출을 없앴다)</li>
 *   <li>{@code match-v5} — 최근 경기 id 목록 <b>(대륙 주소)</b>. {@code queue} · {@code type} 을 싣지 않는다 — 랭크 · 일반 · 칼바람이 다 들어와야 하는데
 *       {@code type} 은 값 하나만 받는다. 그래서 <b>토너먼트 코드로 연 커스텀 경기도 섞여 온다</b>(2026-10-02 실제 키로 확인) — 빼는 것은 {@link LolStatsProvider} 다</li>
 *   <li>{@code match-v5} — 경기 하나 <b>(대륙 주소)</b></li>
 *   <li>{@code champion-mastery-v4} — 그 소환사의 숙련도 <b>점수 상위 몇 개</b>({@code top?count=}) <b>(플랫폼 주소)</b>.
 *       (2026-09-30 까지는 {@code by-puuid/{puuid}} 로 숙련도 <b>전부</b>를 받아 최근 경기의 챔피언과 맞췄다 — 모스트 챔피언이 숙련도 상위 셋이 되며
 *       {@code top} 으로 바꿨다. 호출 수는 그대로 한 번이고 응답이 작다. 2026-09-30 실제 키로 확인 — 200 · 점수 내림차순 배열
 *       {@code [{puuid, championId(숫자), championLevel, championPoints, lastPlayTime, …}]} · 이름 칸은 없다)</li>
 * </ol>
 *
 * <p><b>키는 헤더로만 보낸다</b>({@code X-Riot-Token}) — 쿼리에 실으면 주소가 로그 · 예외 메시지에 남는다. 키를 어디에도 찍지 않는다.
 *
 * <p><b>실패는 전부 {@link RiotApiException} 이다</b> — 4xx · 5xx · 타임아웃 · JSON 이 아닌 응답. 재시도하지 않는다
 * (개발용 키는 2분당 100회라 429 를 받으면 되풀이해도 소용이 없다 — 그 자리에서 포기한다. 2026-09-29 실제 헤더 — {@code X-App-Rate-Limit: 100:120,20:1}).
 */
@Slf4j
@Component
@EnableConfigurationProperties(RiotProperties.class)
public class RiotApiClient {

    /** 키를 싣는 헤더. 쿼리 파라미터({@code ?api_key=})로 보내지 않는다 */
    static final String API_KEY_HEADER = "X-Riot-Token";

    // ---- 경로. Riot 이 바꾸면 여기만 고친다 ----
    static final String ACCOUNT_BY_RIOT_ID = "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}";
    /** 2026-09-29 실제 키로 확인 — 200 · {@code [{queueType, tier, rank, puuid, leaguePoints, wins, losses, …}]} */
    static final String LEAGUE_ENTRIES_BY_PUUID = "/lol/league/v4/entries/by-puuid/{puuid}";
    static final String MATCH_IDS_BY_PUUID = "/lol/match/v5/matches/by-puuid/{puuid}/ids?start=0&count={count}";
    static final String MATCH_BY_ID = "/lol/match/v5/matches/{matchId}";
    static final String TOP_CHAMPION_MASTERIES_BY_PUUID = "/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}/top?count={count}";

    private final RiotProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RiotApiClient(RiotProperties properties, ObjectMapper objectMapper)
    {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        // 경로 변수를 반드시 퍼센트 인코딩한다 — 게임 닉네임에 한글 · 공백이 들어온다(DefaultUriBuilderFactory 의 기본값이지만 못 박아 둔다)
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .uriBuilderFactory(uriBuilderFactory)
                .build();
    }

    /** Riot ID → 계정({@code puuid} 가 들어 있다) */
    JsonNode account(String gameName, String tagLine)
    {
        return get(properties.regionalBaseUrl() + ACCOUNT_BY_RIOT_ID, gameName, tagLine);
    }

    /** 그 {@code puuid} 의 리그 목록(배열) — 솔로랭크 · 자유랭크 줄이 그 안에 있을 수도 있고 없을 수도 있다(언랭) */
    JsonNode leagueEntries(String puuid)
    {
        return get(properties.platformBaseUrl() + LEAGUE_ENTRIES_BY_PUUID, puuid);
    }

    /** 최근 경기 id 의 배열 — <b>새 경기가 먼저</b> 온다 */
    JsonNode matchIds(String puuid, int count)
    {
        return get(properties.regionalBaseUrl() + MATCH_IDS_BY_PUUID, puuid, count);
    }

    JsonNode match(String matchId)
    {
        return get(properties.regionalBaseUrl() + MATCH_BY_ID, matchId);
    }

    /** 숙련도 <b>점수 상위 {@code count} 개</b>(배열 — 점수 내림차순 · {@code championId} 는 숫자다). 한 번에 받는다 */
    JsonNode topChampionMasteries(String puuid, int count)
    {
        return get(properties.platformBaseUrl() + TOP_CHAMPION_MASTERIES_BY_PUUID, puuid, count);
    }

    private JsonNode get(String uriTemplate, Object... uriVariables)
    {
        try
        {
            String body = restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .header(API_KEY_HEADER, properties.apiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if(body == null || body.isBlank())
            {
                throw new RiotApiException("Riot 의 응답이 비어 있다 path=" + path(uriTemplate), 0);
            }
            return objectMapper.readTree(body);
        }
        catch(RestClientResponseException e)
        {
            // 응답 본문을 메시지에 담지 않는다 — 상태와 경로만 남긴다
            throw new RiotApiException("Riot 이 거절했다 path=" + path(uriTemplate) + " status=" + e.getStatusCode().value(),
                    e.getStatusCode().value(), e);
        }
        catch(RestClientException | JacksonException e)
        {
            throw new RiotApiException("Riot 을 부르다 실패했다 path=" + path(uriTemplate) + " cause=" + e.getClass().getSimpleName(),
                    0, e);
        }
    }

    /**
     * 메시지에 남기는 것은 <b>주소가 아니라 경로의 틀</b>이다 — 값은 {@code {gameName}} 처럼 자리로 남아 있어서
     * 닉네임 · {@code puuid} 가 로그에 쌓이지 않는다.
     */
    private static String path(String uriTemplate)
    {
        int slash = uriTemplate.indexOf('/', uriTemplate.indexOf("://") + 3);
        return slash < 0 ? uriTemplate : uriTemplate.substring(slash);
    }
}
