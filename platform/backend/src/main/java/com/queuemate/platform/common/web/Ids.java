package com.queuemate.platform.common.web;

import java.util.Optional;

/**
 * 요청 본문의 id 칸을 {@code Long} 으로 판다. 사용자 번호 · 글의 id 는 전부 bigint identity 라 <b>1 이상의 정수</b>만 있을 수 있다
 * (2026-09-22 소유자 결정 — {@code contracts/platform-api.md} "공통").
 *
 * <p>본문의 DTO 가 {@code Long} 이 아니라 {@code String} 으로 받는 이유 — {@code Long} 으로 받으면 숫자가 아닌 값이 "본문을 읽을 수 없다"로 떨어져
 * 어느 필드가 틀렸는지 말해 줄 수 없다(모집 글의 {@code game} 과 같은 이유다). 클라이언트가 JSON 숫자로 보내도 Jackson 이 문자열로 받아 준다.
 * 경로 변수는 {@code Long} 으로 받는다 — 숫자가 아니면 400 이다({@code GlobalExceptionHandler#handleTypeMismatch}).
 */
public final class Ids {

    private Ids()
    {
    }

    /** 있을 수 있는 id 면 그 값, 아니면(비어 있다 · 숫자가 아니다 · 0 이하 · 너무 크다) 비어 있다 */
    public static Optional<Long> parse(String value)
    {
        if(value == null)
        {
            return Optional.empty();
        }
        try
        {
            long id = Long.parseLong(value.strip());
            return id > 0 ? Optional.of(id) : Optional.empty();
        }
        catch(NumberFormatException e)
        {
            return Optional.empty();
        }
    }
}
