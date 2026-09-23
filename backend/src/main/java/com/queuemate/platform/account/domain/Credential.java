package com.queuemate.platform.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * 비밀번호 해시. 사용자와 1:1 이고 {@code users} 와 떼어 둔다 — 프로필 조회가 해시를 같이 끌고 다니지 않게 한다.
 * {@code passwordHash} 는 {@code {bcrypt}…} 처럼 방식의 접두사가 붙은 값이다. <b>로그 · 응답에 싣지 않는다.</b>
 *
 * <p><b>{@link Persistable} 을 구현한 이유.</b> PK({@code userId} = 사용자 번호)를 직접 주는 엔티티는 Spring Data 가 "새것인지"를 알 수 없어
 * {@code save()} 가 {@code merge} 로 빠진다 — SELECT 로 있는지 보고 없으면 INSERT 하는 {@code 조회 → 판단 → 삽입}이다.
 * {@link #isNew()} 가 {@code true} 면 {@code persist} 로 가서 <b>반드시 INSERT 가 나간다.</b>
 */
@Entity
@Table(schema = "account", name = "credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credential implements Persistable<Long> {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    public Credential(Long userId, String passwordHash, Instant now)
    {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    @Override
    public Long getId()
    {
        return userId;
    }

    @Override
    public boolean isNew()
    {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew()
    {
        this.isNew = false;
    }

    /** 해시가 실수로 로그에 찍히지 않게 한다 */
    @Override
    public String toString()
    {
        return "Credential[userId=" + userId + "]";
    }
}
