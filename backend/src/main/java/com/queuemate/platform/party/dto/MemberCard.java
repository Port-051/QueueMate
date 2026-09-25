package com.queuemate.platform.party.dto;

import com.queuemate.platform.account.dto.GameProfileResponse;

/**
 * 방 안 사람 한 명의 카드 — 글 한 줄의 {@code host} 와 {@code members[]} 가 같은 모양을 쓴다 ({@code contracts/platform-api.md} "글 한 줄").
 *
 * <p>화면이 글을 눌러 펼치지 않고 한 줄에 전원을 보여 주므로 <b>게임 프로필 전체</b>({@code gameNickname} · {@code verified} · {@code tier} ·
 * {@code mainPosition} · {@code server} · {@code stats})를 싣는다.
 *
 * @param userId   사용자 번호
 * @param nickname 이 앱에 없는 번호면 {@code null} 이다(방에 들어온 뒤 사라진 계정 등 — 그런 사람도 카드에서 빼지 않는다.
 *                 빼면 {@code memberCount} 와 어긋난다. 숫자가 아닌 값은 사용자 번호일 수 없어 방 키를 읽을 때 이미 걸러졌다 — {@code room.domain.RoomMemberIds})
 * @param host     글을 쓴 사람인가
 * @param profile  <b>그 글의 게임</b>에 연결한 게임 계정. 없으면 {@code null}
 */
public record MemberCard(Long userId, String nickname, boolean host, GameProfileResponse profile) {
}
