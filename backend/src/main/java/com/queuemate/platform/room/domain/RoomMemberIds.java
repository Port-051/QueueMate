package com.queuemate.platform.room.domain;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 방 키에서 읽은 멤버(문자열)를 <b>사용자 번호</b>로 바꾼다 — 게시판이 카드를 그리고 파티원을 적을 때 쓰는 모양이다.
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
                log.warn("멤버 SET 에 사용자 번호가 아닌 값이 있다 — 건너뛴다 roomId={}", roomId);
            }
        }
        return members;
    }
}
