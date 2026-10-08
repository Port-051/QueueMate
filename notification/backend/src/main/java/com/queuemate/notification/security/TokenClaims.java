package com.queuemate.notification.security;

/**
 * access 토큰을 검증할 때 보는 고정값. <b>원본은 이 파일이 아니다</b> — {@code platform} 의
 * {@code common/security/TokenClaims.java} 가 원본이고({@code ../platform/contracts/platform-api.md} "access 토큰"),
 * 여기는 그 값을 글자 그대로 베껴 둔 것이다. <b>여기서만 바꾸면 모든 연결이 401 이 된다</b> — 바꿔야 하면 {@code platform} 과 같이 바꾼다.
 *
 * <p>이 서비스는 서명하지 않는다 — 소셜 가입 대기 토큰({@code social_signup})의 값은 거절하려고 알 필요도 없어서 두지 않았다
 * ({@code token_use} 가 {@link #TOKEN_USE_ACCESS} 가 아니면 전부 거절한다).
 */
public final class TokenClaims {

    /** {@code iss} — platform 의 {@code TokenClaims.ISSUER} */
    public static final String ISSUER = "queuemate-platform";

    /**
     * {@code sub} 는 사용자 번호({@code users.id})의 십진 문자열이다 — {@code "42"}. 이 값이 그대로 알림 채널
     * {@code qm:pubsub:push:{userId}} 의 {@code {userId}} 가 된다. platform 의 {@code TokenClaims.SUBJECT_PATTERN}
     */
    public static final String SUBJECT_PATTERN = "^[0-9]{1,19}$";

    /** 토큰의 쓰임새를 가르는 클레임의 이름 — platform 의 {@code TokenClaims.TOKEN_USE} */
    public static final String TOKEN_USE = "token_use";

    /** access 토큰의 {@code token_use} — platform 의 {@code TokenClaims.TOKEN_USE_ACCESS} */
    public static final String TOKEN_USE_ACCESS = "access";

    /** access 토큰을 싣는 쿠키의 이름 — platform 의 {@code TokenClaims.ACCESS_COOKIE} */
    public static final String ACCESS_COOKIE = "qm_access";

    private TokenClaims() {
    }
}
