package com.queuemate.matching.domain;

import java.util.UUID;

/**
 * 파티 조립에 올릴 수 있는 후보 하나.
 *
 * <p>실시간 요청이든 예약이든 조립기가 알아야 하는 것은 "누구인가"와 "무슨 조건인가" 둘뿐이다.
 * 그 밖의 것(대기 시각, 예약 시간대)은 각 매처가 자기 타입 안에서 다룬다.
 */
public interface PartyCandidate {

    UUID userId();

    MatchCondition condition();
}
