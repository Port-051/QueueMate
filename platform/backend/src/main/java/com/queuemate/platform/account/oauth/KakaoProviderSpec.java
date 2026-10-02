package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 카카오. 사용자 정보({@code /v2/user/me})의 {@code id} 는 <b>숫자</b>다. 닉네임은 {@code kakao_account.profile.nickname} 에 오고,
 * 옛 모양인 {@code properties.nickname} 에만 오기도 한다 — 둘 다 본다. 동의하지 않았으면 둘 다 없다.
 */
@Component
public class KakaoProviderSpec implements OAuthProviderSpec {

    @Override
    public SocialProvider provider()
    {
        return SocialProvider.KAKAO;
    }

    @Override
    public String scope()
    {
        return "profile_nickname";
    }

    @Override
    public OAuthUser parseUser(JsonNode userInfo)
    {
        JsonNode id = userInfo.path("id");
        if(!id.isIntegralNumber())
        {
            throw new OAuthException("카카오의 사용자 정보에 id(숫자)가 없다");
        }
        String nickname = JsonText.of(userInfo.path("kakao_account").path("profile").path("nickname"));
        if(nickname == null)
        {
            nickname = JsonText.of(userInfo.path("properties").path("nickname"));
        }
        return new OAuthUser(Long.toString(id.longValue()), nickname);
    }
}
