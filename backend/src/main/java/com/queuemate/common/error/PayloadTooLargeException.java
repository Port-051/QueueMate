package com.queuemate.common.error;

/** 413으로 매핑되는 요청 크기 초과. code는 클라이언트가 분기할 수 있는 안정된 값이다. */
public class PayloadTooLargeException extends RuntimeException {

    private final String code;

    public PayloadTooLargeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
