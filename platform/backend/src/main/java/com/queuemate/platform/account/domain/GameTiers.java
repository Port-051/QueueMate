package com.queuemate.platform.account.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 게임 계정의 <b>사다리별 티어</b>({@code game_accounts.tiers} — jsonb)를 읽고 쓰는 곳(2026-09-29 소유자 결정 · {@code contracts/platform-api.md} P-36).
 * 칸의 모양은 {@code {사다리: 티어 이름}} 의 JSON 객체 하나이고 사다리 키는 {@link Game#tierLadders()} 다.
 *
 * <p><b>읽을 때는 그 게임의 사다리 키가 전부 나온다</b> — 적혀 있지 않은 사다리는 {@code null} 이다(게임 프로필의 {@code tiers} 가 이 모양이다 —
 * 프런트가 키의 있고 없음을 따지지 않게). <b>쓸 때는 값이 있는 사다리만 적는다</b> — {@code null} 은 키를 빼는 것과 같다.
 * 그 게임의 사다리가 아닌 키를 쓰려 들면 {@link IllegalArgumentException} 이다(코드의 잘못이다 — 요청이 이 키를 고르지 않는다).
 *
 * <p>DB 의 칸을 엔티티에서 글자({@code String})로 들고 여기서 푼다 — 전적 스냅숏의 {@code detail} 과 같은 방식이다.
 * 읽기 전용 엔티티라 JSON 을 맵으로 옮기는 Hibernate 의 장치를 들이지 않았다.
 */
public final class GameTiers {

    /** 설정이 필요 없는 공용 매퍼 — 여기는 문자열 맵 하나를 읽고 쓸 뿐이다 */
    private static final JsonMapper JSON = JsonMapper.shared();

    private GameTiers()
    {
    }

    /**
     * jsonb 의 글자 → 그 게임의 사다리 키 전부(적은 순서) → 티어 이름 또는 {@code null}.
     * 글자가 없거나 · 객체가 아니거나 · 값이 문자열이 아니면 그 사다리는 {@code null} 이다(DB 의 CHECK 가 객체만 받으므로 정상이면 오지 않는다).
     * 그 게임의 사다리가 아닌 키는 버린다.
     */
    public static Map<String, String> read(Game game, String json)
    {
        JsonNode root = parse(json);
        Map<String, String> tiers = new LinkedHashMap<>();
        for(String ladder : game.tierLadders())
        {
            JsonNode value = (root == null) ? null : root.get(ladder);
            tiers.put(ladder, (value != null && value.isString() && !value.stringValue().isBlank()) ? value.stringValue() : null);
        }
        return Collections.unmodifiableMap(tiers);
    }

    /**
     * 사다리 → 티어 이름 → jsonb 에 넣을 글자. 값이 {@code null} 인 사다리는 적지 않는다. 비어 있으면 {@code {}} 다.
     *
     * @throws IllegalArgumentException 그 게임의 사다리가 아닌 키가 있다
     */
    public static String write(Game game, Map<String, String> tiers)
    {
        ObjectNode node = JSON.createObjectNode();
        if(tiers != null)
        {
            for(Map.Entry<String, String> entry : tiers.entrySet())
            {
                if(!game.tierLadders().contains(entry.getKey()))
                {
                    throw new IllegalArgumentException(game + " 의 티어 사다리가 아니다: " + entry.getKey());
                }
            }
            for(String ladder : game.tierLadders())
            {
                String tier = tiers.get(ladder);
                if(tier != null)
                {
                    node.put(ladder, tier);
                }
            }
        }
        return node.toString();
    }

    private static JsonNode parse(String json)
    {
        if(json == null || json.isBlank())
        {
            return null;
        }
        try
        {
            JsonNode root = JSON.readTree(json);
            return root.isObject() ? root : null;
        }
        catch(JacksonException e)
        {
            return null;
        }
    }
}
