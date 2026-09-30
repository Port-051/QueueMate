package com.queuemate.platform.account.stats;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
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
import java.time.Instant;

/**
 * PUBG API 를 부르는 <b>전송 쪽</b>(2026-09-29 소유자 결정 — P-36) — 주소 · 경로 · 키 헤더 · 타임아웃만 안다. 응답의 JSON 을 해석하지 않고 {@link JsonNode} 를
 * 그대로 돌려준다(칸을 읽는 곳은 {@link PubgStatsProvider} 한 곳이다). {@link RiotApiClient} 를 본떴다.
 *
 * <p><b>경로가 이 한 곳에 모여 있다</b> — 지금 부르는 것은 넷이다(공식 문서 {@code github.com/pubg/api-documentation-content} 의 swagger 에서 옮겼다).
 * 전부 <b>플랫폼 shard</b>({@code steam} · {@code kakao}) 아래다.
 * <ol>
 *   <li>{@code GET /shards/{shard}/players?filter[playerNames]={name}} — 닉네임 → 계정 id({@code data[].id}). 이름은 대소문자를 가린다</li>
 *   <li>{@code GET /shards/{shard}/seasons} — 시즌 목록({@code attributes.isCurrentSeason}). 캐시한다({@link PubgSeasonCache})</li>
 *   <li>{@code GET /shards/{shard}/players/{accountId}/seasons/{seasonId}/ranked} — 이번 시즌 랭크 전적({@code rankedGameModeStats})</li>
 *   <li>{@code GET /shards/{shard}/players/{accountId}/seasons/{seasonId}} — 이번 시즌 일반 전적({@code gameModeStats}). 랭크 판이 0 일 때만</li>
 * </ol>
 *
 * <p><b>키는 헤더로만 보낸다</b>({@code Authorization: Bearer …}) · {@code Accept: application/vnd.api+json}(문서의 약속). 키를 어디에도 찍지 않는다.
 * {@code filter[playerNames]} 의 대괄호는 주소를 만들 때 퍼센트 인코딩된다({@code %5B} · {@code %5D} — JDK 의 HTTP 클라이언트는 날것의 대괄호를 받지 않는다).
 *
 * <p><b>실패는 전부 {@link PubgApiException} 이다</b> — 4xx · 5xx · 타임아웃 · JSON 이 아닌 응답. 재시도하지 않는다 — 개발용 키는 <b>분당 10회</b>라
 * 429 를 되풀이해도 소용이 없다. <b>429 면 {@code X-RateLimit-Reset}(UNIX 초)까지 남은 초를 싣는다</b>(없으면 60초) — 응답의 {@code Retry-After} 가 된다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(PubgProperties.class)
public class PubgApiClient {

    static final MediaType JSON_API = MediaType.valueOf("application/vnd.api+json");
    static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";

    // ---- 경로. PUBG 가 바꾸면 여기만 고친다 ----
    static final String PLAYERS_BY_NAME = "/shards/{shard}/players?filter[playerNames]={name}";
    static final String SEASONS = "/shards/{shard}/seasons";
    static final String RANKED_STATS = "/shards/{shard}/players/{accountId}/seasons/{seasonId}/ranked";
    static final String SEASON_STATS = "/shards/{shard}/players/{accountId}/seasons/{seasonId}";

    private final PubgProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public PubgApiClient(PubgProperties properties, ObjectMapper objectMapper)
    {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        // 경로 변수를 반드시 퍼센트 인코딩한다 — 닉네임에 무엇이 들어올지 모른다
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .uriBuilderFactory(uriBuilderFactory)
                .build();
        if(properties.configured())
        {
            log.info("PUBG 전적 동기화가 켜져 있다 — {}", properties);
        }
        else
        {
            // 키를 찍지 않는다. 환경변수의 이름만 남긴다
            log.info("PUBG_API_KEY 가 없어 PUBG 전적을 긁지 않는다 — PUBG 게임 계정 연결이 503 이고 로그인 때 다시 받지도 않는다");
        }
    }

    /** 닉네임 → 플레이어 목록({@code data} 배열). 그 이름이 없으면 PUBG 가 404 를 준다 — {@link PubgApiException} 의 {@code status} 로 가른다 */
    JsonNode players(String shard, String playerName)
    {
        return get(PLAYERS_BY_NAME, shard, playerName);
    }

    JsonNode seasons(String shard)
    {
        return get(SEASONS, shard);
    }

    JsonNode rankedStats(String shard, String accountId, String seasonId)
    {
        return get(RANKED_STATS, shard, accountId, seasonId);
    }

    JsonNode seasonStats(String shard, String accountId, String seasonId)
    {
        return get(SEASON_STATS, shard, accountId, seasonId);
    }

    private JsonNode get(String path, Object... uriVariables)
    {
        try
        {
            String body = restClient.get()
                    .uri(properties.baseUrl() + path, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .accept(JSON_API)
                    .retrieve()
                    .body(String.class);
            if(body == null || body.isBlank())
            {
                throw new PubgApiException("PUBG 의 응답이 비어 있다 path=" + path, 0);
            }
            return objectMapper.readTree(body);
        }
        catch(RestClientResponseException e)
        {
            int status = e.getStatusCode().value();
            long retryAfter = (status == 429) ? retryAfterSeconds(e.getResponseHeaders()) : 0;
            // 응답 본문을 메시지에 담지 않는다 — 상태와 경로의 틀만 남긴다(닉네임 · 계정 id 가 로그에 쌓이지 않게)
            throw new PubgApiException("PUBG 가 거절했다 path=" + path + " status=" + status
                    + (status == 429 ? " retryAfter=" + retryAfter + "s" : ""), status, retryAfter, e);
        }
        catch(RestClientException | JacksonException e)
        {
            throw new PubgApiException("PUBG 를 부르다 실패했다 path=" + path + " cause=" + e.getClass().getSimpleName(), 0, 0, e);
        }
    }

    /** {@code X-RateLimit-Reset}(UNIX 초)까지 남은 초 — 1 이상. 헤더가 없거나 숫자가 아니면 60 */
    static long retryAfterSeconds(HttpHeaders headers)
    {
        String reset = (headers == null) ? null : headers.getFirst(RATE_LIMIT_RESET_HEADER);
        if(reset == null || reset.isBlank())
        {
            return PubgApiException.DEFAULT_RETRY_AFTER_SECONDS;
        }
        try
        {
            return Math.max(1L, Long.parseLong(reset.trim()) - Instant.now().getEpochSecond());
        }
        catch(NumberFormatException e)
        {
            return PubgApiException.DEFAULT_RETRY_AFTER_SECONDS;
        }
    }
}
