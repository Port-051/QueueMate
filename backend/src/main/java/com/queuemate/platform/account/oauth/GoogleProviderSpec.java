package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 구글(2026-09-29 소유자 결정 — {@code contracts/platform-api.md} P-33). 사용자 정보는 OpenID Connect 의 userinfo
 * ({@code https://openidconnect.googleapis.com/v1/userinfo})이고, 회원 번호는 {@code sub} — <b>문자열</b>이다
 * (구글 문서는 "계정마다 유일하고 다시 쓰이지 않는 255자까지의 ASCII" 라고만 약속한다 — 숫자로 읽지 않는다. 그래서 V3 가 칸을 255 로 넓혔다).
 * 닉네임은 표시 이름 {@code name} 이고, 없으면 {@code null} 이다.
 *
 * <p>scope 는 {@code openid profile} 이다 — OpenID Connect 의 userinfo 를 부르므로 {@code openid} 를 같이 청하고, 이름은 {@code profile} 에 있다.
 * <b>{@code email} 은 청하지 않는다</b> — 다른 제공자와 같이 회원 번호와 닉네임만 받는다. 토큰 응답에 같이 오는 {@code id_token} 은 읽지 않는다
 * (회원 번호는 userinfo 에서 얻는다 — 흐름이 카카오 · 디스코드와 같다).
 *
 * <p><b>실제 구글로는 붙여 보지 않았다</b> — 가짜 제공자로만 테스트했다(소유자가 앱을 등록해야 한다).
 */
@Component
public class GoogleProviderSpec implements OAuthProviderSpec {

    @Override
    public SocialProvider provider()
    {
        return SocialProvider.GOOGLE;
    }

    @Override
    public String scope()
    {
        return "openid profile";
    }

    @Override
    public OAuthUser parseUser(JsonNode userInfo)
    {
        String sub = JsonText.of(userInfo.path("sub"));
        if(sub == null)
        {
            throw new OAuthException("구글의 사용자 정보에 sub(문자열)가 없다");
        }
        return new OAuthUser(sub, JsonText.of(userInfo.path("name")));
    }
}
