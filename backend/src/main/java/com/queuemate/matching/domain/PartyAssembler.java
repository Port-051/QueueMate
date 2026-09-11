package com.queuemate.matching.domain;

import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.gameconfig.domain.GameModeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 한 판의 후보 집합 위에서 파티를 조립한다 (docs/03 §1·§5).
 *
 * <p>선택 규칙은 바뀌지 않는다. hard filtering → 매 자리에서 가장 좋은 등급의 후보군 →
 * 그 안에서 무작위. 달라진 것은 "같은 값을 몇 번 구하는가"다.
 *
 * <p>후보 집합이 정해지면 두 사람의 차단 여부와 호환 등급은 그 판 내내 변하지 않는다.
 * 그래서 한 번 구한 쌍은 기억해 두고 seed를 바꿔 가며 재사용한다. 예전에는 seed마다
 * 차단 조회를 처음부터 다시 했고(후보 50명·seed 50개면 최대 2500회), 한 자리를 채울 때마다
 * 이미 검증이 끝난 파티 내부 쌍까지 다시 판정했다.
 *
 * <p>쌍 판정과 차단 조회는 모두 실제로 필요할 때 한다. seed가 하나뿐인 예약 매칭에서
 * 쓰지도 않을 쌍을 미리 채우지 않기 위해서다.
 *
 * <p>여기서 거르는 차단은 후보 필터 수준이다. 확정 직전 재검증은 매처가 따로 한다 (INV-6).
 */
public final class PartyAssembler<C extends PartyCandidate> {

    /**
     * 등급만으로는 판단할 수 없는 추가 제약. 예약의 "전원 시간대가 겹쳐야 한다"가 여기 해당한다.
     *
     * <p>쌍이 아니라 파티 전체를 봐야 하므로 기억해 둘 수 없다. 대신 차단·등급을 통과한
     * 후보에게만 묻는다.
     */
    @FunctionalInterface
    public interface PartyFilter<C> {
        boolean accepts(List<C> party, C candidate);
    }

    private static final CompatibilityTier[] TIERS = CompatibilityTier.values();
    /** 아직 판정하지 않은 쌍. byte 배열의 기본값이 그대로 이 뜻이다. */
    private static final byte UNKNOWN = 0;
    /** 차단이거나 hard 조건을 어긴 쌍. */
    private static final byte IMPOSSIBLE = 1;
    private static final byte FIRST_TIER = 2;

    private final List<C> candidates;
    private final GameModeConfig config;
    private final RandomSource random;
    private final BlockLookupPort blocks;
    private final Map<UUID, Set<UUID>> blockedBy = new HashMap<>();
    private final byte[][] pairs;

    private PartyAssembler(List<C> candidates, GameModeConfig config,
                           BlockLookupPort blocks, RandomSource random) {
        this.candidates = candidates;
        this.config = config;
        this.blocks = blocks;
        this.random = random;
        this.pairs = new byte[candidates.size()][candidates.size()];
    }

    /**
     * 후보 집합 위에 조립기를 만든다.
     *
     * @param candidates 대기 순서대로 놓인 후보. 순서가 seed 우선순위다 (docs/03 §6 aging)
     */
    public static <C extends PartyCandidate> PartyAssembler<C> over(
            List<C> candidates, GameModeConfig config, BlockLookupPort blocks, RandomSource random) {
        return new PartyAssembler<>(List.copyOf(candidates), config, blocks, random);
    }

    public int size() {
        return candidates.size();
    }

    /**
     * seed 하나를 기준으로 정원을 채운다.
     *
     * @return 정원을 채웠으면 파티, 조합이 없으면 empty
     */
    public Optional<List<C>> assembleFrom(int seedIndex) {
        return assembleFrom(seedIndex, null);
    }

