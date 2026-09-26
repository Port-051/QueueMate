package com.queuemate.platform.social;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.social.service.BlockReader;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 차단 — {@code contracts/platform-api.md} "차단" 의 세 요청과, 모집 글 목록 · 방의 입장 검사가 쓰는 창구 {@link BlockReader}.
 *
 * <p>주고받는 것은 전부 <b>사용자 번호</b>(숫자)다 — 로그인 아이디는 가입 · 로그인에만 쓴다(2026-09-22 소유자 결정).
 * 응답의 {@code userId} 는 JSON 숫자라 {@code jsonPath(…, equalTo(번호), Long.class)} 로 본다 — Jackson 이 {@code int} 로 읽어
 * {@code value(long)} 은 맞지 않는다.
 */
class BlockApiTest extends ApiTestSupport {

    @Autowired
    BlockReader blockReader;

    @Test
    @DisplayName("차단 → 목록 → 해제 → 목록. 목록에는 내가 차단한 사람만 있다 — 나를 차단한 사람은 보이지 않는다")
    void blockListUnblock() throws Exception
    {
        String me = newLoginId();
        String target = newLoginId();
        String another = newLoginId();
        String blocksMe = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        signup(target, PASSWORD, nicknameOf(target)).andExpect(status().isCreated());
        signup(another, PASSWORD, nicknameOf(another)).andExpect(status().isCreated());
        Cookie theirCookie = signupAndLogin(blocksMe);
        Long myId = userIdOf(me);
        Long targetId = userIdOf(target);
        Long anotherId = userIdOf(another);
        Long blocksMeId = userIdOf(blocksMe);

        mockMvc.perform(get("/api/v1/blocks").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks").isArray())
                .andExpect(jsonPath("$.blocks").isEmpty());

        block(myCookie, targetId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", equalTo(targetId), Long.class))
                .andExpect(jsonPath("$.nickname").value(nicknameOf(target)))
                .andExpect(jsonPath("$.createdAt").isString());
        block(myCookie, anotherId).andExpect(status().isCreated());
        block(theirCookie, myId).andExpect(status().isCreated());

        // 새로 차단한 사람이 먼저 온다
        mockMvc.perform(get("/api/v1/blocks").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks.length()").value(2))
                .andExpect(jsonPath("$.blocks[0].userId", equalTo(anotherId), Long.class))
                .andExpect(jsonPath("$.blocks[1].userId", equalTo(targetId), Long.class))
                .andExpect(jsonPath("$.blocks[1].nickname").value(nicknameOf(target)))
                .andExpect(jsonPath("$.blocks[1].createdAt").isString());

        mockMvc.perform(delete("/api/v1/blocks/{id}", targetId).cookie(myCookie)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/blocks").cookie(myCookie))
                .andExpect(jsonPath("$.blocks.length()").value(1))
                .andExpect(jsonPath("$.blocks[0].userId", equalTo(anotherId), Long.class));
        // 내가 푼 것은 내 차단뿐이다 — 남이 나를 차단한 줄은 그대로다
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from blocks where blocker_id = ? and blocked_id = ?",
                Integer.class, blocksMeId, myId)).isEqualTo(1);
        // 풀었으면 다시 차단할 수 있다
        block(myCookie, targetId).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("같은 사람을 두 번 차단하면 409 ALREADY_BLOCKED 이고 줄은 하나다. 서로 차단하는 것은 된다")
    void blockTwice() throws Exception
    {
        String me = newLoginId();
        String target = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        Cookie theirCookie = signupAndLogin(target);
        Long myId = userIdOf(me);
        Long targetId = userIdOf(target);

        block(myCookie, targetId).andExpect(status().isCreated());
        block(myCookie, targetId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BLOCKED"))
                .andExpect(jsonPath("$.details").isArray());
        // 방향이 있는 한 줄이다 — 반대 방향은 다른 줄이다
        block(theirCookie, myId).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from blocks where blocker_id = ? and blocked_id = ?",
                Integer.class, myId, targetId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 차단을 여러 스레드가 동시에 보내면 하나만 201 이고 나머지는 409 다 — DB 의 UNIQUE 가 지킨다")
    void concurrentBlocks() throws Exception
    {
        String me = newLoginId();
        String target = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        signup(target, PASSWORD, nicknameOf(target)).andExpect(status().isCreated());
        Long myId = userIdOf(me);
        Long targetId = userIdOf(target);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try
        {
            for(int i = 0; i < threads; i++)
            {
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return block(myCookie, targetId).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Integer> statuses = new ArrayList<>();
            for(Future<Integer> future : futures)
            {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(statuses).filteredOn(status -> status == 409).hasSize(threads - 1);
        }
        finally
        {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from blocks where blocker_id = ? and blocked_id = ?",
                Integer.class, myId, targetId)).isEqualTo(1);
    }

    @Test
    @DisplayName("자기 자신은 400 CANNOT_BLOCK_SELF, 없는 사용자 · 숫자가 아닌 번호는 404 USER_NOT_FOUND, 빈 본문은 400 이다")
    void invalidTargets() throws Exception
    {
        String me = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        Long myId = userIdOf(me);

        block(myCookie, myId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_BLOCK_SELF"));
        block(myCookie, unknownUserId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        // 숫자가 아닌 번호는 있을 수 없는 사용자다 — 400 이 아니라 없는 사용자와 같은 404 여야 사람의 존재가 새지 않는다
        blockRaw(myCookie, "NOT A VALID ID")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/blocks").cookie(myCookie).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("userId"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from blocks where blocker_id = ?", Integer.class, myId)).isZero();
    }

    @Test
    @DisplayName("해제는 두 번 다 204 다 — 차단한 적이 없어도, 없는 사용자여도 성공이다. 경로의 번호가 숫자가 아니면 400 이다")
    void unblockIsIdempotent() throws Exception
    {
        String me = newLoginId();
        String target = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        signup(target, PASSWORD, nicknameOf(target)).andExpect(status().isCreated());
        Long targetId = userIdOf(target);
        block(myCookie, targetId).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/blocks/{id}", targetId).cookie(myCookie)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/blocks/{id}", targetId).cookie(myCookie)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/blocks/{id}", unknownUserId()).cookie(myCookie)).andExpect(status().isNoContent());
        // 경로 변수는 Long 이다 — 숫자가 아니면 컨트롤러에 닿지 못하고 400 이다
        mockMvc.perform(delete("/api/v1/blocks/{id}", "not-a-number").cookie(myCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("userId"));
    }

    @Test
    @DisplayName("차단은 로그인해야 하고, POST · DELETE 는 Origin 검사를 거친다")
    void requiresLoginAndOrigin() throws Exception
    {
        Cookie myCookie = signupAndLogin(newLoginId());

        mockMvc.perform(get("/api/v1/blocks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/v1/blocks").contentType(MediaType.APPLICATION_JSON).content(json("userId", 1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/blocks/{id}", 1).cookie(myCookie).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("창구 findBlockedEitherWay — 내가 차단했든 나를 차단했든 같이 나온다. 상관없는 사람은 안 나온다")
    void findBlockedEitherWay() throws Exception
    {
        String me = newLoginId();
        String iBlocked = newLoginId();
        String blockedMe = newLoginId();
        String mutual = newLoginId();
        String stranger = newLoginId();
        String thirdParty = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        signup(iBlocked, PASSWORD, nicknameOf(iBlocked)).andExpect(status().isCreated());
        Cookie blockedMeCookie = signupAndLogin(blockedMe);
        Cookie mutualCookie = signupAndLogin(mutual);
        Cookie strangerCookie = signupAndLogin(stranger);
        signup(thirdParty, PASSWORD, nicknameOf(thirdParty)).andExpect(status().isCreated());
        Long myId = userIdOf(me);
        Long iBlockedId = userIdOf(iBlocked);
        Long blockedMeId = userIdOf(blockedMe);
        Long mutualId = userIdOf(mutual);
        Long strangerId = userIdOf(stranger);
        Long thirdPartyId = userIdOf(thirdParty);
        block(myCookie, iBlockedId).andExpect(status().isCreated());
        block(blockedMeCookie, myId).andExpect(status().isCreated());
        block(myCookie, mutualId).andExpect(status().isCreated());
        block(mutualCookie, myId).andExpect(status().isCreated());
        // 나와 상관없는 두 사람 사이의 차단
        block(strangerCookie, thirdPartyId).andExpect(status().isCreated());

        assertThat(blockReader.findBlockedEitherWay(myId, List.of(iBlockedId, blockedMeId, mutualId, strangerId, thirdPartyId)))
                .containsExactlyInAnyOrder(iBlockedId, blockedMeId, mutualId);
        // 물어본 사람들 가운데서만 답한다
        assertThat(blockReader.findBlockedEitherWay(myId, List.of(strangerId, blockedMeId))).containsExactly(blockedMeId);
        assertThat(blockReader.findBlockedEitherWay(myId, List.of(strangerId, thirdPartyId))).isEmpty();
        assertThat(blockReader.findBlockedEitherWay(myId, List.of())).isEmpty();
        // 상대의 눈으로 봐도 같다
        assertThat(blockReader.findBlockedEitherWay(iBlockedId, List.of(myId, strangerId))).containsExactly(myId);
    }

    /** 본문의 {@code userId} 는 상대의 사용자 번호다. JSON 숫자로 보낸다 */
    private ResultActions block(Cookie cookie, Long targetUserId) throws Exception
    {
        return mockMvc.perform(post("/api/v1/blocks").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json("userId", targetUserId)));
    }

    /** 본문의 {@code userId} 를 글자 그대로 보낸다 — 숫자가 아닌 값을 보내 볼 때 쓴다 */
    private ResultActions blockRaw(Cookie cookie, String targetUserId) throws Exception
    {
        return mockMvc.perform(post("/api/v1/blocks").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json("userId", targetUserId)));
    }
}
