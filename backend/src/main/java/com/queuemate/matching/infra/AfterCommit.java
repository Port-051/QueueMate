package com.queuemate.matching.infra;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Redis 조작을 DB 커밋 뒤로 미룬다.
 *
 * <p>Redis는 트랜잭션에 참여하지 않는다. 커밋 전에 잠금을 풀거나 큐를 건드리면,
 * 그 트랜잭션이 롤백됐을 때 DB는 되돌아가고 Redis만 바뀐 채로 남는다.
 * 그 순간 "DB에는 아직 제안이 살아 있는데 Redis 잠금은 풀린" 상태가 되어 INV-2가 깨진다.
 *
 * <p>반대로 커밋 직후 프로세스가 죽으면 Redis 작업이 유실될 수 있다. 그쪽은 잠금이
 * 남거나 큐 항목이 빠지는 방향이라 잘못된 매칭을 만들지는 않고, 주기 reconciliation이 되돌린다.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /** 트랜잭션이 없으면 즉시 실행한다. */
    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
