package com.queuemate.user.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Riot Games API 접속 설정.
 *
 * <p>api-key는 저장소에 두지 않는다. 환경변수로만 들어온다 (docs/13).
 * 개발자 키는 24시간마다 만료되므로 비어 있거나 죽어 있는 상태가 정상 범위 안에 있다.
 * 그 경우 티어를 비워 둘 뿐 계정 연결 자체는 그대로 된다.
 */
@ConfigurationProperties(prefix = "queuemate.riot")
public record RiotProperties(
        String apiKey,
        /** account-v1의 대륙 라우팅 base URL. 한국은 asia다. */
        String accountBaseUrl,
        /** 리그 정보를 읽는 플랫폼 base URL. 한국은 kr이다. */
        String platformBaseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        /** 이만큼 지난 티어는 다시 읽는다. */
        Duration rankTtl,
        /** 랭크가 없는 계정을 다시 확인하기까지의 간격. 언랭은 흔하고 잘 변하지 않는다. */
        Duration unrankedTtl
) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
