package com.queuemate.platform.party.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;
import java.util.List;

/**
 * <b>글 한 줄</b> — 목록 · 단건 · 쓰기 · 확정이 전부 이 모양을 내려 준다 ({@code contracts/platform-api.md} "글 한 줄").
 *
 * @param postId      글의 id(숫자)이자 {@code roomId} — {@code room} 에는 이 숫자를 문자열로 준다
 * @param hostId      글을 쓴 사람의 사용자 번호. <b>방장이 탈퇴한 확정된 글은 {@code null}</b> 이다(2026-10-02 소유자 결정 — 확정된 파티 기록은 남기고 작성자 칸만 비운다, P-48)
 * @param conditions  jsonb 의 글자 그대로({@link JsonRawValue})
 * @param allowAutoJoin 빠른매치로 들어오는 것을 허용하는가(2026-10-02 소유자 결정 — P-50). {@code false} 면 게시판 방 먼저 합류가 이 방에 넣지 않는다(직접 입장은 된다).
 *                    V10 전에 쓴 글은 {@code true} 다
 * @param hostPosition 방장 자신의 포지션(2026-09-30 소유자 결정 — P-38). 포지션이 없는 모드 · 그 전에 쓴 글은 {@code null} 이다.
 *                    카드({@link MemberCard})가 아니라 글의 칸이다 — 방장의 카드에 붙여 그리는 것은 화면이다
 * @param memberCount {@code members} 의 수 — 모집 중이면 방 안 인원(방 키를 못 읽었으면 0), <b>확정이면 파티원 수</b>(2026-09-30 — P-40), 만료면 0 이다
 * @param capacity    그 방의 정원(방장 포함) — <b>그 모드의 인원</b>이다(2026-09-30 소유자 결정 — P-41. gameconfig 의 {@code targetPartySize} · 솔로 랭크는 2).
 *                    글을 쓸 때 정해진다(글은 고칠 수 없다 — 2026-10-01). 그 전에 쓴 글과 gameconfig 를 못 읽은 채 쓴 글은 5 다. 입장({@code POST /rooms/{roomId}/members})이 이 값으로 만석을 가른다
 * @param full        <b>모집 중인</b> 글의 방이 정원({@code capacity})에 찼는가. 확정 · 만료된 글은 늘 {@code false} 다(들어갈 수 없는 까닭은 {@code status} 가 말한다 — P-40)
 * @param closed      <b>확정된 글의 파티가 닫혔는가</b>(2026-10-01 소유자 결정) — {@code status} 가 {@code CONFIRMED} 이고 그 파티({@code parties.status})가
 *                    {@code CLOSED} 일 때만 {@code true} 다. 파티가 닫혀도 글은 {@code CONFIRMED} 그대로라 이 칸이 "확정(진행 중)" 과 "끝남" 을 가른다.
 *                    모집 중 · 만료 · 파티가 열려 있는 확정은 {@code false}. 목록 · 단건이 그 자리에서 파티를 닫았으면 그 응답부터 {@code true} 다
 * @param host        글을 쓴 사람의 카드. <b>방이 없거나 만료 · 확정이어도 채운다</b> — 단 <b>방장이 탈퇴한 확정된 글은 {@code null}</b> 이다(그릴 사람이 없다 — P-48)
 * @param members     모집 중이면 방 안에 <b>지금</b> 있는 사람 전원(방장이 방 안에 있으면 여기에도 있다). <b>확정이면 확정 순간의 파티원 전원</b> —
 *                    방에서 나간 뒤에도 남는다(2026-09-30 소유자 결정 — P-40). 만료면 비어 있다. 방장 먼저, 나머지는 닉네임순
 */
public record PostResponse(
        Long postId,
        Long hostId,
        String game,
        String mode,
        String title,
        String description,
        String voice,
        @JsonRawValue String conditions,
        List<String> wantedPositions,
        String hostPosition,
        boolean allowAutoJoin,
        String status,
        Instant createdAt,
        Instant autoConfirmAt,
        Instant autoConfirmWarningAt,
        int memberCount,
        int capacity,
        boolean full,
        boolean closed,
        MemberCard host,
        List<MemberCard> members
) {
}
