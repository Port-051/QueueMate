package com.queuemate.platform.social.service;

import com.queuemate.platform.account.service.UserReader;
import com.queuemate.platform.social.domain.RecentPlayer;
import com.queuemate.platform.social.dto.RecentPlayerListResponse;
import com.queuemate.platform.social.dto.RecentPlayerResponse;
import com.queuemate.platform.social.repository.RecentPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 최근 함께한 사람 — <b>읽는 쪽만 있다</b>({@code contracts/platform-api.md} "친구 · 신고 · 최근 함께한 사람").
 * 채우는 것은 파티가 닫힐 때({@code PartyClosed.fifo} — CLAUDE.md §3.4)인데 SQS 배선이 미정이라 <b>아직 아무도 채우지 않는다</b> — 지금은 늘 빈 목록이다.
 *
 * <p>같이 한 사람만 나온다 — 사람을 둘러보는 기능이 아니다(CLAUDE.md §1).
 */
@Service
@RequiredArgsConstructor
public class RecentPlayerService {

    /** 계약의 "50명까지" */
    static final int MAX_PLAYERS = 50;

    private final RecentPlayerRepository recentPlayerRepository;
    private final UserReader userReader;

    /**
     * 최근순 · 50명까지. <b>나와 어느 방향으로든 차단 관계인 사람은 뺀다</b> — 쿼리에서 뺀다(받아 온 뒤에 빼면 50명이 모자란다).
     * 쿼리는 둘이다 — 목록 하나, 닉네임 하나. 닉네임을 찾지 못한 줄(그 사용자가 없어졌다)은 뺀다.
     */
    @Transactional(readOnly = true)
    public RecentPlayerListResponse list(Long me)
    {
        List<RecentPlayer> players = recentPlayerRepository.findRecentExcludingBlocked(me, Limit.of(MAX_PLAYERS));
        Map<Long, String> nicknames = userReader.findNicknames(players.stream().map(RecentPlayer::getOtherUserId).toList());
        return new RecentPlayerListResponse(players.stream()
                .filter(player -> nicknames.containsKey(player.getOtherUserId()))
                .map(player -> new RecentPlayerResponse(player.getOtherUserId(), nicknames.get(player.getOtherUserId()),
                        player.getLastPartyId(), player.getLastPlayedAt()))
                .toList());
    }
}
