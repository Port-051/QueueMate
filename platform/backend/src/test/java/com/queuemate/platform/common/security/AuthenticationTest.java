package com.queuemate.platform.common.security;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.account.domain.SocialProvider;
import com.queuemate.platform.account.oauth.OAuthUser;
import com.queuemate.platform.account.oauth.SocialSignupTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * access 토큰의 검증 — 쿠키에서 꺼내는지, 무엇을 거절하는지. 거절은 전부 같은 401 {@code UNAUTHENTICATED} 다.
 *
 * <p>{@code sub} 는 <b>사용자 번호를 문자열로 찍은 것</b>이다({@code "42"} — {@link TokenClaims#SUBJECT_PATTERN}).
 * 로그인 아이디는 가입 · 로그인 본문({@code nickname})에만 나온다.
 */
class AuthenticationTest extends ApiTestSupport {

    /** 앱이 서명에 쓰는 바로 그 인코더다 — 서명은 멀쩡한데 클레임이 다른 토큰을 만들어 본다 */
    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    SocialSignupTokens socialSignupTokens;

    @Test
    @DisplayName("쿠키가 없으면 401 UNAUTHENTICATED 이고 에러 본문은 계약의 모양이다")
    void noCookie() throws Exception
    {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("깨진 쿠키 · 남의 키로 서명한 토큰은 401 이다")
    void brokenOrForgedCookie() throws Exception
    {
        expectUnauthenticated(me(new Cookie("qm_access", "not-a-jwt")));

        // 클레임은 멀쩡하지만 다른 키로 서명했다
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
                .privateKey(pair.getPrivate()).keyID("dev-1").build();
        JwtEncoder forger = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk)));
        expectUnauthenticated(me(new Cookie("qm_access", token(forger, claims -> { }))));
    }

    @Test
    @DisplayName("sub 가 사용자 번호가 아니면 401 이다 — 로그인 아이디를 sub 에 담은 토큰은 통하지 않는다")
    void subjectMustBeUserNumber() throws Exception
    {
        String nickname = newNickname();
        login(nickname);
        Long userId = userIdOf(nickname);

        // 서명 · iss · exp · token_use 가 전부 멀쩡한데 sub 만 로그인 아이디다 (2026-09-22 전의 모양이다)
        expectUnauthenticated(me(new Cookie("qm_access", token(jwtEncoder, claims -> claims.subject(nickname)))));
        // 사용자 번호를 담으면 통한다 — 위가 다른 이유로 거절된 것이 아니다
        me(new Cookie("qm_access", token(jwtEncoder, claims -> claims.subject(String.valueOf(userId)))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("서명이 멀쩡해도 token_use 가 access 가 아니면 401 이다 — 소셜 가입 대기 토큰 · 옛 입장권을 access 토큰으로 쓸 수 없다")
    void wrongTokenUse() throws Exception
    {
        String nickname = newNickname();
        login(nickname);
        Long userId = userIdOf(nickname);

        // 같은 키 · 같은 iss · 같은 sub 인데 쓰임새만 다르다 — 소셜 가입 대기 토큰이 같은 키로 서명된다
        expectUnauthenticated(me(new Cookie("qm_access", token(jwtEncoder,
                claims -> claims.subject(String.valueOf(userId)).claim("token_use", "social_signup")))));
        // 옛 입장권(room_ticket — 2026-09-25 2단계로 없어졌다)의 모양도 그대로 거절된다
        expectUnauthenticated(me(new Cookie("qm_access", token(jwtEncoder,
                claims -> claims.subject(String.valueOf(userId)).claim("token_use", "room_ticket")))));
        // token_use 가 아예 없다
        expectUnauthenticated(me(new Cookie("qm_access", token(jwtEncoder,
                claims -> claims.subject(String.valueOf(userId)).claims(map -> map.remove("token_use"))))));
        // 같은 방식으로 만든 access 토큰은 통한다 — 위 둘이 다른 이유로 거절된 것이 아니다
        me(new Cookie("qm_access", token(jwtEncoder, claims -> claims.subject(String.valueOf(userId)))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("qm_social_signup 의 토큰은 access 토큰으로 통하지 않고, access 토큰은 qm_social_signup 으로 통하지 않는다")
    void socialSignupTokenIsNotAnAccessToken() throws Exception
    {
        String nickname = newNickname();
        Cookie access = login(nickname);
        Long userId = userIdOf(nickname);
        String socialSignupToken = socialSignupTokens.issue(SocialProvider.KAKAO, new OAuthUser("12345", "닉네임"));

        // 같은 키로 서명했지만 token_use 가 social_signup 이다 — sub 를 억지로 넣어 봐도 마찬가지다
        expectUnauthenticated(me(new Cookie("qm_access", socialSignupToken)));
        expectUnauthenticated(me(new Cookie("qm_access", token(jwtEncoder,
                claims -> claims.subject(String.valueOf(userId)).claim("token_use", "social_signup")))));

        // 반대 방향 — 로그인한 사람의 access 토큰을 qm_social_signup 에 넣어도 가입을 기다리는 것으로 쳐 주지 않는다
        assertThat(socialSignupTokens.verify(access.getValue())).isEmpty();
        mockMvc.perform(get("/api/v1/auth/social/pending").cookie(new Cookie("qm_social_signup", access.getValue())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_PENDING_SOCIAL_SIGNUP"));
        // access 의 모양에 provider 클레임을 얹어 봐도 token_use 가 access 라서 거절된다
        String dressedUp = token(jwtEncoder, claims -> claims.subject(String.valueOf(userId))
                .claim("provider", "KAKAO").claim("provider_user_id", "12345"));
        assertThat(socialSignupTokens.verify(dressedUp)).isEmpty();
        mockMvc.perform(post("/api/v1/auth/social/signup").cookie(new Cookie("qm_social_signup", dressedUp))
                        .contentType("application/json")
                        .content(json("nickname", newNickname(), "nickname", "n_" + nickname.substring(2))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_PENDING_SOCIAL_SIGNUP"));

        // 제대로 된 토큰은 통한다 — 위가 다른 이유로 거절된 것이 아니다
        assertThat(socialSignupTokens.verify(socialSignupToken)).hasValueSatisfying(pending -> {
            assertThat(pending.provider()).isEqualTo(SocialProvider.KAKAO);
            assertThat(pending.providerUserId()).isEqualTo("12345");
            assertThat(pending.suggestedNickname()).isEqualTo("닉네임");
        });
        // 만료된 것은 안 된다
        Instant past = Instant.now().minusSeconds(3600);
        assertThat(socialSignupTokens.verify(token(jwtEncoder, claims -> claims
                .claim("token_use", "social_signup").claim("provider", "KAKAO").claim("provider_user_id", "12345")
                .issuedAt(past.minusSeconds(60)).expiresAt(past)))).isEmpty();
    }

    @Test
    @DisplayName("iss 가 다르거나 만료된 토큰은 401 이다")
    void wrongIssuerOrExpired() throws Exception
    {
        String nickname = newNickname();
        login(nickname);
        String subject = String.valueOf(userIdOf(nickname));

        expectUnauthenticated(me(new Cookie("qm_access",
                token(jwtEncoder, claims -> claims.subject(subject).issuer("someone-else")))));
        // 디코더가 시계 오차로 60초를 봐준다 — 그보다 넉넉히 지난 것으로 만든다
        Instant past = Instant.now().minusSeconds(3600);
        expectUnauthenticated(me(new Cookie("qm_access",
                token(jwtEncoder, claims -> claims.subject(subject).issuedAt(past.minusSeconds(60)).expiresAt(past)))));
    }

    @Test
    @DisplayName("Authorization 헤더의 토큰은 받지 않는다 — 쿠키로만 받는다")
    void authorizationHeaderIsIgnored() throws Exception
    {
        Cookie cookie = login(newNickname());

        expectUnauthenticated(mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue())));
    }

    @Test
    @DisplayName("깨진 쿠키를 든 채로도 재발급 · 로그아웃은 된다 — 토큰이 만료된 사용자가 다시 로그인할 수 있어야 한다")
    void brokenCookieDoesNotBlockAuthEndpoints() throws Exception
    {
        Cookie broken = new Cookie("qm_access", "not-a-jwt");
        Cookie refresh = refreshCookieFor(newNickname());

        // 재발급이 준 새 refresh 는 끝에 Redis 에서 지운다(테스트가 자기 키만 치운다 — ApiTestSupport)
        refreshCookieOf(mockMvc.perform(post("/api/v1/auth/refresh").cookie(broken, refresh))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/v1/auth/logout").cookie(broken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("health 는 인증 없이 열려 있다. 깨진 쿠키가 있어도 그렇다")
    void healthIsOpen() throws Exception
    {
        mockMvc.perform(get("/health/live")).andExpect(status().isOk());
        mockMvc.perform(get("/health/ready").cookie(new Cookie("qm_access", "not-a-jwt")))
                .andExpect(status().isOk())
                // readiness 에 DB 가 들어 있다 (application.yaml)
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("로그인한 사용자가 모르는 경로를 부르면 404 NOT_FOUND 다")
    void unknownPath() throws Exception
    {
        Cookie cookie = login(newNickname());

        mockMvc.perform(get("/api/v1/no-such-thing").cookie(cookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray());
    }

    private ResultActions me(Cookie cookie) throws Exception
    {
        return mockMvc.perform(get("/api/v1/users/me").cookie(cookie));
    }

    private static void expectUnauthenticated(ResultActions actions) throws Exception
    {
        actions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /**
     * 계약대로의 access 토큰을 바탕으로 두고 {@code customizer} 가 일부만 바꾼다.
     * 바탕의 {@code sub} 는 아무 사용자 번호다 — 그 사람으로 통해야 하는 테스트는 {@code customizer} 에서 제 번호를 넣는다.
     */
    private static String token(JwtEncoder encoder, Consumer<JwtClaimsSet.Builder> customizer)
    {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(TokenClaims.ISSUER)
                .subject("0")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_ACCESS);
        customizer.accept(claims);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("dev-1").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
