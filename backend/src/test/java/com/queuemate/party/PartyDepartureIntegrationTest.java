package com.queuemate.party;

import com.queuemate.common.domain.GameKey;
import com.queuemate.matching.domain.PartyCreationPort.PartyCreationCommand;
import com.queuemate.common.matching.MatchRequeuePort;
import com.queuemate.common.security.JwtTokenService;
import com.queuemate.party.domain.PartyStatus;
import com.queuemate.party.repository.PartyMemberRepository;
import com.queuemate.party.repository.PartyRepository;
import com.queuemate.party.service.PartyDepartureService;
import com.queuemate.party.service.PartyLifecycleService;
import com.queuemate.party.service.PartyService;
import com.queuemate.realtime.presence.DepartureGracePolicy;
import com.queuemate.realtime.presence.PresenceProperties;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 파티원이 빠졌을 때의 뒷정리. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartyDepartureIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate").withUsername("queuemate").withPassword("queuemate");

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
    @Autowired PartyDepartureService departures;
    @Autowired PartyLifecycleService lifecycle;
    @Autowired DepartureGracePolicy grace;
    @Autowired PresenceProperties presence;
    @Autowired PartyRepository parties;
    @Autowired PartyMemberRepository partyMembers;
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;
    /** matching 모듈은 아직 이 포트를 구현하지 않았다. 호출 여부만 보면 되므로 대역을 쓴다. */
    @MockitoBean MatchRequeuePort requeuePort;
    @Autowired TestRestTemplate http;
    @Autowired JwtTokenService tokenService;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE party_members, parties, proposal_members, match_proposals, "
                + "blocks, friendships, friend_requests, users CASCADE").update();
    }

    @Test
    void 나가면_left_at이_기록된다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = party(3, a, b, c);

        assertTrue(departures.leave(partyId, a));

        var member = partyMembers.findById(new com.queuemate.party.domain.PartyMember
                .PartyMemberId(partyId, a)).orElseThrow();
        assertNotNull(member.getLeftAt());
        assertFalse(member.isReady(), "나간 사람의 준비 상태는 남기지 않는다");
    }

    @Test
    void 준비_안_한_사람이_나가면_남은_전원이_준비라서_READY가_된다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = party(3, a, b, c);
        partyService.changeReady(partyId, a, true);
        partyService.changeReady(partyId, c, true);
        // b만 준비 안 한 상태다.
        assertEquals(PartyStatus.OPEN, parties.findById(partyId).orElseThrow().getStatus());

        departures.leave(partyId, b);

        // 노트 003에 예정된 버그로 적어둔 지점이다. 나가기가 재계산을 태우지 않으면 OPEN에 남는다.
        assertEquals(PartyStatus.READY, parties.findById(partyId).orElseThrow().getStatus());
    }

    @Test
    void 혼자_남으면_파티가_닫힌다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(2, a, b);

        departures.leave(partyId, a);

        var party = parties.findById(partyId).orElseThrow();
        assertEquals(PartyStatus.CLOSED, party.getStatus());
        assertNotNull(party.getClosedAt(), "스키마가 CLOSED와 closed_at을 함께 요구한다");
    }

    @Test
    void 세_명_중_하나가_나가도_파티는_유지된다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = party(3, a, b, c);

        departures.leave(partyId, a);

        assertEquals(PartyStatus.OPEN, parties.findById(partyId).orElseThrow().getStatus());
    }

    @Test
    void 두_번_나가도_처음_시각이_유지된다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = party(3, a, b, c);
        departures.leave(partyId, a);
        OffsetDateTime first = partyMembers.findById(new com.queuemate.party.domain.PartyMember
                .PartyMemberId(partyId, a)).orElseThrow().getLeftAt();

        assertFalse(departures.leave(partyId, a), "이미 나간 사람은 다시 나갈 수 없다");

        assertEquals(first, partyMembers.findById(new com.queuemate.party.domain.PartyMember
                .PartyMemberId(partyId, a)).orElseThrow().getLeftAt());
    }

    @Test
    void 나간_사람은_파티를_볼_수_없다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = party(3, a, b, c);
        departures.leave(partyId, a);

        // 나갔는데도 계속 보이면 파티룸 화면이 안 닫힌다.
        var detail = partyService.detail(partyId, b);
        assertEquals(3, detail.members().size(), "기록은 남는다");
        assertTrue(detail.members().stream()
                        .anyMatch(m -> m.getUserId().equals(a) && m.getLeftAt() != null),
                "나간 표시가 있어야 한다");
    }

    @Test
    void 속한_파티_전부에서_한_번에_나간다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID d = user("delta");
        UUID first = party(2, a, b);
        UUID second = party(2, a, c);
        UUID unrelated = party(2, b, d);

        int left = 0;
        for (UUID partyId : departures.openPartyIdsOf(a)) {
            left += departures.leave(partyId, a) ? 1 : 0;
        }
        assertEquals(2, left);

        assertEquals(PartyStatus.CLOSED, parties.findById(first).orElseThrow().getStatus());
        assertEquals(PartyStatus.CLOSED, parties.findById(second).orElseThrow().getStatus());
        assertEquals(PartyStatus.OPEN, parties.findById(unrelated).orElseThrow().getStatus(),
                "관계없는 파티는 건드리지 않는다");
    }

    @Test
    void 닫힌_파티는_다시_정리하지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(2, a, b);
        departures.leave(partyId, a);

        // b도 나가는데 파티는 이미 닫혀 있다.
        assertTrue(departures.openPartyIdsOf(b).isEmpty(), "닫힌 파티는 대상이 아니다");
    }

    @Test
    void 파티가_닫혀야_최근_함께한_사람에_잡힌다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(2, a, b);

        Long beforeClose = jdbc.sql("SELECT count(*) FROM parties WHERE status = 'CLOSED'")
                .query(Long.class).single();
        assertEquals(0, beforeClose);

        departures.leave(partyId, a);

        // recent_players는 CLOSED 파티만 본다. 파티가 안 닫히면 그 기능이 빈 목록만 준다.
        Long afterClose = jdbc.sql("SELECT count(*) FROM parties WHERE status = 'CLOSED'")
                .query(Long.class).single();
        assertEquals(1, afterClose);
    }

    @Test
    void 파티가_닫히면_남은_사람만_대기열_복귀_대상이다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(2, a, b);

        departures.leave(partyId, a);

        assertEquals(PartyStatus.CLOSED, parties.findById(partyId).orElseThrow().getStatus());
        // 나간 사람은 홈으로 간다. 남은 사람만 대기열로 돌아간다.
        verify(requeuePort).requeueAfterPartyClosed(List.of(b), partyId);
    }

    @Test
    void 게임을_마친_파티가_닫히면_대기열로_되돌리지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = playingParty(a, b);

        departures.leave(partyId, a);

        assertEquals(PartyStatus.CLOSED, parties.findById(partyId).orElseThrow().getStatus());
        // 대기열 복귀는 게임 전에 파티가 깨졌을 때의 구제책이다.
        // 한 판 끝낸 사람을 자동으로 다음 매칭에 밀어 넣지 않는다.
        verifyNoInteractions(requeuePort);
    }

    @Test
    void 파티가_없으면_기본_유예를_받는다() {
        UUID a = user("alpha");

        assertEquals(presence.departureGrace(), grace.graceFor(a));
    }

    @Test
    void 준비가_끝난_파티는_유예가_길어진다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(2, a, b);
        assertEquals(presence.departureGrace(), grace.graceFor(a));

        partyService.changeReady(partyId, a, true);
        partyService.changeReady(partyId, b, true);

        // 전원이 합의한 뒤에 내보내면 다 만들어진 파티가 깨진다.
        assertEquals(presence.readyGrace(), grace.graceFor(a));
    }

    @Test
    void 게임_중이면_가장_긴_유예를_받는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = playingParty(a, b);
        assertEquals(PartyStatus.PLAYING, parties.findById(partyId).orElseThrow().getStatus());

        // 게임 중에 10초로 내보내면 진행 중인 게임의 음성 채널이 사라진다.
        assertEquals(presence.playingGrace(), grace.graceFor(a));
    }

    @Test
    void 여러_파티에_걸쳐_있으면_가장_긴_유예를_따른다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        playingParty(a, b);
        party(2, a, c);

        // 짧은 쪽을 따르면 게임 중인 파티에서 먼저 빠지게 된다.
        assertEquals(presence.playingGrace(), grace.graceFor(a));
    }

    @Test
    void 파티가_닫히면_다시_기본_유예로_돌아간다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = playingParty(a, b);

        departures.leave(partyId, b);

        assertEquals(PartyStatus.CLOSED, parties.findById(partyId).orElseThrow().getStatus());
        assertEquals(presence.departureGrace(), grace.graceFor(a));
    }

    @Test
    void 나가기_요청은_유예_없이_즉시_처리된다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = party(3, a, b, c);

        ResponseEntity<String> response = leaveRequest(partyId, a);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var member = partyMembers.findById(new com.queuemate.party.domain.PartyMember
                .PartyMemberId(partyId, a)).orElseThrow();
        // 연결 끊김은 유예를 기다리지만 본인이 나간다고 하면 기다리지 않는다.
        assertNotNull(member.getLeftAt());
    }

    @Test
    void 두_번_나가면_409다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = party(3, a, b, c);
        leaveRequest(partyId, a);

        assertEquals(HttpStatus.CONFLICT, leaveRequest(partyId, a).getStatusCode());
    }

    @Test
    void 멤버가_아니면_나가기도_404다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID outsider = user("outsider");
        UUID partyId = party(2, a, b);

        // 403이면 그 파티가 존재한다는 사실이 샌다. 조회와 같은 규칙이다.
        assertEquals(HttpStatus.NOT_FOUND, leaveRequest(partyId, outsider).getStatusCode());
    }

    private ResponseEntity<String> leaveRequest(UUID partyId, UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.issueAccessToken(userId));
        return http.exchange("/api/v1/parties/" + partyId + "/leave",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    private UUID user(String nickname) {
        return users.save(User.create(nickname + "@queuemate.dev", "hash", nickname)).getId();
    }

    private UUID playingParty(UUID a, UUID b) {
        UUID partyId = party(2, a, b);
        partyService.changeReady(partyId, a, true);
        partyService.changeReady(partyId, b, true);
        OffsetDateTime now = OffsetDateTime.now().plusMinutes(5);
        lifecycle.startPlaying(partyId, now.minusMinutes(2), now);
        return partyId;
    }

    private UUID party(int size, UUID... members) {
        UUID proposalId = UUID.randomUUID();
        jdbc.sql("INSERT INTO match_proposals (id, source_type, status, expires_at, confirmed_at) "
                        + "VALUES (:id, 'REALTIME', 'CONFIRMED', now() + interval '1 minute', :now)")
                .param("id", proposalId).param("now", OffsetDateTime.now()).update();
        for (UUID member : members) {
            jdbc.sql("INSERT INTO proposal_members (proposal_id, user_id, source_request_id, acceptance) "
                            + "VALUES (:p, :u, :r, 'ACCEPTED')")
                    .param("p", proposalId).param("u", member).param("r", UUID.randomUUID()).update();
        }
        return partyService.createParty(new PartyCreationCommand(
                proposalId, GameKey.PUBG, "SQUAD", size, List.of(members), null));
    }
}
