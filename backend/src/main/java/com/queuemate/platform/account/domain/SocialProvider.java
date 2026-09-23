package com.queuemate.platform.account.domain;

import java.util.Optional;

/**
 * 소셜 로그인 제공자 ({@code contracts/platform-api.md} "소셜 로그인"). 이름(대문자)은 DB 의 {@code social_identities.provider} 와
 * 응답의 {@code socialProviders} 에, 소문자 이름은 경로({@code /auth/oauth/{provider}/…})에 나간다.
 * DB 의 CHECK({@code social_identities_provider_check})도 같은 두 이름을 건다.
 */
public enum SocialProvider {

    KAKAO("kakao"),
    DISCORD("discord");

    private final String pathName;

    SocialProvider(String pathName)
    {
        this.pathName = pathName;
    }

    public String pathName()
    {
        return pathName;
    }

    /** 경로 변수로 들어온 이름을 찾는다. 대소문자를 봐주지 않는다 — 제공자에 등록한 Redirect URI 와 글자 그대로 같아야 한다 */
    public static Optional<SocialProvider> fromPathName(String pathName)
    {
        for(SocialProvider provider : values())
        {
            if(provider.pathName.equals(pathName))
            {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

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
