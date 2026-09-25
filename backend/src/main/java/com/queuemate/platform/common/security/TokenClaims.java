package com.queuemate.platform.common.security;

/**
 * 토큰에 찍는 고정값. 원본은 {@code contracts/platform-api.md} "access 토큰" 표다 —
 * <b>검증하는 옆 서비스({@code matching} · {@code notification})와의 약속이다.</b> 따로 바꾸면 그쪽의 검증이 전부 실패한다.
 */
public final class TokenClaims {

    /** {@code iss} */
    public static final String ISSUER = "queuemate-platform";

    /**
     * {@code sub} 는 <b>사용자 번호</b>({@code account.users.id}, bigint)를 문자열로 찍은 것이다 — {@code "42"}. 로그인 아이디가 아니다
     * (2026-09-22 소유자 결정 — {@code contracts/platform-api.md} "access 토큰"). 검증하는 쪽은 숫자 문자열인지도 본다({@code JwtConfig#jwtDecoder}).
     */
    public static final String SUBJECT_PATTERN = "^[0-9]{1,19}$";

    /**
     * 토큰의 쓰임새를 가르는 클레임의 이름. access 토큰과 소셜 가입 대기 토큰을 <b>같은 키로 서명하기 때문에</b> 이것을 안 보면
     * 한쪽을 다른 쪽으로 쓸 수 있다. 입장권({@code room_ticket})도 같은 키였는데 2026-09-25 2단계로 없어졌다 — 입장이 같은 앱 안에서 글을 검사한다.
     */
    public static final String TOKEN_USE = "token_use";

    /** access 토큰의 {@code token_use} */
    public static final String TOKEN_USE_ACCESS = "access";

    /**
     * 소셜로 처음 온 사람이 아이디를 정할 때까지 들고 있는 토큰의 {@code token_use} ({@code contracts/platform-api.md} "소셜 로그인").
     * access 토큰의 검증기는 이것을 거절하고, 이 토큰의 검증기는 access 토큰을 거절한다
     */
    public static final String TOKEN_USE_SOCIAL_SIGNUP = "social_signup";

    /** access 토큰을 싣는 쿠키의 이름 */
    public static final String ACCESS_COOKIE = "qm_access";

    private TokenClaims()
    {
    }
}
