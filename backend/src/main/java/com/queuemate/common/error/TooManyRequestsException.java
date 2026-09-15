package com.queuemate.common.error;

/** 429. 한도를 넘겨 거절한다. 잠시 뒤 다시 시도하면 되는 상태다. */
public class TooManyRequestsException extends RuntimeException {

    private final String code;

    public TooManyRequestsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
