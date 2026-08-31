package com.queuemate.matching.infra;

import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, UUID> {

    /** INV-1 확인용. DB의 partial unique index 덕분에 결과는 최대 하나다. */
    Optional<MatchRequest> findByUserIdAndStatusIn(UUID userId, Collection<MatchRequestStatus> statuses);

    List<MatchRequest> findAllByIdInAndStatus(Collection<UUID> ids, MatchRequestStatus status);

    /**
     * 매칭에 쓸 후보를 잠그고 읽는다.
     *
     * <p>잠그지 않으면 매처가 QUEUED로 읽은 요청을 사용자가 그 사이 취소했을 때,
     * 어느 쪽 변경이 살아남을지 알 수 없다. 취소가 되살아나거나 제안만 남는다.
     * queuedAt 순으로 잠가 매처들 사이의 잠금 순서를 일정하게 유지한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MatchRequest r where r.id in :ids and r.status = :status order by r.queuedAt asc")
    List<MatchRequest> lockAllByIdInAndStatus(@Param("ids") Collection<UUID> ids,
                                              @Param("status") MatchRequestStatus status);

    /** 취소처럼 상태를 바꾸기 전에 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MatchRequest r where r.id = :id")
    Optional<MatchRequest> findByIdForUpdate(@Param("id") UUID id);

    List<MatchRequest> findAllByProposalId(UUID proposalId);

    /** 큐 재구성용. DB가 영속 진실이므로 여기서 Redis를 다시 세운다 (docs/07 §10). */
    List<MatchRequest> findAllByStatusOrderByQueuedAtAsc(MatchRequestStatus status);
}
