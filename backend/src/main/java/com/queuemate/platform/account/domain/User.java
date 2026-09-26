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
 * 사용자. 식별자가 둘이다 (2026-09-22 소유자 결정 — CLAUDE.md §3.5 · {@code contracts/platform-api.md} "계정").
 * <ul>
 *   <li>{@code id} — <b>사용자 번호</b>(bigint identity). 시스템 안팎에서 쓰는 {@code userId} 가 이것이다 — JWT 의 {@code sub}, 알림 채널,
 *       URL, 요청 · 응답 본문, 다른 테이블의 {@code *_id} 컬럼 전부</li>
 *   <li>{@code loginId} — 가입할 때 정한 <b>로그인 아이디</b>. 로그인할 때만 쓴다. 바꿀 수 없다</li>
 * </ul>
 *
 * <p>id 를 DB 가 채번하므로({@code IDENTITY}) 새 엔티티는 id 가 {@code null} 이고 {@code save()} 가 {@code persist} 로 간다 — 반드시 INSERT 가 나가고,
 * 중복(로그인 아이디 · 닉네임)은 DB 의 UNIQUE 가 막는다 (CLAUDE.md §5 "불변식은 DB가 강제한다"). {@code Persistable} 이 필요 없어졌다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /** 로그인 아이디의 형식. DB 의 CHECK({@code users_login_id_format})와 같아야 한다 */
    public static final String LOGIN_ID_PATTERN = "^[a-z0-9_]{4,20}$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "login_id", nullable = false, updatable = false, length = 20)
    private String loginId;

    @Column(name = "nickname", nullable = false, length = 16)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(String loginId, String nickname, Instant now)
    {
        this.loginId = loginId;
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
