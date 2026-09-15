package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.auth.oauth.OAuthProperties.Registration;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 네이버 OAuth2.
 *
 * 토큰 교환에서도 state를 요구한다는 점이 카카오와 다르다. 그래서 state를 인가 요청에서만
 * 쓰고 버릴 수 없고 콜백까지 들고 와야 한다.
 */
@Component
public class NaverOAuthClient implements OAuthClient {

    private final OAuthProperties properties;
    private final RestClient http;

    public NaverOAuthClient(OAuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.http = builder.build();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public boolean configured() {
        Registration r = properties.registration(OAuthProvider.NAVER);
        return r != null && r.configured();
    }

    @Override
    public URI authorizationUri(String state) {
        Registration r = registration();
        return UriComponentsBuilder.fromUriString(r.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", r.clientId())
                .queryParam("redirect_uri", r.redirectUri())
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    @Override
    public OAuthUserInfo fetchUser(String code, String state) {
        return requestProfile(requestAccessToken(code, state));
    }

    private String requestAccessToken(String code, String state) {
        Registration r = registration();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", r.clientId());
        form.add("client_secret", r.clientSecret());
        form.add("code", code);
        form.add("state", state);
        Map<?, ?> body;
        try {
            body = http.post().uri(r.tokenUri()).body(form).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            throw new OAuthExchangeFailedException("네이버 토큰 교환 실패", e);
        }
        if (body == null || body.get("access_token") == null) {
            throw new OAuthExchangeFailedException("네이버 토큰 응답에 access_token이 없다");
        }
        return body.get("access_token").toString();
    }

    private OAuthUserInfo requestProfile(String accessToken) {
        Registration r = registration();
        Map<?, ?> body;
        try {
            body = http.get()
                    .uri(r.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException e) {
            throw new OAuthExchangeFailedException("네이버 사용자 정보 조회 실패", e);
        }
        // 네이버는 실패도 HTTP 200으로 준다. resultcode를 봐야 성공 여부를 안다.
        if (body == null || !"00".equals(String.valueOf(body.get("resultcode")))) {
            throw new OAuthExchangeFailedException("네이버 사용자 정보 조회가 실패로 응답했다");
        }
        Map<?, ?> response = nested(body, "response");
        Object id = response.get("id");
        if (id == null) {
            throw new OAuthExchangeFailedException("네이버 사용자 정보에 id가 없다");
        }
        String email = asString(response.get("email"));
        // 네이버 계정의 이메일은 네이버가 확인한 값이다. 별도 검증 플래그를 주지 않는다.
        return new OAuthUserInfo(
                id.toString(),
                email,
                email != null && !email.isBlank(),
                asString(response.get("nickname")),
                asString(response.get("profile_image")));
    }

    private Registration registration() {
        Registration r = properties.registration(OAuthProvider.NAVER);
        if (r == null || !r.configured()) {
            throw new OAuthExchangeFailedException("네이버 자격 증명이 설정되지 않았다");
        }
        return r;
    }

    private static Map<?, ?> nested(Map<?, ?> body, String key) {
        Object value = body.get(key);
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
