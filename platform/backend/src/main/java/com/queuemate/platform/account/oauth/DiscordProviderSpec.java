package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 디스코드. 사용자 정보({@code /users/@me})의 {@code id} 는 <b>문자열</b>이다(snowflake — 숫자로 읽으면 자바스크립트 쪽에서 깨지는 크기다).
 * 닉네임은 표시 이름 {@code global_name} 이고, 정하지 않은 사람은 {@code null} 이라 {@code username} 으로 받는다.
 *
 * <p><b>이메일</b>(2026-10-13 소유자 결정 — P-58) — scope {@code email} 을 더 청하면 {@code /users/@me} 에 {@code email} 과 {@code verified} 가 온다.
 * <b>{@code verified} 가 {@code true} 일 때만</b> 든다 — 아니거나 칸이 없으면 {@code null} 이다(디스코드 콘솔의 OAuth2 scope 에 {@code email} 을 켜는 것은 소유자의 일).
 */
@Component
public class DiscordProviderSpec implements OAuthProviderSpec {

    @Override
    public SocialProvider provider()
    {
        return SocialProvider.DISCORD;
    }

    @Override
    public String scope()
    {
        return "identify email";
    }

    @Override
    public OAuthUser parseUser(JsonNode userInfo)
    {
        String id = JsonText.of(userInfo.path("id"));
        if(id == null)
        {
            throw new OAuthException("디스코드의 사용자 정보에 id(문자열)가 없다");
        }
        String nickname = JsonText.of(userInfo.path("global_name"));
        if(nickname == null)
        {
            nickname = JsonText.of(userInfo.path("username"));
        }
        String email = JsonText.isTrue(userInfo.path("verified")) ? JsonText.email(userInfo.path("email")) : null;
        return new OAuthUser(id, nickname, email);
    }
}
