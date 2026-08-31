package com.queuemate.reservation.infra;

import com.queuemate.reservation.domain.Reservation;
import com.queuemate.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findAllByUserIdOrderByAvailableFromAsc(UUID userId);

    List<Reservation> findAllByIdInAndStatus(Collection<UUID> ids, ReservationStatus status);

    List<Reservation> findAllByStatusOrderByAvailableFromAsc(ReservationStatus status);

    /**
     * INV-9 확인용. DB의 EXCLUDE 제약은 ACTIVE/PROPOSED만 막으므로
     * MATCHED까지 포함한 검사는 여기서 한다 (docs/04 §9).
     */
    @Query("""
            select r from Reservation r
            where r.userId = :userId
              and r.status in :statuses
              and r.id <> :excludedId
              and r.availableFrom < :to
              and :from < r.availableTo
            """)
    List<Reservation> findOverlapping(@Param("userId") UUID userId,
                                      @Param("statuses") Collection<ReservationStatus> statuses,
                                      @Param("from") OffsetDateTime from,
                                      @Param("to") OffsetDateTime to,
                                      @Param("excludedId") UUID excludedId);
}
