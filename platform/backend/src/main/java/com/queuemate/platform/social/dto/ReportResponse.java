package com.queuemate.platform.social.dto;

import java.time.Instant;

/** 접수된 신고 — {@code {reportId, createdAt}}. 처리 상태는 돌려주지 않는다(처리 화면이 없다) */
public record ReportResponse(Long reportId, Instant createdAt) {
}
