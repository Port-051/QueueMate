package com.queuemate.common.error;

/** 409로 매핑되는 도메인 충돌. code는 클라이언트가 분기할 수 있는 안정된 값이다. */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
