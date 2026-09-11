package com.queuemate.matching.domain;

/**
 * 비어 있지 않은 bucket 하나의 현재 상태 (docs/07 §3.2).
 *
 * @param bucket         어느 bucket인지
 * @param oldestQueuedAt 그 bucket 최고참의 대기 시작 시각(epoch millis). aging 기준이다
 * @param waiting        현재 대기 인원
 */
public record BucketDepth(MatchBucket bucket, long oldestQueuedAt, int waiting) {

    public BucketDepth {
        if (bucket == null) {
            throw new IllegalArgumentException("bucket은 필수다");
        }
        if (waiting <= 0) {
            throw new IllegalArgumentException("비어 있는 bucket은 후보가 아니다: " + waiting);
        }
    }
}
