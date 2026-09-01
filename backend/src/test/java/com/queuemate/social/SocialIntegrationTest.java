package com.queuemate.social;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.social.domain.FriendRequest;
import com.queuemate.social.domain.FriendRequestStatus;
import com.queuemate.social.domain.Friendship;
import com.queuemate.social.domain.ReportReason;
import com.queuemate.social.repository.FriendRequestRepository;
import com.queuemate.social.repository.FriendshipRepository;
import com.queuemate.social.service.BlockService;
import com.queuemate.social.service.FriendService;
import com.queuemate.social.service.RecentPlayerService;
import com.queuemate.social.service.RecentPlayerService.RecentPlayer;
import com.queuemate.social.service.ReportService;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 소셜 안전 루프 통합 검증. 트랜잭션 롤백을 쓰지 않는다.
 * 차단 캐시 무효화가 afterCommit에 걸려 있어 롤백하면 검증할 수 없다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate")
                    .withUsername("queuemate")
                    .withPassword("queuemate");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired FriendService friendService;
    @Autowired BlockService blockService;
    @Autowired ReportService reportService;
    @Autowired RecentPlayerService recentPlayerService;
    @Autowired BlockLookupPort blockLookup;
    @Autowired UserRepository users;
    @Autowired FriendRequestRepository friendRequests;
    @Autowired FriendshipRepository friendships;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE reports, blocks, friendships, friend_requests, "
                + "party_members, parties, match_proposals, users CASCADE").update();
        jdbc.sql("SELECT 1").query().singleRow();
    }

    // ---- 친구 ----

    @Test
    void acceptingRequestCreatesFriendshipBothCanSee() {
        UUID sender = user("sender");
        UUID receiver = user("receiver");

        FriendRequest request = friendService.request(sender, receiver);
        Friendship friendship = friendService.accept(receiver, request.getId());

        assertEquals(receiver, friendship.counterpartOf(sender));
        assertEquals(sender, friendship.counterpartOf(receiver));
        assertEquals(1, friendService.listFriends(sender).size());
        assertEquals(1, friendService.listFriends(receiver).size());
        assertEquals(FriendRequestStatus.ACCEPTED,
                friendRequests.findById(request.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectsDuplicatePendingRequestInSameDirection() {
        UUID sender = user("sender");
        UUID receiver = user("receiver");
        friendService.request(sender, receiver);

        assertThrows(ConflictException.class, () -> friendService.request(sender, receiver));
    }

    @Test
    void rejectsRequestWhenInverseRequestIsPending() {
        UUID a = user("a");
        UUID b = user("b");
        friendService.request(a, b);

        assertThrows(ConflictException.class, () -> friendService.request(b, a));
    }

    @Test
    void rejectsSelfRequest() {
        UUID me = user("me");

        assertThrows(ConflictException.class, () -> friendService.request(me, me));
    }

    @Test
    void declinedRequestCannotBeAcceptedLater() {
        UUID sender = user("sender");
        UUID receiver = user("receiver");
        FriendRequest request = friendService.request(sender, receiver);
        friendService.decline(receiver, request.getId());

        assertThrows(ConflictException.class, () -> friendService.accept(receiver, request.getId()));
        assertTrue(friendService.listFriends(sender).isEmpty());
    }

    @Test
    void senderCannotAcceptOwnRequest() {
        UUID sender = user("sender");
        UUID receiver = user("receiver");
        FriendRequest request = friendService.request(sender, receiver);

        assertThrows(NotFoundException.class, () -> friendService.accept(sender, request.getId()));
    }

    @Test
    void removingFriendClearsBothDirections() {
        UUID a = user("a");
        UUID b = user("b");
        friendService.accept(b, friendService.request(a, b).getId());

        friendService.remove(a, b);

        assertTrue(friendService.listFriends(a).isEmpty());
        assertTrue(friendService.listFriends(b).isEmpty());
    }

    // ---- 차단 ----

    @Test
    void blockingRemovesFriendshipAndCancelsPendingRequests() {
        UUID blocker = user("blocker");
        UUID target = user("target");
        UUID other = user("other");
        friendService.accept(target, friendService.request(blocker, target).getId());
        FriendRequest pending = friendService.request(other, blocker);

        blockService.block(blocker, target);

        assertTrue(friendService.listFriends(blocker).isEmpty());
        assertTrue(friendService.listFriends(target).isEmpty());
        // 무관한 제3자의 요청은 건드리지 않는다.
        assertEquals(FriendRequestStatus.PENDING,
                friendRequests.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void blockingCancelsPendingRequestInEitherDirection() {
        UUID blocker = user("blocker");
        UUID target = user("target");
        FriendRequest incoming = friendService.request(target, blocker);

        blockService.block(blocker, target);

        assertEquals(FriendRequestStatus.CANCELLED,
                friendRequests.findById(incoming.getId()).orElseThrow().getStatus());
    }

    /** INV-6: 차단은 방향과 무관하다. 차단당한 쪽에서도 관계를 다시 만들 수 없다. */
    @Test
    void blockedUserCannotSendRequestInEitherDirection() {
        UUID blocker = user("blocker");
        UUID target = user("target");
        blockService.block(blocker, target);

        assertThrows(ConflictException.class, () -> friendService.request(blocker, target));
        assertThrows(ConflictException.class, () -> friendService.request(target, blocker));
    }

    @Test
    void blockLookupSeesNewBlockImmediatelyInBothDirections() {
        UUID blocker = user("blocker");
        UUID target = user("target");
        // 캐시를 먼저 채워서 무효화가 실제로 동작하는지 본다.
        assertTrue(blockLookup.blockedUserIds(blocker).isEmpty());
        assertTrue(blockLookup.blockedUserIds(target).isEmpty());

        blockService.block(blocker, target);

        assertEquals(List.of(target), List.copyOf(blockLookup.blockedUserIds(blocker)));
        assertEquals(List.of(blocker), List.copyOf(blockLookup.blockedUserIds(target)));
        assertTrue(blockLookup.anyBlockBetween(List.of(blocker, target)));
    }

    @Test
    void unblockingClearsCacheToo() {
        UUID blocker = user("blocker");
        UUID target = user("target");
        blockService.block(blocker, target);
        assertFalse(blockLookup.blockedUserIds(blocker).isEmpty());

        blockService.unblock(blocker, target);

        assertTrue(blockLookup.blockedUserIds(blocker).isEmpty());
        assertTrue(blockLookup.blockedUserIds(target).isEmpty());
        assertFalse(blockLookup.anyBlockBetween(List.of(blocker, target)));
    }

    @Test
    void anyBlockBetweenIgnoresUnrelatedUsers() {
        UUID a = user("a");
        UUID b = user("b");
        UUID c = user("c");
        blockService.block(a, b);

        assertFalse(blockLookup.anyBlockBetween(List.of(a, c)));
        assertTrue(blockLookup.anyBlockBetween(List.of(a, b, c)));
    }

    @Test
    void rejectsSelfBlockAndDuplicateBlock() {
        UUID me = user("me");
        UUID other = user("other");
        blockService.block(me, other);

        assertThrows(ConflictException.class, () -> blockService.block(me, me));
        assertThrows(ConflictException.class, () -> blockService.block(me, other));
    }

    @Test
    void unblockingSomeoneNotBlockedIsNotFound() {
        UUID me = user("me");
        UUID other = user("other");

        assertThrows(NotFoundException.class, () -> blockService.unblock(me, other));
    }

    // ---- 신고 ----

    @Test
    void storesReportWithoutConversationContent() {
        UUID reporter = user("reporter");
        UUID target = user("target");

        UUID reportId = reportService.report(
                reporter, target, null, ReportReason.ABUSIVE_LANGUAGE, "욕설").getId();

        Boolean stored = jdbc.sql("SELECT EXISTS(SELECT 1 FROM reports WHERE id = ?)")
                .param(reportId).query(Boolean.class).single();
        assertTrue(stored);
    }

    @Test
    void rejectsSelfReport() {
        UUID me = user("me");

        assertThrows(ConflictException.class,
                () -> reportService.report(me, me, null, ReportReason.OTHER, null));
    }

    // ---- 최근 함께한 사람 ----

    @Test
    void recentPlayersComeFromClosedPartiesAndExcludeBlockedUsers() {
        UUID me = user("me");
        UUID mate = user("mate");
        UUID blocked = user("blocked");
        UUID stillPlaying = user("stillPlaying");

        closedPartyWith(me, mate);
        closedPartyWith(me, blocked);
        openPartyWith(me, stillPlaying);
        blockService.block(me, blocked);

        List<RecentPlayer> recent = recentPlayerService.recentPlayers(me, 20);

        assertEquals(1, recent.size(), "닫힌 파티의 차단되지 않은 동료만 남아야 한다");
        assertEquals(mate, recent.getFirst().userId());
        assertFalse(recent.getFirst().friend());
    }

    @Test
    void recentPlayersMarkFriendsAndCountPlays() {
        UUID me = user("me");
        UUID mate = user("mate");
        closedPartyWith(me, mate);
        closedPartyWith(me, mate);
        friendService.accept(mate, friendService.request(me, mate).getId());

        RecentPlayer player = recentPlayerService.recentPlayers(me, 20).getFirst();

        assertEquals(2, player.playCount());
        assertTrue(player.friend());
    }

    // ---- helpers ----

    private UUID user(String name) {
        String unique = UUID.randomUUID().toString().substring(0, 6);
        // 닉네임은 16자 제약이 있다. 접두어를 잘라 유일성만 유지한다.
        String prefix = name.length() > 10 ? name.substring(0, 10) : name;
        return users.saveAndFlush(
                User.create(name + unique + "@queuemate.test", "hash", prefix + unique)).getId();
    }

    private void closedPartyWith(UUID... members) {
        party("CLOSED", members);
    }

    private void openPartyWith(UUID... members) {
        party("OPEN", members);
    }

    /** 파티 엔티티는 Phase 3 소관이라 여기서는 행을 직접 넣는다. */
    private void party(String status, UUID... members) {
        UUID proposalId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO match_proposals (id, source_type, status, expires_at, confirmed_at)
                VALUES (?, 'REALTIME', 'CONFIRMED', now(), now())
                """).param(proposalId).update();
        UUID partyId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO parties (id, proposal_id, game_key, mode_key, target_size, status, closed_at)
                VALUES (?, ?, 'LOL', 'SOLO_DUO_RANKED', 2, ?, CASE WHEN ? = 'CLOSED' THEN now() END)
                """).param(partyId).param(proposalId).param(status).param(status).update();
        for (UUID member : members) {
            jdbc.sql("INSERT INTO party_members (party_id, user_id) VALUES (?, ?)")
                    .param(partyId).param(member).update();
        }
    }
}
