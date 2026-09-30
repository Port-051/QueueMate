package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.account.controller.DevLoginController;
import com.queuemate.platform.account.service.DevLoginService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEMP-DEV-LOGIN — 개발용 로그인이 <b>꺼져 있을 때</b>(운영의 기본값 — {@code DEV_LOGIN_ENABLED} 가 없다). 걷어낼 때 이 파일째 지운다.
 *
 * <p>테스트 설정은 켜 두었으므로 {@code @TestPropertySource} 로 끈다 — <b>이 클래스만 스프링 컨텍스트를 따로 띄운다.</b>
 */
@TestPropertySource(properties = DevLoginService.ENABLED_PROPERTY + "=false")
class DevLoginDisabledTest extends ApiTestSupport {

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("꺼져 있으면 경로가 아예 없다 — 없는 경로와 글자까지 같은 404 NOT_FOUND 이고, 쿠키도 사용자도 없다")
    void notFoundLikeAnUnknownPath() throws Exception
    {
        String nickname = newNickname();

        MvcResult devLogin = mockMvc.perform(post("/api/v1/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON).content(json("nickname", nickname)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn();
        MvcResult unknown = mockMvc.perform(post("/api/v1/auth/no-such-thing")
                        .contentType(MediaType.APPLICATION_JSON).content(json("nickname", nickname)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(devLogin.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(unknown.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(devLogin.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(userIdOf(nickname)).isNull();
        // 본문이 없어도 같다
        mockMvc.perform(post("/api/v1/auth/dev-login"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // 빈 자체가 없다 — 컨트롤러도 서비스도
        assertThat(context.getBeansOfType(DevLoginController.class)).isEmpty();
        assertThat(context.getBeansOfType(DevLoginService.class)).isEmpty();
    }
}
