package com.queuemate.platform.room.controller;

import com.queuemate.platform.common.error.GlobalExceptionHandler;
import com.queuemate.platform.common.security.CurrentUserIdArgumentResolver;
import com.queuemate.platform.party.service.PostService;
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
 * 방의 요청을 <b>HTTP 로</b> — <b>쿠키 → 사용자 번호 → 방 키</b>의 연결과, 결과가 계약의 상태 코드 · 에러 코드({@code contracts/platform-api.md} "방")로
 * 옮겨지는지를 본다. 방의 규칙 자체(갈래 · 동시성 · 알림)는 서비스 테스트들이, 글과 방이 맞물리는 것(글 쓰기 = 방 만들기 · 입장의 검사 · 확정 한 길)은
 * {@code party.PostRoomFlowTest} 가 본다.
 *
 * <p>방은 {@code roomService.create} 로 직접 만든다 — 2026-09-25 2단계로 방 만들기 HTTP 요청이 없어졌다(게시판의 방은 글 쓰기가 만든다).
 * 그 번호의 글은 {@link RoomTestSupport#r} 가 넣어 둔다(입장이 글부터 보기 때문이다).
 */
class RoomApiTest extends RoomTestSupport {

    @Autowired
    private RedisScript<Long> createRoomScript;

    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> confirmRoomScript;

    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> leaveRoomScript;

    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> enterMatchRoomScript;

    @Autowired
    private RoomProperties roomProperties;

    @Autowired
    private RoomNotifier roomNotifier;

    @Autowired
    private PostService postService;

    @Test
    @DisplayName("쿠키가 없으면 방의 요청은 전부 401 UNAUTHENTICATED 다 — /api/v1/rooms/** 는 인증이 필요한 경로다")
    void requiresLogin() throws Exception
    {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", r("r1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/rooms/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/confirm", r("r1")))
                .andExpect(status().isUnauthorized());

        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("방 만들기 요청(POST /api/v1/rooms/{roomId})은 없다 — 404 NOT_FOUND 이고 방이 생기지 않는다(2026-09-25 2단계 · 게시판의 방은 글 쓰기가 만든다)")
    void noCreateRequest() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        adopt("host", userIdOf(nickname));

        mockMvc.perform(post("/api/v1/rooms/{roomId}", r("r1")).cookie(cookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("멤버 SET · 입장 표시 키에 쿠키의 사용자 번호가 들어간다. ?userId= 는 없다 — 붙여 보내도 무시된다")
    void userComesFromTheCookie() throws Exception
    {
        String member = newNickname();
        Cookie memberCookie = login(member);
        String hostId = u("host");
        String memberId = adopt("member", userIdOf(member));
        roomService.create(r("r1"), hostId);

        // 남의 번호(방장)를 파라미터로 적어도 들어오는 사람은 쿠키의 사용자다
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", r("r1")).param("userId", hostId).cookie(memberCookie))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", r("r1")).cookie(memberCookie))
                .andExpect(status().isOk());

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
    @DisplayName("거절은 계약의 상태 코드 · 에러 코드로 나간다 — 403 NOT_HOST · 403 NOT_IN_ROOM · 404 ROOM_NOT_FOUND · 404 TARGET_NOT_IN_ROOM")
    void rejectionsFollowTheContract() throws Exception
    {
        String host = newNickname();
        String member = newNickname();
        String stranger = newNickname();
        Cookie hostCookie = login(host);
        Cookie memberCookie = login(member);
        Cookie strangerCookie = login(stranger);
        String hostId = adopt("host", userIdOf(host));
        String memberId = adopt("member", userIdOf(member));
        adopt("stranger", userIdOf(stranger));

        roomService.create(r("r1"), hostId);
        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", r("r1")).cookie(memberCookie)).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/{target}", r("r1"), hostId).cookie(memberCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOST"))
                .andExpect(jsonPath("$.details").isArray());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/heartbeat", r("r1")).cookie(strangerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_IN_ROOM"));
        // 글은 있는데(r9 가 넣었다) 방이 없다 — 확정 스크립트가 답한다
        mockMvc.perform(post("/api/v1/rooms/{roomId}/confirm", r("r9")).cookie(hostCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        // 글 번호일 수 없는 roomId — 글이 없으니 방도 있을 수 없다
        mockMvc.perform(post("/api/v1/rooms/{roomId}/confirm", "not-a-number").cookie(hostCookie))
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
    @DisplayName("시그널 본문이 틀리면 400 VALIDATION_FAILED 다 — 2026-09-25 2단계로 방의 INVALID_REQUEST 를 이 앱의 공통 코드로 합쳤다")
    void invalidSignalIsValidationFailed() throws Exception
    {
        Cookie cookie = login(newNickname());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/signals", r("r1")).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"signal\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("toUserId"));
        mockMvc.perform(post("/api/v1/rooms/{roomId}/signals", r("r1")).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("허용하지 않는 Origin 의 방 요청은 403 ORIGIN_NOT_ALLOWED 이고 들어가지지 않는다")
    void foreignOriginIsRejected() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        adopt("member", userIdOf(nickname));
        roomService.create(r("r1"), u("host"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/members", r("r1")).cookie(cookie).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

        assertThat(members("r1")).containsExactly("host");
        assertThat(marker("member")).isNull();
    }

    @Test
    @DisplayName("Redis 에 닿지 못하면 503 ROOM_STATE_UNAVAILABLE + Retry-After: 5 다 — 2026-09-25 2단계로 방의 ROOM_UNAVAILABLE 을 이 이름으로 합쳤다")
    void redisDownIsRoomStateUnavailable() throws Exception
    {
        // 아무도 듣지 않는 포트다. 앱 전체의 Redis 를 죽일 수 없어 방의 서비스만 죽은 Redis 로 만들어 끼운다
        LettuceConnectionFactory dead = new LettuceConnectionFactory("localhost", 1);
        dead.afterPropertiesSet();
        try
        {
            RoomService broken = new RoomService(new StringRedisTemplate(dead), createRoomScript, roomProperties,
                    confirmRoomScript, roomNotifier, leaveRoomScript, enterMatchRoomScript);
            // 방만의 예외 처리기는 없어졌다 — 이 앱의 처리기 하나로 503 이 나와야 한다(500 이면 Redis 오류가 새어 나온 것이다)
            MockMvc standalone = MockMvcBuilders.standaloneSetup(new RoomController(broken, postService))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                    .build();
            Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(u("host")).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

            standalone.perform(get("/api/v1/rooms/me"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                    .andExpect(jsonPath("$.code").value("ROOM_STATE_UNAVAILABLE"));
        }
        finally
        {
            SecurityContextHolder.clearContext();
            dead.destroy();
        }
    }
}
