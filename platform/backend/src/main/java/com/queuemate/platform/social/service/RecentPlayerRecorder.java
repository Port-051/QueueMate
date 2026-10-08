package com.queuemate.platform.social.service;

import com.queuemate.platform.social.repository.RecentPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 최근 함께한 사람을 <b>채우는</b> 창구 — {@code party} 가 파티를 닫는 트랜잭션 안에서 부른다({@code party.service.PostLifecycle#closeParty}.
 * 2026-09-26 소유자 결정 — "확정된 방이 없어질 때 파티가 닫히고, 그 순간 파티원끼리 서로를 최근 함께한 사람에 적는다").
 *
 * <p>읽는 쪽({@link RecentPlayerService})과 나눈 이유 — 남의 도메인이 부르는 것은 창구로만 한다({@code package-info}). 읽기는 HTTP 가, 쓰기는 {@code party} 가 부른다.
 */
@Service
@RequiredArgsConstructor
public class RecentPlayerRecorder {

    private final RecentPlayerRepository recentPlayerRepository;

    /**
     * 파티 {@code partyId} 의 파티원끼리 서로를 적는다 — 방향마다 한 줄 · 다시 만난 사람은 마지막 것으로 덮는다. 쿼리 한 번이다.
     * <b>부르는 쪽의 트랜잭션에 합류한다</b> — 파티를 닫는 조건부 UPDATE 와 함께 커밋되거나 함께 되돌려진다.
     *
     * @return 넣거나 덮은 줄 수. 파티원이 n명이면 n(n-1)이다
     */
    @Transactional
    public int recordParty(Long partyId, Instant at)
    {
        return recentPlayerRepository.recordParty(partyId, at);
    }
}
