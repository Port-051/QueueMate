package com.queuemate.user.riot;

import com.queuemate.common.domain.GameKey;
import com.queuemate.user.domain.GameAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RiotRankServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-12T12:00:00Z");

    private final RiotClient riot = mock(RiotClient.class);

    private static RiotProperties properties(String apiKey) {
        return new RiotProperties(apiKey, "https://asia.test", "https://kr.test",
                Duration.ofSeconds(2), Duration.ofSeconds(3),
                Duration.ofHours(6), Duration.ofHours(24));
    }

    private RiotRankService service() {
        return new RiotRankService(riot, properties("RGAPI-test-key"));
    }

    private static GameAccount account(GameKey game, String externalGameId) {
        return GameAccount.create(UUID.randomUUID(), game, externalGameId, "KR");
    }

    @Test
    @DisplayName("연결 직후 솔로 랭크를 읽어 rankCode를 채운다")
    void fillsRankOnLink() {
        when(riot.findPuuid("QueueMaster", "KR1")).thenReturn(Optional.of("PUUID-1"));
        when(riot.findRanks("PUUID-1")).thenReturn(new RiotRanks(new RiotRank("GOLD", "II", 45), new RiotRank("PLATINUM", "IV", 3)));
        GameAccount account = account(GameKey.LOL, "QueueMaster#KR1");

        service().syncNewlyLinked(account, NOW);

        assertEquals("GOLD_2", account.getRankCode());
        assertEquals("PLATINUM_4", account.getFlexRankCode());
        assertEquals(NOW, account.getRankUpdatedAt());
        assertEquals(NOW, account.getRankSyncedAt());
    }

    @Test
    @DisplayName("언랭이면 조회 시각만 남고 rankCode는 비어 있다")
    void unrankedRecordsOnlyTheAttempt() {
        when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.of("PUUID-1"));
        when(riot.findRanks("PUUID-1")).thenReturn(RiotRanks.NONE);
        GameAccount account = account(GameKey.LOL, "QueueMaster#KR1");

        service().syncNewlyLinked(account, NOW);

        assertNull(account.getRankCode());
        assertNull(account.getFlexRankCode());
        assertNull(account.getRankUpdatedAt(), "채운 적이 없으므로 갱신 시각도 없다");
        // 시각은 남긴다. 남기지 않으면 언랭 계정을 매 조회마다 다시 묻는다.
        assertEquals(NOW, account.getRankSyncedAt());
    }

    @Test
    @DisplayName("Riot을 못 읽으면 아무것도 쓰지 않는다")
    void outageWritesNothing() {
        when(riot.findPuuid(anyString(), anyString())).thenThrow(new RiotUnavailableException(new RuntimeException()));
        GameAccount account = account(GameKey.LOL, "QueueMaster#KR1");

        service().syncNewlyLinked(account, NOW);

        // 시각을 남기면 잠깐의 장애가 '확인했고 랭크가 없다'로 굳는다.
        assertNull(account.getRankSyncedAt());
        assertNull(account.getRankCode());
    }

    @Test
    @DisplayName("장애로 실패해도 이미 알던 티어는 지워지지 않는다")
    void outageKeepsTheKnownRank() {
        GameAccount account = account(GameKey.LOL, "QueueMaster#KR1");
        when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.of("PUUID-1"));
        when(riot.findRanks("PUUID-1")).thenReturn(new RiotRanks(new RiotRank("DIAMOND", "IV", 10), null));
        service().syncNewlyLinked(account, NOW.minusDays(2));
        assertEquals("DIAMOND_4", account.getRankCode());

        when(riot.findPuuid(anyString(), anyString())).thenThrow(new RiotUnavailableException(new RuntimeException()));
        service().refreshStale(List.of(account), NOW);

        assertEquals("DIAMOND_4", account.getRankCode(), "장애가 티어를 지워서는 안 된다");
    }

    @Test
    @DisplayName("없는 Riot ID는 확인된 결과라 시각을 남긴다")
    void missingRiotIdIsARecordedResult() {
        when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.empty());
        GameAccount account = account(GameKey.LOL, "없는사람#KR1");

        service().syncNewlyLinked(account, NOW);

        assertEquals(NOW, account.getRankSyncedAt());
        assertNull(account.getRankCode());
    }

    @Test
    @DisplayName("Riot ID 형식이 아니면 부르지도 않는다")
    void doesNotCallRiotForNonRiotIds() {
        GameAccount account = account(GameKey.LOL, "옛날소환사명");

        service().syncNewlyLinked(account, NOW);

        verifyNoInteractions(riot);
        assertEquals(NOW, account.getRankSyncedAt(), "다시 물어도 같은 결과다");
    }

    @Test
    @DisplayName("발로란트와 PUBG는 조회하지 않는다")
    void onlyLolIsSupported() {
        // 발로란트 개인 랭크를 주는 공개 엔드포인트가 Riot에 없다. PUBG는 Riot 게임이 아니다.
        GameAccount valorant = account(GameKey.VALORANT, "QueueMaster#KR1");
        GameAccount pubg = account(GameKey.PUBG, "QueueMaster");

        service().syncNewlyLinked(valorant, NOW);
        service().syncNewlyLinked(pubg, NOW);

        verifyNoInteractions(riot);
        assertNull(valorant.getRankSyncedAt());
        assertNull(pubg.getRankSyncedAt());
    }

    @Test
    @DisplayName("키가 없으면 Riot을 부르지 않는다")
    void skipsEverythingWithoutAnApiKey() {
        RiotRankService withoutKey = new RiotRankService(riot, properties(""));
        GameAccount account = account(GameKey.LOL, "QueueMaster#KR1");

        withoutKey.syncNewlyLinked(account, NOW);
        assertFalse(withoutKey.refreshStale(List.of(account), NOW));

        verifyNoInteractions(riot);
    }

    @Nested
    @DisplayName("갱신 주기")
    class Staleness {

        private GameAccount synced(String rankCode, OffsetDateTime at) {
            GameAccount account = account(GameKey.LOL, "QueueMaster#KR1");
            account.applyRank(rankCode, null, at);
            return account;
        }

        @Test
        @DisplayName("한 번도 읽지 않았으면 읽는다")
        void neverSyncedIsStale() {
            when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.empty());

            assertTrue(service().refreshStale(List.of(account(GameKey.LOL, "QueueMaster#KR1")), NOW));
        }

        @Test
        @DisplayName("랭크가 있으면 6시간마다 다시 읽는다")
        void rankedRefreshesAfterSixHours() {
            when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.of("PUUID-1"));
            when(riot.findRanks(anyString())).thenReturn(new RiotRanks(new RiotRank("GOLD", "I", 80), null));

            assertFalse(service().refreshStale(List.of(synced("GOLD_2", NOW.minusHours(5))), NOW));
            assertTrue(service().refreshStale(List.of(synced("GOLD_2", NOW.minusHours(7))), NOW));
        }

        @Test
        @DisplayName("언랭은 24시간마다 확인한다")
        void unrankedRefreshesAfterADay() {
            when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.empty());

            // 6시간이 지나도 아직 안 본다. 언랭은 흔하고 잘 바뀌지 않는다.
            assertFalse(service().refreshStale(List.of(synced(null, NOW.minusHours(7))), NOW));
            assertTrue(service().refreshStale(List.of(synced(null, NOW.minusHours(25))), NOW));
        }

        @Test
        @DisplayName("갱신이 필요 없으면 Riot을 부르지 않는다")
        void freshAccountsCostNoCalls() {
            service().refreshStale(List.of(synced("GOLD_2", NOW.minusMinutes(10))), NOW);

            verify(riot, never()).findPuuid(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("rankCode 형식")
    class RankCodes {

        @Test
        @DisplayName("티어와 단계를 아라비아 숫자로 잇는다")
        void tierAndDivision() {
            assertEquals("GOLD_2", new RiotRank("GOLD", "II", 45).toRankCode());
            assertEquals("IRON_4", new RiotRank("IRON", "IV", 0).toRankCode());
            assertEquals("EMERALD_1", new RiotRank("EMERALD", "I", 99).toRankCode());
        }

        @Test
        @DisplayName("마스터 위로는 단계가 없어서 티어만 남는다")
        void apexTiersHaveNoDivision() {
            // Riot은 이 구간에도 rank를 "I"로 채워 보낸다. 그대로 쓰면 MASTER_1이 되고
            // 있지도 않은 단계가 있는 것처럼 보인다.
            assertEquals("MASTER", new RiotRank("MASTER", "I", 300).toRankCode());
            assertEquals("GRANDMASTER", new RiotRank("GRANDMASTER", "I", 700).toRankCode());
            assertEquals("CHALLENGER", new RiotRank("CHALLENGER", "I", 2090).toRankCode());
        }

        @Test
        @DisplayName("rank_code 컬럼 길이를 넘지 않는다")
        void fitsInTheColumn() {
            // VARCHAR(40)이다. 가장 긴 값이 GRANDMASTER라 여유가 크다.
            assertTrue(new RiotRank("GRANDMASTER", "I", 700).toRankCode().length() <= 40);
        }
    }

    @Nested
    @DisplayName("Riot ID 파싱")
    class RiotIdParsing {

        @Test
        @DisplayName("마지막 #을 기준으로 이름과 태그를 가른다")
        void splitsOnTheLastHash() {
            RiotRankService.RiotId id = RiotRankService.RiotId.parse("Hide on bush#KR1");

            assertNotNull(id);
            assertEquals("Hide on bush", id.gameName());
            assertEquals("KR1", id.tagLine());
        }

        @Test
        @DisplayName("이름에 #이 들어가도 마지막 것이 구분자다")
        void nameMayContainHash() {
            RiotRankService.RiotId id = RiotRankService.RiotId.parse("a#b#KR1");

            assertNotNull(id);
            assertEquals("a#b", id.gameName());
            assertEquals("KR1", id.tagLine());
        }

        @Test
        @DisplayName("형식이 아니면 null이다")
        void rejectsNonRiotIds() {
            assertNull(RiotRankService.RiotId.parse("소환사명만"));
            assertNull(RiotRankService.RiotId.parse("#KR1"));
            assertNull(RiotRankService.RiotId.parse("이름#"));
            assertNull(RiotRankService.RiotId.parse("   #  "));
            assertNull(RiotRankService.RiotId.parse(null));
        }
    }

    @Test
    @DisplayName("여러 계정 중 오래된 것만 읽는다")
    void refreshesOnlyStaleAccounts() {
        GameAccount fresh = account(GameKey.LOL, "Fresh#KR1");
        fresh.applyRank("GOLD_2", null, NOW.minusMinutes(1));
        GameAccount stale = account(GameKey.LOL, "Stale#KR1");
        stale.applyRank("SILVER_1", null, NOW.minusDays(1));
        when(riot.findPuuid("Stale", "KR1")).thenReturn(Optional.of("PUUID-2"));
        when(riot.findRanks("PUUID-2")).thenReturn(new RiotRanks(new RiotRank("PLATINUM", "III", 20), null));

        assertTrue(service().refreshStale(List.of(fresh, stale), NOW));

        assertEquals("GOLD_2", fresh.getRankCode());
        assertEquals("PLATINUM_3", stale.getRankCode());
        verify(riot, never()).findPuuid("Fresh", "KR1");
    }

    @Test
    @DisplayName("한 계정이 실패해도 나머지는 갱신된다")
    void oneFailureDoesNotStopTheRest() {
        GameAccount broken = account(GameKey.LOL, "Broken#KR1");
        GameAccount ok = account(GameKey.LOL, "Ok#KR1");
        when(riot.findPuuid("Broken", "KR1")).thenThrow(new RiotUnavailableException(new RuntimeException()));
        when(riot.findPuuid("Ok", "KR1")).thenReturn(Optional.of("PUUID-3"));
        when(riot.findRanks("PUUID-3")).thenReturn(new RiotRanks(new RiotRank("BRONZE", "III", 5), null));

        assertTrue(service().refreshStale(List.of(broken, ok), NOW));

        assertNull(broken.getRankSyncedAt());
        assertEquals("BRONZE_3", ok.getRankCode());
    }

    @Test
    @DisplayName("시즌이 초기화돼 랭크가 사라지면 지운다")
    void seasonResetClearsTheStaleRank() {
        GameAccount account = account(GameKey.LOL, "QueueMaster#KR1");
        when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.of("PUUID-1"));
        when(riot.findRanks("PUUID-1")).thenReturn(
                new RiotRanks(new RiotRank("DIAMOND", "IV", 10), new RiotRank("GOLD", "I", 5)));
        service().syncNewlyLinked(account, NOW.minusDays(2));

        // 새 시즌이 열려 둘 다 배치가 풀렸다.
        when(riot.findRanks("PUUID-1")).thenReturn(RiotRanks.NONE);
        service().refreshStale(List.of(account), NOW);

        // 남겨 두면 지난 시즌 티어가 계속 보인다. 장애(아무것도 안 씀)와는 다른 경우다.
        assertNull(account.getRankCode());
        assertNull(account.getFlexRankCode());
    }

    @Test
    @DisplayName("한쪽 큐만 배치가 풀려도 나머지는 남는다")
    void oneQueueResetKeepsTheOther() {
        GameAccount account = account(GameKey.LOL, "QueueMaster#KR1");
        when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.of("PUUID-1"));
        when(riot.findRanks("PUUID-1")).thenReturn(
                new RiotRanks(new RiotRank("DIAMOND", "IV", 10), new RiotRank("GOLD", "I", 5)));
        service().syncNewlyLinked(account, NOW.minusDays(2));

        when(riot.findRanks("PUUID-1")).thenReturn(new RiotRanks(null, new RiotRank("GOLD", "I", 5)));
        service().refreshStale(List.of(account), NOW);

        assertNull(account.getRankCode());
        assertEquals("GOLD_1", account.getFlexRankCode());
    }

    @Test
    @DisplayName("자유 랭크만 있어도 '랭크 있음'으로 보고 6시간 주기를 쓴다")
    void flexAloneCountsAsRanked() {
        GameAccount flexOnly = account(GameKey.LOL, "QueueMaster#KR1");
        flexOnly.applyRank(null, "GOLD_1", NOW.minusHours(7));

        // 언랭 주기(24시간)를 썼다면 아직 갱신하지 않았을 시점이다.
        when(riot.findPuuid(anyString(), anyString())).thenReturn(Optional.of("PUUID-1"));
        when(riot.findRanks("PUUID-1")).thenReturn(new RiotRanks(null, new RiotRank("GOLD", "I", 9)));

        assertTrue(service().refreshStale(List.of(flexOnly), NOW));
    }

    @Test
    @DisplayName("계정이 없으면 아무 일도 하지 않는다")
    void emptyListIsANoop() {
        assertFalse(service().refreshStale(List.of(), NOW));
        verify(riot, never()).findRanks(any());
    }
}
