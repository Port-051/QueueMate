package com.queuemate.notification.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토큰 검증의 설정값. {@code application.yaml} 의 {@code queuemate.jwt.*} 이고 환경변수로 바꾼다.
 *
 * @param publicKey     검증하는 공개 키(X.509 PEM, 환경변수 {@code JWT_PUBLIC_KEY}). {@code platform} 이 받는 것과 같은 값이다.
 *                      운영은 이것으로 넣는다(Secrets Manager)
 * @param publicKeyFile {@code publicKey} 가 비어 있을 때 읽는 PEM 파일(환경변수 {@code JWT_PUBLIC_KEY_FILE}). 기본값은
 *                      {@code platform} 이 만드는 개발용 공개 키({@code ../../platform/backend/.dev-keys/public.pem} — {@code backend/} 기준)다
 */
@ConfigurationProperties(prefix = "queuemate.jwt")
public record JwtProperties(String publicKey, String publicKeyFile) {
}
