package com.queuemate.matching.app;

import com.queuemate.common.domain.GameKey;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매칭을 돌리는 주기 작업.
 *
 * <p>요청 생성 요청-응답 안에서 매칭까지 끝내지 않는다. 그렇게 하면 먼저 온 사람이
 * 항상 손해를 보고, 응답 시간이 대기열 길이에 끌려간다.
 *
 * <p>Redis가 죽으면 새 제안을 만들지 않고 조용히 넘어간다. 이미 만들어진 파티에는 영향이 없다 (INV-10).
 */
@Component
public class MatchingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchingScheduler.class);

    private final RealtimeMatcher matcher;
    private final ProposalService proposals;
    private final GameModeConfigProvider modes;
    private final int maxProposalsPerTick;

    public MatchingScheduler(RealtimeMatcher matcher, ProposalService proposals,
                             GameModeConfigProvider modes,
                             @Value("${queuemate.matching.max-proposals-per-tick:20}") int maxProposalsPerTick) {
        this.matcher = matcher;
        this.proposals = proposals;
        this.modes = modes;
        this.maxProposalsPerTick = maxProposalsPerTick;
    }

    @Scheduled(fixedDelayString = "${queuemate.matching.tick-ms:1000}")
    public void matchWaitingUsers() {
        for (GameKey game : GameKey.values()) {
            for (GameModeConfig config : modes.activeModes(game)) {
                drainQueue(config);
            }
        }
    }

    /** 만료 판정을 Redis TTL에만 맡기지 않는다 (docs/07 §6). */
    @Scheduled(fixedDelayString = "${queuemate.proposal.sweep-ms:2000}")
    public void expireOverdueProposals() {
        try {
            proposals.expireOverdue();
        } catch (DataAccessException e) {
            log.warn("만료 정리를 건너뛴다", e);
        }
    }

    /** 한 tick에서 더 만들 조합이 없을 때까지, 다만 상한을 두고 돈다. */
    private void drainQueue(GameModeConfig config) {
        for (int i = 0; i < maxProposalsPerTick; i++) {
            try {
                if (matcher.tryMatch(config.game(), config.modeKey()).isEmpty()) {
                    return;
                }
            } catch (DataAccessException e) {
                log.warn("매칭을 건너뛴다 game={} mode={}", config.game(), config.modeKey(), e);
                return;
            }
        }
    }
}
