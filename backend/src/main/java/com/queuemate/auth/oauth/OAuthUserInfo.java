package com.queuemate.auth.oauth;

/**
 * 제공자에서 가져온 최소 정보.
 *
 * 필요한 것만 담는다(docs/13 PII). 이메일은 없을 수 있고, emailVerified가 false면
 * 기존 계정에 붙이는 근거로 쓰지 않는다.
 */
public record OAuthUserInfo(
        String providerUserId,
        String email,
        boolean emailVerified,
        String nickname,
        String avatarUrl
) {
    public boolean hasVerifiedEmail() {
        return emailVerified && email != null && !email.isBlank();
    }
}
