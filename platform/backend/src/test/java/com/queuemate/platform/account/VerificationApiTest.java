package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VerificationApiTest extends ApiTestSupport {
    @Test
    void returnsOnlyBadgeStatusAndDeduplicatesUsers() throws Exception {
        String name = newNickname();
        var cookie = login(name);
        long id = userIdOf(name);
        long other = insertUser();
        jdbcTemplate.update("INSERT INTO game_accounts(user_id, game, game_nickname, verified, tiers, created_at, updated_at) VALUES (?, 'VALORANT', 'verification-fixture', true, '{}'::jsonb, now(), now())", id);
        mockMvc.perform(get("/api/v1/users/verification").cookie(cookie).param("userIds", id + "," + other + "," + id + "," + unknownUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].verified").value(true))
                .andExpect(jsonPath("$[1].verified").value(false))
                .andExpect(jsonPath("$[2].verified").value(false))
                .andExpect(jsonPath("$[0].gameNickname").doesNotExist())
                .andExpect(jsonPath("$[0].profile").doesNotExist());
        jdbcTemplate.update("UPDATE game_accounts SET verified = false WHERE user_id = ?", id);
        mockMvc.perform(get("/api/v1/users/verification").cookie(cookie).param("userIds", String.valueOf(id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].verified").value(false));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/verification").param("userIds", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void limitsBatchSizeAndRejectsInvalidIds() throws Exception {
        var cookie = login(newNickname());
        for (String ids : new String[]{"", "-1", "abc", String.join(",", Collections.nCopies(101, "1"))}) {
            mockMvc.perform(get("/api/v1/users/verification").cookie(cookie).param("userIds", ids)).andExpect(status().isBadRequest());
        }
    }
}
