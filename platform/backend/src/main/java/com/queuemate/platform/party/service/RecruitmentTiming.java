package com.queuemate.platform.party.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** 멘토링에는 시간값이 없었다. 기본값은 원본 UI의 10분 대기 + 1분 안내를 따른다. */
@Component
public class RecruitmentTiming {
    private final long idleSeconds;
    private final long warningSeconds;
    private final boolean enabled;

    @Autowired
    public RecruitmentTiming(@Value("${platform.room.auto-confirm-idle-seconds:600}") long idleSeconds,
                             @Value("${platform.room.auto-confirm-warning-seconds:60}") long warningSeconds,
                             @Value("${platform.room.auto-confirm-enabled:false}") boolean enabled) {
        if (idleSeconds < 1 || warningSeconds < 1) throw new IllegalArgumentException("모집 시간은 양수여야 합니다");
        this.idleSeconds = idleSeconds;
        this.warningSeconds = warningSeconds;
        this.enabled = enabled;
    }

    public RecruitmentTiming(long idleSeconds, long warningSeconds) { this(idleSeconds, warningSeconds, true); }
    public boolean enabled() { return enabled; }
    public Instant deadline(Instant now) { return enabled ? now.plusSeconds(idleSeconds + warningSeconds) : null; }
    public Instant warningAt(Instant deadline) { return !enabled || deadline == null ? null : deadline.minusSeconds(warningSeconds); }
}
