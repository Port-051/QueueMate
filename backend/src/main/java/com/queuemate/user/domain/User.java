package com.queuemate.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** V1__init_schema.sql의 users 테이블과 1:1로 대응한다. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // 이메일을 주지 않는 소셜 제공자가 있어 null이 허용된다 (V4).
    @Column(name = "email", length = 255)
    private String email;

    // 소셜로만 가입한 사용자는 비밀번호가 없다 (V4).
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 16)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // updated_at은 DB 트리거가 갱신하므로 애플리케이션에서 쓰지 않는다.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected User() {
    }

    private User(UUID id, String email, String passwordHash, String nickname) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.status = UserStatus.ACTIVE;
    }

    public static User create(String email, String passwordHash, String nickname) {
        return new User(UUID.randomUUID(), email, passwordHash, nickname);
    }

    /** 소셜 로그인으로만 만들어진 계정. 비밀번호가 없고 이메일도 없을 수 있다. */
    public static User createSocial(String email, String nickname) {
        return new User(UUID.randomUUID(), email, null, nickname);
    }

    /** 비밀번호로 로그인할 수 있는 계정인가. 소셜 전용 계정은 false다. */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UserStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
