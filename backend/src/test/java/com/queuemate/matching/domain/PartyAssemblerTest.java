package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.gameconfig.domain.GameModeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조립기는 기존 규칙을 그대로 지키면서 같은 값을 두 번 구하지 않아야 한다.
 *
 * <p>규칙 쪽 기준은 {@link ConditionCompatibility#forParty}다. 조립 결과의 등급은
 * 언제나 그 판정과 같아야 한다. 등급 계산을 증분으로 바꾼 것이 규칙을 바꾼 것은 아니기 때문이다.
 */
class PartyAssemblerTest {

    private static final GameModeConfig LOL_DUO =
            new GameModeConfig(GameKey.LOL, "SOLO_DUO_RANKED", 2, true, true);
    private static final GameModeConfig PUBG_SQUAD =
            new GameModeConfig(GameKey.PUBG, "SQUAD", 4, false, true);

    /** 후보 순서대로 고르는 random. 같은 입력이면 언제나 같은 파티가 나온다. */
    private static final RandomSource FIRST = new RandomSource() {
        @Override
        public <T> T pick(List<T> candidates) {
            return candidates.get(0);
        }
    };

    @Test
    @DisplayName("정원을 채우면 파티가 나오고 등급은 forParty 판정과 같다")
    void assemblesPartyMatchingForPartyTier() {
        Candidate seed = lol(LolPosition.TOP, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
        Candidate other = lol(LolPosition.MID, VoicePreference.OPTIONAL, PlayPurpose.NORMAL);
        List<Candidate> all = List.of(seed, other);

        Optional<List<Candidate>> party =
                PartyAssembler.over(all, LOL_DUO, noBlocks(), FIRST).assembleFrom(0);

        assertThat(party).isPresent();
        assertThat(party.get()).containsExactly(seed, other);
        assertThat(ConditionCompatibility.forParty(conditionsOf(party.get()), LOL_DUO))
                .contains(CompatibilityTier.GOOD);
    }

    @Test
    @DisplayName("등급이 더 좋은 후보를 먼저 고른다")
    void prefersBetterTier() {
        Candidate seed = lol(LolPosition.TOP, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
        Candidate worse = lol(LolPosition.MID, VoicePreference.REQUIRED, PlayPurpose.NORMAL);
        Candidate better = lol(LolPosition.JUNGLE, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);

        // 대기 순서상 worse가 앞이지만 등급이 결정한다.
        Optional<List<Candidate>> party = PartyAssembler
                .over(List.of(seed, worse, better), LOL_DUO, noBlocks(), FIRST)
                .assembleFrom(0);

        assertThat(party).isPresent();
        assertThat(party.get()).containsExactly(seed, better);
    }

    @Test
    @DisplayName("차단 관계는 같은 파티가 되지 않는다 (INV-6)")
    void excludesBlockedPair() {
        Candidate seed = lol(LolPosition.TOP, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
        Candidate blocked = lol(LolPosition.MID, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);

        BlockLookupPort blocks = blocksBetween(seed.userId(), blocked.userId());

        assertThat(PartyAssembler.over(List.of(seed, blocked), LOL_DUO, blocks, FIRST)
                .assembleFrom(0)).isEmpty();
    }

    @Test
    @DisplayName("hard 조건이 맞는 후보가 정원에 모자라면 조립하지 않는다")
    void failsWhenNotEnoughCompatibleCandidates() {
        Candidate seed = pubg(PubgPlayStyle.AGGRESSIVE, VoicePreference.REQUIRED);
        // 음성 요구가 정면으로 충돌해 seed와 같은 파티가 될 수 없다.
        List<Candidate> all = new ArrayList<>(List.of(seed));
        for (int i = 0; i < 5; i++) {
            all.add(pubg(PubgPlayStyle.AGGRESSIVE, VoicePreference.NO_VOICE));
        }

        assertThat(PartyAssembler.over(all, PUBG_SQUAD, noBlocks(), FIRST).assembleFrom(0))
                .isEmpty();
    }

    @Test
    @DisplayName("추가 제약을 거절하는 후보는 파티에 들어가지 않는다")
    void honoursExtraFilter() {
        Candidate seed = lol(LolPosition.TOP, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
        Candidate rejected = lol(LolPosition.MID, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
        Candidate accepted = lol(LolPosition.ADC, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);

        Optional<List<Candidate>> party = PartyAssembler
                .over(List.of(seed, rejected, accepted), LOL_DUO, noBlocks(), FIRST)
                .assembleFrom(0, (current, candidate) -> !candidate.equals(rejected));

        assertThat(party).isPresent();
        assertThat(party.get()).containsExactly(seed, accepted);
    }

    @Test
    @DisplayName("seed를 몇 번 바꿔도 차단 조회는 사용자당 한 번뿐이다")
    void looksUpBlocksOncePerUser() {
        List<Candidate> all = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            // 서로 호환되지 않게 해서 seed마다 후보 전원을 훑도록 만든다.
            all.add(pubg(PubgPlayStyle.AGGRESSIVE,
                    i % 2 == 0 ? VoicePreference.REQUIRED : VoicePreference.NO_VOICE));
        }
        CountingBlockLookup blocks = new CountingBlockLookup();

        PartyAssembler<Candidate> assembler =
                PartyAssembler.over(all, PUBG_SQUAD, blocks, FIRST);
        for (int seed = 0; seed < all.size(); seed++) {
            assembler.assembleFrom(seed);
        }

        assertThat(blocks.calls).isEqualTo(all.size());
    }

    private static List<MatchCondition> conditionsOf(List<Candidate> party) {
        return party.stream().map(Candidate::condition).toList();
    }

    private static Candidate lol(LolPosition position, VoicePreference voice, PlayPurpose purpose) {
        return new Candidate(UUID.randomUUID(), new MatchCondition(
                GameKey.LOL, "SOLO_DUO_RANKED", position, voice, purpose));
    }

    private static Candidate pubg(PubgPlayStyle style, VoicePreference voice) {
        return new Candidate(UUID.randomUUID(), new MatchCondition(
                GameKey.PUBG, "SQUAD", style, voice, PlayPurpose.NORMAL));
    }

    private static BlockLookupPort noBlocks() {
        return new CountingBlockLookup();
    }

    private static BlockLookupPort blocksBetween(UUID one, UUID other) {
        CountingBlockLookup lookup = new CountingBlockLookup();
        lookup.blocked.computeIfAbsent(one, key -> new HashSet<>()).add(other);
        return lookup;
    }

    private record Candidate(UUID userId, MatchCondition condition) implements PartyCandidate {
    }

    private static final class CountingBlockLookup implements BlockLookupPort {
        private final Map<UUID, Set<UUID>> blocked = new HashMap<>();
        private int calls;

        @Override
        public Set<UUID> blockedUserIds(UUID userId) {
            calls++;
            return blocked.getOrDefault(userId, Set.of());
        }

        @Override
        public boolean anyBlockBetween(Collection<UUID> userIds) {
            throw new UnsupportedOperationException("조립기는 확정 재검증을 하지 않는다");
        }
    }
}
