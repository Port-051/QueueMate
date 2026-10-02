package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제공자마다 다른 사용자 정보 JSON 에서 회원 번호와 닉네임을 꺼내는 법. 스프링을 띄우지 않는다.
 * JSON 의 모양은 각 제공자의 문서에 있는 응답 예시에서 필요한 칸만 남긴 것이다(구글은 OpenID Connect 의 userinfo).
 */
class OAuthProviderSpecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KakaoProviderSpec kakao = new KakaoProviderSpec();
    private final DiscordProviderSpec discord = new DiscordProviderSpec();
    private final GoogleProviderSpec google = new GoogleProviderSpec();

    @Test
    @DisplayName("카카오 — id 는 숫자다. 닉네임은 kakao_account.profile.nickname, 없으면 properties.nickname")
    void kakao()
    {
        assertThat(kakao.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(kakao.scope()).isEqualTo("profile_nickname");

        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 1234567890123, "connected_at": "2026-01-01T00:00:00Z",
                 "properties": {"nickname": "옛 자리"},
                 "kakao_account": {"profile_nickname_needs_agreement": false, "profile": {"nickname": "홍길동"}}}
                """))).isEqualTo(new OAuthUser("1234567890123", "홍길동"));
        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 42, "properties": {"nickname": "옛 자리"}}
                """))).isEqualTo(new OAuthUser("42", "옛 자리"));
        // 닉네임 제공에 동의하지 않았다
        assertThat(kakao.parseUser(objectMapper.readTree("{\"id\": 42, \"kakao_account\": {}}")))
                .isEqualTo(new OAuthUser("42", null));

        assertThatThrownBy(() -> kakao.parseUser(objectMapper.readTree("{\"properties\": {\"nickname\": \"x\"}}")))
                .isInstanceOf(OAuthException.class);
        // 문자열 id 는 카카오의 것이 아니다
        assertThatThrownBy(() -> kakao.parseUser(objectMapper.readTree("{\"id\": \"42\"}")))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("디스코드 — id 는 문자열이다. 닉네임은 global_name, 없으면 username")
    void discord()
    {
        assertThat(discord.provider()).isEqualTo(SocialProvider.DISCORD);
        assertThat(discord.scope()).isEqualTo("identify");

        assertThat(discord.parseUser(objectMapper.readTree("""
                {"id": "80351110224678912", "username": "nelly", "global_name": "Nelly", "discriminator": "0"}
                """))).isEqualTo(new OAuthUser("80351110224678912", "Nelly"));
        assertThat(discord.parseUser(objectMapper.readTree("""
                {"id": "80351110224678912", "username": "nelly", "global_name": null}
                """))).isEqualTo(new OAuthUser("80351110224678912", "nelly"));

        assertThatThrownBy(() -> discord.parseUser(objectMapper.readTree("{\"username\": \"nelly\"}")))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("구글 — sub 는 문자열이다(숫자로 와도 받지 않는다). 닉네임은 name, 없으면 null. scope 는 openid profile(이메일 없음)")
    void google()
    {
        assertThat(google.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(google.scope()).isEqualTo("openid profile");

        assertThat(google.parseUser(objectMapper.readTree("""
                {"sub": "110169484474386276334", "name": "홍길동", "given_name": "길동", "family_name": "홍",
                 "picture": "https://lh3.googleusercontent.com/a/x", "locale": "ko"}
                """))).isEqualTo(new OAuthUser("110169484474386276334", "홍길동"));
        // 이름을 비워 둔 계정 — given_name 으로 채우지 않는다
        assertThat(google.parseUser(objectMapper.readTree("{\"sub\": \"110169484474386276334\", \"given_name\": \"길동\"}")))
                .isEqualTo(new OAuthUser("110169484474386276334", null));

        assertThatThrownBy(() -> google.parseUser(objectMapper.readTree("{\"name\": \"홍길동\"}")))
                .isInstanceOf(OAuthException.class);
        assertThatThrownBy(() -> google.parseUser(objectMapper.readTree("{\"sub\": 110169484474386276334}")))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("미리 채울 닉네임 —앞뒤 공백을 걷고 16자로 자른다. 두 칸짜리 글자의 가운데를 자르지 않는다. 남는 것이 없으면 null")
    void suggestedNickname()
    {
        assertThat(SocialSignupTokens.suggestedNickname("  홍길동  ")).isEqualTo("홍길동");
        assertThat(SocialSignupTokens.suggestedNickname("a".repeat(17))).isEqualTo("a".repeat(16));
        assertThat(SocialSignupTokens.suggestedNickname("a".repeat(15) + "😀")).isEqualTo("a".repeat(15));
        assertThat(SocialSignupTokens.suggestedNickname("a".repeat(14) + "😀")).isEqualTo("a".repeat(14) + "😀");
        assertThat(SocialSignupTokens.suggestedNickname("   ")).isNull();
        assertThat(SocialSignupTokens.suggestedNickname(null)).isNull();
    }
}
