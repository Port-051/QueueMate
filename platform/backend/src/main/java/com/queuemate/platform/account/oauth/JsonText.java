package com.queuemate.platform.account.oauth;

import tools.jackson.databind.JsonNode;

/** 제공자의 JSON 에서 문자열 칸을 꺼낸다 — 없거나 · {@code null} 이거나 · 문자열이 아니거나 · 비어 있으면 전부 {@code null} 로 본다 */
final class JsonText {

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
}
