package com.queuemate.platform.account.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 전적 동기화의 설정값. {@code application.yaml} 의 {@code platform.riot.*} 이고 환경변수로 바꾼다.
 * 환경변수의 이름은 {@code contracts/platform-api.md} "게임 프로필" 의 "전적을 긁는 것" 이 원본이다.
 *
 * <p><b>주소가 둘이다</b> — Riot 의 API 는 계정 · 경기가 <b>대륙</b> 주소({@code asia}), 리그 · 숙련도가 <b>플랫폼</b> 주소({@code kr})에 있다.
 * 테스트는 둘 다 가짜 서버로 돌린다.
 *
 * @param apiKey          Riot 개발자 키(환경변수 {@code RIOT_API_KEY}). <b>비어 있으면 긁는 일 자체를 하지 않는다</b> — 기동은 정상이고
 *                        {@code stats} 가 {@code null} 로 남는다. <b>로그에 찍지 않는다</b>({@link #toString()})
 * @param regionalBaseUrl 대륙 주소 — {@code account-v1}(Riot ID → puuid) · {@code match-v5}(경기)
 * @param platformBaseUrl 플랫폼 주소 — {@code league-v4}(솔로 · 자유랭크의 티어 · 솔로랭크의 승/패 — {@code puuid} 로 부른다, 2026-09-29) · {@code champion-mastery-v4}
 * @param matchCount      최근 몇 경기를 읽어 평균 · 연승을 낼지. 경기 하나가 요청 하나다 — <b>기본 20판</b>(2026-10-03 멤버 상세 요청).
 *                        모스트 챔피언은 경기가 아니라 통산 숙련도 상위 셋이라 이 수와 무관하다(2026-09-30 — P-39).
 *                        20판이면 한 번에 Riot 호출 최대 24번(대륙 22 · 플랫폼 2)이다. 기존 호출 제한과 재수집 간격을 유지한다.
 *                        승/패는 이 수와 무관하다(솔로랭크 시즌 누적 — {@code league-v4})
 * @param connectTimeout  Riot 을 부를 때의 연결 타임아웃
 * @param readTimeout     Riot 을 부를 때의 읽기 타임아웃. 긁는 것은 전용 풀이라 요청 스레드를 붙잡지는 않지만, 느린 응답에 전용 풀이 묶이면 안 된다
 * @param refreshTimeout  <b>LoL · PUBG 게임 계정 연결</b>({@code PUT …/game-accounts/{game}})이 <b>다 긁기를 기다리는 상한</b>(<b>30초</b> — 2026-09-24 소유자 결정 ·
 *                        연결은 2026-09-27 부터). 넘으면 요청은 503 으로 끝내고 늦게 끝난 긁기는 저장하지 않고 버린다({@link GameStatsRefresher}).
 *                        이름의 {@code refresh} 는 옛 "전적 갱신" 요청(2026-09-24 ~ 09-30 — P-42 로 없어졌다)의 것이다 — 설정 이름을 바꾸지 않았다.
 *                        (그 요청의 2분 쿨타임 {@code refresh-cooldown} 은 요청과 함께 없어졌다)
 * @param staleAfter      <b>로그인 · 재발급 때 뒤에서 다시 받는 기준</b>(2026-09-30 소유자 결정 — <b>1시간</b> · P-42). 마지막으로 받은 때
 *                        ({@code game_account_stats.synced_at})가 이보다 오래된 게임 계정만 다시 받는다 — 전적 줄이 없는 계정도 받는다.
 *                        게임사 키의 한도를 지키는 바닥이다 — 한 번이 Riot 호출 최대 24번(경기 20판 · PUBG 는 2 ~ 4번)이고 재발급은 15분마다 온다({@link GameStatsLoginRefresher}).
 *                        LoL · PUBG 가 같은 값을 쓴다(환경변수 {@code GAME_STATS_STALE_AFTER})
 */
@ConfigurationProperties(prefix = "platform.riot")
public record RiotProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://asia.api.riotgames.com") String regionalBaseUrl,
        @DefaultValue("https://kr.api.riotgames.com") String platformBaseUrl,
        @DefaultValue("20") int matchCount,
        @DefaultValue("PT3S") Duration connectTimeout,
        @DefaultValue("PT3S") Duration readTimeout,
        @DefaultValue("PT30S") Duration refreshTimeout,
        @DefaultValue("PT1H") Duration staleAfter
) {
    /** 키가 있는가 — 없으면 긁지 않는다 */
    public boolean configured()
    {
        return apiKey != null && !apiKey.isBlank();
    }

    /** <b>키가 실수로 로그에 찍히지 않게 한다</b> — record 의 기본 toString 은 모든 칸을 찍는다 */
    @Override
    public String toString()
    {
        return "RiotProperties[configured=" + configured() + ", regionalBaseUrl=" + regionalBaseUrl
                + ", platformBaseUrl=" + platformBaseUrl + ", matchCount=" + matchCount
                + ", refreshTimeout=" + refreshTimeout + ", staleAfter=" + staleAfter + "]";
    }
}