    /**
     * seed 하나를 기준으로 정원을 채우되, 매 자리에서 추가 제약을 함께 본다.
     *
     * @param extra 차단·등급을 통과한 후보에게만 묻는다. 없으면 {@code null}
     */
    public Optional<List<C>> assembleFrom(int seedIndex, PartyFilter<C> extra) {
        int target = config.targetPartySize();
        int n = candidates.size();
        if (n < target) {
            return Optional.empty();
        }

        boolean[] used = new boolean[n];
        used[seedIndex] = true;
        int[] members = new int[target];
        members[0] = seedIndex;
        int size = 1;
        List<C> party = new ArrayList<>(target);
        party.add(candidates.get(seedIndex));
        // 이미 확정된 쌍 중 가장 나쁜 등급. 파티 전체 등급은 이것과 새로 생기는 쌍들로만 결정된다.
        CompatibilityTier partyTier = CompatibilityTier.BEST;

        while (size < target) {
            CompatibilityTier bestTier = null;
            List<Integer> bucket = new ArrayList<>();
            int possible = 0;
            for (int c = 0; c < n; c++) {
                if (used[c]) {
                    continue;
                }
                CompatibilityTier withParty = tierAgainstParty(members, size, partyTier, c);
                if (withParty == null) {
                    continue;
                }
                possible++;
                // 이미 더 좋은 등급을 찾았으면 추가 제약을 물어볼 것도 없다.
                if (bestTier != null && bestTier.isBetterThan(withParty)) {
                    continue;
                }
                if (extra != null && !extra.accepts(party, candidates.get(c))) {
                    continue;
                }
                if (bestTier == null || withParty.isBetterThan(bestTier)) {
                    bestTier = withParty;
                    bucket.clear();
                }
                bucket.add(c);
            }
            if (bucket.isEmpty()) {
                return Optional.empty();
            }
            // 남은 자리를 채울 만큼 짝이 없다. 지금 한 명을 넣어 봐야 다음 자리에서 막힌다.
            if (possible < target - size) {
                return Optional.empty();
            }
            int chosen = random.pick(bucket);
            partyTier = tierAgainstParty(members, size, partyTier, chosen);
            members[size] = chosen;
            party.add(candidates.get(chosen));
            used[chosen] = true;
            size++;
        }
        return Optional.of(party);
    }

    /**
     * 후보를 넣었을 때의 파티 등급.
     *
     * <p>파티 내부 쌍은 이미 {@code partyTier}에 반영돼 있으므로 새로 생기는 쌍만 본다.
     *
     * @return 한 쌍이라도 불가능하면 {@code null}
     */
    private CompatibilityTier tierAgainstParty(int[] members, int size,
                                               CompatibilityTier partyTier, int candidate) {
        CompatibilityTier worst = partyTier;
        for (int m = 0; m < size; m++) {
            CompatibilityTier tier = pairTier(members[m], candidate);
            if (tier == null) {
                return null;
            }
            worst = worst.worseOf(tier);
        }
        return worst;
    }

    /** 두 후보의 등급. 한 번 구하면 기억한다. 불가능한 쌍이면 {@code null}. */
    private CompatibilityTier pairTier(int i, int j) {
        byte stored = pairs[i][j];
        if (stored == UNKNOWN) {
            stored = evaluate(i, j);
            pairs[i][j] = stored;
            pairs[j][i] = stored;
        }
        return stored == IMPOSSIBLE ? null : TIERS[stored - FIRST_TIER];
    }

    private byte evaluate(int i, int j) {
        C a = candidates.get(i);
        C b = candidates.get(j);
        // 차단된 쌍과 같은 사용자의 중복 후보를 여기서 함께 걷어 낸다 (INV-6, INV-7).
        if (a.userId().equals(b.userId()) || isBlocked(a.userId(), b.userId())) {
            return IMPOSSIBLE;
        }
        return ConditionCompatibility.between(a.condition(), b.condition(), config)
                .map(tier -> (byte) (FIRST_TIER + tier.ordinal()))
                .orElse(IMPOSSIBLE);
    }

    /** 차단은 방향과 무관하다. 사용자당 조회는 한 번뿐이다. */
    private boolean isBlocked(UUID one, UUID other) {
        return blockedBy.computeIfAbsent(one, blocks::blockedUserIds).contains(other)
                || blockedBy.computeIfAbsent(other, blocks::blockedUserIds).contains(one);
    }
}
