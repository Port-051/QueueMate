package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제공자마다 다른 사용자 정보 JSON 에서 회원 번호 · 닉네임 · 검증된 이메일을 꺼내는 법. 스프링을 띄우지 않는다.
 * JSON 의 모양은 각 제공자의 문서에 있는 응답 예시에서 필요한 칸만 남긴 것이다(구글은 OpenID Connect 의 userinfo).
 *
 * <p>이메일(2026-10-13 소유자 결정 — P-58)은 <b>제공자가 검증됐다고 말한 값만</b> 든다 — 구글 {@code email_verified} · 디스코드 {@code verified} ·
 * 카카오 {@code is_email_valid && is_email_verified}(그리고 {@code email_needs_agreement} 가 아니어야). 아니면 {@code null} 이고 회원 번호만 있으면 성공이다.
 */
class OAuthProviderSpecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KakaoProviderSpec kakao = new KakaoProviderSpec();
    private final DiscordProviderSpec discord = new DiscordProviderSpec();
    private final GoogleProviderSpec google = new GoogleProviderSpec();

    @Test
    @DisplayName("카카오 — id 는 숫자다. 닉네임은 kakao_account.profile.nickname, 없으면 properties.nickname. scope 에 account_email")
    void kakao()
    {
        assertThat(kakao.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(kakao.scope()).isEqualTo("profile_nickname account_email");

        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 1234567890123, "connected_at": "2026-01-01T00:00:00Z",
                 "properties": {"nickname": "옛 자리"},
                 "kakao_account": {"profile_nickname_needs_agreement": false, "profile": {"nickname": "홍길동"}}}
                """))).isEqualTo(new OAuthUser("1234567890123", "홍길동", null));
        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 42, "properties": {"nickname": "옛 자리"}}
                """))).isEqualTo(new OAuthUser("42", "옛 자리", null));
        // 닉네임 제공에 동의하지 않았다
        assertThat(kakao.parseUser(objectMapper.readTree("{\"id\": 42, \"kakao_account\": {}}")))
                .isEqualTo(new OAuthUser("42", null, null));

        assertThatThrownBy(() -> kakao.parseUser(objectMapper.readTree("{\"properties\": {\"nickname\": \"x\"}}")))
                .isInstanceOf(OAuthException.class);
        // 문자열 id 는 카카오의 것이 아니다
        assertThatThrownBy(() -> kakao.parseUser(objectMapper.readTree("{\"id\": \"42\"}")))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("카카오 이메일 — is_email_valid 와 is_email_verified 가 둘 다 true 일 때만. 하나라도 아니거나 · 동의 전이거나 · 칸이 없으면 null")
    void kakaoEmail()
    {
        // 카카오 문서의 예시 — 비즈 앱에서 동의한 사람
        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 42, "kakao_account": {"profile": {"nickname": "홍길동"},
                 "has_email": true, "email_needs_agreement": false, "is_email_valid": true, "is_email_verified": true,
                 "email": "sample@sample.com"}}
                """))).isEqualTo(new OAuthUser("42", "홍길동", "sample@sample.com"));
        // 검증되지 않은 이메일 — 값이 와도 버린다
        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 42, "kakao_account": {"is_email_valid": true, "is_email_verified": false, "email": "sample@sample.com"}}
                """)).email()).isNull();
        // 유효하지 않은 이메일(다른 카카오계정으로 옮겨 간 주소)
        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 42, "kakao_account": {"is_email_valid": false, "is_email_verified": true, "email": "sample@sample.com"}}
                """)).email()).isNull();
        // 아직 동의하지 않았다(선택 동의를 건너뛰었다) — 카카오는 이때 email 칸을 주지 않지만 줘도 들지 않는다
        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 42, "kakao_account": {"has_email": true, "email_needs_agreement": true, "is_email_valid": true, "is_email_verified": true, "email": "x@y.z"}}
                """)).email()).isNull();
        // 플래그만 있고 이메일 칸이 없다 · 플래그가 문자열 "true" 다 — 둘 다 null
        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 42, "kakao_account": {"is_email_valid": true, "is_email_verified": true}}
                """)).email()).isNull();
        assertThat(kakao.parseUser(objectMapper.readTree("""
                {"id": 42, "kakao_account": {"is_email_valid": "true", "is_email_verified": "true", "email": "x@y.z"}}
                """)).email()).isNull();
    }

    @Test
    @DisplayName("디스코드 — id 는 문자열이다. 닉네임은 global_name, 없으면 username. 이메일은 verified 가 true 일 때만. scope 에 email")
    void discord()
    {
        assertThat(discord.provider()).isEqualTo(SocialProvider.DISCORD);
        assertThat(discord.scope()).isEqualTo("identify email");

        assertThat(discord.parseUser(objectMapper.readTree("""
                {"id": "80351110224678912", "username": "nelly", "global_name": "Nelly", "discriminator": "0"}
                """))).isEqualTo(new OAuthUser("80351110224678912", "Nelly", null));
        assertThat(discord.parseUser(objectMapper.readTree("""
                {"id": "80351110224678912", "username": "nelly", "global_name": null}
                """))).isEqualTo(new OAuthUser("80351110224678912", "nelly", null));
        // 디스코드 문서의 예시 — verified: true 면 든다
        assertThat(discord.parseUser(objectMapper.readTree("""
                {"id": "80351110224678912", "username": "nelly", "global_name": "Nelly", "verified": true, "email": "nelly@discord.com"}
                """))).isEqualTo(new OAuthUser("80351110224678912", "Nelly", "nelly@discord.com"));
        // verified: false · verified 없음 — 값이 와도 null
        assertThat(discord.parseUser(objectMapper.readTree("""
                {"id": "80351110224678912", "username": "nelly", "verified": false, "email": "nelly@discord.com"}
                """)).email()).isNull();
        assertThat(discord.parseUser(objectMapper.readTree("""
                {"id": "80351110224678912", "username": "nelly", "email": "nelly@discord.com"}
                """)).email()).isNull();

        assertThatThrownBy(() -> discord.parseUser(objectMapper.readTree("{\"username\": \"nelly\"}")))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("구글 — sub 는 문자열이다(숫자로 와도 받지 않는다). 닉네임은 name, 없으면 null. 이메일은 email_verified 가 true 일 때만. scope 는 openid profile email")
    void google()
    {
        assertThat(google.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(google.scope()).isEqualTo("openid profile email");

        assertThat(google.parseUser(objectMapper.readTree("""
                {"sub": "110169484474386276334", "name": "홍길동", "given_name": "길동", "family_name": "홍",
                 "picture": "https://lh3.googleusercontent.com/a/x", "locale": "ko"}
                """))).isEqualTo(new OAuthUser("110169484474386276334", "홍길동", null));
        // 이름을 비워 둔 계정 — given_name 으로 채우지 않는다
        assertThat(google.parseUser(objectMapper.readTree("{\"sub\": \"110169484474386276334\", \"given_name\": \"길동\"}")))
                .isEqualTo(new OAuthUser("110169484474386276334", null, null));
        // 구글 userinfo 의 예시 — email_verified: true 면 든다
        assertThat(google.parseUser(objectMapper.readTree("""
                {"sub": "110169484474386276334", "name": "홍길동", "email": "gildong@gmail.com", "email_verified": true}
                """))).isEqualTo(new OAuthUser("110169484474386276334", "홍길동", "gildong@gmail.com"));
        // email_verified: false · 없음 — null
        assertThat(google.parseUser(objectMapper.readTree("""
                {"sub": "110169484474386276334", "email": "gildong@gmail.com", "email_verified": false}
                """)).email()).isNull();
        assertThat(google.parseUser(objectMapper.readTree("""
                {"sub": "110169484474386276334", "email": "gildong@gmail.com"}
                """)).email()).isNull();

        assertThatThrownBy(() -> google.parseUser(objectMapper.readTree("{\"name\": \"홍길동\"}")))
                .isInstanceOf(OAuthException.class);
        assertThatThrownBy(() -> google.parseUser(objectMapper.readTree("{\"sub\": 110169484474386276334}")))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("이메일 칸 — 앞뒤 공백을 걷고, @ 가 없거나 320자를 넘으면 null 이다(DB 칸에 들어가지 않는 값을 미리 버린다)")
    void emailShape()
    {
        assertThat(JsonText.email(objectMapper.readTree("\"  a@b.c  \""))).isEqualTo("a@b.c");
        assertThat(JsonText.email(objectMapper.readTree("\"no-at-sign\""))).isNull();
        assertThat(JsonText.email(objectMapper.readTree("\"   \""))).isNull();
        assertThat(JsonText.email(objectMapper.readTree("12"))).isNull();
        assertThat(JsonText.email(objectMapper.readTree("\"" + "a".repeat(316) + "@b.cd\""))).isNull();
        assertThat(JsonText.email(objectMapper.readTree("\"" + "a".repeat(315) + "@b.cd\""))).hasSize(320);
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
