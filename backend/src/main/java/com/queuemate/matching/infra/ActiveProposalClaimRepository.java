package com.queuemate.matching.infra;

import com.queuemate.matching.domain.ActiveProposalClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface ActiveProposalClaimRepository extends JpaRepository<ActiveProposalClaim, UUID> {

    /**
     * 이 중 누구라도 이미 다른 제안에 묶여 있는지.
     *
     * <p>확정 직전 조기 탈출용이다. 진짜 관문은 PK다. 이 확인과 저장 사이에 끼어든 경합은
     * PK가 막는다. 그래도 먼저 보는 이유는, 흔한 어긋남을 예외가 아니라 "이번엔 못 만든다"로
     * 끝내기 위해서다. 예외로 끝나면 같은 트랜잭션에서 앞서 만든 제안까지 함께 되돌아간다.
     */
    boolean existsByUserIdIn(Collection<UUID> userIds);

    /** 제안이 끝났다. 그 제안이 잡고 있던 사람들을 한 번에 놓아 준다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ActiveProposalClaim c where c.proposalId = :proposalId")
    int deleteByProposalId(@Param("proposalId") UUID proposalId);

    /**
     * 제안이 끝났는데 claim만 남은 행을 걷어 낸다.
     *
     * <p>정상 경로에서는 제안 종료와 같은 트랜잭션에서 지우므로 남지 않는다.
     * 남는다면 사람 손이 DB를 건드렸거나 버그다. 그대로 두면 그 사용자는 영영 매칭되지 못한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ActiveProposalClaim c where c.expiresAt <= :cutoff")
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}
