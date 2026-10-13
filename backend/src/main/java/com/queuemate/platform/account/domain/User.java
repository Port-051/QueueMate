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

    /**
     * 소셜 제공자가 <b>검증했다고 말한</b> 이메일(2026-10-13 소유자 결정 — P-58 · V13). 선택이라 {@code null} 일 수 있다 — 동의하지 않았거나 ·
     * 제공자가 주지 않았거나 · 개발용 로그인이다. <b>식별자가 아니다</b>(UNIQUE 없음) — 이것으로 사용자를 찾거나 합치지 않는다.
     * 가입 때 적고 로그인 · 잇기 때 검증된 값이 오면 덮어쓴다({@code UserRepository#updateEmail}) — {@code null} 로는 덮어쓰지 않는다.
     */
    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(String nickname, String email, Instant now)
    {
        this.nickname = nickname;
        this.email = email;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeNickname(String nickname, Instant now)
    {
        this.nickname = nickname;
        this.updatedAt = now;
    }
}
