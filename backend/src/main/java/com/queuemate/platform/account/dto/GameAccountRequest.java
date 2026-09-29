package com.queuemate.platform.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 게임 계정 연결 요청. 어느 게임인지는 경로에 있다.
 *
 * <p><b>LoL 은 {@code gameNickname}(이름#태그) 하나만 받는다</b>(2026-09-27 소유자 결정 · 2026-09-29 주 포지션을 뺐다) — {@code tier} · {@code server} 는
 * 보내면 400 이다(티어는 Riot 에서 채우고 LoL 에 서버가 없다).
 * 아래 {@code tier} 설명의 "자기신고"는 <b>VALORANT · PUBG</b> 의 것이다. 게임별로 가르는 것은 서비스다({@code UserService#putGameAccount}).
 *
 * <p><b>주 포지션은 게임 계정에 없다</b>(2026-09-29 소유자 결정 — {@code contracts/platform-api.md} P-35. 포지션은 게시판에 글을 쓸 때 정하는 것이라
 * 계정 연동에서 받을 이유가 없다). <b>그래도 {@code mainPosition} 칸은 남겨 둔다 — 값이 오면 400 으로 거절하려는 것이다</b>:
 * 모르는 칸처럼 조용히 버리면 옛 클라이언트가 값이 저장된다고 착각한다(LoL 이 {@code tier} 를 거절하는 것과 같은 까닭).
 * 자료형을 {@code Object} 로 둔 것은 문자열이 아닌 값(숫자 · 배열)도 같은 {@code "mainPosition: …"} 한 줄로 거절하려는 것이다.
 * {@code null} 은 안 보낸 것과 같아 통과한다({@code tier} 의 {@code null} 과 같다). 서비스는 이 칸을 읽지 않는다.
 *
 * @param gameNickname 그 게임 안에서의 이름. 40자까지
 * @param tier         자기신고 티어. 없어도 된다(안 적을 수 있다 — 값이 있을 때만 본다). <b>그 게임의 티어 사다리에 있는 이름이어야 한다</b>
 *                     (2026-09-24 소유자 결정) — 목록의 원본은 {@code matching} 의 gameconfig(Redis)라 서비스가 읽어서 본다({@code GameConfigReader}).
 *                     아래 {@code @Pattern} 은 사다리 검사보다 넓지만 <b>남겨 둔다</b> — Redis 를 못 읽어 검증을 건너뛸 때(fail-open) DB 칸({@code varchar(20)})에
 *                     들어갈 수 없는 값을 막는 것이 이것뿐이다
 * @param mainPosition <b>받지 않는다</b> — 값이 있으면 400 {@code VALIDATION_FAILED} 다(위)
 * @param server       PUBG 만({@code STEAM} · {@code KAKAO}). 없어도 된다. 다른 게임은 {@code null} 만 받는다 — 서비스에서 검증한다({@code Game#allowsServer})
 *
 * <p><b>{@code verified} · {@code externalId} · {@code stats} 칸이 없다</b> — 읽기 전용이라 요청으로 바꿀 수 없다.
 * 본문에 그런 이름이 들어 있어도 무시된다(모르는 칸은 읽지 않는다).
 */
public record GameAccountRequest(
        @NotBlank(message = "필요합니다")
        @Size(max = 40, message = "40자를 넘을 수 없습니다")
        String gameNickname,

        @Pattern(regexp = "^[A-Z0-9_]{1,20}$", message = "대문자 · 숫자 · 밑줄로 1~20자여야 합니다")
        String tier,

        @Null(message = "게임 계정에서 없앴습니다 — 보내지 마세요")
        Object mainPosition,

        String server
) {
}
