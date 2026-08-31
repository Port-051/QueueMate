package com.queuemate.party.repository;

import com.queuemate.party.domain.Party;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
