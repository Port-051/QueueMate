package com.queuemate.platform.account.oauth;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제공자가 설정되지 않았을 때 — 기본 설정에서는 {@code KAKAO_CLIENT_ID} · {@code DISCORD_CLIENT_ID} · {@code GOOGLE_CLIENT_ID} 가 비어 있다.
 * (설정된 쪽은 {@link SocialLoginApiTest} 가 본다.)
 */
class OAuthNotConfiguredTest extends ApiTestSupport {

    @Test
    @DisplayName("클라이언트 id 가 비어 있는 제공자의 start 는 404 OAUTH_PROVIDER_NOT_CONFIGURED 다 — 인증 없이 그 답을 받는다")
    void startIsNotFound() throws Exception
    {
        for(String provider : new String[]{"KAKAO", "DISCORD", "GOOGLE"})
        {
            mockMvc.perform(get("/api/v1/auth/oauth/" + provider + "/start"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("OAUTH_PROVIDER_NOT_CONFIGURED"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(result -> assertThat(result.getResponse().getCookie("qm_oauth_state")).isNull());
        }
    }

    @Test
    @DisplayName("설정되지 않은 제공자의 callback 은 /login?error=OAUTH_FAILED 로 302 다 — 콜백은 JSON 에러를 내지 않는다")
    void callbackRedirects() throws Exception
    {
        mockMvc.perform(get("/api/v1/auth/oauth/KAKAO/callback").param("code", "x").param("state", "y"))
                .andExpect(status().isFound())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                        .isEqualTo("http://localhost:5173/login?error=OAUTH_FAILED"));
    }
}
