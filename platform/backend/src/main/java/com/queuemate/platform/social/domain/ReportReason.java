package com.queuemate.platform.social.domain;

/**
 * 신고의 사유 ({@code contracts/platform-api.md} "친구 · 신고 · 최근 함께한 사람"). DB 의 {@code reports_reason_check} 와 같은 목록이다.
 */
public enum ReportReason {

    /** 욕설 · 비매너 */
    ABUSE,

    /** 핵 · 대리 */
    CHEATING,

    /** 도배 · 광고 */
    SPAM,

    /** 잠수 · 탈주 */
    NO_SHOW,

    /** 그 밖의 것 — {@code detail} 이 필수다 */
    OTHER
}
