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

/** 카카오 OAuth2. 사용자 정보는 kakao_account 아래에 모여 있다. */
@Component
public class KakaoOAuthClient implements OAuthClient {

    private final OAuthProperties properties;
    private final RestClient http;

    public KakaoOAuthClient(OAuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.http = builder.build();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public boolean configured() {
        Registration r = properties.registration(OAuthProvider.KAKAO);
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
                .queryParam("scope", r.scope())
                .build()
                .encode()
                .toUri();
    }

    @Override
    public OAuthUserInfo fetchUser(String code, String state) {
        return requestProfile(requestAccessToken(code));
    }

    private String requestAccessToken(String code) {
        Registration r = registration();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", r.clientId());
        form.add("redirect_uri", r.redirectUri());
        form.add("code", code);
        // 카카오는 client_secret이 선택이다. 콘솔에서 켠 경우에만 보낸다.
        if (r.clientSecret() != null && !r.clientSecret().isBlank()) {
            form.add("client_secret", r.clientSecret());
        }
        Map<?, ?> body;
        try {
            body = http.post().uri(r.tokenUri()).body(form).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            throw new OAuthExchangeFailedException("카카오 토큰 교환 실패", e);
        }
        if (body == null || body.get("access_token") == null) {
            throw new OAuthExchangeFailedException("카카오 토큰 응답에 access_token이 없다");
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
            throw new OAuthExchangeFailedException("카카오 사용자 정보 조회 실패", e);
        }
        if (body == null || body.get("id") == null) {
            throw new OAuthExchangeFailedException("카카오 사용자 정보에 id가 없다");
        }
        Map<?, ?> account = nested(body, "kakao_account");
        Map<?, ?> profile = nested(account, "profile");
        return new OAuthUserInfo(
                String.valueOf(body.get("id")),
                asString(account.get("email")),
                Boolean.TRUE.equals(account.get("is_email_verified")),
                asString(profile.get("nickname")),
                asString(profile.get("profile_image_url")));
    }

    private Registration registration() {
        Registration r = properties.registration(OAuthProvider.KAKAO);
        if (r == null || !r.configured()) {
            throw new OAuthExchangeFailedException("카카오 자격 증명이 설정되지 않았다");
        }
        return r;
    }

    /** 제공자 응답은 중첩 Map이다. 없는 키는 빈 Map으로 받아 호출부에서 분기하지 않는다. */
    private static Map<?, ?> nested(Map<?, ?> body, String key) {
        Object value = body.get(key);
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
