package com.queuemate.user.riot;

import java.util.Locale;
import java.util.Map;

/**
 * Riot이 돌려준 랭크 한 줄.
 *
 * <p>{@code rankCode}는 docs/14의 예시 형식(`GOLD_2`)을 따른다. 마스터 위로는 단계가
 * 없어서 티어 이름만 남는다. Riot은 그 구간에도 rank를 "I"로 채워 보내지만
 * 화면에 "MASTER_1"이라고 쓰면 그런 단계가 있는 것처럼 보인다.
 */
public record RiotRank(String tier, String division, int leaguePoints) {

    /** Riot은 로마 숫자로 준다. 계약의 rankCode는 아라비아 숫자다. */
    private static final Map<String, String> DIVISIONS =
            Map.of("I", "1", "II", "2", "III", "3", "IV", "4");

    public String toRankCode() {
        String upper = tier.toUpperCase(Locale.ROOT);
        if (isApex(upper)) {
            return upper;
        }
        String number = DIVISIONS.get(division == null ? "" : division.toUpperCase(Locale.ROOT));
        return number == null ? upper : upper + "_" + number;
    }

    private static boolean isApex(String tier) {
        return "MASTER".equals(tier) || "GRANDMASTER".equals(tier) || "CHALLENGER".equals(tier);
    }
}
