package com.queuemate.platform.account.domain;

import java.util.Optional;

/**
 * 소셜 로그인 제공자 ({@code contracts/platform-api.md} "소셜 로그인"). 구글은 2026-09-29 소유자 결정으로 더했다(P-33). 이름(대문자)이 DB 의 {@code social_identities.provider} · 응답의 {@code socialProviders} ·
 * <b>경로({@code /auth/oauth/{provider}/…})</b> 에 그대로 나간다 — 경로는 스프링의 enum 변환이 받으므로 소문자 · 모르는 이름은 400 이다
 * (게시판 목록의 {@code game} 과 같다. 2026-09-26 소유자 지시 — 소문자 이름을 따로 두던 것을 없앴다). 제공자에 등록하는 Redirect URI 도 대문자다.
 * DB 의 CHECK({@code social_identities_provider_check})도 같은 이름을 건다 — 값을 더하면 새 마이그레이션으로 그 CHECK 도 넓힌다(구글은 V3).
 */
public enum SocialProvider {

    KAKAO,
    DISCORD,
    GOOGLE;

    /** 토큰의 클레임에 실린 이름(대문자)을 찾는다 */
    public static Optional<SocialProvider> fromName(String name)
    {
        for(SocialProvider provider : values())
        {
            if(provider.name().equals(name))
            {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
