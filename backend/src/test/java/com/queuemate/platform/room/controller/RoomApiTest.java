package com.queuemate.platform.room.controller;

import com.queuemate.platform.common.error.GlobalExceptionHandler;
import com.queuemate.platform.common.security.CurrentUserIdArgumentResolver;
import com.queuemate.platform.room.RoomProperties;
import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.service.RoomNotifier;
import com.queuemate.platform.room.service.RoomService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 방의 요청을 <b>HTTP 로</b> — 2026-09-25 에 {@code room} 을 합치며 바뀐 것은 인증 하나다: {@code ?userId=} 가 없어지고 {@code qm_access} 쿠키의 사용자가 "나"다.
 * 방의 규칙 자체(갈래 · 동시성 · 알림)는 서비스 테스트들이 본다 — 여기서는 <b>쿠키 → 사용자 번호 → 방 키</b>의 연결과, 결과가 계약의
 * 상태 코드 · 에러 코드({@code contracts/room-api.md})로 옮겨지는지를 본다.
 */
class RoomApiTest extends RoomTestSupport {

    @Autowired
    private RedisScript<Long> createRoomScript;

    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> confirmRoomScript;

    @Autowired
    private RoomProperties roomProperties;

    @Autowired
    private RoomNotifier roomNotifier;

    @Test
    @DisplayName("쿠키가 없으면 방의 요청은 전부 401 UNAUTHENTICATED 다 — /api/v1/rooms/** 는 인증이 필요한 경로다")
    void requiresLogin() throws Exception
    {
        mockMvc.perform(post("/api/v1/rooms/{roomId}", r("r1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/rooms/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", r("r1")))
                .andExpect(status().isUnauthorized());

        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("방장 키 · 멤버 SET · 입장 표시 키에 쿠키의 사용자 번호가 들어간다. ?userId= 는 없다 — 붙여 보내도 무시된다")
    void userComesFromTheCookie() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        String hostId = adopt("host", userIdOf(host));
        String memberId = adopt("member", userIdOf(member));

        // 남의 번호를 파라미터로 적어도 만드는 사람은 쿠키의 사용자다
        mockMvc.perform(post("/api/v1/rooms/{roomId}", r("r1")).param("userId", memberId).cookie(hostCookie))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/rooms/{roomId}", r("r1")).cookie(hostCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", r("r1")).cookie(memberCookie))
                .andExpect(status().isCreated());

        assertThat(redisTemplate.opsForValue().get(key("qm:room:r1:host"))).isEqualTo(hostId);
        assertThat(redisTemplate.opsForSet().members(key("qm:room:r1:members"))).containsExactlyInAnyOrder(hostId, memberId);
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + memberId)).isEqualTo(r("r1"));
        assertThat(ownKeys()).containsExactlyInAnyOrder("qm:room:r1:host", "qm:room:r1:members",
                "qm:user:active-room:host", "qm:user:active-room:member");

        mockMvc.perform(get("/api/v1/rooms/{roomId}/members", r("r1")).cookie(memberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(r("r1")))
                .andExpect(jsonPath("$.hostId").value(hostId))
                .andExpect(jsonPath("$.members", containsInAnyOrder(hostId, memberId)));
        mockMvc.perform(get("/api/v1/rooms/me").cookie(memberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(r("r1")));
    }

    @Test
    @DisplayName("거절은 계약의 상태 코드 · 에러 코드로 나간다 — 409 ROOM_ALREADY_EXISTS · 403 NOT_HOST · 403 NOT_IN_ROOM · 404 ROOM_NOT_FOUND")
    void rejectionsFollowTheContract() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        String stranger = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Cookie strangerCookie = signupAndLogin(stranger);
        String hostId = adopt("host", userIdOf(host));
        String memberId = adopt("member", userIdOf(member));
        adopt("stranger", userIdOf(stranger));

        mockMvc.perform(post("/api/v1/rooms/{roomId}", r("r1")).cookie(hostCookie)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", r("r1")).cookie(memberCookie)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/rooms/{roomId}", r("r1")).cookie(strangerCookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.details").isArray());
        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/{target}", r("r1"), hostId).cookie(memberCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOST"));
        mockMvc.perform(post("/api/v1/rooms/{roomId}/heartbeat", r("r1")).cookie(strangerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_IN_ROOM"));
        mockMvc.perform(post("/api/v1/rooms/{roomId}/confirm", r("r9")).cookie(hostCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/{target}", r("r1"), "not-a-number").cookie(hostCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_IN_ROOM"));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/{target}", r("r1"), memberId).cookie(hostCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/me", r("r1")).cookie(hostCookie))
                .andExpect(status().isNoContent());
        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("시그널 본문이 틀리면 400 INVALID_REQUEST 다 — 이 앱의 공통 코드(VALIDATION_FAILED)가 아니라 방의 계약의 이름이다")
    void invalidSignalIsInvalidRequest() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/signals", r("r1")).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"signal\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(detailFor("toUserId"));
        mockMvc.perform(post("/api/v1/rooms/{roomId}/signals", r("r1")).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        // 방 밖의 요청은 그대로 이 앱의 공통 코드다 — 범위를 방의 컨트롤러로 좁혀 두었다
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("허용하지 않는 Origin 의 방 요청은 403 ORIGIN_NOT_ALLOWED 이고 방이 생기지 않는다")
    void foreignOriginIsRejected() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        adopt("host", userIdOf(loginId));

        mockMvc.perform(post("/api/v1/rooms/{roomId}", r("r1")).cookie(cookie).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("Redis 에 닿지 못하면 503 ROOM_UNAVAILABLE + Retry-After: 5 다 — 이 앱의 처리기가 500 으로 가로채지 않는다")
    void redisDownIsRoomUnavailable() throws Exception
    {
        // 아무도 듣지 않는 포트다. 앱 전체의 Redis 를 죽일 수 없어 방의 서비스만 죽은 Redis 로 만들어 끼운다
        LettuceConnectionFactory dead = new LettuceConnectionFactory("localhost", 1);
        dead.afterPropertiesSet();
        try
        {
            RoomService broken = new RoomService(new StringRedisTemplate(dead), createRoomScript, roomProperties,
                    confirmRoomScript, roomNotifier);
            // 두 처리기를 함께 건다 — 이 앱의 GlobalExceptionHandler 는 Exception 을 다 받으므로, 순서가 틀리면 여기서 500 이 나온다
            MockMvc standalone = MockMvcBuilders.standaloneSetup(new RoomController(broken))
                    .setControllerAdvice(new GlobalExceptionHandler(), new RoomExceptionHandler())
                    .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                    .build();
            Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(u("host")).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

            standalone.perform(post("/api/v1/rooms/{roomId}", r("r1")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                    .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
        }
        finally
        {
            SecurityContextHolder.clearContext();
            dead.destroy();
        }
    }
}
