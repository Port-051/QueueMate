package com.queuemate.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * access 토큰 검증의 설정값. {@code application.yaml} 의 {@code queuemate.jwt.*} 이다.
 *
 * <p><b>이 앱은 공개 키만 갖는다.</b> 서명(개인 키)은 {@code platform} 만 한다 (docs/11 D-24 — RS256 · JWKS 없음).
 *
 * @param publicKey     공개 키 PEM(X.509 — {@code -----BEGIN PUBLIC KEY-----}, 환경변수 {@code JWT_PUBLIC_KEY}).
 *                      있으면 이것을 쓴다. 운영은 이것이다(Secrets Manager)
 * @param publicKeyFile {@code publicKey} 가 비어 있을 때 읽는 PEM 파일(환경변수 {@code JWT_PUBLIC_KEY_FILE}).
 *                      기본값은 {@code ../../platform/backend/.dev-keys/public.pem} — {@code backend/} 에서 띄운다는 전제의
 *                      <b>로컬 개발용</b>이다({@code platform} 이 처음 뜰 때 만든다). 둘 다 없으면 기동하지 않는다
 */
@ConfigurationProperties(prefix = "queuemate.jwt")
public record JwtProperties(String publicKey, String publicKeyFile) {
}
