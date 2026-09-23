package com.queuemate.platform.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 토큰 서명의 설정값. {@code application.yaml} 의 {@code platform.jwt.*} 이고 환경변수로 바꾼다.
 * 환경변수의 이름과 기본값은 {@code contracts/platform-api.md} "access 토큰" 표가 원본이다.
 *
 * @param privateKey     서명하는 개인 키(PKCS#8 PEM, 환경변수 {@code JWT_PRIVATE_KEY}). <b>이 앱만 갖는다</b> (CLAUDE.md §5.1 (가))
 * @param publicKey      검증하는 공개 키(X.509 PEM, 환경변수 {@code JWT_PUBLIC_KEY}). 옆 서비스도 같은 값을 받는다
 * @param keyId          JWT 헤더의 {@code kid}(환경변수 {@code JWT_KEY_ID}). 나중에 키를 바꿀 때 어느 키로 서명했는지 가르는 자리다
 * @param accessTokenTtl access 토큰의 수명이자 쿠키의 {@code Max-Age}(환경변수 {@code ACCESS_TOKEN_TTL})
 * @param devKeyDir      두 키가 다 비어 있을 때 개발용 키를 만들어 두는 폴더. 작업 디렉터리 기준이다 —
 *                       {@code backend/} 에서 띄우면 {@code backend/.dev-keys/} 다. git 에 올리지 않는다
 */
@ConfigurationProperties(prefix = "platform.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        @DefaultValue("dev-1") String keyId,
        @DefaultValue("PT24H") Duration accessTokenTtl,
        @DefaultValue(".dev-keys") String devKeyDir
) {
}
