package com.queuemate.reservation;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayAmount;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.common.error.ConflictException;
import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.matching.infra.ProposalClaimRepository;
import com.queuemate.matching.infra.ProposalMemberRepository;
import com.queuemate.reservation.app.ReservationMatcher;
import com.queuemate.reservation.app.ReservationService;
import com.queuemate.reservation.domain.Reservation;
import com.queuemate.reservation.domain.ReservationStatus;
import com.queuemate.reservation.infra.ReservationRepository;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 예약 매칭 통합 검증 (docs/04).
 * 스케줄러 sweep은 꺼 두고 매처를 직접 돌린다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ReservationMatchingIntegrationTest {

    private static final String MODE = "SOLO_DUO_RANKED";

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
        registry.add("queuemate.reservation.sweep-ms", () -> 3_600_000);
    }

    @Autowired ReservationService service;
    @Autowired ReservationMatcher matcher;
    @Autowired ProposalService proposals;
    @Autowired ReservationRepository reservations;
    @Autowired ProposalMemberRepository proposalMembers;
    @Autowired ProposalClaimRepository claims;
    @Autowired BlockService blocks;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void reset() {
        jdbc.sql("TRUNCATE proposal_members, match_proposals, reservations, blocks, users CASCADE")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("30분 단위가 아닌 시간은 거부한다")
    void rejectsUnalignedWindow() {
        UUID user = newUser();

        assertThatThrownBy(() -> service.create(user, lol(LolPosition.JUNGLE),
                at("20:10"), at("22:00"), PlayAmount.ONE_GAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30분");
    }

    @Test
    @DisplayName("INV-9: 같은 사용자의 시간이 겹치는 예약은 거부한다")
    void rejectsOverlappingReservation() {
        UUID user = newUser();
        service.create(user, lol(LolPosition.JUNGLE), at("20:00"), at("22:00"), PlayAmount.ONE_GAME);

        assertThatThrownBy(() -> service.create(user, lol(LolPosition.MID),
                at("21:00"), at("23:00"), PlayAmount.ONE_GAME))
                .isInstanceOf(ConflictException.class);

        // 맞닿기만 하는 시간대는 겹친 것이 아니다.
        assertThat(service.create(user, lol(LolPosition.MID),
                at("22:00"), at("23:00"), PlayAmount.ONE_GAME)).isNotNull();
    }

    @Test
    @DisplayName("시간이 겹치고 조건이 호환되면 제안이 만들어지고 가장 이른 슬롯이 약속 시각이 된다")
    void matchesOverlappingReservations() {
        UUID jungler = newUser();
        UUID mid = newUser();
        Reservation first = service.create(jungler, lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        service.create(mid, lol(LolPosition.MID), at("21:30"), at("22:30"), PlayAmount.ONE_GAME);

        Optional<UUID> proposalId = matcher.tryMatchFor(first.getId());

        assertThat(proposalId).isPresent();
        assertThat(proposalMembers.findAllByIdProposalId(proposalId.get())).hasSize(2);
        assertThat(reservations.findById(first.getId()))
                .get().satisfies(r -> {
                    assertThat(r.getStatus()).isEqualTo(ReservationStatus.PROPOSED);
                    assertThat(r.getScheduledStart()).isEqualTo(at("21:30"));
                });
        assertThat(claims.activeProposalOf(jungler)).contains(proposalId.get());
    }

    @Test
    @DisplayName("시간이 겹치지 않으면 매칭하지 않는다")
    void doesNotMatchWithoutOverlap() {
        UUID first = newUser();
        Reservation reservation = service.create(first, lol(LolPosition.JUNGLE),
                at("20:00"), at("21:00"), PlayAmount.ONE_GAME);
        service.create(newUser(), lol(LolPosition.MID), at("21:00"), at("22:00"), PlayAmount.ONE_GAME);

        assertThat(matcher.tryMatchFor(reservation.getId())).isEmpty();
    }

    @Test
    @DisplayName("플레이할 양이 다르면 매칭하지 않는다")
    void doesNotMatchDifferentPlayAmount() {
        Reservation reservation = service.create(newUser(), lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        service.create(newUser(), lol(LolPosition.MID), at("20:00"), at("23:00"), PlayAmount.TWO_PLUS);

        assertThat(matcher.tryMatchFor(reservation.getId())).isEmpty();
    }

    @Test
    @DisplayName("실시간과 같은 hard 조건이 예약에도 적용된다")
    void appliesSameHardFilters() {
        Reservation sameRole = service.create(newUser(), lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        service.create(newUser(), lol(LolPosition.JUNGLE), at("20:00"), at("23:00"), PlayAmount.ONE_GAME);

        assertThat(matcher.tryMatchFor(sameRole.getId())).isEmpty();
    }

    @Test
    @DisplayName("INV-6: 차단한 사이는 예약에서도 묶이지 않는다")
    void neverMatchesBlockedUsers() {
        UUID blocker = newUser();
        UUID blocked = newUser();
        blocks.block(blocker, blocked);
        Reservation reservation = service.create(blocker, lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        service.create(blocked, lol(LolPosition.MID), at("20:00"), at("23:00"), PlayAmount.ONE_GAME);

        assertThat(matcher.tryMatchFor(reservation.getId())).isEmpty();
    }

    @Test
    @DisplayName("수정하면 낡은 슬롯 색인이 남지 않는다")
    void editRebuildsSlotIndex() {
        UUID owner = newUser();
        Reservation reservation = service.create(owner, lol(LolPosition.JUNGLE),
                at("20:00"), at("21:00"), PlayAmount.ONE_GAME);
        // 상대는 늦은 시간대만 가능하다.
        service.create(newUser(), lol(LolPosition.MID), at("22:00"), at("23:00"), PlayAmount.ONE_GAME);
        assertThat(matcher.tryMatchFor(reservation.getId())).isEmpty();

        service.edit(owner, reservation.getId(), lol(LolPosition.JUNGLE),
                at("22:00"), at("23:00"), PlayAmount.ONE_GAME);

        assertThat(matcher.tryMatchFor(reservation.getId())).isPresent();
        // 옛 슬롯에는 더 이상 남아 있지 않다.
        assertThat(redis.opsForSet().members(
                "qm:reservation:slot:LOL:SOLO_DUO_RANKED:" + slotKeyAt("20:00")))
                .isNullOrEmpty();
    }

    @Test
    @DisplayName("전원이 수락하면 예약이 MATCHED가 된다")
    void confirmMarksReservationsMatched() {
        UUID jungler = newUser();
        UUID mid = newUser();
        Reservation first = service.create(jungler, lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        service.create(mid, lol(LolPosition.MID), at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        UUID proposalId = matcher.tryMatchFor(first.getId()).orElseThrow();

        proposals.accept(jungler, proposalId);
        proposals.accept(mid, proposalId);

        assertThat(proposals.get(jungler, proposalId).status()).isEqualTo(ProposalStatus.CONFIRMED);
        assertThat(reservations.findById(first.getId()))
                .get().extracting(Reservation::getStatus).isEqualTo(ReservationStatus.MATCHED);
        assertThat(claims.activeProposalOf(jungler)).isEmpty();
    }

    @Test
    @DisplayName("거절하면 예약이 다시 ACTIVE가 되어 재매칭된다")
    void declineReturnsReservationsToActive() {
        UUID jungler = newUser();
        UUID mid = newUser();
        Reservation first = service.create(jungler, lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        service.create(mid, lol(LolPosition.MID), at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        UUID proposalId = matcher.tryMatchFor(first.getId()).orElseThrow();

        proposals.decline(jungler, proposalId);

        assertThat(reservations.findById(first.getId()))
                .get().satisfies(r -> {
                    assertThat(r.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
                    assertThat(r.getScheduledStart()).isNull();
                });
        assertThat(claims.activeProposalOf(jungler)).isEmpty();
        assertThat(matcher.tryMatchFor(first.getId())).isPresent();
    }

    @Test
    @DisplayName("PROPOSED 상태에서는 수정할 수 없다")
    void cannotEditWhileProposed() {
        UUID jungler = newUser();
        Reservation first = service.create(jungler, lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        service.create(newUser(), lol(LolPosition.MID), at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        matcher.tryMatchFor(first.getId()).orElseThrow();

        assertThatThrownBy(() -> service.edit(jungler, first.getId(), lol(LolPosition.TOP),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("sweep이 나중에 들어온 상대를 찾아 준다")
    void sweepFindsLateArrivals() {
        Reservation early = service.create(newUser(), lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        assertThat(matcher.tryMatchFor(early.getId())).isEmpty();

        service.create(newUser(), lol(LolPosition.MID), at("21:00"), at("22:00"), PlayAmount.ONE_GAME);

        assertThat(matcher.sweep()).isEqualTo(1);
        assertThat(reservations.findById(early.getId()))
                .get().extracting(Reservation::getStatus).isEqualTo(ReservationStatus.PROPOSED);
    }

    @Test
    @DisplayName("취소하면 슬롯 색인에서도 빠져 후보가 되지 않는다")
    void cancelRemovesFromIndex() {
        UUID owner = newUser();
        Reservation cancelled = service.create(owner, lol(LolPosition.MID),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        Reservation other = service.create(newUser(), lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);

        service.cancel(owner, cancelled.getId());

        assertThat(matcher.tryMatchFor(other.getId())).isEmpty();
    }

    private UUID newUser() {
        int n = sequence.incrementAndGet();
        return users.save(User.create("booker" + n + "@queuemate.test", "hash", "booker" + n)).getId();
    }

    private static MatchCondition lol(LolPosition position) {
        return new MatchCondition(GameKey.LOL, MODE, position,
                VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
    }

    /** 고정된 날짜의 UTC 시각. 테스트가 오늘 날짜에 흔들리지 않게 한다. */
    private static OffsetDateTime at(String hhmm) {
        return OffsetDateTime.parse("2026-09-15T" + hhmm + ":00Z");
    }

    private static String slotKeyAt(String hhmm) {
        return "20260915T" + hhmm.replace(":", "");
    }
}
