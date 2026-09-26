package com.queuemate.platform.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자가 연결한 게임 계정. 게임마다 하나다({@code UNIQUE (user_id, game)}). 티어 · 주 포지션은 자기신고다 —
 * 게임사 API 에서 가져오는 것은 전적({@code GameAccountStats})과 {@code externalId} 뿐이다({@code account.stats}).
 *
 * <p><b>읽기 전용으로 쓴다.</b> 만들기 · 바꾸기는 엔티티를 거치지 않고 {@code INSERT … ON CONFLICT DO UPDATE} 한 문장으로 한다
 * ({@code GameAccountRepository#upsert}) — "있는지 보고 없으면 넣는다"로 하면 동시에 온 두 요청이 둘 다 넣으려 든다.
 * 그래서 생성자도 세터도 없다.
 *
 * <p>{@code externalId} · {@code verified} 는 <b>사용자의 요청으로 바뀌지 않는다</b> — upsert 문장이 그 두 칸을 건드리지 않는다.
 * {@code externalId} 는 전적을 긁을 때 {@code account.stats} 가 적고({@code GameAccountRepository#updateExternalId}),
 * {@code verified} 는 <b>아직 켜는 길이 없다</b> — 식별자를 알아낸 것은 본인 확인이 아니다(RSO 인증은 미정 — CLAUDE.md §7 "게임 계정 연동").
 */
@Entity
@Table(name = "game_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 사용자 번호({@code users.id}) */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game", nullable = false, updatable = false, length = 10)
    private Game game;

    @Column(name = "game_nickname", nullable = false, length = 40)
    private String gameNickname;

    @Column(name = "tier", length = 20)
    private String tier;

    @Column(name = "main_position", length = 20)
    private String mainPosition;

    /** 게임사 쪽 계정 식별자 — LoL 은 {@code puuid} 다({@code account.stats} 가 전적을 긁을 때 적는다). VALORANT · PUBG 는 아직 {@code null} 이다. 응답에 싣지 않는다 */
    @Column(name = "external_id", length = 100)
    private String externalId;

    /** 게임사 인증(RSO 등)으로 본인 계정임을 확인했는가. <b>지금은 켜는 길이 없어 늘 {@code false} 다</b> — 전적을 긁어도 켜지 않는다 */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    /** PUBG 만 쓴다({@code STEAM} · {@code KAKAO}). 다른 게임은 {@code null} 이다 — DB 의 CHECK 도 같은 것을 건다 */
    @Column(name = "server", length = 10)
    private String server;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
