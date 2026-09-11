package com.queuemate.matching.domain;

import com.queuemate.gameconfig.domain.GameModeConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이번 판에 어느 bucket에서 몇 명씩 읽을지 (docs/07 §3.2).
 *
 * <p>anchor를 정하고, 그 anchor와 같은 파티가 될 수 있는 bucket을 등급 좋은 순으로 담는다.
 * anchor를 누가 정하느냐로 두 가지 입구가 있다.
 *
 * <ul>
 *   <li>{@link #around} &mdash; 요청이 들어온 bucket이 anchor다. 평소 경로다.</li>
 *   <li>{@link #of} &mdash; 가장 오래 기다린 bucket부터 anchor로 삼고, 예산이 남으면 다음
 *       anchor로 넘어간다. 어디를 볼지 모르는 채로 훑는 안전망 경로다.</li>
 * </ul>
 *
 * <p>aging은 두 군데서 지켜진다. anchor를 오래된 순으로 고르고, bucket 안에서도 오래된 순으로
 * 꺼낸다. 그래서 가장 오래 기다린 사람은 언제나 후보에 들어가고 언제나 첫 seed가 된다 (docs/03 §6).
 */
public record BucketScanPlan(List<MatchBucket> buckets, List<Integer> quotas) {

    public BucketScanPlan {
        if (buckets.size() != quotas.size()) {
            throw new IllegalArgumentException("bucket과 quota 수가 다르다");
        }
        buckets = List.copyOf(buckets);
        quotas = List.copyOf(quotas);
    }

    public boolean isEmpty() {
        return buckets.isEmpty();
    }

    /**
     * 읽기 계획을 세운다.
     *
     * @param depths 비어 있지 않은 bucket들. 순서는 상관없다
     * @param budget 이번 판에 읽을 총 인원 상한
     */
    public static BucketScanPlan of(List<BucketDepth> depths, GameModeConfig config, int budget) {
        if (depths.isEmpty() || budget <= 0) {
            return new BucketScanPlan(List.of(), List.of());
        }
        int target = config.targetPartySize();
        List<BucketDepth> byAge = new ArrayList<>(depths);
        byAge.sort(Comparator.comparingLong(BucketDepth::oldestQueuedAt));

        Map<MatchBucket, BucketDepth> selected = new LinkedHashMap<>();
        for (BucketDepth anchor : byAge) {
            // 정원만큼도 못 읽을 만큼 bucket을 담았으면 멈춘다.
            if ((long) selected.size() * target >= budget) {
                break;
            }
            // anchor 자신도 담는다. 같은 bucket끼리 못 맞는 모드(LoL 랭크)에서도
            // anchor는 seed로서 후보에 있어야 한다.
            selected.putIfAbsent(anchor.bucket(), anchor);
            for (BucketDepth partner : partnersOf(anchor, byAge, config)) {
                selected.putIfAbsent(partner.bucket(), partner);
            }
        }

        return shareBudget(selected.values(), target, budget);
    }

    /**
     * anchor 하나를 기준으로 읽기 계획을 세운다 (docs/07 §3.2).
     *
     * <p>요청이 들어온 bucket에서 출발하는 경로가 쓴다. 모드 전체를 훑는 {@link #of}와 달리
     * 이미 볼 자리를 알고 있으므로 anchor와 그 상대만 본다.
     *
     * @param depths anchor와 그 상대 bucket들의 깊이. anchor가 비어 있으면 계획도 비어 있다
     */
    public static BucketScanPlan around(MatchBucket anchor, List<BucketDepth> depths,
                                        GameModeConfig config, int budget) {
        if (depths.isEmpty() || budget <= 0) {
            return new BucketScanPlan(List.of(), List.of());
        }
        BucketDepth anchorDepth = depths.stream()
                .filter(depth -> depth.bucket().equals(anchor))
                .findFirst()
                .orElse(null);
        if (anchorDepth == null) {
            // trigger가 도착하기 전에 그 자리가 비었다. 다른 trigger가 이미 데려갔다는 뜻이다.
            return new BucketScanPlan(List.of(), List.of());
        }
        int target = config.targetPartySize();
        List<BucketDepth> byAge = new ArrayList<>(depths);
        byAge.sort(Comparator.comparingLong(BucketDepth::oldestQueuedAt));

        Map<MatchBucket, BucketDepth> selected = new LinkedHashMap<>();
        selected.put(anchor, anchorDepth);
        for (BucketDepth partner : partnersOf(anchorDepth, byAge, config)) {
            if ((long) selected.size() * target >= budget) {
                break;
            }
            selected.putIfAbsent(partner.bucket(), partner);
        }
        return shareBudget(selected.values(), target, budget);
    }

    /** 남는 예산은 고르게 나눈다. 한 bucket이 창을 독차지하면 나누어 놓은 의미가 없다. */
    private static BucketScanPlan shareBudget(
            Collection<BucketDepth> selected, int target, int budget) {
        int perBucket = Math.max(target, budget / selected.size());
        List<MatchBucket> buckets = new ArrayList<>(selected.size());
        List<Integer> quotas = new ArrayList<>(selected.size());
        for (BucketDepth depth : selected) {
            buckets.add(depth.bucket());
            quotas.add(Math.min(depth.waiting(), perBucket));
        }
        return new BucketScanPlan(buckets, quotas);
    }

    /** anchor와 같은 파티가 될 수 있는 bucket을 등급 좋은 순, 같으면 오래 기다린 순으로. */
    private static List<BucketDepth> partnersOf(
            BucketDepth anchor, List<BucketDepth> all, GameModeConfig config) {
        record Scored(BucketDepth depth, CompatibilityTier tier) {
        }
        List<Scored> scored = new ArrayList<>(all.size());
        for (BucketDepth candidate : all) {
            ConditionCompatibility
                    .between(anchor.bucket().representative(),
                            candidate.bucket().representative(), config)
                    .ifPresent(tier -> scored.add(new Scored(candidate, tier)));
        }
        scored.sort(Comparator.comparing(Scored::tier)
                .thenComparingLong(s -> s.depth().oldestQueuedAt()));
        return scored.stream().map(Scored::depth).toList();
    }
}
