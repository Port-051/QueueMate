package com.queuemate.platform.room.domain;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 방 키에서 읽은 멤버(문자열)를 <b>사용자 번호</b>로 바꾼다 — 게시판이 카드를 그리고 파티원을 적을 때 쓰는 모양이다.
 * 멤버 키는 2026-09-30 부터 HASH(필드 = {@code userId} · 값 = 참가할 때 고른 포지션 — P-44)다 — 스크립트가 돌려준 필드 목록은 {@link #parse},
 * {@code HGETALL} 로 읽은 맵은 {@link #parsePositions} 가 바꾼다.
 *
 * <p>방에는 로그인한 사용자만 들어오지만(입장 · 글 쓰기가 access 토큰의 사용자 번호를 적는다) 손으로 넣은 값까지 막을 수는 없다 —
 * <b>숫자가 아닌 값은 건너뛰고 WARN 을 남긴다.</b> 사용자 번호일 수 없는 값이라 카드에도 파티원에도 실을 수 없다(그런 값은 {@code memberCount} 에서도 빠진다).
 * 숫자지만 가입하지 않은 번호는 그대로 둔다 — 카드에 {@code null} 로 남는다.
 */
@Slf4j
public final class RoomMemberIds {

    private RoomMemberIds()
    {
    }

    /** 값 자체는 로그에 남기지 않는다 — 무엇이 들어 있을지 모른다 */
    public static Set<Long> parse(String roomId, Collection<?> raw)
    {
        Set<Long> members = new LinkedHashSet<>();
        if(raw == null)
        {
            return members;
        }
        for(Object member : raw)
        {
            if(member == null)
            {
                continue;
            }
            try
            {
                members.add(Long.parseLong(member.toString()));
            }
            catch(NumberFormatException e)
            {
                log.warn("멤버 HASH 에 사용자 번호가 아닌 필드가 있다 — 건너뛴다 roomId={}", roomId);
            }
        }
        return members;
    }

    /**
     * {@code HGETALL} 로 읽은 멤버 HASH 를 <b>사용자 번호 → 고른 포지션</b>으로 바꾼다(P-44). 포지션을 고르지 않은 사람(방장 · 포지션이 없는 방)의 값은 {@code ""} 그대로다.
     * 숫자가 아닌 필드는 {@link #parse} 와 같이 건너뛴다. 값 자체는 로그에 남기지 않는다
     */
    public static Map<Long, String> parsePositions(String roomId, Map<?, ?> raw)
    {
        Map<Long, String> positions = new LinkedHashMap<>();
        if(raw == null)
        {
            return positions;
        }
        for(Map.Entry<?, ?> entry : raw.entrySet())
        {
            if(entry.getKey() == null)
            {
                continue;
            }
            try
            {
                positions.put(Long.parseLong(entry.getKey().toString()), entry.getValue() == null ? "" : entry.getValue().toString());
            }
            catch(NumberFormatException e)
            {
                log.warn("멤버 HASH 에 사용자 번호가 아닌 필드가 있다 — 건너뛴다 roomId={}", roomId);
            }
        }
        return positions;
    }
}
