package com.queuemate.matching;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.common.error.ConflictException;
import com.queuemate.matching.app.MatchRequestService;
import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.app.RealtimeMatcher;
import com.queuemate.matching.domain.Acceptance;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.matching.domain.ValorantRole;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.queuemate.matching.infra.MatchingRedisKeys;
import com.queuemate.matching.infra.ProposalClaimRepository;
import com.queuemate.matching.infra.ProposalMemberRepository;
import com.queuemate.social.service.BlockService;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실시간 매칭 전체 흐름 통합 검증.
 *
 * <p>스케줄러는 꺼 두고 매처를 직접 돌린다. 백그라운드 tick이 끼어들면
 * 어떤 제안이 어느 시점에 생겼는지 단정할 수 없다.
 */
@Testcontainers(disabledWithoutDocker = true)
// 애플리케이션에 WebSocket 엔드포인트가 있어 실제 서블릿 컨테이너가 필요하다.
// MOCK 환경에는 jakarta.websocket의 ServerContainer가 없어 컨텍스트가 뜨지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeMatchingIntegrationTest {

    private static final String LOL_MODE = "SOLO_DUO_RANKED";
    private static final String VALORANT_MODE = "COMPETITIVE";

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
        // 주기 작업이 테스트 중에 끼어들지 않게 사실상 끈다.
        registry.add("queuemate.matching.tick-ms", () -> 3_600_000);
        registry.add("queuemate.proposal.sweep-ms", () -> 3_600_000);
    }

    @Autowired MatchRequestService matchRequests;
    @Autowired RealtimeMatcher matcher;
    @Autowired ProposalService proposals;
    @Autowired MatchRequestRepository requestRepository;
    @Autowired MatchProposalRepository proposalRepository;
    @Autowired ProposalMemberRepository memberRepository;
    @Autowired MatchQueueRepository queueRepository;
    @Autowired ProposalClaimRepository claimRepository;
    @Autowired BlockService blocks;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void reset() {
        jdbc.sql("TRUNCATE proposal_members, match_proposals, match_requests, blocks, users CASCADE")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("매칭을 시작하면 대기열에 들어가고, 같은 사용자의 두 번째 요청은 409다")
    void startsAndRejectsDuplicate() {
        UUID user = newUser();

        MatchRequest request = matchRequests.start(user, lol(LolPosition.JUNGLE));

        assertThat(request.getStatus()).isEqualTo(MatchRequestStatus.QUEUED);
        assertThat(queueRepository.waitingCount(lolQueueKey())).isEqualTo(1);
        assertThatThrownBy(() -> matchRequests.start(user, lol(LolPosition.MID)))
                .isInstanceOf(ConflictException.class);
        assertThat(queueRepository.waitingCount(lolQueueKey())).isEqualTo(1);
    }

    @Test
    @DisplayName("호환되는 두 사람이 대기하면 제안이 만들어지고 대기열에서 빠진다")
    void createsProposalForCompatiblePair() {
        UUID jungler = newUser();
        UUID mid = newUser();
        matchRequests.start(jungler, lol(LolPosition.JUNGLE));
        matchRequests.start(mid, lol(LolPosition.MID));

        Optional<UUID> proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE);

        assertThat(proposalId).isPresent();
        assertThat(memberRepository.findAllByIdProposalId(proposalId.get()))
                .hasSize(2)
                .allSatisfy(member -> assertThat(member.getAcceptance()).isEqualTo(Acceptance.PENDING));
        assertThat(queueRepository.waitingCount(lolQueueKey())).isZero();
        assertThat(claimRepository.activeProposalOf(jungler)).contains(proposalId.get());
        assertThat(requestRepository.findAllByProposalId(proposalId.get()))
                .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(MatchRequestStatus.PROPOSED));
    }

    @Test
    @DisplayName("INV-8: LoL 랭크에서 같은 포지션끼리는 제안이 만들어지지 않는다")
    void neverMatchesSameLolPosition() {
        matchRequests.start(newUser(), lol(LolPosition.JUNGLE));
        matchRequests.start(newUser(), lol(LolPosition.JUNGLE));

        assertThat(matcher.tryMatch(GameKey.LOL, LOL_MODE)).isEmpty();
        assertThat(queueRepository.waitingCount(lolQueueKey())).isEqualTo(2);
    }

    @Test
    @DisplayName("음성 REQUIRED와 NO_VOICE는 함께 묶이지 않는다")
    void neverMatchesConflictingVoice() {
        matchRequests.start(newUser(), new MatchCondition(GameKey.LOL, LOL_MODE,
                LolPosition.JUNGLE, VoicePreference.REQUIRED, PlayPurpose.RANK_UP));
        matchRequests.start(newUser(), new MatchCondition(GameKey.LOL, LOL_MODE,
                LolPosition.MID, VoicePreference.NO_VOICE, PlayPurpose.RANK_UP));

        assertThat(matcher.tryMatch(GameKey.LOL, LOL_MODE)).isEmpty();
    }

    @Test
    @DisplayName("INV-6: 차단한 사이는 같은 제안에 들어가지 않는다")
    void neverMatchesBlockedUsers() {
        UUID blocker = newUser();
        UUID blocked = newUser();
        blocks.block(blocker, blocked);
        matchRequests.start(blocker, lol(LolPosition.JUNGLE));
        matchRequests.start(blocked, lol(LolPosition.MID));

        assertThat(matcher.tryMatch(GameKey.LOL, LOL_MODE)).isEmpty();
        assertThat(queueRepository.waitingCount(lolQueueKey())).isEqualTo(2);
    }

    @Test
    @DisplayName("INV-4: 한 명이라도 응답이 남아 있으면 확정되지 않는다")
    void doesNotConfirmUntilEveryoneAccepts() {
        UUID first = newUser();
        UUID second = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        proposals.accept(first, proposalId);

        assertThat(proposalRepository.findById(proposalId))
                .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.PENDING);

        proposals.accept(second, proposalId);

        assertThat(proposalRepository.findById(proposalId))
                .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.CONFIRMED);
        assertThat(requestRepository.findAllByProposalId(proposalId))
                .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(MatchRequestStatus.MATCHED));
        // 확정되면 대기열과 잠금에서 완전히 빠진다.
        assertThat(claimRepository.activeProposalOf(first)).isEmpty();
        assertThat(queueRepository.activeRequestOf(first)).isEmpty();
    }

    @Test
    @DisplayName("INV-3: 같은 사람이 여러 번 수락해도 확정은 한 번뿐이다")
    void repeatedAcceptDoesNotConfirmTwice() {
        UUID first = newUser();
        UUID second = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        proposals.accept(first, proposalId);
        proposals.accept(first, proposalId);
        proposals.accept(second, proposalId);

        assertThat(proposalRepository.findById(proposalId))
                .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.CONFIRMED);
        // 확정된 제안에 다시 수락하면 거부한다 (INV-5).
        assertThatThrownBy(() -> proposals.accept(second, proposalId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("확정 응답에 파티 id가 실린다")
    void confirmResponseCarriesPartyId() {
        UUID first = newUser();
        UUID second = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        proposals.accept(first, proposalId);
        var confirmed = proposals.accept(second, proposalId);

        // 클라이언트는 이 값으로 파티룸에 들어간다. null이면 갈 곳을 모른다.
        assertThat(confirmed.status()).isEqualTo(ProposalStatus.CONFIRMED);
        assertThat(confirmed.partyId()).isNotNull();
    }

    @Test
    @DisplayName("확정된 제안을 다시 조회해도 파티 id가 실린다")
    void refetchedProposalCarriesPartyId() {
        UUID first = newUser();
        UUID second = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();
        proposals.accept(first, proposalId);
        UUID partyId = proposals.accept(second, proposalId).partyId();

        // 화면을 새로고침한 경우다. 방금 만든 id를 들고 있지 않으니 파티 쪽에 물어야 한다.
        assertThat(proposals.get(first, proposalId).partyId()).isEqualTo(partyId);
    }

    @Test
    @DisplayName("확정 전에는 파티 id가 없다")
    void pendingProposalHasNoPartyId() {
        UUID first = newUser();
        UUID second = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        var pending = proposals.accept(first, proposalId);

        assertThat(pending.status()).isEqualTo(ProposalStatus.PENDING);
        assertThat(pending.partyId()).isNull();
    }

    @Test
    @DisplayName("거절하면 제안이 끝나고 두 사람 모두 대기열로 돌아간다")
    void declineReturnsEveryoneToQueue() {
        UUID decliner = newUser();
        UUID other = newUser();
        matchRequests.start(decliner, lol(LolPosition.JUNGLE));
        matchRequests.start(other, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        proposals.decline(decliner, proposalId);

        assertThat(proposalRepository.findById(proposalId))
                .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.DECLINED);
        assertThat(requestRepository.findAllByProposalId(proposalId)).isEmpty();
        assertThat(queueRepository.waitingCount(lolQueueKey())).isEqualTo(2);
        assertThat(claimRepository.activeProposalOf(decliner)).isEmpty();
        // 조건이 그대로이므로 곧바로 다시 매칭될 수 있다.
        assertThat(matcher.tryMatch(GameKey.LOL, LOL_MODE)).isPresent();
    }

    @Test
    @DisplayName("INV-5: 끝난 제안은 되살아나지 않는다")
    void terminalProposalCannotBeRevived() {
        UUID first = newUser();
        UUID second = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();
        proposals.decline(first, proposalId);

        assertThatThrownBy(() -> proposals.accept(second, proposalId))
                .isInstanceOf(ConflictException.class);
        assertThat(proposalRepository.findById(proposalId))
                .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.DECLINED);
    }

    @Test
    @DisplayName("제안 중에는 요청을 취소할 수 없다")
    void cannotCancelWhileProposed() {
        UUID first = newUser();
        UUID second = newUser();
        MatchRequest request = matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        matcher.tryMatch(GameKey.LOL, LOL_MODE).orElseThrow();

        assertThatThrownBy(() -> matchRequests.cancel(first, request.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("취소하면 대기열과 guard가 함께 비워져 다시 시작할 수 있다")
    void cancelFreesTheUser() {
        UUID user = newUser();
        MatchRequest request = matchRequests.start(user, lol(LolPosition.JUNGLE));

        matchRequests.cancel(user, request.getId());

        assertThat(queueRepository.waitingCount(lolQueueKey())).isZero();
        assertThat(queueRepository.activeRequestOf(user)).isEmpty();
        assertThat(matchRequests.start(user, lol(LolPosition.MID))).isNotNull();
    }

    @Test
    @DisplayName("정원이 5인 모드는 다섯 명이 모여야 제안이 만들어진다")
    void waitsUntilTargetPartySizeIsReached() {
        List<ValorantRole> roles = List.of(ValorantRole.DUELIST, ValorantRole.INITIATOR,
                ValorantRole.CONTROLLER, ValorantRole.SENTINEL);
        for (ValorantRole role : roles) {
            matchRequests.start(newUser(), valorant(role));
        }

        assertThat(matcher.tryMatch(GameKey.VALORANT, VALORANT_MODE)).isEmpty();

        matchRequests.start(newUser(), valorant(ValorantRole.DUELIST));

        UUID proposalId = matcher.tryMatch(GameKey.VALORANT, VALORANT_MODE).orElseThrow();
        assertThat(memberRepository.findAllByIdProposalId(proposalId)).hasSize(5);
    }

    private UUID newUser() {
        int n = sequence.incrementAndGet();
        return users.save(User.create("player" + n + "@queuemate.test", "hash", "player" + n)).getId();
    }

    private static MatchCondition lol(LolPosition position) {
        return new MatchCondition(GameKey.LOL, LOL_MODE, position,
                VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
    }

    private static MatchCondition valorant(ValorantRole role) {
        return new MatchCondition(GameKey.VALORANT, VALORANT_MODE, role,
                VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
    }

    private static String lolQueueKey() {
        return MatchingRedisKeys.queue(GameKey.LOL, LOL_MODE);
    }
}
