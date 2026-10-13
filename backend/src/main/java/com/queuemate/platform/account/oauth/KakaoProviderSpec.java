package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 카카오. 사용자 정보({@code /v2/user/me})의 {@code id} 는 <b>숫자</b>다. 닉네임은 {@code kakao_account.profile.nickname} 에 오고,
 * 옛 모양인 {@code properties.nickname} 에만 오기도 한다 — 둘 다 본다. 동의하지 않았으면 둘 다 없다.
 *
 * <p><b>이메일</b>(2026-10-13 소유자 결정 — P-58) — scope {@code account_email}(동의 항목 "카카오계정(이메일)" · <b>선택 동의</b>) 로 청하고
 * {@code kakao_account.email} 을 읽는다. <b>{@code is_email_valid} 와 {@code is_email_verified} 가 둘 다 {@code true} 일 때만</b> 든다 —
 * 하나라도 아니거나 · {@code email_needs_agreement} 가 {@code true}(아직 동의하지 않았다)거나 · 칸이 없으면 {@code null} 이다.
 * <b>이 칸은 소유자가 카카오 콘솔에서 앱을 "개인 개발자 비즈 앱" 으로 전환하고 동의 항목을 켠 뒤에만 온다</b> — 그 전에는 scope 를 청해도
 * 카카오가 응답에 칸을 넣지 않는다(동의 화면에도 안 보인다). 그래서 칸이 있다고 가정하지 않는다 — 없으면 {@code null} 이고 로그인은 그대로 된다.
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
        return "profile_nickname account_email";
    }

    @Override
    public OAuthUser parseUser(JsonNode userInfo)
    {
        JsonNode id = userInfo.path("id");
        if(!id.isIntegralNumber())
        {
            throw new OAuthException("카카오의 사용자 정보에 id(숫자)가 없다");
        }
        JsonNode account = userInfo.path("kakao_account");
        String nickname = JsonText.of(account.path("profile").path("nickname"));
        if(nickname == null)
        {
            nickname = JsonText.of(userInfo.path("properties").path("nickname"));
        }
        return new OAuthUser(Long.toString(id.longValue()), nickname, verifiedEmail(account));
    }

    private static String verifiedEmail(JsonNode account)
    {
        if(JsonText.isTrue(account.path("email_needs_agreement"))
                || !JsonText.isTrue(account.path("is_email_valid"))
                || !JsonText.isTrue(account.path("is_email_verified")))
        {
            return null;
        }
        return JsonText.email(account.path("email"));
    }
}
