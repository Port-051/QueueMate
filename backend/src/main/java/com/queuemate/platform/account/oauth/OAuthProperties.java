package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 소셜 로그인의 설정값. {@code application.yaml} 의 {@code platform.oauth.*} 이고 환경변수로 바꾼다.
 * 환경변수의 이름과 기본값은 {@code contracts/platform-api.md} "소셜 로그인" 이 원본이다.
 *
 * @param redirectBaseUrl 제공자에 등록하는 Redirect URI 의 앞부분(환경변수 {@code OAUTH_REDIRECT_BASE_URL}).
 *                        Redirect URI 는 이 값 + {@code /api/v1/auth/oauth/{provider}/callback} 이다 — 제공자에 등록한 것과 글자 그대로 같아야 한다
 * @param frontBaseUrl    콜백이 끝나고 브라우저를 돌려보내는 프런트의 주소(환경변수 {@code FRONT_BASE_URL})
 * @param connectTimeout  제공자를 부를 때의 연결 타임아웃. 제공자가 느리면 콜백을 붙잡은 요청 스레드가 같이 묶인다 — 짧게 끊는다
 * @param readTimeout     제공자를 부를 때의 읽기 타임아웃
 */
@ConfigurationProperties(prefix = "platform.oauth")
public record OAuthProperties(
        @DefaultValue("http://localhost:8082") String redirectBaseUrl,
        @DefaultValue("http://localhost:5173") String frontBaseUrl,
        @DefaultValue("PT3S") Duration connectTimeout,
        @DefaultValue("PT3S") Duration readTimeout,
        @DefaultValue Provider kakao,
        @DefaultValue Provider discord
) {
    /**
     * 제공자 하나의 설정. 주소 셋의 기본값(실제 주소)은 {@code application.yaml} 에 있다 — 제공자마다 달라서 여기에 기본값을 둘 수 없다.
     * 테스트는 주소 셋을 가짜 제공자 서버로 돌린다.
     *
     * @param clientId     비어 있으면 <b>그 제공자는 설정되지 않은 것이다</b> — start 가 404 {@code OAUTH_PROVIDER_NOT_CONFIGURED} 로 답한다
     * @param clientSecret 있을 때만 토큰 요청에 싣는다(카카오는 없어도 된다)
     */
    public record Provider(String clientId, String clientSecret, String authorizeUri, String tokenUri, String userInfoUri) {

        public boolean configured()
        {
            return clientId != null && !clientId.isBlank();
        }

        /** 비밀 값이 실수로 로그에 찍히지 않게 한다 — record 의 기본 toString 은 모든 칸을 찍는다 */
        @Override
        public String toString()
        {
            return "Provider[clientId=" + clientId + ", authorizeUri=" + authorizeUri + "]";
        }
    }

    public Provider of(SocialProvider provider)
    {
        return switch(provider)
        {
            case KAKAO -> kakao;
            case DISCORD -> discord;
        };
    }

    /** 이 앱의 콜백 주소 — 인가 요청과 토큰 요청에 <b>같은 값</b>을 실어야 한다(제공자가 대조한다) */
    public String redirectUri(SocialProvider provider)
    {
        return stripTrailingSlash(redirectBaseUrl) + "/api/v1/auth/oauth/" + provider.name() + "/callback";
    }

    /** 프런트의 주소 + 경로. 설정에 끝 슬래시를 붙여 적어도 슬래시가 겹치지 않게 한다 */
    public String frontUrl(String path)
    {
        return stripTrailingSlash(frontBaseUrl) + path;
    }

    private static String stripTrailingSlash(String url)
    {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
