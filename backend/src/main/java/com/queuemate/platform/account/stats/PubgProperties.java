package com.queuemate.platform.account.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * PUBG 전적 동기화의 설정값(2026-09-29 소유자 결정 — P-36). {@code application.yaml} 의 {@code platform.pubg.*} 이고 키 · 주소는 환경변수로 바꾼다.
 * 환경변수의 이름은 {@code contracts/platform-api.md} "전적을 긁는 것" 의 PUBG 절이 원본이다. {@link RiotProperties} 를 본떴다.
 *
 * <p><b>연결의 상한(30초) · 로그인 때 다시 받는 기준(1시간)은 여기 없다</b> — LoL 과 같은 값을 쓴다({@link RiotProperties#refreshTimeout()} ·
 * {@link RiotProperties#staleAfter()} — P-42). 게임마다 두지 않았다(자물쇠 키도 게임 계정 번호 하나로 같이 쓴다).
 *
 * @param apiKey         PUBG 개발자 키(환경변수 {@code PUBG_API_KEY}). <b>비어 있으면 긁는 일 자체를 하지 않는다</b> — 기동은 정상이고
 *                       PUBG 게임 계정 연결이 503 이고 로그인 때 다시 받지도 않는다. <b>로그에 찍지 않는다</b>({@link #toString()})
 * @param baseUrl        PUBG API 주소(환경변수 {@code PUBG_BASE_URL}). 테스트는 가짜 서버로 돌린다
 * @param connectTimeout PUBG 를 부를 때의 연결 타임아웃
 * @param readTimeout    PUBG 를 부를 때의 읽기 타임아웃. 긁는 것은 전용 풀이라 요청 스레드를 붙잡지는 않지만, 느린 응답에 전용 풀이 묶이면 안 된다
 * @param seasonCacheTtl 현재 시즌 번호를 Redis 에 두는 수명({@code qm:pubg:season:{shard}}) — <b>30일</b>. 문서가 "시즌 목록은 바뀌는 것이 두 달에 한 번쯤이니
 *                       <b>한 달에 한 번보다 자주 묻지 마라</b>" 고 적었다(2026-09-29 — 그 지침에 맞췄다). <b>대가</b> — 시즌이 바뀐 뒤 캐시가 끝날 때까지(최대 30일)
 *                       <b>지난 시즌의 전적 · 티어</b>를 긁는다(새 시즌 초에는 오히려 판이 있는 쪽이라 화면이 비지 않는다). 급하면 운영자가 그 키를 지우면 된다
 */
@ConfigurationProperties(prefix = "platform.pubg")
public record PubgProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://api.pubg.com") String baseUrl,
        @DefaultValue("PT3S") Duration connectTimeout,
        @DefaultValue("PT3S") Duration readTimeout,
        @DefaultValue("P30D") Duration seasonCacheTtl
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
        return "PubgProperties[configured=" + configured() + ", baseUrl=" + baseUrl
                + ", seasonCacheTtl=" + seasonCacheTtl + "]";
    }
}
