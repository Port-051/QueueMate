package com.queuemate.user.riot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Riot Games API 호출.
 *
 * <p>두 단계다. Riot ID(이름#태그)를 puuid로 바꾸고, 그 puuid로 리그 항목을 읽는다.
 * 예전에는 가운데에 summoner-v4로 summonerId를 얻는 단계가 있었지만 Riot이 2025년 6월에
 * SummonerID 기반 엔드포인트를 제거했다. 지금 summoner-v4 응답에는 id 필드가 아예 없다.
 * 아직 그 경로를 쓰는 예제가 많으므로 주의한다.
 *
 * <p><b>"없음"과 "못 읽음"을 구분해서 돌려준다.</b> 없는 Riot ID는 빈 Optional이고,
 * 키 만료·한도 초과·장애는 {@link RiotUnavailableException}이다. 호출부가 이 둘을
 * 같게 다루면, 오타 난 Riot ID를 영원히 다시 묻거나 반대로 일시적 장애를
 * "확인했고 없다"로 굳혀 버린다.
 */
@Component
public class RiotClient {

    private static final Logger log = LoggerFactory.getLogger(RiotClient.class);

    /** 솔로 랭크만 쓴다. 자유 랭크는 따로 표시할 자리도 없고 실력의 근거로도 약하다. */
    private static final String SOLO_QUEUE = "RANKED_SOLO_5x5";

    private final RiotProperties properties;
    private final RestClient http;

    public RiotClient(RiotProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 외부 API가 느려도 계정 연결 응답이 그만큼 늦어지면 안 된다.
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        this.http = builder.requestFactory(factory).build();
    }

    /**
     * `이름#태그`를 puuid로 바꾼다.
     *
     * @return 그런 Riot ID가 없으면 빈 값. 오타이거나 다른 지역 계정이다
     * @throws RiotUnavailableException 조회 자체를 못 했다
     */
    public Optional<String> findPuuid(String gameName, String tagLine) {
        Map<?, ?> body = get(properties.accountBaseUrl(),
                "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", Map.class,
                gameName, tagLine);
        if (body == null || body.get("puuid") == null) {
            return Optional.empty();
        }
        return Optional.of(body.get("puuid").toString());
    }

    /**
     * @return 언랭이면 빈 값. 배치를 마치지 않았거나 이번 시즌을 안 한 계정이다
     * @throws RiotUnavailableException 조회 자체를 못 했다
     */
    public Optional<RiotRank> findSoloRank(String puuid) {
        List<?> entries = get(properties.platformBaseUrl(),
                "/lol/league/v4/entries/by-puuid/{puuid}", List.class, puuid);
        if (entries == null) {
            return Optional.empty();
        }
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(entry -> (Map<?, ?>) entry)
                .filter(entry -> SOLO_QUEUE.equals(asString(entry.get("queueType"))))
                .findFirst()
                .map(entry -> new RiotRank(
                        asString(entry.get("tier")),
                        asString(entry.get("rank")),
                        asInt(entry.get("leaguePoints"))));
    }

    private <T> T get(String baseUrl, String path, Class<T> type, Object... uriVariables) {
        try {
            return http.get()
                    .uri(baseUrl + path, uriVariables)
                    .header("X-Riot-Token", properties.apiKey())
                    .retrieve()
                    .body(type);
        } catch (HttpClientErrorException.NotFound e) {
            // 그런 Riot ID가 없다. 오류가 아니라 조회 결과다.
            return null;
        } catch (RuntimeException e) {
            // 키 만료(401)·한도 초과(429)·Riot 장애·타임아웃이 전부 여기로 온다.
            // Riot ID와 puuid는 PII라 경로에 실린 값을 그대로 남기지 않는다 (docs/13 PII).
            log.warn("Riot API 호출 실패 base={} path={} 원인={}", baseUrl, path, e.getMessage());
            throw new RiotUnavailableException(e);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
