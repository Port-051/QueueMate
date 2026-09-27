package com.queuemate.common.security;

/**
 * access 토큰에서 검증하는 고정값. <b>원본은 {@code platform} 이다</b> —
 * {@code ../platform/backend/src/main/java/com/queuemate/platform/common/security/TokenClaims.java} 와
 * {@code ../platform/contracts/platform-api.md} "access 토큰" 표(docs/11 D-24). 값을 똑같이 베꼈다.
 *
 * <p><b>따로 바꾸지 마라.</b> 한 글자만 어긋나도 모든 요청이 401 이 된다. 바꿀 때는 {@code platform} 과 같이 바꾼다.
 */
public final class TokenClaims {

    /** {@code iss} */
    public static final String ISSUER = "queuemate-platform";

    /** {@code sub} 는 사용자 번호({@code users.id}, bigint)의 십진 문자열이다 — {@code "42"} */
    public static final String SUBJECT_PATTERN = "^[0-9]{1,19}$";

    /**
     * 토큰의 쓰임새를 가르는 클레임. {@code platform} 은 access 토큰과 소셜 가입 대기 토큰({@code social_signup})을
     * <b>같은 키로 서명한다</b> — 이것을 안 보면 가입 대기 토큰이 access 토큰으로 통한다.
     */
    public static final String TOKEN_USE = "token_use";

    /** access 토큰의 {@code token_use} */
    public static final String TOKEN_USE_ACCESS = "access";

    /** access 토큰을 싣는 쿠키의 이름 */
    public static final String ACCESS_COOKIE = "qm_access";

    private TokenClaims() {
    }
}
