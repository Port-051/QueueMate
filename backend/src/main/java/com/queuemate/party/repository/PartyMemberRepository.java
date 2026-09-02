package com.queuemate.party.repository;

import com.queuemate.party.domain.PartyMember;
import com.queuemate.party.domain.PartyStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PartyMemberRepository
        extends JpaRepository<PartyMember, PartyMember.PartyMemberId> {

    List<PartyMember> findByIdPartyIdOrderByJoinedAtAsc(UUID partyId);

    /**
     * 주어진 사용자들 중 이 파티에 아직 남아 있는 사람의 수.
     * signaling은 ICE 후보 때문에 초당 여러 번 들어오므로 멤버 목록 전체를 읽지 않는다.
     */
    @Query("select count(m) from PartyMember m "
            + "where m.id.partyId = :partyId and m.id.userId in :userIds and m.leftAt is null")
    long countActiveMembers(@Param("partyId") UUID partyId,
                            @Param("userIds") Collection<UUID> userIds);

    /** 이 사용자가 아직 속해 있는, 끝나지 않은 파티들. */
    @Query("select m.id.partyId from PartyMember m, Party p "
            + "where m.id.userId = :userId and m.leftAt is null "
            + "and p.id = m.id.partyId and p.status <> com.queuemate.party.domain.PartyStatus.CLOSED")
    List<UUID> findOpenPartyIdsOf(@Param("userId") UUID userId);

    /**
     * 끝나지 않은 파티들의 상태. 이탈 유예를 정하는 데 쓴다.
     *
     * 게임 전과 게임 중은 잘못 내보냈을 때의 비용이 다르다. 어느 쪽인지 모르면
     * 하나의 유예를 모든 상황에 쓰게 되고, 그러면 둘 중 하나는 반드시 틀린다.
     */
    @Query("select p.status from PartyMember m, Party p "
            + "where m.id.userId = :userId and m.leftAt is null "
            + "and p.id = m.id.partyId and p.status <> com.queuemate.party.domain.PartyStatus.CLOSED")
    List<PartyStatus> findOpenPartyStatusesOf(@Param("userId") UUID userId);

    /**
     * 끝나지 않은 파티에 아직 남아 있는 사람들. 접속 여부와 대조하는 데 쓴다.
     *
     * 오래된 파티부터 준다. 정상 경로가 실패해 방치된 파티는 시간이 지나도 그대로 남으므로,
     * 한 번에 볼 양을 제한하더라도 문제가 있는 쪽이 먼저 잡힌다.
     */
    @Query("select m.id.userId from PartyMember m, Party p "
            + "where m.leftAt is null and p.id = m.id.partyId "
            + "and p.status <> com.queuemate.party.domain.PartyStatus.CLOSED "
            + "group by m.id.userId order by min(p.createdAt) asc")
    List<UUID> findActiveMembersOfOpenParties(Limit limit);
}
