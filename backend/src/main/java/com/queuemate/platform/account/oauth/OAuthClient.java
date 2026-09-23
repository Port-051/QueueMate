package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth 2.0 인가 코드 흐름을 <b>직접</b> 짠 것 — 동의 화면의 주소를 만들고, {@code code} 를 토큰으로 바꾸고, 그 토큰으로 회원 번호를 얻는다.
 *
 * <p><b>Spring 의 oauth2-client 를 쓰지 않는다</b> — 기본값이 인가 요청을 HTTP 세션에 넣는다. 이 앱은 stateless 다
 * (CLAUDE.md §5 · {@code contracts/platform-api.md} "소셜 로그인"). {@code state} 는 쿠키에 둔다({@link OAuthStateCookie}).
 *
 * <p><b>제공자의 access token 은 저장하지 않는다</b> — 이 클래스의 지역 변수로만 살고, 회원 번호를 얻고 나면 버린다. 로그에도 남기지 않는다.
 *
 * <p>외부 호출이라 연결 · 읽기 타임아웃을 건다 — 제공자가 느려지면 콜백을 붙잡은 요청 스레드가 같이 묶인다.
 */
@Component
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthClient {

    private final OAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Map<SocialProvider, OAuthProviderSpec> specs = new EnumMap<>(SocialProvider.class);

    public OAuthClient(OAuthProperties properties, ObjectMapper objectMapper, List<OAuthProviderSpec> specs)
    {
        this.properties = properties;
        this.objectMapper = objectMapper;
        specs.forEach(spec -> this.specs.put(spec.provider(), spec));
        for(SocialProvider provider : SocialProvider.values())
        {
            if(!this.specs.containsKey(provider))
            {
                throw new IllegalStateException("OAuthProviderSpec 이 없는 제공자가 있다 provider=" + provider);
            }
        }

        // 리다이렉트를 따라가지 않는다(JDK HttpClient 의 기본값) — 토큰 · 사용자 정보 주소가 다른 곳으로 넘기는 일은 없어야 한다
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean configured(SocialProvider provider)
    {
        return properties.of(provider).configured();
    }

    /** 제공자의 동의 화면 주소. {@code state} 는 콜백에서 쿠키의 값과 대조한다 */
    public URI authorizationUri(SocialProvider provider, String state)
    {
        OAuthProperties.Provider config = properties.of(provider);
        // 값을 {자리}에 끼워 넣는다 — 그래야 값 안의 : / & = 가 전부 퍼센트로 바뀐다. 값을 queryParam 에 바로 주면 쿼리에 써도 되는 글자(: /)는
        // 그대로 남아 redirect_uri 가 글자 그대로 나간다(대개는 통하지만 제공자가 등록된 값과 글자 단위로 대조한다 — 어긋날 여지를 두지 않는다)
        return UriComponentsBuilder.fromUriString(config.authorizeUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("scope", "{scope}")
                .queryParam("state", "{state}")
                .encode()
                .buildAndExpand(Map.of(
                        "clientId", config.clientId(),
                        "redirectUri", properties.redirectUri(provider),
                        "scope", specs.get(provider).scope(),
                        "state", state))
                .toUri();
    }

    /**
     * {@code code} → 제공자의 토큰 → 회원 번호와 닉네임.
     *
     * @throws OAuthException 제공자가 거절했거나 · 응답이 없거나(타임아웃) · 응답을 읽을 수 없다
     */
    public OAuthUser fetchUser(SocialProvider provider, String code)
    {
        OAuthProperties.Provider config = properties.of(provider);
        try
        {
            String providerAccessToken = exchangeCode(provider, config, code);
            String userInfo = restClient.get()
                    .uri(config.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(providerAccessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return specs.get(provider).parseUser(readJson(userInfo));
        }
        catch(RestClientException | JacksonException e)
        {
            // 예외의 메시지에는 제공자의 응답 본문이 들어 있을 수 있다 — 부른 쪽이 로그에 남길 때 종류만 남긴다
            throw new OAuthException("제공자를 부르다 실패했다 provider=" + provider, e);
        }
    }

    private String exchangeCode(SocialProvider provider, OAuthProperties.Provider config, String code)
    {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.clientId());
        form.add("redirect_uri", properties.redirectUri(provider));
        form.add("code", code);
        // 카카오는 client secret 을 안 켜 둔 앱이 많다 — 빈 값을 실으면 거절하는 제공자가 있어 있을 때만 싣는다
        if(config.clientSecret() != null && !config.clientSecret().isBlank())
        {
            form.add("client_secret", config.clientSecret());
        }
        String tokenResponse = restClient.post()
                .uri(config.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(String.class);
        String providerAccessToken = JsonText.of(readJson(tokenResponse).path("access_token"));
        if(providerAccessToken == null)
        {
            throw new OAuthException("토큰 응답에 access_token 이 없다 provider=" + provider);
        }
        return providerAccessToken;
    }

    private JsonNode readJson(String body)
    {
        if(body == null || body.isBlank())
        {
            throw new OAuthException("제공자의 응답이 비어 있다");
        }
        return objectMapper.readTree(body);
    }
}
