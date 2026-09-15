package com.queuemate.common.error;

/**
 * 의존 인프라 장애로 요청을 처리할 수 없다. 503으로 매핑된다.
 * INV-10과 같은 원칙으로 임의 통과시키지 않고 fail-closed 한다.
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String code;

    public ServiceUnavailableException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
