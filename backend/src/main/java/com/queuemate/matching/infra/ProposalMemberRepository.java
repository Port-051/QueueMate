package com.queuemate.matching.infra;

import com.queuemate.matching.domain.ProposalMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProposalMemberRepository
        extends JpaRepository<ProposalMember, ProposalMember.ProposalMemberId> {

    List<ProposalMember> findAllByIdProposalId(UUID proposalId);
}
