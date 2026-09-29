package com.queuemate.platform.account.stats;

/**
 * 그 shard(서버)에 그 닉네임의 PUBG 플레이어가 없다 — {@code GET /shards/{shard}/players?filter[playerNames]=} 가 404 를 줬거나,
 * 준 목록에 <b>글자 그대로 같은 이름</b>이 없다(PUBG 의 이름은 대소문자를 가린다).
 *
 * <p><b>PUBG 게임 계정 연결</b>({@code PUT …/game-accounts/PUBG} — 2026-09-29 소유자 결정 · P-36)이 이것을 받아 <b>404 {@code PUBG_PLAYER_NOT_FOUND}</b> 로 옮기고
 * 저장하지 않는다. {@link PubgApiException} 과 가르는 이유는 {@link RiotIdNotFoundException} 과 같다 — 사용자가 고칠 수 있는 실패다(닉네임 · 서버를 잘못 골랐다).
 * 전적 갱신에서 이것이 나면 LoL 과 같이 503 이다.
 */
public class PubgPlayerNotFoundException extends RuntimeException {

    PubgPlayerNotFoundException()
    {
        super("그 shard 에 그 PUBG 닉네임이 없다");
    }
}
