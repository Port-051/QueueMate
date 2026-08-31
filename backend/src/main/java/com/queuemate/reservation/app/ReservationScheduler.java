package com.queuemate.reservation.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 예약 매칭 주기 sweep (docs/04 §6).
 *
 * <p>등록 직후 한 번 훑는 것만으로는 부족하다. A가 등록할 때 B가 아직 없었다면
 * B가 나중에 들어와도 A는 영영 매칭되지 않는다.
 */
@Component
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);

    private final ReservationMatcher matcher;
    private final ReservationService reservations;
    private final Clock clock;

    public ReservationScheduler(ReservationMatcher matcher, ReservationService reservations) {
        this.matcher = matcher;
        this.reservations = reservations;
        this.clock = Clock.systemUTC();
    }

    @Scheduled(fixedDelayString = "${queuemate.reservation.sweep-ms:30000}")
    public void sweep() {
        try {
            int created = matcher.sweep();
            if (created > 0) {
                log.info("예약 sweep으로 제안 생성 count={}", created);
            }
        } catch (DataAccessException e) {
            // Redis/DB 장애 중에는 새 제안을 만들지 않는다 (INV-10).
            log.warn("예약 sweep을 건너뛴다", e);
        }
    }

    /**
     * 시간이 지난 예약을 정리한다.
     * 남겨 두면 지난 시간대가 계속 후보로 올라오고, 그 시간에 새 예약을 잡지 못한다.
     */
    @Scheduled(fixedDelayString = "${queuemate.reservation.expire-ms:60000}")
    public void expireOverdue() {
        try {
            int expired = reservations.expireOverdue(OffsetDateTime.now(clock));
            if (expired > 0) {
                log.info("시간이 지난 예약 만료 count={}", expired);
            }
        } catch (DataAccessException e) {
            log.warn("예약 만료 정리를 건너뛴다", e);
        }
    }
}
