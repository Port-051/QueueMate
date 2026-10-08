package com.queuemate.platform.account.stats;

/**
 * 그 Riot ID({@code 이름#태그})가 Riot 에 없다 — {@code account-v1} 이 404 를 줬다.
 *
 * <p><b>LoL 게임 계정 연결</b>({@code PUT …/game-accounts/LOL} — 2026-09-27 소유자 결정)이 이것을 받아 <b>404 {@code RIOT_ID_NOT_FOUND}</b> 로 옮기고
 * 저장하지 않는다. {@link RiotApiException} 과 가르는 이유는 <b>사용자가 고칠 수 있는 실패</b>라서다 — 이름#태그를 잘못 적었다.
 * 나머지(Riot 이 죽었다 · 429 · 타임아웃)는 사용자가 할 수 있는 것이 "잠시 뒤 다시"뿐이라 503 하나로 합친다.
 *
 * <p>로그인 · 재발급 때 뒤에서 다시 받다가 이것이 나면(그 사이에 Riot ID 를 바꿨다) 다른 실패와 같이 WARN 한 줄로 끝내고 기존 전적 줄을 지우지 않는다(P-42).
 */
public class RiotIdNotFoundException extends RuntimeException {

    RiotIdNotFoundException(Throwable cause)
    {
        super("Riot 에 그 Riot ID 가 없다", cause);
    }
}
