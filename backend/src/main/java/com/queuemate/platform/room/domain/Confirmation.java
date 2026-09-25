package com.queuemate.platform.room.domain;

import java.util.List;

/**
 * 방장 확정 한 번의 결과 — 코드와, 확정했으면 <b>그 순간 방에 있던 전원</b>. 파티원을 적는 게시판({@code PostStore#confirmRoom})이 멤버를
 * 따로 읽지 않고 이것을 쓴다 — 확정 뒤에 읽으면 그 사이에 나간 사람이 빠진다.
 *
 * @param members {@code confirm-room.lua} 가 돌려준 멤버(문자열 그대로 — 알림의 {@code payload} 가 이 모양이다). 확정하지 않았으면 비어 있다
 */
public record Confirmation(ConfirmResult result, List<String> members) {

    public Confirmation
    {
        members = (members == null) ? List.of() : List.copyOf(members);
    }
}
