package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 실패 제한 — {@code contracts/platform-api.md} "계정"의 "로그인 실패 제한".
 *
 * <p><b>잠금을 짧게 덮어 쓴다</b>(첫 잠금 2초 · 상한 4초) — 기본값(1분 · 15분)으로는 "잠금이 풀리면 다시 된다"를 기다릴 수 없다.
 * 허용 횟수(5)와 창(15분)은 기본값 그대로다. 설정이 달라 다른 테스트와 스프링 컨텍스트를 같이 쓰지 못한다(하나 더 뜬다).
 *
 * <p>세는 열쇠는 <b>로그인 아이디</b>다 — 사용자 번호가 아니다(사용자를 찾기 전에 세야 하고, 없는 아이디도 센다).
 * 키 이름을 main 의 상수에서 가져오지 않고 글자로 적었다 — 계약에 적힌 이름이다.
 */
@TestPropertySource(properties = {
        "platform.auth.login-throttle.first-lock=PT2S",
        "platform.auth.login-throttle.max-lock=PT4S"
})
class LoginThrottleTest extends ApiTestSupport {

    private static final String WRONG = "wrong-password-1";

    @Test
    @DisplayName("5번 틀리면 6번째는 맞는 비밀번호여도 429 TOO_MANY_LOGIN_ATTEMPTS + Retry-After 다. 잠금이 풀리면 다시 되고, 성공하면 횟수가 지워진다")
    void locksAfterFiveFailures() throws Exception
    {
        String loginId = newLoginId();
        signup(loginId, PASSWORD, nicknameOf(loginId)).andExpect(status().isCreated());

        for(int i = 0; i < 5; i++)
        {
            // 다섯 번째까지는 그냥 401 이다 — 잠금은 그다음 시도부터 보인다
            login(loginId, WRONG).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        assertThat(redisTemplate.opsForValue().get(failKey(loginId))).isEqualTo("5");
        // 횟수 키에는 반드시 수명이 있다 — 수명 없는 키가 남으면 그 계정의 횟수가 영영 사라지지 않는다
        assertThat(redisTemplate.getExpire(failKey(loginId), TimeUnit.SECONDS)).isPositive();

        MvcResult locked = login(loginId, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.details").isArray())
                .andReturn();
        assertThat(Long.parseLong(locked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 2L);
        assertThat(locked.getResponse().getCookie("qm_access")).isNull();
        // 잠긴 동안의 시도는 비밀번호를 비교하지 않으므로 세지도 않는다
        login(loginId, WRONG).andExpect(status().isTooManyRequests());
        assertThat(redisTemplate.opsForValue().get(failKey(loginId))).isEqualTo("5");

        waitUntilUnlocked(loginId);
        login(loginId, PASSWORD).andExpect(status().isOk());
        assertThat(redisTemplate.hasKey(failKey(loginId))).isFalse();
        assertThat(redisTemplate.hasKey(lockKey(loginId))).isFalse();

        // 지워졌으니 다시 네 번까지는 틀려도 잠기지 않는다
        for(int i = 0; i < 4; i++)
        {
            login(loginId, WRONG).andExpect(status().isUnauthorized());
        }
        login(loginId, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("잠금이 풀린 뒤에 또 틀리면 잠금이 두 배가 되고, 상한을 넘지 않는다")
    void lockDoublesUpToMax() throws Exception
    {
        String loginId = newLoginId();
        signup(loginId, PASSWORD, nicknameOf(loginId)).andExpect(status().isCreated());
        for(int i = 0; i < 5; i++)
        {
            login(loginId, WRONG).andExpect(status().isUnauthorized());
        }
        assertThat(redisTemplate.getExpire(lockKey(loginId), TimeUnit.MILLISECONDS)).isBetween(1L, 2_000L);

        waitUntilUnlocked(loginId);
        login(loginId, WRONG).andExpect(status().isUnauthorized());
        assertThat(redisTemplate.getExpire(lockKey(loginId), TimeUnit.MILLISECONDS)).isBetween(2_001L, 4_000L);

        waitUntilUnlocked(loginId);
        // 8초가 아니라 상한인 4초다
        login(loginId, WRONG).andExpect(status().isUnauthorized());
        assertThat(redisTemplate.getExpire(lockKey(loginId), TimeUnit.MILLISECONDS)).isBetween(2_001L, 4_000L);
        // 잠근 뒤에도 횟수가 남아 있다 — 잠금이 끝날 때 같이 사라지면 두 배로 늘던 잠금이 처음으로 돌아간다
        assertThat(redisTemplate.opsForValue().get(failKey(loginId))).isEqualTo("7");
    }

    @Test
    @DisplayName("없는 아이디도 똑같이 잠긴다 — 잠기는지로 아이디의 존재가 새지 않는다. 형식이 틀린 아이디는 세지 않는다")
    void unknownIdLocksToo() throws Exception
    {
        String nobody = newLoginId();
        for(int i = 0; i < 5; i++)
        {
            login(nobody, WRONG).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login(nobody, WRONG).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));

        String malformed = "NOT A VALID ID";
        for(int i = 0; i < 7; i++)
        {
            login(malformed, WRONG).andExpect(status().isUnauthorized());
        }
        assertThat(redisTemplate.hasKey(failKey(malformed))).isFalse();
        assertThat(redisTemplate.hasKey(lockKey(malformed))).isFalse();
    }

    private void waitUntilUnlocked(String loginId) throws Exception
    {
        long deadline = System.currentTimeMillis() + 10_000;
        while(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(loginId))))
        {
            assertThat(System.currentTimeMillis()).as("잠금이 풀리지 않았다").isLessThan(deadline);
            Thread.sleep(100);
        }
    }

    private static String failKey(String loginId)
    {
        return "qm:auth:login-fail:" + loginId;
    }

    private static String lockKey(String loginId)
    {
        return "qm:auth:login-lock:" + loginId;
    }
}
