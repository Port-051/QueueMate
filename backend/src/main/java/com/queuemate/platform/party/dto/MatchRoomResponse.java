package com.queuemate.platform.party.dto;

/**
 * 자동 매칭 파티의 방 요청({@code POST /api/v1/match-parties/{partyId}/room})의 응답 본문(2026-09-27 — docs/11 D-42).
 *
 * @param roomId 방 번호. <b>{@code partyId} 와 같은 값</b>(matching 의 UUID 문자열)이다 — 클라이언트는 이 값으로 방의 요청({@code /api/v1/rooms/{roomId}/…})을 부른다.
 *               같은 값을 굳이 돌려주는 이유 — 게시판 방의 클라이언트가 "응답의 roomId 로 방에 붙는다"는 한 가지 모양을 그대로 쓰게 하려는 것이다
 */
public record MatchRoomResponse(String roomId) {
}
