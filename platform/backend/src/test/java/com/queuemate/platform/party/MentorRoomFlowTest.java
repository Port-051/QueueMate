package com.queuemate.platform.party;

import com.queuemate.platform.party.service.PostStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.ArrayList;
import jakarta.servlet.http.Cookie;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MentorRoomFlowTest extends PostTestSupport {
    @Autowired PostStore store;

    @Test
    void fullRoomConfirmsAtomicallyAndKeepsPositionsAfterDeparture() throws Exception {
        Cookie host = login(newNickname());
        Long id = createFivePersonLolPost(host);
        var members = new ArrayList<Cookie>();
        for (String position : FIVE_PERSON_LOL_WANTED) {
            String nickname = newNickname(); Cookie cookie = login(nickname); members.add(cookie);
            track(id, userIdOf(nickname));
            enterRoom(cookie, id, position).andExpect(status().isCreated());
        }
        var confirmed = body(mockMvc.perform(get("/api/v1/posts/" + id).cookie(host)).andExpect(status().isOk()));
        assertThat(confirmed.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(texts(confirmed.get("members"), "position")).containsExactlyInAnyOrder("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");
        mockMvc.perform(delete("/api/v1/rooms/" + id + "/members/me").cookie(members.getFirst())).andExpect(status().isNoContent());
        enterRoom(login(newNickname()), id, "TOP").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
        var after = body(mockMvc.perform(get("/api/v1/posts/" + id).cookie(host)));
        assertThat(after.get("members").size()).isEqualTo(5);
        assertThat(texts(after.get("members"), "position")).contains("TOP");
    }

    @Test
    void warningCanBeExtendedOnlyByHostAndOldDeadlineDoesNotCloseExtendedRoom() throws Exception {
        String hostName = newNickname(); Cookie host = login(hostName);
        Long id = createFivePersonLolPost(host);
        String memberName = newNickname(); Cookie member = login(memberName); track(id, userIdOf(memberName));
        enterRoom(member, id, "TOP").andExpect(status().isCreated());
        Instant firstDeadline = store.find(id).orElseThrow().getAutoConfirmAt();
        assertThat(firstDeadline).isNotNull();
        mockMvc.perform(post("/api/v1/rooms/" + id + "/recruitment/extend").cookie(member)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/rooms/" + id + "/recruitment/extend").cookie(host)).andExpect(status().isConflict());
        store.extendRecruitment(userIdOf(hostName), id, firstDeadline.minusSeconds(30));
        Instant nextDeadline = store.find(id).orElseThrow().getAutoConfirmAt();
        assertThat(nextDeadline).isAfter(firstDeadline);
        store.checkAutomaticConfirmation(id, firstDeadline);
        assertThat(statusOf(id)).isEqualTo("RECRUITING");
        store.checkAutomaticConfirmation(id, nextDeadline);
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
        assertThatThrownBy(() -> store.extendRecruitment(userIdOf(hostName), id, nextDeadline)).hasMessageContaining("모집 중");
    }

    @Test
    void returningToOneMemberCancelsDeadline() throws Exception {
        Cookie host = login(newNickname()); Long id = createFivePersonLolPost(host);
        String name = newNickname(); Cookie member = login(name); track(id, userIdOf(name));
        enterRoom(member, id, "TOP").andExpect(status().isCreated());
        Instant deadline = store.find(id).orElseThrow().getAutoConfirmAt();
        mockMvc.perform(delete("/api/v1/rooms/" + id + "/members/me").cookie(member)).andExpect(status().isNoContent());
        assertThat(store.find(id).orElseThrow().getAutoConfirmAt()).isNull();
        store.checkAutomaticConfirmation(id, deadline.plusSeconds(1));
        assertThat(statusOf(id)).isEqualTo("RECRUITING");
    }
}
