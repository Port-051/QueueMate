package com.queuemate.platform.account.oauth;

/**
 * 제공자와 주고받다 난 실패(토큰 교환 실패 · 사용자 정보에 회원 번호가 없음 등). 콜백이 잡아서 {@code /login?error=OAUTH_FAILED} 로 돌려보낸다 —
 * <b>JSON 에러로 새지 않는다</b>(콜백은 브라우저의 최상위 이동이다).
 */
public class OAuthException extends RuntimeException {

    public OAuthException(String message)
    {
        super(message);
    }

    public OAuthException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
