package com.queuemate.platform.account;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 · 로그인 · 로그아웃 — {@code contracts/platform-api.md} "계정" · "access 토큰".
 *
 * <p>가입 · 로그인 본문의 아이디는 <b>로그인 아이디</b>({@code loginId})이고, 응답의 {@code userId} 는 DB 가 매긴 <b>사용자 번호</b>다
 * (2026-09-22 소유자 결정). 번호는 JSON 에서 숫자라 {@code value(equalTo(…), Long.class)} 로 본다 — Jackson 이 int 로 읽어
 * {@code value(long)} 과는 맞지 않는다.
 */
class AuthApiTest extends ApiTestSupport {

    @Test
    @DisplayName("가입 → 로그인하면 계약대로의 쿠키가 오고, 그 쿠키로 내 프로필을 읽는다")
    void signupLoginThenReadMe() throws Exception
    {
        String loginId = newLoginId();

        signup(loginId, PASSWORD, nicknameOf(loginId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.loginId").value(loginId))
                .andExpect(jsonPath("$.nickname").value(nicknameOf(loginId)))
                // 가입은 로그인시키지 않는다
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull());

        // 가입 응답이 준 번호다 — 로그인과 users/me 가 같은 번호를 줘야 한다
        Long userId = userIdOf(loginId);
        assertThat(userId).isNotNull();

        MvcResult loggedIn = login(loginId, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class))
                .andExpect(jsonPath("$.loginId").value(loginId))
                .andExpect(jsonPath("$.nickname").value(nicknameOf(loginId)))
                // 토큰은 본문에 싣지 않는다
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        String setCookie = loggedIn.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("qm_access=");
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
        // 수명은 토큰 수명과 같다 — 기본값 PT24H
        assertThat(setCookie).contains("Max-Age=" + Duration.ofHours(24).toSeconds());
        // 로컬 기본값은 Secure 를 붙이지 않고(COOKIE_SECURE=false), Domain 은 어디서든 붙이지 않는다(host-only)
        assertThat(setCookie).doesNotContain("Secure").doesNotContain("Domain");

        Cookie cookie = loggedIn.getResponse().getCookie("qm_access");
        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class))
                .andExpect(jsonPath("$.loginId").value(loginId))
                .andExpect(jsonPath("$.nickname").value(nicknameOf(loginId)))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.gameAccounts").isEmpty());
    }

    @Test
    @DisplayName("access 토큰은 RS256 이고 kid 가 있으며 클레임은 계약의 여섯 개뿐이다")
    void accessTokenShape() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);

        SignedJWT jwt = SignedJWT.parse(cookie.getValue());
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("dev-1");

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        // 닉네임처럼 바뀌는 값이 섞여 들어오지 않았는지도 같이 본다
        assertThat(claims.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti", "token_use");
        assertThat(claims.getIssuer()).isEqualTo("queuemate-platform");
        // sub 는 사용자 번호를 문자열로 찍은 것이다 — 로그인 아이디가 아니다
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userIdOf(loginId)));
        assertThat(claims.getStringClaim("token_use")).isEqualTo("access");
        assertThat(UUID.fromString(claims.getJWTID())).isNotNull();
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("비밀번호가 틀린 것과 아이디가 없는 것은 같은 401 INVALID_CREDENTIALS 다")
    void wrongPasswordAndUnknownIdLookTheSame() throws Exception
    {
        String loginId = newLoginId();
        signup(loginId, PASSWORD, nicknameOf(loginId)).andExpect(status().isCreated());

        MvcResult wrongPassword = login(loginId, "wrong-password-1").andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknownId = login(newLoginId(), PASSWORD).andExpect(status().isUnauthorized()).andReturn();
        // 형식이 아닌 아이디 · BCrypt 가 못 보는 길이의 비밀번호도 400 이 아니라 같은 401 이다
        MvcResult malformedId = login("NOT A VALID ID", PASSWORD).andExpect(status().isUnauthorized()).andReturn();
        MvcResult tooLongPassword = login(loginId, "가".repeat(30)).andExpect(status().isUnauthorized()).andReturn();

        String body = wrongPassword.getResponse().getContentAsString();
        assertThat(body).contains("\"code\":\"INVALID_CREDENTIALS\"");
        assertThat(unknownId.getResponse().getContentAsString()).isEqualTo(body);
        assertThat(malformedId.getResponse().getContentAsString()).isEqualTo(body);
        assertThat(tooLongPassword.getResponse().getContentAsString()).isEqualTo(body);
        assertThat(wrongPassword.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("같은 로그인 아이디로 두 번 가입하면 409 LOGIN_ID_TAKEN 이고 먼저 가입한 계정은 그대로다")
    void duplicateUserId() throws Exception
    {
        String loginId = newLoginId();
        signup(loginId, PASSWORD, nicknameOf(loginId)).andExpect(status().isCreated());

        signup(loginId, "another-password", nicknameOf(newLoginId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOGIN_ID_TAKEN"))
                .andExpect(jsonPath("$.details").isArray());

        // merge 로 빠져 남의 계정을 덮어쓰지 않았다 — 닉네임도 비밀번호도 처음 것이다
        login(loginId, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nicknameOf(loginId)));
        login(loginId, "another-password").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 로그인 아이디를 여러 스레드가 동시에 가입시키면 하나만 201 이고 나머지는 409 다 — DB 의 UNIQUE 가 지킨다")
    void concurrentSignupWithTheSameId() throws Exception
    {
        int threads = 8;
        String loginId = newLoginId();
        // 닉네임은 서로 다르게 준다 — 같으면 진 쪽이 NICKNAME_TAKEN 으로 질 수도 있어 무엇을 본 것인지 흐려진다
        List<String> nicknames = new ArrayList<>();
        for(int i = 0; i < threads; i++)
        {
            nicknames.add(nicknameOf(newLoginId()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try
        {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for(String nickname : nicknames)
            {
                Callable<MvcResult> task = () -> {
                    start.await();
                    return signup(loginId, PASSWORD, nickname).andReturn();
                };
                futures.add(pool.submit(task));
            }
            start.countDown();

            int created = 0;
            int conflicts = 0;
            for(Future<MvcResult> future : futures)
            {
                MvcResult result = future.get(60, TimeUnit.SECONDS);
                int status = result.getResponse().getStatus();
                if(status == 201)
                {
                    created++;
                }
                else
                {
                    assertThat(status).isEqualTo(409);
                    assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"LOGIN_ID_TAKEN\"");
                    conflicts++;
                }
            }
            assertThat(created).isEqualTo(1);
            assertThat(conflicts).isEqualTo(threads - 1);
        }
        finally
        {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.users where login_id = ?", Integer.class, loginId)).isEqualTo(1);
        // 진 쪽의 비밀번호 해시가 남지 않았다(트랜잭션이 통째로 되돌려졌다)
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.credentials where user_id = ?", Integer.class, userIdOf(loginId))).isEqualTo(1);
    }

    @Test
    @DisplayName("닉네임이 겹치면 409 NICKNAME_TAKEN 이고 그 아이디는 만들어지지 않는다")
    void duplicateNickname() throws Exception
    {
        String first = newLoginId();
        String second = newLoginId();
        signup(first, PASSWORD, nicknameOf(first)).andExpect(status().isCreated());

        signup(second, PASSWORD, nicknameOf(first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.users where login_id = ?", Integer.class, second)).isZero();
    }

    @Test
    @DisplayName("검증 실패는 400 VALIDATION_FAILED 이고 details 에 \"필드: 사유\" 꼴로 필드마다 한 줄이 온다")
    void validationFailures() throws Exception
    {
        String loginId = newLoginId();

        // 대문자 아이디
        signup("UPPER_CASE", PASSWORD, nicknameOf(loginId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("loginId"));
        // URL 을 깨는 글자
        signup("ab:cd/ef", PASSWORD, nicknameOf(loginId))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("loginId"));
        // 짧은 아이디
        signup("abc", PASSWORD, nicknameOf(loginId)).andExpect(status().isBadRequest());
        // 짧은 비밀번호
        signup(loginId, "short", nicknameOf(loginId))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("password"));
        // 글자 수는 72 이하지만 UTF-8 로 72바이트를 넘는 비밀번호 — BCrypt 가 못 본다
        signup(loginId, "가".repeat(30), nicknameOf(loginId))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("password"));
        // 앞뒤 공백이 있는 닉네임 · 한 글자 닉네임 · 17자 닉네임
        signup(loginId, PASSWORD, " padded ")
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("nickname"));
        signup(loginId, PASSWORD, "a").andExpect(status().isBadRequest());
        signup(loginId, PASSWORD, "a".repeat(17)).andExpect(status().isBadRequest());
        // 빠진 칸 — 여러 필드가 한꺼번에 온다
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("loginId"))
                .andExpect(detailFor("password"))
                .andExpect(detailFor("nickname"));
        // JSON 이 아닌 본문
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 로그인은 비어 있는지만 본다
        login("", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.users where login_id = ?", Integer.class, loginId)).isZero();
    }

    @Test
    @DisplayName("한글 닉네임으로 가입할 수 있다")
    void koreanNickname() throws Exception
    {
        String loginId = newLoginId();
        // 끝 8자로 겹치지 않게 한다. 한글 4자 + 8자 = 12자
        String nickname = "큐메이트" + loginId.substring(loginId.length() - 8);

        signup(loginId, PASSWORD, nickname)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value(nickname));
    }

    @Test
    @DisplayName("로그아웃은 Max-Age=0 인 쿠키를 돌려준다. 로그인하지 않았어도 204 다")
    void logout() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());

        for(MvcResult result : List.of(
                mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andReturn(),
                mockMvc.perform(post("/api/v1/auth/logout")).andReturn()))
        {
            assertThat(result.getResponse().getStatus()).isEqualTo(204);
            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).startsWith("qm_access=;");
            // 발급 때와 속성이 같아야 브라우저가 같은 쿠키로 보고 지운다
            assertThat(setCookie).contains("Max-Age=0").contains("Path=/").contains("HttpOnly").contains("SameSite=Lax");
        }
    }
}
