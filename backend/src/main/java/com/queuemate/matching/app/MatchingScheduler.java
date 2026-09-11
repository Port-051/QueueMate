package com.queuemate.matching.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매칭 주변의 주기 작업.
 *
 * <p>매칭 자체는 여기서 돌리지 않는다. 대기열이 변한 순간 {@link MatchTrigger}가 돌린다.
 * 여기 남은 것은 시간이 지나야만 할 일이 생기는 작업들이다.
 *
 * <p>Redis가 죽으면 새 제안을 만들지 않고 조용히 넘어간다. 이미 만들어진 파티에는 영향이 없다 (INV-10).
 */
@Component
public class MatchingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchingScheduler.class);

    private final MatchTrigger trigger;
    private final ProposalService proposals;
    private final MatchQueueRecoveryService recovery;

    public MatchingScheduler(MatchTrigger trigger, ProposalService proposals,
                             MatchQueueRecoveryService recovery) {
        this.trigger = trigger;
        this.proposals = proposals;
        this.recovery = recovery;
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

    /**
     * 멈춰 선 대기열이 없는지 훑는다.
     *
     * <p>매칭 trigger는 커밋 뒤에 별도 스레드에서 돈다. 그 사이 프로세스가 죽거나 일감이
     * 버려지면 그 bucket은 다음 대기자가 올 때까지 아무도 보지 않는다. 한산한 모드에서는
     * 그것이 몇 시간일 수 있다.
     *
     * <p>여기서 무언가 만들어졌다면 정상 경로가 새고 있다는 뜻이다. 주기를 줄여 덮지 말고
     * 왜 trigger를 잃었는지를 본다.
     */
    @Scheduled(fixedDelayString = "${queuemate.matching.sweep-ms:15000}")
    public void sweepStalledQueues() {
        try {
            trigger.sweepStalledQueues();
        } catch (DataAccessException e) {
            log.warn("대기열 sweep을 건너뛴다", e);
        }
    }

    /**
     * DB와 Redis의 어긋남을 되돌린다.
     *
     * <p>Redis는 트랜잭션에 참여하지 않으므로 커밋 직후 프로세스가 죽으면 Redis 작업이 유실된다.
     * 특히 활성 요청 guard에는 TTL이 없어 저절로 사라지지 않는다. 그대로 두면 그 사용자는
     * 영영 새 매칭을 시작하지 못한다.
     */
    @Scheduled(fixedDelayString = "${queuemate.matching.reconcile-ms:60000}")
    public void reconcileStores() {
        try {
            recovery.reconcile();
        } catch (DataAccessException e) {
            log.warn("정합성 정리를 건너뛴다", e);
        }
    }
}
