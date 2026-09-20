package com.queuemate.notification.domain;

/**
 * 지원하는 게임. 셋뿐이다 (docs/11 #8). 이름은 {@code matching} 의 {@code domain/GameKey} 와 글자까지 같다.
 *
 * <p>이 서비스에서 게임이 필요한 곳은 <b>게시판 채널</b> 하나다 — 게시판 채널 {@code qm:pubsub:board:{game}} 의 끝에
 * {@link #name()} 이 그대로 붙는다 (docs/11 D-20). 발행하는 쪽({@code platform} · {@code room})과 다르게 적으면
 * <b>발행하는 채널과 구독하는 채널이 어긋난 채로 아무 에러도 나지 않는다</b> — 알림 채널 접두사와 같은 위험이다 (CLAUDE.md §3).
 *
 * <p>이 서비스는 게임의 뜻을 모른다. 알림의 본문도 열어 보지 않는다 — 어느 채널을 구독할지 정할 때만 쓴다.
 */
public enum GameKey {
    LOL, VALORANT, PUBG
}
