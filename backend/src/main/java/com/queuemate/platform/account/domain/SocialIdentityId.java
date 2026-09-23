package com.queuemate.platform.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** {@link SocialIdentity} 의 PK — (제공자, 제공자 쪽 회원 번호). 회원 번호는 카카오가 숫자, 디스코드가 문자열이라 문자열로 든다 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialIdentityId implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 10)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, updatable = false, length = 64)
    private String providerUserId;

    public SocialIdentityId(SocialProvider provider, String providerUserId)
    {
        this.provider = provider;
        this.providerUserId = providerUserId;
    }
}
