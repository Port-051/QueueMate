package com.queuemate.platform.account.oauth;

import tools.jackson.databind.JsonNode;

/** 제공자의 JSON 에서 문자열 칸을 꺼낸다 — 없거나 · {@code null} 이거나 · 문자열이 아니거나 · 비어 있으면 전부 {@code null} 로 본다 */
final class JsonText {

    /** {@code users.email varchar(320)} — RFC 가 허용하는 주소의 최대 길이. 넘는 값은 제공자의 것이 아니라고 보고 버린다(V13) */
    static final int EMAIL_MAX_LENGTH = 320;

    private JsonText()
    {
    }

    static String of(JsonNode node)
    {
        if(node == null || !node.isString())
        {
            return null;
        }
        String value = node.stringValue();
        return (value == null || value.isBlank()) ? null : value;
    }

    /** JSON 의 불리언 칸이 {@code true} 인가 — 없거나 · {@code null} 이거나 · 불리언이 아니면({@code "true"} 문자열도) 전부 {@code false} 다 */
    static boolean isTrue(JsonNode node)
    {
        return node != null && node.isBoolean() && node.booleanValue();
    }

    /**
     * 이메일 칸 — {@link #of} 에 더해 앞뒤 공백을 걷고, {@code @} 가 없거나 320자를 넘으면 {@code null} 이다(DB 칸에 들어가지 않는 값을 미리 버린다).
     * <b>검증 여부는 보지 않는다</b> — 그것은 제공자마다 칸이 달라 {@code *ProviderSpec} 이 본다(P-58).
     */
    static String email(JsonNode node)
    {
        String value = of(node);
        if(value == null)
        {
            return null;
        }
        value = value.strip();
        if(value.isEmpty() || value.indexOf('@') < 0 || value.length() > EMAIL_MAX_LENGTH)
        {
            return null;
        }
        return value;
    }
}
