package com.queuemate.matching.infra;

import com.queuemate.matching.domain.MatchProposal;
import com.queuemate.matching.domain.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, UUID> {

    /** 만료 정리용. Redis key expiry만 믿지 않는다 (docs/07 §6). */
    List<MatchProposal> findAllByStatusAndExpiresAtLessThanEqual(ProposalStatus status, OffsetDateTime now);
}
