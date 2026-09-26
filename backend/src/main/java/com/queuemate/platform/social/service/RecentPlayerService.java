package com.queuemate.platform.social.service;

import com.queuemate.platform.social.dto.RecentPlayerListResponse;
import com.queuemate.platform.social.repository.RecentPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 함께한 사람 — 읽는 쪽({@code contracts/platform-api.md} "친구 · 신고 · 최근 함께한 사람").
 * 채우는 것은 게시판 파티가 닫힐 때다({@link RecentPlayerRecorder} — 2026-09-26 소유자 결정). 자동 매칭 파티({@code PartyClosed.fifo})는 SQS 배선이 미정이다.
 *
 * <p>같이 한 사람만 나온다 — 사람을 둘러보는 기능이 아니다(CLAUDE.md §1).
 */
@Service
@RequiredArgsConstructor
public class RecentPlayerService {

    /** 계약의 "50명까지" */
    static final int MAX_PLAYERS = 50;

    private final RecentPlayerRepository recentPlayerRepository;

    /** 최근순 · 50명까지. <b>나와 어느 방향으로든 차단 관계인 사람은 뺀다</b> — 쿼리에서 뺀다. 쿼리 한 번이다 — 닉네임은 JOIN 으로 붙인다 */
    @Transactional(readOnly = true)
    public RecentPlayerListResponse list(Long me)
    {
        return new RecentPlayerListResponse(recentPlayerRepository.findRecentExcludingBlocked(me, Limit.of(MAX_PLAYERS)));
    }
}
