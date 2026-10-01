package com.queuemate.platform.party.match;

import com.queuemate.platform.account.domain.Game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code matching} 의 파티 HASH({@code qm:party:{partyId}})에서 읽은 <b>제안 중이거나 확정된 파티의 파티원</b>({@link MatchPartyReader#findRoster} —
 * 2026-10-01 소유자 결정 "퀵 매칭 파티의 팀원 카드"). 방을 만드는 {@link MatchParty} 와 달리 <b>제안 중({@code PENDING})인 파티도</b> 읽는다 —
 * 수락 창에서 팀원을 보여 주려는 것이다. 그래서 확정 뒤에만 적히는 칸({@code game} 등)이 없을 수 있다.
 *
 * @param partyId {@code matching} 이 매긴 UUID 문자열
 * @param game    {@code game} 필드 — <b>확정된 파티에만 있다</b>({@code matching} 의 {@code proposal/cleanup-confirmed.lua} 가 확정 뒤에 베껴 적는다). 제안 중이거나 모르는 이름이면 {@code null}
 * @param members {@code member:{userId}} 필드 — 사용자 번호 → 값(퀵 매칭에서 고른 keyValue — LoL 포지션 · VALORANT 역할군 · 포지션이 없는 모드는 {@code NONE} ·
 *                PUBG 는 자리 채움 {@code EXIST}). 숫자가 아닌 필드는 읽는 자리에서 걸러졌다. 순서는 HASH 의 것이라 뜻이 없다
 */
public record MatchPartyRoster(String partyId, Game game, Map<Long, String> members) {

    public MatchPartyRoster
    {
        members = (members == null) ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }
}
