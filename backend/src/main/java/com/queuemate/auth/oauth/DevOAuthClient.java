package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 자격 증명 없이 전체 흐름을 돌려보기 위한 가짜 제공자.
 *
 * local/test 프로파일에서만 빈으로 등록된다. prod에서 이 빈이 뜨면 아무나 임의의 계정으로
 * 로그인할 수 있으므로 프로파일 조건이 유일한 안전장치다. 동의 화면 대신 콜백으로 곧장 돌린다.
 */
@Component
@Profile({"local", "test"})
public class DevOAuthClient implements OAuthClient {

    private static final Logger log = LoggerFactory.getLogger(DevOAuthClient.class);
    private static final String DEFAULT_USER = "dev-1";

    private final String callbackUri;

    public DevOAuthClient(OAuthProperties properties) {
        OAuthProperties.Registration r = properties.registration(OAuthProvider.DEV);
        this.callbackUri = r == null || r.redirectUri() == null
                ? "http://localhost:8080/api/v1/auth/oauth/dev/callback"
                : r.redirectUri();
    }

    @PostConstruct
    void warn() {
        log.warn("개발용 가짜 OAuth 제공자가 켜져 있다. prod 프로파일에서는 등록되지 않아야 한다.");
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.DEV;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public URI authorizationUri(String state) {
        return UriComponentsBuilder.fromUriString(callbackUri)
                .queryParam("code", DEFAULT_USER)
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    @Override
    public OAuthUserInfo fetchUser(String code, String state) {
        String id = code == null || code.isBlank() ? DEFAULT_USER : code;
        return new OAuthUserInfo(id, id + "@dev.queuemate.local", true, "개발자" + id.replace("dev-", ""), null);
    }
}
