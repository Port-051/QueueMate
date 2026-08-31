package com.queuemate.matching.infra;

import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, UUID> {

    /** INV-1 확인용. DB의 partial unique index 덕분에 결과는 최대 하나다. */
    Optional<MatchRequest> findByUserIdAndStatusIn(UUID userId, Collection<MatchRequestStatus> statuses);

    List<MatchRequest> findAllByIdInAndStatus(Collection<UUID> ids, MatchRequestStatus status);

    List<MatchRequest> findAllByProposalId(UUID proposalId);
}
