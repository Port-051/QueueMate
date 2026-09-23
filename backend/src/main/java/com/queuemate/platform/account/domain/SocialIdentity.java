package com.queuemate.platform.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
 * 소셜 계정과 사용자의 연결. <b>제공자에게서 받아 남기는 것은 회원 번호뿐이다</b> — 제공자의 access token 도 이메일도 저장하지 않는다
 * ({@code contracts/platform-api.md} "소셜 로그인").
 *
 * <p>{@link Persistable} 인 이유는 {@link Credential} 과 같다 — id 를 직접 주는 엔티티라 {@code save()} 가 {@code merge}(조회 → 판단 → 삽입)로
 * 빠지지 않게 한다. 반드시 INSERT 가 나가고, 같은 소셜 계정의 두 번째 연결은 PK({@code social_identities_pkey})가 막는다.
 */
@Entity
@Table(schema = "account", name = "social_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialIdentity implements Persistable<SocialIdentityId> {

    @EmbeddedId
    private SocialIdentityId id;

    /** 사용자 번호({@code account.users.id}). 제공자의 회원 번호({@code id.providerUserId})와 다른 것이다 */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    public SocialIdentity(SocialProvider provider, String providerUserId, Long userId, Instant now)
    {
        this.id = new SocialIdentityId(provider, providerUserId);
        this.userId = userId;
        this.createdAt = now;
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
}
