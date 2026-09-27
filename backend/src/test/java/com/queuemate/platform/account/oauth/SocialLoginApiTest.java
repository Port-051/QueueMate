package com.queuemate.platform.account.oauth;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.common.security.TokenClaims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소셜 로그인 — {@code contracts/platform-api.md} "소셜 로그인" 의 네 요청을 <b>가짜 제공자</b>({@link FakeOAuthProvider})에 붙여서 본다.
 * 진짜 카카오 · 디스코드를 부르지 않는다.
 *
 * <p>{@code @DynamicPropertySource} 가 설정을 바꾸므로 <b>이 클래스만 스프링 컨텍스트를 따로 띄운다</b>(다른 API 테스트는 하나를 같이 쓴다).
 * 제공자가 설정되지 않았을 때의 404 는 그 기본 컨텍스트에서 본다({@link OAuthNotConfiguredTest}).
 *
 * <p>가입 · 로그인은 소셜로만 한다(2026-09-26 소유자 결정). 소셜로 처음 온 사람은 <b>닉네임만</b> 정한다 — 가입 본문은 {@code {nickname}} 이고
 * 응답은 {@code {userId, nickname}} 이다. 제공자의 회원 번호는 {@code social_identities} 에만 있고 사용자 번호가 되지 않는다.
 *
 * <p>소셜 계정 잇기(로그인된 채 온 콜백) · 끊기({@code DELETE /api/v1/users/me/social/{provider}})도 여기서 본다(2026-09-27 소유자 결정 — P-27).
 */
class SocialLoginApiTest extends ApiTestSupport {

    static final FakeOAuthProvider FAKE = new FakeOAuthProvider();
    static final String FRONT = "http://front.test";
    static final String REDIRECT_BASE = "http://api.test";

    @DynamicPropertySource
    static void oauthProperties(DynamicPropertyRegistry registry)
    {
        // 끝 슬래시를 붙여 적어도 슬래시가 겹치지 않는지 같이 본다
        registry.add("platform.oauth.front-base-url", () -> FRONT + "/");
        registry.add("platform.oauth.redirect-base-url", () -> REDIRECT_BASE);
        for(String provider : new String[]{"kakao", "discord"})
        {
            registry.add("platform.oauth." + provider + ".client-id", () -> provider + "-client");
            registry.add("platform.oauth." + provider + ".authorize-uri", () -> FAKE.baseUrl() + "/" + provider + "/authorize");
            registry.add("platform.oauth." + provider + ".token-uri", () -> FAKE.baseUrl() + "/" + provider + "/token");
            registry.add("platform.oauth." + provider + ".user-info-uri", () -> FAKE.baseUrl() + "/" + provider + "/me");
        }
        // 카카오는 client secret 없이, 디스코드는 있게
        registry.add("platform.oauth.discord.client-secret", () -> "discord-secret");
    }

    @Autowired
    JwtEncoder jwtEncoder;

    @AfterAll
    static void stopFakeProvider()
    {
        FAKE.stop();
    }

    @Test
    @DisplayName("start 는 state 를 쿠키에 넣고 제공자의 동의 화면으로 302 한다")
    void start() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth/KAKAO/start"))
                .andExpect(status().isFound())
                .andReturn();

        UriComponents location = UriComponentsBuilder
                .fromUriString(result.getResponse().getHeader(HttpHeaders.LOCATION)).build();
        Map<String, String> query = location.getQueryParams().toSingleValueMap();
        assertThat(location.getPath()).isEqualTo("/kakao/authorize");
        assertThat(query.get("response_type")).isEqualTo("code");
        assertThat(query.get("client_id")).isEqualTo("kakao-client");
        assertThat(query.get("scope")).isEqualTo("profile_nickname");
        // 값 하나로 읽히게 퍼센트로 바뀌어 있다
        assertThat(query.get("redirect_uri")).isEqualTo("http%3A%2F%2Fapi.test%2Fapi%2Fv1%2Fauth%2Foauth%2FKAKAO%2Fcallback");

