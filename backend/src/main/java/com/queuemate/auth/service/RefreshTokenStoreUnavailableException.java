package com.queuemate.auth.service;

import com.queuemate.common.error.ServiceUnavailableException;

/** Redis 장애. fail-closed로 처리하고 토큰 재발급을 거부한다. */
public class RefreshTokenStoreUnavailableException extends ServiceUnavailableException {

    public RefreshTokenStoreUnavailableException(String message, Throwable cause) {
        super("AUTH_STORE_UNAVAILABLE", message, cause);
    }
}
