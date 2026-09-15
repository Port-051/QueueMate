package com.queuemate.api;

import com.queuemate.common.domain.GameKey;
import com.queuemate.matching.domain.PartyCreationPort.PartyCreationCommand;
import com.queuemate.party.service.PartyService;
import com.queuemate.user.domain.GameAccount;
import com.queuemate.user.repository.GameAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PartyGameIdsApiContractTest extends ApiContractTestSupport {
    @Autowired PartyService parties;
    @Autowired GameAccountRepository accounts;

    @Test
    void partyMembersCanReadOnlyThisGamesInviteIds() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        UUID outsider = charlie();
        accounts.saveAllAndFlush(List.of(
                GameAccount.create(alpha, GameKey.LOL, "Alpha#KR1", "KR"),
                GameAccount.create(alpha, GameKey.LOL, "Alternate#KR1", "KR"),
                GameAccount.create(alpha, GameKey.VALORANT, "OtherGame#KR1", "KR"),
                GameAccount.create(outsider, GameKey.LOL, "Outsider#KR1", "KR")));
        UUID proposal = UUID.randomUUID();
        jdbc.sql("INSERT INTO match_proposals (id, source_type, status, expires_at, confirmed_at) "
                        + "VALUES (:id, 'REALTIME', 'CONFIRMED', now() + interval '1 minute', now())")
                .param("id", proposal).update();
        for (UUID userId : List.of(alpha, bravo)) {
            jdbc.sql("INSERT INTO proposal_members (proposal_id, user_id, source_request_id, acceptance) "
                            + "VALUES (:proposal, :userId, :request, 'ACCEPTED')")
                    .param("proposal", proposal).param("userId", userId)
                    .param("request", UUID.randomUUID()).update();
        }
        UUID partyId = parties.createParty(new PartyCreationCommand(
                proposal, GameKey.LOL, "SOLO_DUO_RANKED", 2, List.of(alpha, bravo), null));
        var response = get("/api/v1/parties/" + partyId, bravo);
        assertStatus(response, 200);
        for (var member : body(response).path("members")) {
            if (member.path("userId").asText().equals(alpha.toString())) {
                assertEquals(2, member.path("gameIds").size());
                assertEquals("Alpha#KR1", member.path("gameIds").get(0).asText());
            } else {
                assertEquals(0, member.path("gameIds").size());
            }
        }
        assertFalse(response.getBody().contains("OtherGame"));
        assertFalse(response.getBody().contains("Outsider"));
        assertStatus(get("/api/v1/parties/" + partyId, outsider), 404);
    }
}
