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
import com.queuemate.matching.infra.MatchingRedisKeys;
import com.queuemate.matching.infra.ProposalClaimRepository;
import com.queuemate.matching.infra.ProposalMemberRepository;
import com.queuemate.matching.app.MatchRequestService;
import com.queuemate.matching.app.RealtimeMatcher;
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
// 애플리케이션에 WebSocket 엔드포인트가 있어 실제 서블릿 컨테이너가 필요하다.
// MOCK 환경에는 jakarta.websocket의 ServerContainer가 없어 컨텍스트가 뜨지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
        registry.add("queuemate.matching.sweep-ms", () -> 3_600_000);
        // 매칭은 요청이 들어온 순간 저절로 돈다. 여기서는 매처를 직접 불러 한 판씩 본다.
        registry.add("queuemate.matching.auto-trigger", () -> false);
        registry.add("queuemate.proposal.sweep-ms", () -> 3_600_000);
        registry.add("queuemate.reservation.sweep-ms", () -> 3_600_000);
    }

    @Autowired ReservationService service;
    @Autowired ReservationMatcher matcher;
    @Autowired MatchRequestService matchRequests;
    @Autowired RealtimeMatcher realtimeMatcher;
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
    @DisplayName("시간이 지난 예약은 만료되고 그 시간대를 다시 잡을 수 있다")
    void expiresOverdueReservations() {
        UUID owner = newUser();
        Reservation past = service.create(owner, lol(LolPosition.JUNGLE),
                at("20:00"), at("22:00"), PlayAmount.ONE_GAME);

        // 예약 시간이 모두 지난 시점.
        int expired = service.expireOverdue(at("23:00"));

        assertThat(expired).isEqualTo(1);
        assertThat(reservations.findById(past.getId()))
                .get().extracting(Reservation::getStatus).isEqualTo(ReservationStatus.EXPIRED);
        // 만료됐으므로 같은 시간대를 다시 잡을 수 있다.
        assertThat(service.create(owner, lol(LolPosition.MID),
                at("20:00"), at("22:00"), PlayAmount.ONE_GAME)).isNotNull();
    }

    @Test
    @DisplayName("아직 시간이 남은 예약은 만료시키지 않는다")
    void keepsUpcomingReservations() {
        service.create(newUser(), lol(LolPosition.JUNGLE),
                at("20:00"), at("22:00"), PlayAmount.ONE_GAME);

        assertThat(service.expireOverdue(at("21:00"))).isZero();
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

    /**
     * Redis failover로 claim이 사라져도 같은 사람이 두 제안에 묶이지 않는다 (INV-2).
     *
     * <p>Redis 복제는 비동기다. master가 ack한 claim이 복제되기 전에 failover가 나면
     * 새 master는 그 사용자가 비어 있다고 답한다. 실시간 제안은 match_request 행을
     * {@code FOR UPDATE}로 잠가 스스로를 지키지만, 예약은 원본을 잠그지 않는다.
     * 그래서 "실시간 제안을 들고 있는 사람의 예약"이 가장 약한 지점이다.
     */
    @Test
    @DisplayName("Redis claim이 사라져도 실시간 제안 중인 사람에게 예약 제안을 겹쳐 주지 않는다")
    void survivesLostRedisClaim() {
        UUID jungler = newUser();
        UUID mid = newUser();
        UUID adc = newUser();

        // 예약과 실시간 요청을 동시에 들고 있는 사용자.
        Reservation booked = service.create(jungler, lol(LolPosition.JUNGLE),
                at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        matchRequests.start(jungler, lol(LolPosition.JUNGLE));
        matchRequests.start(mid, lol(LolPosition.MID));
        Optional<UUID> realtime = realtimeMatcher.tryMatch(GameKey.LOL, MODE);
        assertThat(realtime).isPresent();
        assertThat(claims.activeProposalOf(jungler)).contains(realtime.get());

        // failover로 복제되지 않은 claim이 유실된 상태를 그대로 흉내 낸다.
        redis.delete(MatchingRedisKeys.activeProposal(jungler));
        redis.delete(MatchingRedisKeys.activeProposal(mid));
        assertThat(claims.activeProposalOf(jungler)).isEmpty();

        // 이제 예약 매칭이 같은 사람을 다시 데려가려 한다.
        service.create(adc, lol(LolPosition.ADC), at("20:00"), at("23:00"), PlayAmount.ONE_GAME);
        Optional<UUID> second = matcher.tryMatchFor(booked.getId());

        assertThat(second).isEmpty();
        assertThat(reservations.findById(booked.getId()))
                .get().extracting(Reservation::getStatus).isEqualTo(ReservationStatus.ACTIVE);
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
