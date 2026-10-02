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
 * @param accessTokenTtl  access 토큰의 수명이자 쿠키의 {@code Max-Age}(환경변수 {@code ACCESS_TOKEN_TTL})
 * @param refreshTokenTtl refresh 토큰의 수명 — Redis 의 줄({@code qm:auth:refresh:{uuid}})과 쿠키의 {@code Max-Age} 가 같이 쓴다
 *                        (환경변수 {@code REFRESH_TOKEN_TTL}). <b>refresh 는 JWT 가 아니지만</b> 토큰의 수명이라 같은 묶음에 둔다
 *                        ({@link RefreshTokens})
 * @param devKeyDir      두 키가 다 비어 있을 때 개발용 키를 읽는(없으면 만들어 두는) 폴더. 작업 디렉터리 기준이다 —
 *                       {@code backend/} 에서 띄우면 {@code backend/.dev-keys/} 다. git 에 올리지 않는다
 * @param generateDevKeys 두 키가 다 비고 {@code devKeyDir} 에 파일도 없을 때 개발용 키를 새로 만드는가(환경변수
 *                        {@code JWT_GENERATE_DEV_KEYS}, 기본 {@code false}). 끄면 기동을 거부한다 — 파일이 있으면 이 값과 상관없이 읽는다
 *                        (2026-10-02 소유자 결정 · {@code contracts/platform-api.md} P-51 · {@link JwtKeys})
 */
@ConfigurationProperties(prefix = "platform.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        @DefaultValue("dev-1") String keyId,
        @DefaultValue("PT15M") Duration accessTokenTtl,
        @DefaultValue("P7D") Duration refreshTokenTtl,
        @DefaultValue(".dev-keys") String devKeyDir,
        @DefaultValue("false") boolean generateDevKeys
) {

    /** 환경변수로 키를 받았는가 — 아니면 개발용 키 파일을 읽는다(없으면 {@code generateDevKeys} 일 때만 만든다 — {@link JwtKeys#load}) */
    public boolean configured()
    {
        return privateKey != null && !privateKey.isBlank() && publicKey != null && !publicKey.isBlank();
    }

    /**
     * <b>키가 실수로 로그에 찍히지 않게 한다</b> — record 의 기본 {@code toString} 은 모든 칸을 찍는다.
     * 개인 키는 이 앱만 갖는 값이라 로그 · 에러 응답 · actuator 어디에도 나가면 안 된다 (CLAUDE.md §5.1 (가)).
     */
    @Override
    public String toString()
    {
        return "JwtProperties[configured=" + configured() + ", keyId=" + keyId
                + ", accessTokenTtl=" + accessTokenTtl + ", refreshTokenTtl=" + refreshTokenTtl
                + ", devKeyDir=" + devKeyDir + ", generateDevKeys=" + generateDevKeys + "]";
    }
}
