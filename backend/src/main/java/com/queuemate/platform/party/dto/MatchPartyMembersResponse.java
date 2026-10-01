package com.queuemate.platform.party.dto;

import com.queuemate.platform.account.dto.GameProfileResponse;

import java.util.List;

/**
 * 퀵 매칭 파티의 팀원 카드({@code GET /api/v1/match-parties/{partyId}/members} — 2026-10-01 소유자 결정). 내용은 게시판의 카드({@link MemberCard})와
 * 같은 수준이다(소유자 — "게시판 프로필 창 정도의 정보만"). 자동 매칭에는 방장이 없어 {@code host} 칸이 없다.
 *
 * @param partyId {@code matching} 이 매긴 UUID 문자열
 * @param members 파티원 전원(나 포함). 닉네임순(닉네임이 없는 사람은 뒤로, 그 안에서는 사용자 번호순 — 게시판 카드와 같은 순서)
 */
public record MatchPartyMembersResponse(String partyId, List<Member> members) {

    /**
     * 팀원 한 명.
     *
     * @param userId   사용자 번호
     * @param nickname 이 앱에 없는 번호면 {@code null} 이다(게시판 카드와 같다 — 빼지 않는다)
     * @param position 퀵 매칭에서 고른 포지션 · 역할군({@code matching} 의 파티 HASH 의 {@code member:{userId}} 값). 그 게임의 포지션 이름이 아니면
     *                 ({@code NONE} · PUBG 의 자리 채움 · {@code ""}) {@code null}. 파티 HASH 가 수명으로 사라진 뒤(DB 의 기록으로 답할 때)는 늘 {@code null}
     * @param profile  <b>그 파티의 게임</b>에 연결한 게임 계정 — 게시판 카드의 것과 같은 모양이다. 없으면 {@code null}
     */
    public record Member(Long userId, String nickname, String position, GameProfileResponse profile) {
    }
}
