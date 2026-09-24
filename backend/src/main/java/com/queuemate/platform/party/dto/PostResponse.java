package com.queuemate.platform.party.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;
import java.util.List;

/**
 * <b>글 한 줄</b> — 목록 · 단건 · 쓰기 · 고치기 · 확정이 전부 이 모양을 내려 준다 ({@code contracts/platform-api.md} "글 한 줄").
 *
 * @param postId      글의 id(숫자)이자 {@code roomId} — {@code room} 에는 이 숫자를 문자열로 준다
 * @param hostId      글을 쓴 사람의 사용자 번호
 * @param conditions  jsonb 의 글자 그대로({@link JsonRawValue})
 * @param memberCount {@code members} 의 수. 방이 없거나 글이 만료 · 확정됐으면 0 이다(확정 요청의 응답만 파티원 수다)
 * @param capacity    늘 5 — {@code room} 의 정원이다
 * @param host        글을 쓴 사람의 카드. <b>방이 없거나 만료 · 확정이어도 채운다</b>
 * @param members     방 안에 <b>지금</b> 있는 사람 전원. 방장이 방 안에 있으면 여기에도 있다. 방장 먼저, 나머지는 닉네임순
 */
public record PostResponse(
        Long postId,
        Long hostId,
        String game,
        String mode,
        String title,
        String description,
        String voice,
        String purpose,
        @JsonRawValue String conditions,
        List<String> wantedPositions,
        String status,
        Instant createdAt,
        int memberCount,
        int capacity,
        boolean full,
        MemberCard host,
        List<MemberCard> members
) {
}
