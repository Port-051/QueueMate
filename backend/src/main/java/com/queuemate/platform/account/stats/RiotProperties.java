package com.queuemate.platform.account.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 전적 동기화의 설정값. {@code application.yaml} 의 {@code platform.riot.*} 이고 환경변수로 바꾼다.
 * 환경변수의 이름은 {@code contracts/platform-api.md} "게임 프로필" 의 "전적을 긁는 것" 이 원본이다.
 *
 * <p><b>주소가 둘이다</b> — Riot 의 API 는 계정 · 경기가 <b>대륙</b> 주소({@code asia}), 소환사 · 리그가 <b>플랫폼</b> 주소({@code kr})에 있다.
 * 테스트는 둘 다 가짜 서버로 돌린다.
 *
 * @param apiKey          Riot 개발자 키(환경변수 {@code RIOT_API_KEY}). <b>비어 있으면 긁는 일 자체를 하지 않는다</b> — 기동은 정상이고
 *                        {@code stats} 가 {@code null} 로 남는다. <b>로그에 찍지 않는다</b>({@link #toString()})
 * @param regionalBaseUrl 대륙 주소 — {@code account-v1}(Riot ID → puuid) · {@code match-v5}(경기)
 * @param platformBaseUrl 플랫폼 주소 — {@code summoner-v4} · {@code league-v4}(솔로랭크의 승/패)
 * @param matchCount      최근 몇 경기를 읽어 평균을 낼지. 경기 하나가 요청 하나다 — 개발용 키의 한도(2분당 100회)를 생각해 작게 둔다
 * @param connectTimeout  Riot 을 부를 때의 연결 타임아웃
 * @param readTimeout     Riot 을 부를 때의 읽기 타임아웃. 긁는 것은 비동기라 요청 스레드를 붙잡지는 않지만, 느린 응답에 전용 풀이 묶이면 안 된다
 */
@ConfigurationProperties(prefix = "platform.riot")
public record RiotProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://asia.api.riotgames.com") String regionalBaseUrl,
        @DefaultValue("https://kr.api.riotgames.com") String platformBaseUrl,
        @DefaultValue("20") int matchCount,
        @DefaultValue("PT3S") Duration connectTimeout,
        @DefaultValue("PT3S") Duration readTimeout
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
                + ", platformBaseUrl=" + platformBaseUrl + ", matchCount=" + matchCount + "]";
    }
}