        Cookie stateCookie = result.getResponse().getCookie("qm_oauth_state");
        assertThat(stateCookie).isNotNull();
        // 32바이트의 base64url(패딩 없음)은 43자다
        assertThat(stateCookie.getValue()).matches("^[A-Za-z0-9_-]{43}$").isEqualTo(query.get("state"));
        assertThat(stateCookie.isHttpOnly()).isTrue();
        assertThat(stateCookie.getPath()).isEqualTo("/api/v1/auth/oauth");
        assertThat(stateCookie.getMaxAge()).isEqualTo(600);
        assertThat(stateCookie.getAttribute("SameSite")).isEqualTo("Lax");

        // 부를 때마다 새 값이다
        assertThat(startAndGetState("kakao").getValue()).isNotEqualTo(stateCookie.getValue());

        MvcResult discord = mockMvc.perform(get("/api/v1/auth/oauth/DISCORD/start")).andExpect(status().isFound()).andReturn();
        Map<String, String> discordQuery = UriComponentsBuilder
                .fromUriString(discord.getResponse().getHeader(HttpHeaders.LOCATION)).build()
                .getQueryParams().toSingleValueMap();
        assertThat(discordQuery.get("client_id")).isEqualTo("discord-client");
        assertThat(discordQuery.get("scope")).isEqualTo("identify");
    }

    @Test
    @DisplayName("모르는 제공자 · 소문자 이름은 400 VALIDATION_FAILED 다 — 경로의 이름은 대문자 enum 이다(game 과 같다)")
    void unknownProvider() throws Exception
    {
        mockMvc.perform(get("/api/v1/auth/oauth/GOOGLE/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 소문자는 받지 않는다 — 제공자에 등록한 Redirect URI 도 대문자다
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/start")).andExpect(status().isBadRequest());
        // 콜백도 같다 — 제공자가 그런 주소로 돌려보낼 일은 없다
        mockMvc.perform(get("/api/v1/auth/oauth/google/callback").param("code", "x").param("state", "y"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("카카오로 처음 온 사람 — callback → qm_social_signup + /signup/social → pending → signup 201 + qm_access → users/me")
    void firstVisitThenSignup() throws Exception
    {
        long kakaoId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        String code = newCode();
        FAKE.stubUser("kakao", code, "{\"id\":" + kakaoId + ",\"kakao_account\":{\"profile\":{\"nickname\":\"카카오 닉네임\"}}}");
        Cookie state = startAndGetState("kakao");

        MvcResult callback = callback("kakao", code, state.getValue(), state)
                .andExpect(status().isFound())
                .andReturn();

        assertThat(callback.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/signup/social");
        assertThat(callback.getResponse().getCookie("qm_access")).isNull();
        // state 쿠키는 쓴 뒤 지운다
        assertThat(callback.getResponse().getCookie("qm_oauth_state").getMaxAge()).isZero();
        Cookie signupCookie = callback.getResponse().getCookie("qm_social_signup");
        assertThat(signupCookie.isHttpOnly()).isTrue();
        assertThat(signupCookie.getPath()).isEqualTo("/api/v1/auth/social");
        assertThat(signupCookie.getMaxAge()).isEqualTo(600);
        assertThat(signupCookie.getAttribute("SameSite")).isEqualTo("Lax");
        // 토큰 요청 — form POST 이고, redirect_uri 가 인가 요청과 같고, 카카오는 client secret 이 없어 싣지 않았다
        Map<String, String> form = FAKE.lastTokenForm("kakao");
        assertThat(form.get("(method)")).isEqualTo("POST");
        assertThat(form.get("(content-type)")).startsWith("application/x-www-form-urlencoded");
        assertThat(form.get("grant_type")).isEqualTo("authorization_code");
        assertThat(form.get("client_id")).isEqualTo("kakao-client");
        assertThat(form.get("redirect_uri")).isEqualTo(REDIRECT_BASE + "/api/v1/auth/oauth/KAKAO/callback");
        assertThat(form.get("code")).isEqualTo(code);
        assertThat(form).doesNotContainKey("client_secret");

        mockMvc.perform(get("/api/v1/auth/social/pending").cookie(signupCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andExpect(jsonPath("$.suggestedNickname").value("카카오 닉네임"));

        String nickname = newNickname();
        MvcResult signup = socialSignup(signupCookie, nickname)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.nickname").value(nickname))
                // 본문은 둘뿐이다 — 로그인 아이디가 없다
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        // 가입 응답이 준 사용자 번호다 — users/me 와 DB 가 같은 번호를 줘야 한다
        Long userId = userIdOf(nickname);
        assertThat(userId).isNotNull();
        Cookie access = signup.getResponse().getCookie("qm_access");
        assertThat(access).isNotNull();
        assertThat(access.getValue()).isNotBlank();
        // 쿠키 둘이다 — refresh 도 같이 온다 (2026-09-23)
        Cookie refresh = refreshCookieOf(signup);
        assertThat(refresh).isNotNull();
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth/refresh");
        assertThat(refresh.getMaxAge()).isEqualTo(604800);
        assertThat(signup.getResponse().getCookie("qm_social_signup").getMaxAge()).isZero();

        mockMvc.perform(get("/api/v1/users/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class))
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.socialProviders.length()").value(1))
                .andExpect(jsonPath("$.socialProviders[0]").value("KAKAO"));
        // 제공자 쪽 회원 번호는 문자열로 남는다
        assertThat(jdbcTemplate.queryForObject(
                "select provider_user_id from social_identities where user_id = ? and provider = 'KAKAO'",
                String.class, userId)).isEqualTo(Long.toString(kakaoId));

        // 같은 사람이 다시 오면 바로 로그인된다
        String secondCode = newCode();
        FAKE.stubUser("kakao", secondCode, "{\"id\":" + kakaoId + "}");
        Cookie secondState = startAndGetState("kakao");
        MvcResult again = callback("kakao", secondCode, secondState.getValue(), secondState)
                .andExpect(status().isFound())
                .andReturn();
        assertThat(again.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/");
        assertThat(again.getResponse().getCookie("qm_social_signup")).isNull();
        assertThat(again.getResponse().getCookie("qm_oauth_state").getMaxAge()).isZero();
        Cookie accessAgain = again.getResponse().getCookie("qm_access");
        assertThat(accessAgain.isHttpOnly()).isTrue();
        assertThat(accessAgain.getPath()).isEqualTo("/");
        // 이미 연결된 사람의 콜백도 쿠키 둘을 준다
        Cookie refreshAgain = refreshCookieOf(again);
        assertThat(refreshAgain).isNotNull();
        assertThat(refreshAgain.getValue()).isNotBlank().isNotEqualTo(refresh.getValue());
        assertThat(refreshAgain.getPath()).isEqualTo("/api/v1/auth/refresh");
        mockMvc.perform(get("/api/v1/users/me").cookie(accessAgain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class));
    }

    @Test
    @DisplayName("디스코드 — id 는 문자열이고 닉네임은 global_name, 없으면 username 이다. client secret 을 토큰 요청에 싣는다")
    void discord() throws Exception
    {
        String discordId = "80351110224678" + ThreadLocalRandom.current().nextInt(1000, 9999);
        String code = newCode();
        FAKE.stubUser("discord", code, "{\"id\":\"" + discordId + "\",\"username\":\"nelly\",\"global_name\":null}");
        Cookie state = startAndGetState("discord");

        MvcResult callback = callback("discord", code, state.getValue(), state).andExpect(status().isFound()).andReturn();

        assertThat(callback.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/signup/social");
        assertThat(FAKE.lastTokenForm("discord").get("client_secret")).isEqualTo("discord-secret");
        Cookie signupCookie = callback.getResponse().getCookie("qm_social_signup");
        mockMvc.perform(get("/api/v1/auth/social/pending").cookie(signupCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("DISCORD"))
                .andExpect(jsonPath("$.suggestedNickname").value("nelly"));

        // 한글 닉네임도 된다. 끝 8자로 겹치지 않게 한다 — 한글 4자 + 8자 = 12자
        String generated = newNickname();
        String nickname = "큐메이트" + generated.substring(generated.length() - 8);
        socialSignup(signupCookie, nickname)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value(nickname));
        assertThat(jdbcTemplate.queryForObject(
                "select provider_user_id from social_identities where user_id = ? and provider = 'DISCORD'",
                String.class, userIdOf(nickname))).isEqualTo(discordId);
    }

    @Test
    @DisplayName("callback 의 실패는 전부 /login?error=OAUTH_FAILED 로 302 다 — JSON 에러로 새지 않고 쿠키도 주지 않는다")
    void callbackFailures() throws Exception
    {
        String code = newCode();
        FAKE.stubUser("kakao", code, "{\"id\":" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L) + "}");
        Cookie state = startAndGetState("kakao");

        // state 가 쿠키와 다르다
        expectOAuthFailed(callback("kakao", code, "someone-elses-state", state));
        // state 쿠키가 없다
        expectOAuthFailed(callback("kakao", code, state.getValue(), null));
        // state 파라미터가 없다 · code 가 없다
        expectOAuthFailed(mockMvc.perform(get("/api/v1/auth/oauth/KAKAO/callback").param("code", code).cookie(state)));
        expectOAuthFailed(mockMvc.perform(get("/api/v1/auth/oauth/KAKAO/callback")
                .param("state", state.getValue()).cookie(state)));
        // 사용자가 동의 화면에서 거절했다
        expectOAuthFailed(mockMvc.perform(get("/api/v1/auth/oauth/KAKAO/callback")
                .param("error", "access_denied").param("state", state.getValue()).cookie(state)));
        // 제공자의 토큰 주소가 500
        expectOAuthFailed(callback("kakao", FakeOAuthProvider.CODE_TOKEN_ERROR, state.getValue(), state));
        // 제공자의 사용자 정보 주소가 500 (넣어 둔 사용자가 없는 code)
        expectOAuthFailed(callback("kakao", newCode(), state.getValue(), state));
        // 사용자 정보에 회원 번호가 없다
        String noIdCode = newCode();
        FAKE.stubUser("kakao", noIdCode, "{\"properties\":{\"nickname\":\"x\"}}");
        expectOAuthFailed(callback("kakao", noIdCode, state.getValue(), state));
        // 카카오에서 시작한 state 를 디스코드 콜백에 가져와도 제공자의 응답이 맞지 않으면 실패다 (디스코드에 넣어 둔 사용자가 없다)
        expectOAuthFailed(callback("discord", code, state.getValue(), state));
    }

    @Test
    @DisplayName("suggested_nickname 은 16자로 자르고, 비어 있으면 뺀다")
    void suggestedNickname() throws Exception
    {
        String longCode = newCode();
        FAKE.stubUser("kakao", longCode, "{\"id\":" + randomKakaoId() + ",\"properties\":{\"nickname\":\"  "
                + "가".repeat(30) + "  \"}}");
        Cookie state = startAndGetState("kakao");
        Cookie longNickname = callback("kakao", longCode, state.getValue(), state).andReturn()
                .getResponse().getCookie("qm_social_signup");
        mockMvc.perform(get("/api/v1/auth/social/pending").cookie(longNickname))
                .andExpect(jsonPath("$.suggestedNickname").value("가".repeat(16)));

        String blankCode = newCode();
        FAKE.stubUser("kakao", blankCode, "{\"id\":" + randomKakaoId() + ",\"properties\":{\"nickname\":\"   \"}}");
        Cookie noNickname = callback("kakao", blankCode, state.getValue(), state).andReturn()
                .getResponse().getCookie("qm_social_signup");
        mockMvc.perform(get("/api/v1/auth/social/pending").cookie(noNickname))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andExpect(jsonPath("$.suggestedNickname").isEmpty());
    }

    @Test
    @DisplayName("social/signup — 쿠키가 없거나 깨졌으면 401 NO_PENDING_SOCIAL_SIGNUP, 같은 소셜 계정으로 두 번이면 409 SOCIAL_ALREADY_LINKED")
    void signupFailures() throws Exception
    {
        String nickname = newNickname();
        socialSignup(null, nickname)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_PENDING_SOCIAL_SIGNUP"))
                .andExpect(jsonPath("$.details").isArray());
        socialSignup(new Cookie("qm_social_signup", "not-a-jwt"), nickname)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_PENDING_SOCIAL_SIGNUP"));
        mockMvc.perform(get("/api/v1/auth/social/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_PENDING_SOCIAL_SIGNUP"));

        String code = newCode();
        FAKE.stubUser("kakao", code, "{\"id\":" + randomKakaoId() + "}");
        Cookie state = startAndGetState("kakao");
        Cookie signupCookie = callback("kakao", code, state.getValue(), state).andReturn()
                .getResponse().getCookie("qm_social_signup");

        // 닉네임의 검증은 닉네임 바꾸기와 같다 — 2~16자 · 앞뒤 공백 없음 · 없으면 안 된다
        for(String invalid : new String[]{"x", "a".repeat(17), " padded "})
        {
            socialSignup(signupCookie, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(detailFor("nickname"));
        }
        mockMvc.perform(post("/api/v1/auth/social/signup").cookie(signupCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("nickname"));
        // 이미 있는 닉네임 — 사용자도 연결도 남지 않는다
        String taken = newNickname();
        Long takenUserId = insertUser(taken);
        socialSignup(signupCookie, taken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where nickname = ?", Integer.class, taken)).isEqualTo(1);
        // 먼저 가입한 사람은 그대로다 — 소셜 연결이 붙지 않았다
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from social_identities where user_id = ?", Integer.class, takenUserId)).isZero();

        socialSignup(signupCookie, nickname).andExpect(status().isCreated());

        // 같은 qm_social_signup 을 다시 쓴다 — 한 트랜잭션이라 두 번째 사용자도 남지 않는다
        String second = newNickname();
        socialSignup(signupCookie, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIAL_ALREADY_LINKED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where nickname = ?", Integer.class, second)).isZero();
    }

    @Test
    @DisplayName("social/signup 은 POST 라서 Origin 검사를 거친다")
    void signupChecksOrigin() throws Exception
    {
        mockMvc.perform(post("/api/v1/auth/social/signup")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nickname", newNickname())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------------------------------------------
    // 잇기 · 끊기 (2026-09-27 소유자 결정 — P-27)
    // ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("잇기 — 로그인된 채 디스코드 콜백이면 나에게 잇고 /settings?linked=DISCORD 다. 로그인 쿠키는 안 바뀐다. 그 뒤 디스코드로 로그인하면 같은 사용자다")
    void linkWhileLoggedIn() throws Exception
    {
        String nickname = newNickname();
        Cookie access = login(nickname);
        Long userId = userIdOf(nickname);
        insertIdentity("KAKAO", Long.toString(randomKakaoId()), userId);
        String discordId = randomDiscordId();

        MvcResult linked = discordCallback(discordId, access);

        assertThat(linked.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/settings?linked=DISCORD");
        // 잇기는 로그인 쿠키를 새로 주지 않는다 — state 쿠키를 지우는 것 하나뿐이다
        List<String> setCookies = linked.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        assertThat(setCookies.get(0)).startsWith("qm_oauth_state=;");
        assertThat(jdbcTemplate.queryForList(
                "select provider from social_identities where user_id = ? order by provider", String.class, userId))
                .containsExactly("DISCORD", "KAKAO");
        mockMvc.perform(get("/api/v1/users/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialProviders.length()").value(2));

        // 같은 것을 또 이으면 멱등 — 여전히 linked 이고 줄이 늘지 않는다
        assertThat(discordCallback(discordId, access).getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo(FRONT + "/settings?linked=DISCORD");
        assertThat(countIdentities(userId)).isEqualTo(2);

        // 로그인 안 한 채(쿠키 없이) 디스코드로 오면 로그인이고 같은 사용자다
        MvcResult login = discordCallback(discordId, null);
        assertThat(login.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/");
        refreshCookieOf(login);
        mockMvc.perform(get("/api/v1/users/me").cookie(login.getResponse().getCookie("qm_access")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class));
    }

    @Test
    @DisplayName("잇기 — 남의 디스코드는 error=SOCIAL_ALREADY_LINKED, 나한테 이미 다른 디스코드가 있으면 error=PROVIDER_ALREADY_LINKED 이고 안 이어진다")
    void linkRejections() throws Exception
    {
        String nickname = newNickname();
        Cookie access = login(nickname);
        Long userId = userIdOf(nickname);
        insertIdentity("KAKAO", Long.toString(randomKakaoId()), userId);

        Long other = insertUser();
        String othersDiscord = randomDiscordId();
        insertIdentity("DISCORD", othersDiscord, other);
        MvcResult taken = discordCallback(othersDiscord, access);
        assertThat(taken.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/settings?error=SOCIAL_ALREADY_LINKED");
        assertThat(taken.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        assertThat(countIdentities(userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select user_id from social_identities where provider = 'DISCORD' and provider_user_id = ?",
                Long.class, othersDiscord)).isEqualTo(other);

        String mine = randomDiscordId();
        discordCallback(mine, access);
        MvcResult second = discordCallback(randomDiscordId(), access);
        assertThat(second.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/settings?error=PROVIDER_ALREADY_LINKED");
        assertThat(jdbcTemplate.queryForObject("select provider_user_id from social_identities where user_id = ? and provider = 'DISCORD'",
                String.class, userId)).isEqualTo(mine);
    }

    @Test
    @DisplayName("잇기 — 만료된 · 깨진 qm_access 로 오면 잇기가 아니라 지금의 로그인 · 가입 흐름이다")
    void expiredAccessIsNotLoggedIn() throws Exception
    {
        String nickname = newNickname();
        login(nickname);
        Long userId = userIdOf(nickname);
        insertIdentity("KAKAO", Long.toString(randomKakaoId()), userId);

        MvcResult expired = discordCallback(randomDiscordId(), new Cookie("qm_access", expiredAccessToken(userId)));
        assertThat(expired.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/signup/social");
        assertThat(expired.getResponse().getCookie("qm_social_signup")).isNotNull();

        MvcResult broken = discordCallback(randomDiscordId(), new Cookie("qm_access", "not-a-jwt"));
        assertThat(broken.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/signup/social");
        assertThat(countIdentities(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("잇기 — state 가 맞지 않으면 로그인된 채여도 /login?error=OAUTH_FAILED 이고 잇지 않는다")
    void linkStateMismatch() throws Exception
    {
        String nickname = newNickname();
        Cookie access = login(nickname);
        Long userId = userIdOf(nickname);
        insertIdentity("KAKAO", Long.toString(randomKakaoId()), userId);
        String code = newCode();
        FAKE.stubUser("discord", code, "{\"id\":\"" + randomDiscordId() + "\",\"username\":\"x\"}");
        Cookie state = startAndGetState("discord");

        expectOAuthFailed(mockMvc.perform(get("/api/v1/auth/oauth/DISCORD/callback")
                .param("code", code).param("state", "someone-elses-state").cookie(state, access)));
        assertThat(countIdentities(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("끊기 — 둘 중 하나를 끊으면 204 이고 users/me 에서 빠진다. 없는 제공자는 204, 하나뿐이면 409 LAST_SOCIAL_IDENTITY")
    void unlink() throws Exception
    {
        String nickname = newNickname();
        Cookie access = login(nickname);
        Long userId = userIdOf(nickname);
        insertIdentity("KAKAO", Long.toString(randomKakaoId()), userId);
        insertIdentity("DISCORD", randomDiscordId(), userId);

        mockMvc.perform(delete("/api/v1/users/me/social/DISCORD").cookie(access)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me").cookie(access))
                .andExpect(jsonPath("$.socialProviders.length()").value(1))
                .andExpect(jsonPath("$.socialProviders[0]").value("KAKAO"));

        // 없는 제공자 — 멱등. 하나뿐이어도 204 다(지울 것이 없다)
        mockMvc.perform(delete("/api/v1/users/me/social/DISCORD").cookie(access)).andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/users/me/social/KAKAO").cookie(access))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_SOCIAL_IDENTITY"));
        assertThat(countIdentities(userId)).isEqualTo(1);

        // 소문자 · 모르는 이름은 400
        mockMvc.perform(delete("/api/v1/users/me/social/kakao").cookie(access)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("끊기 — 동시에 둘을 끊어도(4 스레드) 하나는 남는다")
    void unlinkConcurrently() throws Exception
    {
        String nickname = newNickname();
        Cookie access = login(nickname);
        Long userId = userIdOf(nickname);
        insertIdentity("KAKAO", Long.toString(randomKakaoId()), userId);
        insertIdentity("DISCORD", randomDiscordId(), userId);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try
        {
            for(int i = 0; i < 4; i++)
            {
                String provider = (i % 2 == 0) ? "KAKAO" : "DISCORD";
                results.add(pool.submit(() -> {
                    go.await();
                    return mockMvc.perform(delete("/api/v1/users/me/social/" + provider).cookie(access))
                            .andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for(Future<Integer> result : results)
            {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).allMatch(code -> code == 204 || code == 409).contains(409);
        }
        finally
        {
            pool.shutdownNow();
        }
        assertThat(countIdentities(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("끊기 — 인증이 없으면 401, 다른 Origin 이면 403 ORIGIN_NOT_ALLOWED 다")
    void unlinkRequiresAuthAndOrigin() throws Exception
    {
        mockMvc.perform(delete("/api/v1/users/me/social/KAKAO"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        String nickname = newNickname();
        Cookie access = login(nickname);
        Long userId = userIdOf(nickname);
        insertIdentity("KAKAO", Long.toString(randomKakaoId()), userId);
        insertIdentity("DISCORD", randomDiscordId(), userId);
        mockMvc.perform(delete("/api/v1/users/me/social/KAKAO").cookie(access).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        assertThat(countIdentities(userId)).isEqualTo(2);
    }

    /** 디스코드 흐름을 한 번 탄다 — start 로 state 를 받고, 그 사용자를 넣어 둔 code 로 콜백을 부른다. {@code access} 가 있으면 싣는다 */
    private MvcResult discordCallback(String discordId, Cookie access) throws Exception
    {
        String code = newCode();
        FAKE.stubUser("discord", code, "{\"id\":\"" + discordId + "\",\"username\":\"nelly\"}");
        Cookie state = startAndGetState("discord");
        var request = get("/api/v1/auth/oauth/DISCORD/callback").param("code", code).param("state", state.getValue()).cookie(state);
        if(access != null)
        {
            request.cookie(access);
        }
        return mockMvc.perform(request).andExpect(status().isFound()).andReturn();
    }

    private void insertIdentity(String provider, String providerUserId, Long userId)
    {
        jdbcTemplate.update("insert into social_identities (provider, provider_user_id, user_id, created_at) values (?, ?, ?, now())",
                provider, providerUserId, userId);
    }

    private int countIdentities(Long userId)
    {
        return jdbcTemplate.queryForObject("select count(*) from social_identities where user_id = ?", Integer.class, userId);
    }

    /** 앱의 키로 서명했지만 한 시간 전에 만료된 access 토큰 — 검증기의 허용 오차(60초)를 넘긴다 */
    private String expiredAccessToken(Long userId)
    {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TokenClaims.ISSUER)
                .subject(Long.toString(userId))
                .issuedAt(past)
                .expiresAt(past.plus(1, ChronoUnit.HOURS))
                .claim(TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_ACCESS)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    private static String randomDiscordId()
    {
        return "80351110224678" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /** 경로의 제공자 이름은 대문자 enum 이다 — 가짜 제공자의 stub 키("kakao:code")는 소문자라 여기서 올린다 */
    private Cookie startAndGetState(String provider) throws Exception
    {
        return mockMvc.perform(get("/api/v1/auth/oauth/" + provider.toUpperCase() + "/start"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getCookie("qm_oauth_state");
    }

    private ResultActions callback(String provider, String code, String state, Cookie stateCookie) throws Exception
    {
        var request = get("/api/v1/auth/oauth/" + provider.toUpperCase() + "/callback").param("code", code).param("state", state);
        if(stateCookie != null)
        {
            request.cookie(stateCookie);
        }
        return mockMvc.perform(request);
    }

    /** 201 이면 사용자 번호를 받아 둔다 — 뒷정리가 그것으로 사용자를 찾는다 */
    private ResultActions socialSignup(Cookie signupCookie, String nickname) throws Exception
    {
        var request = post("/api/v1/auth/social/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nickname", nickname));
        if(signupCookie != null)
        {
            request.cookie(signupCookie);
        }
        ResultActions actions = mockMvc.perform(request);
        rememberUserId(nickname, actions.andReturn());
        return actions;
    }

    private static void expectOAuthFailed(ResultActions actions) throws Exception
    {
        MvcResult result = actions.andExpect(status().isFound()).andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONT + "/login?error=OAUTH_FAILED");
        assertThat(result.getResponse().getCookie("qm_access")).isNull();
        assertThat(result.getResponse().getCookie("qm_social_signup")).isNull();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        assertThat(setCookies.get(0)).startsWith("qm_oauth_state=;");
    }

    private static String newCode()
    {
        return "code-" + UUID.randomUUID();
    }

    private static long randomKakaoId()
    {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
    }
}
