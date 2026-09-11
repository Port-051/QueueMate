package com.queuemate.auth.oauth;

import com.queuemate.common.error.ServiceUnavailableException;

/** Redis 장애. state와 교환 코드를 검증할 수 없으면 로그인을 통과시키지 않는다. */
public class OAuthStoreUnavailableException extends ServiceUnavailableException {

    public OAuthStoreUnavailableException(String message, Throwable cause) {
        super("AUTH_STORE_UNAVAILABLE", message, cause);
    }
}
