package com.queuemate.party.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * party 생성 직전에 proposal의 확정 사실을 DB에서 직접 확인한다.
 *
 * proposal의 상태 기계는 Member 2 소유라 그쪽 서비스를 부르지 않는다. 대신 스키마
 * (Member 3 소유)를 읽기 전용으로 본다. 호출자가 "확정됐다"고 말한 것을 믿지 않고
 * 수락 기록을 직접 확인해야 INV-4를 party 쪽에서도 보장할 수 있다.
 */
@Component
public class ConfirmedProposalReader {

    private static final String ACCEPTED = "ACCEPTED";
    private static final String CONFIRMED = "CONFIRMED";

    private final JdbcClient jdbc;

    public ConfirmedProposalReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProposalSnapshot> read(UUID proposalId) {
        Optional<String> status = jdbc
                .sql("SELECT status FROM match_proposals WHERE id = :id")
                .param("id", proposalId)
                .query(String.class)
                .optional();
        if (status.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> allMembers = jdbc
                .sql("SELECT user_id FROM proposal_members WHERE proposal_id = :id")
                .param("id", proposalId)
                .query(UUID.class)
                .list();
        List<UUID> accepted = jdbc
                .sql("SELECT user_id FROM proposal_members "
                        + "WHERE proposal_id = :id AND acceptance = :acceptance")
                .param("id", proposalId)
                .param("acceptance", ACCEPTED)
                .query(UUID.class)
                .list();
        return Optional.of(new ProposalSnapshot(CONFIRMED.equals(status.get()), allMembers, accepted));
    }

    /**
     * @param confirmed   proposal이 CONFIRMED 상태인가 (INV-5)
     * @param memberIds   proposal에 묶인 전체 참가자
     * @param acceptedIds 그중 수락한 참가자. party 멤버는 여기서만 나온다 (INV-4)
     */
    public record ProposalSnapshot(boolean confirmed, List<UUID> memberIds, List<UUID> acceptedIds) {

        public boolean acceptedByEveryone() {
            return !memberIds.isEmpty() && acceptedIds.size() == memberIds.size();
        }
    }
}
