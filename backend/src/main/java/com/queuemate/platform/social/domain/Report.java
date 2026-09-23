package com.queuemate.platform.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 신고 한 건. <b>접수만 받는다</b> — 처리 화면 · 제재가 없어서 {@code status} 는 늘 {@code RECEIVED} 다({@code REVIEWED} 는 DB 의 CHECK 에만 있다).
 * 같은 사람을 여러 번 신고할 수 있다 — UNIQUE 가 없다.
 *
 * <p>사용자 번호에도 {@code contextId}(글의 id)에도 FK 가 없다(크로스 스키마 FK 금지). {@code contextId} 는 있는지 확인하지도 않는다.
 */
@Entity
@Table(schema = "social", name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    /** 접수된 신고의 상태. 마이그레이션(V6)의 기본값과 같다 */
    private static final String RECEIVED = "RECEIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    @Column(name = "target_user_id", nullable = false, updatable = false)
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 20)
    private ReportReason reason;

    @Column(name = "detail", updatable = false, length = 1000)
    private String detail;

    @Column(name = "context_id", updatable = false)
    private Long contextId;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Report(Long reporterId, Long targetUserId, ReportReason reason, String detail, Long contextId, Instant now)
    {
        this.reporterId = reporterId;
        this.targetUserId = targetUserId;
        this.reason = reason;
        this.detail = detail;
        this.contextId = contextId;
        this.status = RECEIVED;
        this.createdAt = now;
    }
}
