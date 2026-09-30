package com.queuemate.platform.party.service;

import java.util.List;

/**
 * 확정된 글 하나의 <b>파티</b>({@code parties} · {@code source = 'BOARD'}) — 열려 있는가와 <b>확정 순간의 파티원</b>({@code party_members}).
 * 목록 · 단건이 확정된 글의 카드를 그릴 때 쓴다({@link PostStore#findBoardParties} — 2026-09-30 소유자 결정 "확정된 글은 확정 순간의 파티원 전원을 보여 준다" · P-40).
 *
 * @param active 파티가 아직 열려 있는가({@code status = 'ACTIVE'}). 열려 있는 파티의 글만 방 키를 읽어 "방이 없어졌나" 를 본다(파티 닫힘 — P-25)
 * @param seats  파티원 전원. 가입하지 않은 번호는 애초에 파티원으로 적히지 않았고(FK), 탈퇴한 사람의 줄은 FK 의 {@code ON DELETE CASCADE} 로 지워져 여기 없다.
 *               순서는 사용자 번호순이다 — 화면의 순서는 카드를 그릴 때 정한다({@code PostService})
 */
record BoardParty(boolean active, List<Seat> seats) {

    /**
     * 카드 한 장의 자리 — 사용자 번호와 방장인가. <b>방 안 사람(모집 중인 글)도 같은 모양으로 그린다</b>({@code PostService}) — 그때 {@code host} 는 글의 {@code hostId} 와 같은가이고,
     * 파티원일 때는 {@code party_members.is_host} 다(그것도 확정할 때 글의 {@code hostId} 로 정했다 — D-23).
     */
    record Seat(Long userId, boolean host) {
    }
}
