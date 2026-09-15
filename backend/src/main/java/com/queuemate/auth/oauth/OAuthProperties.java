package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * 제공자 자격 증명과 흐름의 수명 설정.
 *
 * client-secret은 저장소에 두지 않는다. 환경변수로만 들어온다(docs/13).
 */
@ConfigurationProperties(prefix = "queuemate.oauth")
public record OAuthProperties(
        String frontendCallbackUri,
        String defaultRedirectPath,
        long stateTtlSeconds,
        long handoffTtlSeconds,
        /**
         * 자격 증명이 없는 제공자도 목록에 내보낼 것인가.
         * 개발 중에는 켠다. 등록 전에도 화면을 볼 수 있어야 한다.
         * 운영에서는 끈다. 누르면 실패할 버튼을 그려두지 않기 위해서다.
         */
        boolean exposeUnconfiguredProviders,
        Map<String, Registration> providers
) {

    public Duration stateTtl() {
        return Duration.ofSeconds(stateTtlSeconds);
    }

    public Duration handoffTtl() {
        return Duration.ofSeconds(handoffTtlSeconds);
    }

    public Registration registration(OAuthProvider provider) {
        return providers == null ? null : providers.get(provider.key());
    }

    public record Registration(
            String clientId,
            String clientSecret,
            /** 제공자 콘솔에 등록한 값과 글자 단위로 같아야 한다. */
            String redirectUri,
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String scope
    ) {
        public boolean configured() {
            return clientId != null && !clientId.isBlank();
        }
    }
}
