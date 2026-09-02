package com.queuemate.party.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 시간이 지나서 일어나야 하는 파티 전이를 주기적으로 돌린다.
 *
 * 반복문이 여기 있는 이유는 PartyLifecycleService 주석에 있다. 파티 하나가 실패해도
 * 나머지는 계속 처리해야 하므로 트랜잭션이 파티 단위로 갈려야 한다.
 */
@Component
public class PartyLifecycleSweeper {

    private static final Logger log = LoggerFactory.getLogger(PartyLifecycleSweeper.class);

    private final PartyLifecycleService lifecycle;
    private final PartyLifecycleProperties properties;

    public PartyLifecycleSweeper(PartyLifecycleService lifecycle, PartyLifecycleProperties properties) {
        this.lifecycle = lifecycle;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${queuemate.party.sweep-ms}")
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        startPlaying(now);
        closeAbandoned(now);
    }

    private void startPlaying(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minus(properties.playStartDelay());
        int started = 0;
        for (UUID partyId : lifecycle.readySince(cutoff)) {
            try {
                started += lifecycle.startPlaying(partyId, cutoff, now) ? 1 : 0;
            } catch (RuntimeException e) {
                // 한 파티의 실패가 나머지를 막지 않는다. 다음 주기에 다시 후보로 잡힌다.
                log.error("게임 시작 전이 실패 partyId={}", partyId, e);
            }
        }
        if (started > 0) {
            log.info("게임 시작으로 전이한 파티 parties={}", started);
        }
    }

    private void closeAbandoned(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minus(properties.maxPlay());
        int closed = 0;
        for (UUID partyId : lifecycle.playingSince(cutoff)) {
            try {
                closed += lifecycle.closeAbandoned(partyId, cutoff, now) ? 1 : 0;
            } catch (RuntimeException e) {
                log.error("방치 파티 종료 실패 partyId={}", partyId, e);
            }
        }
        if (closed > 0) {
            log.info("방치되어 종료한 파티 parties={}", closed);
        }
    }
}
