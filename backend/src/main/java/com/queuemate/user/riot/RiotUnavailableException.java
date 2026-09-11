package com.queuemate.user.riot;

/**
 * Riot API를 읽지 못했다. 키 만료·한도 초과·장애·타임아웃이 모두 여기에 해당한다.
 *
 * <p>"그런 계정이 없다"와 반드시 구분해야 한다. 이쪽은 다시 시도하면 될 수 있는 상태이고,
 * 그래서 조회 시각을 남기지 않는다. 남기면 잠깐의 장애가 "확인했고 랭크가 없다"로 굳는다.
 *
 * <p>사용자에게는 새어 나가지 않는다. 티어는 부가 정보이므로 계정 연결과 목록 조회는
 * 이 예외와 무관하게 성공해야 한다.
 */
public class RiotUnavailableException extends RuntimeException {

    public RiotUnavailableException(Throwable cause) {
        super("Riot API를 읽지 못했다", cause);
    }
}
