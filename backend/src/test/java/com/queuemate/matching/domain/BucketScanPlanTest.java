package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.gameconfig.domain.GameModeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 읽기 계획이 docs/03 §6 aging과 docs/07 §3.2를 지키는지.
 *
 * <p>여기서 잘못 고르면 "대기 중인데 아무도 나를 보지 않는" 사용자가 생긴다.
 */
class BucketScanPlanTest {

    private static final String MODE = "SOLO_DUO_RANKED";
    private static final GameModeConfig LOL_DUO =
            new GameModeConfig(GameKey.LOL, MODE, 2, true, true);

    @Test
    @DisplayName("가장 오래 기다린 bucket이 언제나 계획에 들어간다")
    void alwaysIncludesOldestBucket() {
        BucketDepth oldest = depth(LolPosition.TOP, VoicePreference.OPTIONAL, 1_000, 1);
        BucketDepth newer = depth(LolPosition.MID, VoicePreference.OPTIONAL, 9_000, 1);

        BucketScanPlan plan = BucketScanPlan.of(List.of(newer, oldest), LOL_DUO, 50);

        assertThat(plan.buckets()).first().isEqualTo(oldest.bucket());
    }

    @Test
    @DisplayName("같은 포지션끼리 못 맞는 모드에서도 anchor 자신은 후보에 남는다")
    void keepsAnchorEvenWhenItCannotPairWithItsOwnBucket() {
        // LoL 랭크는 role uniqueness라 TOP끼리는 hard reject다. 그래도 anchor는 seed여야 한다.
        BucketDepth top = depth(LolPosition.TOP, VoicePreference.OPTIONAL, 1_000, 5);

        BucketScanPlan plan = BucketScanPlan.of(List.of(top), LOL_DUO, 50);

        assertThat(plan.buckets()).containsExactly(top.bucket());
    }

    @Test
    @DisplayName("anchor의 상대로는 같은 파티가 될 수 없는 bucket을 고르지 않는다")
    void doesNotPickIncompatibleBucketsAsPartners() {
        BucketDepth anchor = depth(LolPosition.TOP, VoicePreference.REQUIRED, 1_000, 1);
        // 음성 요구가 정면 충돌해 어떤 등급으로도 anchor와 같은 파티가 될 수 없다.
        BucketDepth impossible = depth(LolPosition.MID, VoicePreference.NO_VOICE, 2_000, 1);
        BucketDepth possible = depth(LolPosition.ADC, VoicePreference.REQUIRED, 3_000, 1);

        // 예산을 anchor 한 명분으로 좁힌다. 넉넉하면 impossible도 자기 차례에 anchor가 되는데,
        // 그건 막으면 안 되는 동작이다. 여기서 보려는 것은 "상대로 고르지 않는가"다.
        BucketScanPlan plan = BucketScanPlan.of(
                List.of(anchor, impossible, possible), LOL_DUO, 4);

        assertThat(plan.buckets()).contains(possible.bucket())
                .doesNotContain(impossible.bucket());
    }

    @Test
    @DisplayName("첫 anchor가 상대를 못 찾아도 다음 대기자가 자기 차례를 갖는다")
    void laterWaitersStillGetTheirTurn() {
        // anchor는 아무와도 못 맞는다. 여기서 멈추면 그 모드 전체가 죽는다.
        BucketDepth lonely = depth(LolPosition.TOP, VoicePreference.REQUIRED, 1_000, 1);
        BucketDepth pairA = depth(LolPosition.MID, VoicePreference.NO_VOICE, 2_000, 1);
        BucketDepth pairB = depth(LolPosition.ADC, VoicePreference.NO_VOICE, 3_000, 1);

        BucketScanPlan plan = BucketScanPlan.of(List.of(lonely, pairA, pairB), LOL_DUO, 50);

        assertThat(plan.buckets()).contains(lonely.bucket(), pairA.bucket(), pairB.bucket());
    }

    @Test
    @DisplayName("등급이 좋은 bucket을 먼저 고른다")
    void prefersBetterTierBuckets() {
        BucketDepth anchor = depth(LolPosition.TOP, VoicePreference.OPTIONAL, 1_000, 1);
        // 음성이 달라 한 칸 어긋난다.
        BucketDepth worse = depth(LolPosition.MID, VoicePreference.REQUIRED, 2_000, 1);
        BucketDepth better = depth(LolPosition.ADC, VoicePreference.OPTIONAL, 3_000, 1);

        BucketScanPlan plan = BucketScanPlan.of(List.of(anchor, worse, better), LOL_DUO, 50);

        assertThat(plan.buckets().indexOf(better.bucket()))
                .isLessThan(plan.buckets().indexOf(worse.bucket()));
    }

    @Test
    @DisplayName("한 bucket이 붐벼도 예산을 독차지하지 못한다")
    void crowdedBucketCannotEatTheWholeBudget() {
        // 예전 구조에서 매칭을 멈춰 세우던 모양이다. 같은 포지션이 잔뜩 쌓여 창을 채운다.
        BucketDepth crowded = depth(LolPosition.MID, VoicePreference.OPTIONAL, 1_000, 500);
        BucketDepth thin = depth(LolPosition.SUPPORT, VoicePreference.OPTIONAL, 9_000, 1);

        BucketScanPlan plan = BucketScanPlan.of(List.of(crowded, thin), LOL_DUO, 50);

        assertThat(plan.buckets()).contains(thin.bucket());
        int crowdedQuota = plan.quotas().get(plan.buckets().indexOf(crowded.bucket()));
        assertThat(crowdedQuota).isLessThan(50);
    }

    @Test
    @DisplayName("quota는 그 bucket의 실제 인원을 넘지 않는다")
    void quotaNeverExceedsWhatIsThere() {
        BucketDepth anchor = depth(LolPosition.TOP, VoicePreference.OPTIONAL, 1_000, 1);
        BucketDepth partner = depth(LolPosition.MID, VoicePreference.OPTIONAL, 2_000, 3);

        BucketScanPlan plan = BucketScanPlan.of(List.of(anchor, partner), LOL_DUO, 50);

        assertThat(plan.quotas().get(plan.buckets().indexOf(anchor.bucket()))).isEqualTo(1);
        assertThat(plan.quotas().get(plan.buckets().indexOf(partner.bucket()))).isEqualTo(3);
    }

    @Test
    @DisplayName("bucket이 아주 많아도 예산만큼만 고른다")
    void staysWithinBudget() {
        List<BucketDepth> depths = new ArrayList<>();
        long score = 1_000;
        for (LolPosition position : LolPosition.values()) {
            for (VoicePreference voice : VoicePreference.values()) {
                depths.add(depth(position, voice, score++, 10));
            }
        }

        BucketScanPlan plan = BucketScanPlan.of(depths, LOL_DUO, 20);

        int total = plan.quotas().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isLessThanOrEqualTo(20 + LOL_DUO.targetPartySize());
    }

    @Test
    @DisplayName("대기자가 없으면 읽을 것도 없다")
    void emptyWhenNobodyWaits() {
        assertThat(BucketScanPlan.of(List.of(), LOL_DUO, 50).isEmpty()).isTrue();
    }

    private static BucketDepth depth(LolPosition position, VoicePreference voice,
                                     long oldestQueuedAt, int waiting) {
        return new BucketDepth(
                new MatchBucket(GameKey.LOL, MODE, position, voice, PlayPurpose.RANK_UP),
                oldestQueuedAt, waiting);
    }
}
