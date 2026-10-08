package com.queuemate.platform.party;

import com.queuemate.platform.party.repository.RecruitPostRepository;
import com.queuemate.platform.party.service.PostStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecruitmentDisabledTest extends PostTestSupport {
    @Autowired PostStore store;
    @Autowired RecruitPostRepository posts;

    @Test void noDeadlineOrWarningAndOldDeadlineCannotCloseRoomButManualCloseWorks() throws Exception {
        var host = login(newNickname());
        Long id = createFivePersonLolPost(host);
        enterRoom(login(newNickname()), id, "TOP").andExpect(status().isCreated());
        assertThat(store.find(id).orElseThrow().getAutoConfirmAt()).isNull();
        var post = posts.findById(id).orElseThrow();
        post.setAutoConfirmAt(Instant.now().minusSeconds(120));
        posts.saveAndFlush(post);
        store.checkAutomaticConfirmation(id, Instant.now());
        mockMvc.perform(get("/api/v1/posts/" + id).cookie(host))
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.autoConfirmAt").isEmpty())
                .andExpect(jsonPath("$.autoConfirmWarningAt").isEmpty());
        mockMvc.perform(post("/api/v1/rooms/" + id + "/recruitment/extend").cookie(host))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECRUITMENT_NOT_EXPIRING"));
        mockMvc.perform(post("/api/v1/rooms/" + id + "/confirm").cookie(host)).andExpect(status().isNoContent());
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    @Test void reachingCapacityStillConfirms() throws Exception {
        var host = login(newNickname());
        Long id = createFivePersonLolPost(host);
        for (String position : FIVE_PERSON_LOL_WANTED) enterRoom(login(newNickname()), id, position).andExpect(status().isCreated());
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }
}
