package com.queuemate.party.repository;

import com.queuemate.party.domain.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartyMemberRepository
        extends JpaRepository<PartyMember, PartyMember.PartyMemberId> {

    List<PartyMember> findByIdPartyIdOrderByJoinedAtAsc(UUID partyId);
}
