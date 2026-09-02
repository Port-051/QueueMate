package com.queuemate.matching.infra;

import com.queuemate.matching.domain.MatchProposal;
import com.queuemate.matching.domain.ProposalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, UUID> {

    /** 만료 정리용. Redis key expiry만 믿지 않는다 (docs/07 §6). */
    List<MatchProposal> findAllByStatusAndExpiresAtLessThanEqual(ProposalStatus status, OffsetDateTime now);

    /**
     * 응답 처리용. 제안 행을 잠가 수락/거절/만료를 직렬화한다.
     *
     * <p>잠그지 않으면 두 사람이 같은 순간에 수락할 때 서로의 변경을 보지 못해
     * 둘 다 "아직 전원 수락은 아니다"로 판단하고, 전원이 눌렀는데도 확정되지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MatchProposal p where p.id = :id")
    Optional<MatchProposal> findByIdForUpdate(@Param("id") UUID id);
}
