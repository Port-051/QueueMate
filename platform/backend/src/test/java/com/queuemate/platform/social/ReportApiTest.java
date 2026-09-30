package com.queuemate.platform.social;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신고 — 접수만 받는다 ({@code contracts/platform-api.md} "친구 · 신고 · 최근 함께한 사람").
 * {@code targetUserId} 는 사용자 번호, {@code contextId} 는 글의 id 이고 둘 다 <b>숫자</b>다(2026-09-22 소유자 결정).
 */
class ReportApiTest extends ApiTestSupport {

    @Test
    @DisplayName("신고하면 201 {reportId, createdAt} 이고 RECEIVED 로 저장된다. detail · contextId 는 없어도 되고, 같은 사람을 여러 번 신고할 수 있다")
    void report() throws Exception
    {
        String me = newNickname();
        String target = newNickname();
        Cookie myCookie = login(me);
        insertUser(target);
        Long myId = userIdOf(me);
        Long targetId = userIdOf(target);
        Long contextId = 920_001L;

        report(myCookie, json("targetUserId", targetId, "reason", "ABUSE", "detail", "욕설을 했다", "contextId", contextId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").isNumber())
                .andExpect(jsonPath("$.createdAt").isString());
        // 없어도 되는 칸은 빠져도 · null 이어도 된다
        report(myCookie, json("targetUserId", targetId, "reason", "NO_SHOW")).andExpect(status().isCreated());
        report(myCookie, json("targetUserId", targetId, "reason", "SPAM", "detail", null, "contextId", null)).andExpect(status().isCreated());
        report(myCookie, json("targetUserId", targetId, "reason", "OTHER", "detail", "그 밖의 사유")).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from reports where reporter_id = ? and target_user_id = ?", Integer.class, myId, targetId)).isEqualTo(4);
        Map<String, Object> first = jdbcTemplate.queryForMap(
                "select reason, detail, context_id, status from reports where reporter_id = ? and reason = 'ABUSE'", myId);
        assertThat(first).containsEntry("reason", "ABUSE").containsEntry("detail", "욕설을 했다")
                .containsEntry("context_id", contextId).containsEntry("status", "RECEIVED");
    }

    @Test
    @DisplayName("나를 차단한 사람도 신고할 수 있다 — 신고는 차단 관계를 보지 않는다")
    void reportSomeoneWhoBlockedMe() throws Exception
    {
        String me = newNickname();
        String target = newNickname();
        Cookie myCookie = login(me);
        insertUser(target);
        Long myId = userIdOf(me);
        Long targetId = userIdOf(target);
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", targetId, myId);

        report(myCookie, json("targetUserId", targetId, "reason", "CHEATING")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("자기 자신은 400 CANNOT_REPORT_SELF, 없는 사용자 · 숫자가 아닌 번호는 404 USER_NOT_FOUND 다")
    void invalidTargets() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);

        report(myCookie, json("targetUserId", myId, "reason", "ABUSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REPORT_SELF"));
        report(myCookie, json("targetUserId", unknownUserId(), "reason", "ABUSE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        // 숫자가 아닌 번호는 있을 수 없는 사용자다 — 400 이 아니라 없는 사용자와 같은 404 여야 사람의 존재가 새지 않는다
        report(myCookie, json("targetUserId", "NOT A VALID ID", "reason", "ABUSE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from reports where reporter_id = ?", Integer.class, myId)).isZero();
    }

    @Test
    @DisplayName("OTHER 에 detail 이 없으면(비어 있어도) 400, 모르는 reason · 1000자를 넘는 detail · 숫자가 아닌 contextId · 빈 본문도 400 이다")
    void validation() throws Exception
    {
        String me = newNickname();
        String target = newNickname();
        Cookie myCookie = login(me);
        insertUser(target);
        Long myId = userIdOf(me);
        Long targetId = userIdOf(target);

        report(myCookie, json("targetUserId", targetId, "reason", "OTHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("detail"));
        report(myCookie, json("targetUserId", targetId, "reason", "OTHER", "detail", "   ")).andExpect(status().isBadRequest())
                .andExpect(detailFor("detail"));
        report(myCookie, json("targetUserId", targetId, "reason", "RUDE")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("reason"));
        report(myCookie, json("targetUserId", targetId, "reason", "abuse")).andExpect(status().isBadRequest()).andExpect(detailFor("reason"));
        report(myCookie, json("targetUserId", targetId, "reason", "ABUSE", "detail", "가".repeat(1001))).andExpect(status().isBadRequest())
                .andExpect(detailFor("detail"));
        report(myCookie, json("targetUserId", targetId, "reason", "ABUSE", "contextId", "not-a-number")).andExpect(status().isBadRequest())
                .andExpect(detailFor("contextId"));
        report(myCookie, "{}").andExpect(status().isBadRequest())
                .andExpect(detailFor("targetUserId"))
                .andExpect(detailFor("reason"));
        // 1000자는 된다
        report(myCookie, json("targetUserId", targetId, "reason", "ABUSE", "detail", "가".repeat(1000))).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("select count(*) from reports where reporter_id = ?", Integer.class, myId)).isEqualTo(1);
    }

    @Test
    @DisplayName("신고는 로그인해야 하고, 읽는 요청이 없다 — 처리 화면이 없다")
    void requiresLoginAndNoRead() throws Exception
    {
        Cookie myCookie = login(newNickname());

        mockMvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
                        .content(json("targetUserId", 1, "reason", "ABUSE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/reports").cookie(myCookie)).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions report(Cookie cookie, String body) throws Exception
    {
        return mockMvc.perform(post("/api/v1/reports").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
