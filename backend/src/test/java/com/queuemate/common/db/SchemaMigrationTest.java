package com.queuemate.common.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway migration이 실제 PostgreSQL에 적용되는지, 그리고 스키마가 CLAUDE.md의
 * invariant를 DB 레벨에서 막는지 검증한다. Docker가 없으면 클래스 전체가 skip된다.
 */
@Testcontainers(disabledWithoutDocker = true)
class SchemaMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate")
                    .withUsername("queuemate")
                    .withPassword("queuemate");

    private Connection conn;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void openConnection() throws SQLException {
        conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        // 각 테스트는 rollback으로 격리한다.
        conn.setAutoCommit(false);
    }

    @AfterEach
    void closeConnection() throws SQLException {
        if (conn != null) {
            conn.rollback();
            conn.close();
        }
    }

    @Test
    void migrationCreatesAllCoreTables() throws SQLException {
        String[] expected = {
                "users", "game_accounts", "game_mode_configs",
                "match_proposals", "match_requests", "reservations", "proposal_members",
                "parties", "party_members",
                "friend_requests", "friendships", "blocks", "reports"
        };
        for (String table : expected) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT to_regclass(?::text) IS NOT NULL")) {
                ps.setString(1, "public." + table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next() && rs.getBoolean(1), table + " 테이블이 없다");
                }
            }
        }
    }

    @Test
    void updatedAtTriggerBumpsTimestamp() throws SQLException {
        UUID user = insertUser();
        OffsetDateTime before = selectUpdatedAt(user);
        exec("UPDATE users SET nickname = 'renamed" + shortId() + "' WHERE id = '" + user + "'");
        assertTrue(selectUpdatedAt(user).isAfter(before), "updated_at이 갱신되지 않았다");
    }

    /** INV-1: 한 사용자는 활성 실시간 매칭 요청을 1개만 가진다. */
    @Test
    void rejectsSecondActiveMatchRequestForSameUser() throws SQLException {
        UUID user = insertUser();
        exec(insertMatchRequestSql(user, "QUEUED"));
        assertThrows(SQLException.class, () -> exec(insertMatchRequestSql(user, "PROPOSED")));
    }

    @Test
    void allowsNewMatchRequestAfterPreviousIsCancelled() throws SQLException {
        UUID user = insertUser();
        exec(insertMatchRequestSql(user, "CANCELLED"));
        exec(insertMatchRequestSql(user, "QUEUED"));
        assertEquals(2, countMatchRequests(user));
    }

    /** INV-9: 사용자는 시간이 겹치는 활성 예약을 중복 등록할 수 없다. */
    @Test
    void rejectsOverlappingActiveReservations() throws SQLException {
        UUID user = insertUser();
        exec(insertReservationSql(user, "ACTIVE", "2026-09-01T20:00:00Z", "2026-09-01T22:00:00Z"));
        assertThrows(SQLException.class, () ->
                exec(insertReservationSql(user, "ACTIVE", "2026-09-01T21:00:00Z", "2026-09-01T23:00:00Z")));
    }

    @Test
    void allowsAdjacentAndCancelledReservationWindows() throws SQLException {
        UUID user = insertUser();
        exec(insertReservationSql(user, "ACTIVE", "2026-09-01T20:00:00Z", "2026-09-01T22:00:00Z"));
        // 경계가 맞닿는 구간은 겹침이 아니다.
        exec(insertReservationSql(user, "ACTIVE", "2026-09-01T22:00:00Z", "2026-09-01T23:30:00Z"));
        // 취소된 예약은 겹쳐도 된다.
        exec(insertReservationSql(user, "CANCELLED", "2026-09-01T20:30:00Z", "2026-09-01T21:30:00Z"));
    }

    @Test
    void rejectsReversedReservationWindow() throws SQLException {
        UUID user = insertUser();
        assertThrows(SQLException.class, () ->
                exec(insertReservationSql(user, "ACTIVE", "2026-09-01T22:00:00Z", "2026-09-01T20:00:00Z")));
    }

    /** INV-4: proposal 하나에서 party는 하나만 확정된다. */
    @Test
    void rejectsSecondPartyForSameProposal() throws SQLException {
        UUID proposal = insertProposal("CONFIRMED");
        exec(insertPartySql(proposal));
        assertThrows(SQLException.class, () -> exec(insertPartySql(proposal)));
    }

    /** INV-5 보조: CONFIRMED가 아닌 proposal은 confirmed_at을 가질 수 없다. */
    @Test
    void rejectsConfirmedAtOnNonConfirmedProposal() {
        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO match_proposals (source_type, status, expires_at, confirmed_at) "
                        + "VALUES ('REALTIME', 'EXPIRED', now(), now())"));
    }

    /** INV-7: 동일 사용자의 PartyMember 중복 금지. */
    @Test
    void rejectsDuplicatePartyMember() throws SQLException {
        UUID party = insertParty(insertProposal("CONFIRMED"));
        UUID user = insertUser();
        exec("INSERT INTO party_members (party_id, user_id) VALUES ('" + party + "', '" + user + "')");
        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO party_members (party_id, user_id) VALUES ('" + party + "', '" + user + "')"));
    }

    @Test
    void rejectsDuplicateProposalMember() throws SQLException {
        UUID proposal = insertProposal("PENDING");
        UUID user = insertUser();
        exec(insertProposalMemberSql(proposal, user));
        assertThrows(SQLException.class, () -> exec(insertProposalMemberSql(proposal, user)));
    }

    /** INV-6 기반: block self relation 금지. */
    @Test
    void rejectsSelfBlock() throws SQLException {
        UUID user = insertUser();
        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO blocks (blocker_id, blocked_id) VALUES ('" + user + "', '" + user + "')"));
    }

    @Test
    void rejectsSelfFriendRequest() throws SQLException {
        UUID user = insertUser();

        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO friend_requests (sender_id, receiver_id) VALUES ('" + user + "', '" + user + "')"));
    }

    /**
     * friendship 정규화 순서는 PostgreSQL의 uuid 비교(바이트 순)를 따라야 한다.
     * Java UUID.compareTo는 long 두 개를 부호 있는 값으로 비교해서 최상위 비트가 선
     * UUID에서 결과가 뒤집힌다. 그 차이가 드러나는 값을 고정해서 쓴다.
     */
    @Test
    void enforcesFriendshipPairOrderingByDatabaseComparison() throws SQLException {
        UUID zeros = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID effs = UUID.fromString("ffffffff-0000-4000-8000-000000000001");
        // Java는 부호 있는 비교라 effs를 더 작다고 본다. PostgreSQL은 반대다.
        assertTrue(effs.compareTo(zeros) < 0, "전제가 깨졌다: Java 비교가 바뀌었다");

        insertUser(zeros);
        insertUser(effs);

        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO friendships (user_low_id, user_high_id) VALUES ('" + effs + "', '" + zeros + "')"));
        exec("INSERT INTO friendships (user_low_id, user_high_id) VALUES ('" + zeros + "', '" + effs + "')");
    }

    @Test
    void rejectsSecondPendingFriendRequestInSameDirection() throws SQLException {
        UUID sender = insertUser();
        UUID receiver = insertUser();
        String sql = "INSERT INTO friend_requests (sender_id, receiver_id) VALUES ('"
                + sender + "', '" + receiver + "')";
        exec(sql);
        assertThrows(SQLException.class, () -> exec(sql));
    }

    @Test
    void rejectsSelfReport() throws SQLException {
        UUID user = insertUser();
        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO reports (reporter_id, target_user_id, reason) VALUES ('"
                        + user + "', '" + user + "', 'ABUSIVE')"));
    }

    @Test
    void rejectsUnsupportedGameKey() {
        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO game_mode_configs (game_key, mode_key, target_party_size) "
                        + "VALUES ('OVERWATCH', 'COMP', 5)"));
    }

    // ---- helpers ----

    private void exec(String sql) throws SQLException {
        // 제약 위반 뒤에도 후속 문장을 실행할 수 있도록 savepoint로 감싼다.
        var savepoint = conn.setSavepoint();
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
            conn.releaseSavepoint(savepoint);
        } catch (SQLException e) {
            conn.rollback(savepoint);
            throw e;
        }
    }

    private UUID insertUser() throws SQLException {
        return insertUser(UUID.randomUUID());
    }

    private UUID insertUser(UUID id) throws SQLException {
        exec("INSERT INTO users (id, email, password_hash, nickname) VALUES ('"
                + id + "', 'u" + shortId() + "@queuemate.test', 'hash', 'nick" + shortId() + "')");
        return id;
    }

    private UUID insertProposal(String status) throws SQLException {
        UUID id = UUID.randomUUID();
        String confirmedAt = "CONFIRMED".equals(status) ? "now()" : "NULL";
        exec("INSERT INTO match_proposals (id, source_type, status, expires_at, confirmed_at) VALUES ('"
                + id + "', 'REALTIME', '" + status + "', now() + interval '20 seconds', " + confirmedAt + ")");
        return id;
    }

    private UUID insertParty(UUID proposalId) throws SQLException {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO parties (id, proposal_id, game_key, mode_key, target_size) VALUES ('"
                + id + "', '" + proposalId + "', 'LOL', 'SOLO_DUO_RANKED', 2)");
        return id;
    }

    private String insertPartySql(UUID proposalId) {
        return "INSERT INTO parties (proposal_id, game_key, mode_key, target_size) VALUES ('"
                + proposalId + "', 'LOL', 'SOLO_DUO_RANKED', 2)";
    }

    private String insertProposalMemberSql(UUID proposalId, UUID userId) {
        return "INSERT INTO proposal_members (proposal_id, user_id, source_request_id) VALUES ('"
                + proposalId + "', '" + userId + "', '" + UUID.randomUUID() + "')";
    }

    private String insertMatchRequestSql(UUID userId, String status) {
        return "INSERT INTO match_requests (user_id, status, condition_json) VALUES ('"
                + userId + "', '" + status + "', '{}'::jsonb)";
    }

    private String insertReservationSql(UUID userId, String status, String from, String to) {
        return "INSERT INTO reservations (user_id, status, condition_json, available_from, available_to, play_amount) "
                + "VALUES ('" + userId + "', '" + status + "', '{}'::jsonb, '" + from + "', '" + to + "', 'ONE_GAME')";
    }

    private int countMatchRequests(UUID userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM match_requests WHERE user_id = ?")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private OffsetDateTime selectUpdatedAt(UUID userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT updated_at FROM users WHERE id = ?")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, OffsetDateTime.class);
            }
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
