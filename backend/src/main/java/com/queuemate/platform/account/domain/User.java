package com.queuemate.platform.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자. 식별자는 <b>사용자 번호</b>({@code id} — bigint identity) 하나다. 시스템 안팎에서 쓰는 {@code userId} 가 이것이다 —
 * JWT 의 {@code sub}, 알림 채널, URL, 요청 · 응답 본문, 다른 테이블의 {@code *_id} 컬럼 전부. 보여 주는 이름은 닉네임 하나다.
 * 로그인은 소셜로만 한다 — 로그인 아이디 · 비밀번호가 없다(2026-09-26 소유자 결정).
 *
 * <p>id 를 DB 가 채번하므로({@code IDENTITY}) 새 엔티티는 id 가 {@code null} 이고 {@code save()} 가 {@code persist} 로 간다 — 반드시 INSERT 가 나가고,
 * 닉네임의 중복은 DB 의 UNIQUE 가 막는다 (CLAUDE.md §5 "불변식은 DB가 강제한다").
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nickname", nullable = false, length = 16)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(String nickname, Instant now)
    {
        this.nickname = nickname;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeNickname(String nickname, Instant now)
    {
        this.nickname = nickname;
        this.updatedAt = now;
    }
}
