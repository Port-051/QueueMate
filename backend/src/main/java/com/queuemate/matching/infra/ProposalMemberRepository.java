package com.queuemate.matching.infra;

import com.queuemate.matching.domain.ProposalMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

import java.util.List;
import java.util.UUID;

public interface ProposalMemberRepository
        extends JpaRepository<ProposalMember, ProposalMember.ProposalMemberId> {

    List<ProposalMember> findAllByIdProposalId(UUID proposalId);

    /**
     * 두 사용자가 함께 참가 중인 진행 제안 (INV-6).
     * 차단이 생긴 순간 이미 떠 있던 제안을 찾아 닫는 데 쓴다.
     */
    @Query("""
            select m.id.proposalId from ProposalMember m
            where m.id.userId in :userIds
              and m.id.proposalId in (
                  select p.id from MatchProposal p where p.status = com.queuemate.matching.domain.ProposalStatus.PENDING
              )
            group by m.id.proposalId
            having count(distinct m.id.userId) = 2
            """)
    List<UUID> findPendingProposalsSharedBy(@Param("userIds") Collection<UUID> userIds);
}
