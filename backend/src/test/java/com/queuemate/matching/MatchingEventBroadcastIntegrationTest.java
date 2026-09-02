package com.queuemate.matching;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.matching.app.MatchRequestService;
import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.app.ProposalViewAssembler;
import com.queuemate.matching.app.RealtimeMatcher;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.event.ServerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;

/**
 * 매칭이 만든 사건이 실제로 WebSocket 이벤트가 되는지 본다.
 *
 * <p>이 테스트가 없어서 놓친 적이 있다. matching은 스프링 이벤트를 발행하고 realtime은
 * 내보낼 준비를 마쳤는데, 둘을 잇는 구독자가 없었다. 두 모듈 각각의 테스트는 통과했다.
 * 각자 자기 경계 안에서는 옳았기 때문이다. 경계를 넘는 것을 보는 테스트만 없었다.
 *
 * <p>그래서 여기서는 제안이 만들어졌다는 사실이 아니라, 그 사실이 참가자에게
 * 전달되기까지 갔는지를 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchingEventBroadcastIntegrationTest {

    private static final String LOL_MODE = "SOLO_DUO_RANKED";

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
        registry.add("queuemate.matching.tick-ms", () -> 3_600_000);
        registry.add("queuemate.proposal.sweep-ms", () -> 3_600_000);
    }

    @Autowired MatchRequestService matchRequests;
    @Autowired RealtimeMatcher matcher;
    @Autowired ProposalService proposals;
    @Autowired UserRepository users;
    @Autowired ProposalViewAssembler assembler;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    /** 전달 직전 지점을 잡는다. 실제 세션이 없어도 무엇을 누구에게 보내려 했는지 볼 수 있다. */
    @MockitoSpyBean RealtimeEventPublisher realtime;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void reset() {
        jdbc.sql("TRUNCATE party_members, parties, proposal_members, match_proposals, "
                + "match_requests, blocks, users CASCADE").update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("제안이 만들어지면 참가자 전원에게 MATCH_PROPOSAL_CREATED가 나간다")
    void proposalCreatedReachesEveryMember() {
        UUID a = newUser();
        UUID b = newUser();
        matchRequests.start(a, lol(LolPosition.TOP));
        matchRequests.start(b, lol(LolPosition.SUPPORT));

        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        List<ServerEvent> created = capturedOf(EventType.MATCH_PROPOSAL_CREATED);
        assertThat(created)
                .as("참가자 두 명에게 각각 한 번씩")
                .hasSize(2);
        assertThat(created).allSatisfy(event -> {
            assertThat(event.payload()).containsKey("proposal");
            ProposalService.ProposalView view =
                    (ProposalService.ProposalView) event.payload().get("proposal");
            assertThat(view.id()).isEqualTo(proposalId);
            // 화면이 이 값으로 남은 시간을 그린다. 없으면 카운트다운이 서지 않는다.
            assertThat(view.expiresAt()).isNotNull();
            assertThat(view.members()).hasSize(2);
            // 계약(openapi ProposalMember)이 요구하는 필드다. 빠지면 서버는 조용하고
            // 화면이 이 값을 쓰다가 죽는다. 실제로 그렇게 한 번 죽었다.
            assertThat(view.members())
                    .as("nickname은 계약에 있는 필드다")
                    .allSatisfy(member -> assertThat(member.nickname()).isNotBlank());
        });
        assertThat(recipients()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("전원이 수락하면 MATCH_CONFIRMED가 partyId를 싣고 나간다")
    void confirmedCarriesPartyId() {
        UUID a = newUser();
        UUID b = newUser();
        matchRequests.start(a, lol(LolPosition.MID));
        matchRequests.start(b, lol(LolPosition.JUNGLE));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        proposals.accept(a, proposalId);
        ProposalService.ProposalView confirmed = proposals.accept(b, proposalId);

        List<ServerEvent> events = capturedOf(EventType.MATCH_CONFIRMED);
        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(event ->
                assertThat(event.payload().get("partyId"))
                        .as("클라이언트가 이 값으로 파티룸에 들어간다")
                        .isEqualTo(confirmed.partyId()));
    }

    @Test
    @DisplayName("아무도 수락하지 않고 기한이 지나면 MATCH_PROPOSAL_EXPIRED가 나간다")
    void expiredIsBroadcast() {
        UUID a = newUser();
        UUID b = newUser();
        matchRequests.start(a, lol(LolPosition.TOP));
        matchRequests.start(b, lol(LolPosition.ADC));
        matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        jdbc.sql("UPDATE match_proposals SET expires_at = now() - interval '1 minute'").update();
        assertThat(proposals.expireOverdue()).isEqualTo(1);

        assertThat(capturedOf(EventType.MATCH_PROPOSAL_EXPIRED)).isNotEmpty();
    }

    @Test
    @DisplayName("REST 조회도 같은 모양을 돌려준다")
    void restViewCarriesNicknameToo() {
        UUID a = newUser();
        UUID b = newUser();
        matchRequests.start(a, lol(LolPosition.MID));
        matchRequests.start(b, lol(LolPosition.SUPPORT));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        ProposalService.ProposalView view =
                assembler.withNicknames(proposals.get(a, proposalId));

        // 출구가 둘이라 한쪽만 채우면 경로에 따라 다른 모양이 나간다.
        // 이번 결함이 정확히 그것이었다.
        assertThat(view.members()).allSatisfy(member ->
                assertThat(member.nickname()).isNotBlank());
    }

    @SuppressWarnings("unchecked")
    private List<ServerEvent> capturedOf(EventType type) {
        ArgumentCaptor<ServerEvent> events = ArgumentCaptor.forClass(ServerEvent.class);
        verify(realtime, atLeastOnce()).publish(any(Collection.class), events.capture());
        return events.getAllValues().stream().filter(event -> event.type() == type).toList();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> recipients() {
        ArgumentCaptor<Collection<UUID>> targets = ArgumentCaptor.forClass(Collection.class);
        verify(realtime, atLeastOnce()).publish(targets.capture(), any(ServerEvent.class));
        return targets.getAllValues().stream().flatMap(Collection::stream).distinct().toList();
    }

    private UUID newUser() {
        int n = sequence.incrementAndGet();
        return users.save(User.create("caster" + n + "@queuemate.test", "hash", "caster" + n)).getId();
    }

    private static MatchCondition lol(LolPosition position) {
        return new MatchCondition(GameKey.LOL, LOL_MODE, position,
                VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
    }
}
