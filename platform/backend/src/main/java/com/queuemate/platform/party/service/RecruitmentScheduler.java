package com.queuemate.platform.party.service;

import com.queuemate.platform.party.repository.RecruitPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.Instant;

/** 브라우저의 타이머나 목록 조회에 의존하지 않는다. 여러 인스턴스는 글 행 잠금으로 직렬화한다. */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "platform.room.auto-confirm-enabled", havingValue = "true")
public class RecruitmentScheduler {
    private final RecruitPostRepository posts;
    private final PostStore store;
    private long cursor;

    @Scheduled(fixedDelayString = "${platform.room.auto-confirm-poll-ms:1000}")
    public void tick() {
        var ids = posts.findRecruitingAfter(cursor, Limit.of(200));
        cursor = ids.size() < 200 ? 0 : ids.getLast();
        for (Long id : ids) {
            try { store.checkAutomaticConfirmation(id, Instant.now()); }
            catch (RuntimeException error) { log.warn("자동 확정 재시도 postId={}: {}", id, error.toString()); }
        }
    }
}
