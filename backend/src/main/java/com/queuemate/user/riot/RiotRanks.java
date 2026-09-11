package com.queuemate.user.riot;

/**
 * 한 계정의 랭크 전부. league-v4가 큐별 항목을 한 응답에 주므로 함께 다룬다.
 *
 * <p>둘은 서로 독립이다. 솔로만 배치를 마친 계정도, 자유만 한 계정도 흔하다.
 */
public record RiotRanks(RiotRank solo, RiotRank flex) {

    public static final RiotRanks NONE = new RiotRanks(null, null);

    public String soloRankCode() {
        return solo == null ? null : solo.toRankCode();
    }

    public String flexRankCode() {
        return flex == null ? null : flex.toRankCode();
    }
}
