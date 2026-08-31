package com.queuemate.party;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.common.party.PartyCreationConflictException;
import com.queuemate.party.domain.PartyStatus;
import com.queuemate.party.repository.PartyMemberRepository;
import com.queuemate.party.repository.PartyRepository;
import com.queuemate.party.service.PartyService;
import com.queuemate.social.service.BlockService;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * party 생성/조회/준비의 통합 검증. 동시성 케이스가 있어 트랜잭션 롤백을 쓰지 않는다.
 * 롤백 안에서는 다른 스레드가 그 데이터를 볼 수 없다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PartyIntegrationTest {

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

    @Autowired PartyService partyService;
    @Autowired PartyRepository parties;
    @Autowired PartyMemberRepository partyMembers;
    @Autowired BlockService blockService;
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE party_members, parties, proposal_members, match_proposals, "
                + "blocks, friendships, friend_requests, users CASCADE").update();
    }

    @Test
    void 전원_수락한_proposal은_party가_된다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID proposalId = confirmedProposal(a, b);

        UUID partyId = partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", 2, null);

        var detail = partyService.detail(partyId, a);
        assertEquals(PartyStatus.OPEN, detail.party().getStatus());
        assertEquals(2, detail.members().size());
        assertEquals(proposalId, detail.party().getProposalId());
    }

    @Test
    void 같은_proposal로_다시_불러도_같은_party다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID proposalId = confirmedProposal(a, b);

        UUID first = partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", 2, null);
        UUID second = partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", 2, null);

        assertEquals(first, second);
        assertEquals(1, parties.count());
    }

    @Test
    void 동시에_확정돼도_party는_하나만_생긴다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID proposalId = confirmedProposal(a, b);

        // 조회 후 저장은 원자적이지 않다. 두 트랜잭션이 같은 순간에 "없음"을 보고
        // 둘 다 insert로 진입한다. 마지막 방어선은 parties_proposal_unique다.
        List<Object> results = runConcurrently(2, () ->
                partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", 2, null));

        long created = results.stream().filter(r -> r instanceof UUID).count();
        long conflicts = results.stream()
                .filter(r -> r instanceof PartyCreationConflictException).count();
        assertEquals(1, parties.count(), "party row는 하나여야 한다");
        assertEquals(2, created + conflicts, "성공 아니면 충돌이어야 한다: " + results);
        assertTrue(created >= 1, "적어도 하나는 성공해야 한다: " + results);
        assertEquals(2, partyMembers.count(), "멤버가 중복 삽입되면 안 된다");
    }

    @Test
    void 확정되지_않은_proposal은_party가_되지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID proposalId = proposal("PENDING", null);
        proposalMember(proposalId, a, "ACCEPTED");
        proposalMember(proposalId, b, "ACCEPTED");

        ConflictException e = assertThrows(ConflictException.class, () ->
                partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", 2, null));
        assertEquals("PROPOSAL_NOT_CONFIRMED", e.getCode());
    }

    @Test
    void 한_명이라도_수락하지_않으면_party가_되지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID proposalId = proposal("CONFIRMED", OffsetDateTime.now());
        proposalMember(proposalId, a, "ACCEPTED");
        proposalMember(proposalId, b, "PENDING");

        ConflictException e = assertThrows(ConflictException.class, () ->
                partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", 2, null));
        assertEquals("PROPOSAL_NOT_FULLY_ACCEPTED", e.getCode());
    }

    @Test
    void 확정_직전에_생긴_차단은_party를_막는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID proposalId = confirmedProposal(a, b);
        // proposal이 만들어진 뒤 차단이 생기는 상황이다. 후보 필터는 이미 지나갔다.
        blockService.block(a, b);

        ConflictException e = assertThrows(ConflictException.class, () ->
                partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", 2, null));
        assertEquals("BLOCKED_MEMBERS", e.getCode());
        assertEquals(0, parties.count());
    }

    @Test
    void 정원과_참가자_수가_다르면_party가_되지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID proposalId = confirmedProposal(a, b);

        ConflictException e = assertThrows(ConflictException.class, () ->
                partyService.createFromProposal(proposalId, "PUBG", "SQUAD", 4, null));
        assertEquals("PARTY_SIZE_MISMATCH", e.getCode());
    }

    @Test
    void 멤버가_아니면_파티를_볼_수_없다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID outsider = user("outsider");
        UUID partyId = partyService.createFromProposal(confirmedProposal(a, b), "LOL", "SOLO_DUO", 2, null);

        // 존재 자체를 숨긴다. 403이면 그 파티가 있다는 사실이 새어나간다.
        NotFoundException e = assertThrows(NotFoundException.class,
                () -> partyService.detail(partyId, outsider));
        assertEquals("PARTY_NOT_FOUND", e.getCode());
    }

    @Test
    void 전원이_준비하면_파티가_READY가_된다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = partyService.createFromProposal(confirmedProposal(a, b), "LOL", "SOLO_DUO", 2, null);

        assertEquals(PartyStatus.OPEN, partyService.changeReady(partyId, a, true).party().getStatus());
        assertEquals(PartyStatus.READY, partyService.changeReady(partyId, b, true).party().getStatus());
        // 한 명이 준비를 풀면 다시 OPEN이다.
        assertEquals(PartyStatus.OPEN, partyService.changeReady(partyId, a, false).party().getStatus());
    }

    @Test
    void 동시에_준비를_눌러도_READY로_올라간다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = partyService.createFromProposal(confirmedProposal(a, b), "LOL", "SOLO_DUO", 2, null);

        // party row 잠금이 없으면 서로의 커밋 전 상태를 읽어 양쪽 다 "아직 전원 준비 아님"으로
        // 판단하고, 전원이 준비했는데도 파티가 OPEN에 남는다.
        List<UUID> readyUsers = List.of(a, b);
        List<Object> results = runConcurrently(2, index ->
                partyService.changeReady(partyId, readyUsers.get(index), true));

        assertTrue(results.stream().noneMatch(r -> r instanceof Exception), "실패한 요청: " + results);
        assertEquals(PartyStatus.READY, parties.findById(partyId).orElseThrow().getStatus());
    }

    private List<Object> runConcurrently(int threads, Callable<?> task) throws Exception {
        return runConcurrently(threads, index -> task.call());
    }

    /** 스레드를 barrier로 정렬해 같은 순간에 들여보낸다. */
    private List<Object> runConcurrently(int threads, IndexedTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        return task.run(index);
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private interface IndexedTask {
        Object run(int index) throws Exception;
    }

    private UUID user(String nickname) {
        User saved = users.save(User.create(nickname + "@queuemate.dev", "hash", nickname));
        return saved.getId();
    }

    private UUID confirmedProposal(UUID... members) {
        UUID proposalId = proposal("CONFIRMED", OffsetDateTime.now());
        for (UUID member : members) {
            proposalMember(proposalId, member, "ACCEPTED");
        }
        return proposalId;
    }

    /** proposal 테이블은 Member 2 소유다. 테스트에서는 스키마를 직접 채운다. */
    private UUID proposal(String status, OffsetDateTime confirmedAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO match_proposals (id, source_type, status, expires_at, confirmed_at) "
                        + "VALUES (:id, 'REALTIME', :status, now() + interval '1 minute', :confirmedAt)")
                .param("id", id)
                .param("status", status)
                .param("confirmedAt", confirmedAt)
                .update();
        return id;
    }

    private void proposalMember(UUID proposalId, UUID userId, String acceptance) {
        jdbc.sql("INSERT INTO proposal_members (proposal_id, user_id, source_request_id, acceptance) "
                        + "VALUES (:proposalId, :userId, :requestId, :acceptance)")
                .param("proposalId", proposalId)
                .param("userId", userId)
                .param("requestId", UUID.randomUUID())
                .param("acceptance", acceptance)
                .update();
    }
}
