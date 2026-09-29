package com.queuemate.platform.party.match;

import com.queuemate.platform.account.domain.Game;

import java.time.Instant;
import java.util.Set;

/**
 * {@code matching} 의 파티 HASH({@code qm:party:{partyId}})에서 읽은 <b>확정된 자동 매칭 파티</b> 하나({@link MatchPartyReader#findConfirmed}).
 * 필드 이름의 원본은 {@code matching} 의 {@code proposal/cleanup-confirmed.lua} 머리다(2026-09-27 소유자 결정 — docs/11 D-42).
 *
 * @param partyId         {@code matching} 이 매긴 UUID 문자열. 이 앱에서는 {@code parties.match_party_id} 이자 그 파티의 방 번호({@code roomId})다
 * @param game            {@code game} 필드. 이 앱의 {@link Game} 으로 판 것이다 — 모르는 이름이면 읽는 자리에서 "없는 파티"로 다룬다
 * @param modeKey         {@code modeKey} 필드(예 {@code RANKED_SOLO}). 이 앱은 뜻을 해석하지 않는다
 * @param voicePreference {@code voicePreference} 필드({@code REQUIRED} / {@code NO_VOICE})
 * @param playPurpose     {@code playPurpose} 필드({@code RANK_UP} / {@code TRYHARD} / {@code FUN} — 2026-09-29 소유자 결정으로 {@code NORMAL} 이 {@code TRYHARD}(빡겜)가 됐다, docs/11 D-49).
 *                        이 앱은 읽어서 버린다 — 값을 보지 않으므로 이름이 바뀌어도 코드가 바뀌지 않는다
 * @param target          {@code target} 필드 — 정원. 자동 매칭 파티 방의 정원은 게시판의 5 가 아니라 이것이다
 * @param memberIds       {@code member:{userId}} 필드의 사용자 번호들. 숫자가 아닌 것은 읽는 자리에서 걸러졌다({@code room.domain.RoomMemberIds} 와 같은 정책)
 * @param confirmedAt     {@code confirmedAt} 필드(epoch millis). 없거나 숫자가 아니면 {@code null} — 기록에는 이 앱의 시각을 쓰므로 없어도 파티는 만든다
 */
public record MatchParty(String partyId, Game game, String modeKey, String voicePreference, String playPurpose,
                         int target, Set<Long> memberIds, Instant confirmedAt) {

    public MatchParty
    {
        memberIds = (memberIds == null) ? Set.of() : Set.copyOf(memberIds);
    }
}
