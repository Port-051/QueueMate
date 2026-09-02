package com.queuemate.party.repository;

import com.queuemate.party.domain.Party;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyRepository extends JpaRepository<Party, UUID> {

    Optional<Party> findByProposalId(UUID proposalId);

    /**
     * party row를 잠근다. 같은 파티의 준비 상태 변경을 직렬화하는 용도다.
     *
     * 잠그지 않으면 두 사람이 동시에 준비를 누를 때 서로의 커밋 전 상태를 읽어
     * 양쪽 다 "아직 전원 준비 아님"으로 판단하고, 전원이 준비했는데도 파티가
     * OPEN에 남는다. 파티 인원은 2~5명이라 이 잠금의 경합 비용은 무시할 수준이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Party p where p.id = :id")
    Optional<Party> findByIdForUpdate(@Param("id") UUID id);

    /**
     * 게임 시작으로 넘길 후보. id만 읽는다.
     *
     * 엔티티를 통째로 들고 오면 잠그기 전 상태를 손에 쥔 채 반복문을 돌게 된다.
     * 실제 전이는 파티마다 다시 잠그고 다시 확인한 뒤에 한다.
     * 한 번에 처리할 양을 제한한다. 밀린 파티가 많아도 한 주기를 오래 붙잡지 않는다.
     */
    @Query("select p.id from Party p where p.status = com.queuemate.party.domain.PartyStatus.READY "
            + "and p.readyAt <= :cutoff order by p.readyAt asc")
    List<UUID> findReadyIdsBefore(@Param("cutoff") OffsetDateTime cutoff, Limit limit);

    /** 너무 오래 게임 중으로 남아 있는 파티. 아무도 나가기를 누르지 않으면 여기서 걷힌다. */
    @Query("select p.id from Party p where p.status = com.queuemate.party.domain.PartyStatus.PLAYING "
            + "and p.playedAt <= :cutoff order by p.playedAt asc")
    List<UUID> findPlayingIdsBefore(@Param("cutoff") OffsetDateTime cutoff, Limit limit);
}
