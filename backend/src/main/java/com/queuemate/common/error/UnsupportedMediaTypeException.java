package com.queuemate.common.error;

/**
 * 415로 매핑된다. Spring이 던지는 {@link org.springframework.web.HttpMediaTypeNotSupportedException}은
 * 요청 Content-Type을 두고 하는 판단이고, 이쪽은 본문을 열어 보고 내린 판단이다.
 * 확장자나 헤더는 거짓말을 할 수 있어서 실제로 디코딩해 본 결과를 따로 구분한다.
 */
public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
