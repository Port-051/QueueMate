package com.queuemate.auth.oauth;

/** 제공자와의 통신 또는 응답 해석 실패. 사용자에게는 원인을 그대로 보이지 않는다. */
public class OAuthExchangeFailedException extends RuntimeException {

    public OAuthExchangeFailedException(String message) {
        super(message);
    }

    public OAuthExchangeFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
