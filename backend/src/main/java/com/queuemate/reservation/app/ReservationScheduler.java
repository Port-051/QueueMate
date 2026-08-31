package com.queuemate.reservation.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    public ReservationScheduler(ReservationMatcher matcher) {
        this.matcher = matcher;
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
}
