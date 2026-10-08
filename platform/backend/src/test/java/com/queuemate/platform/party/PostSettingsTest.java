package com.queuemate.platform.party;

import com.queuemate.platform.party.service.PostStore;
import com.queuemate.platform.room.service.RoomMemberService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PostSettingsTest extends PostTestSupport {
    @Autowired PostStore store;
    @Autowired RoomMemberService memberService;

    private String settings(String mode, String host, String... wanted) {
        return postBodyWithHostPosition("LOL", mode, "수정한 방", "{}", host, wanted)
                .replace("REQUIRED", "NO_VOICE").replace("\"allowAutoJoin\":true", "\"allowAutoJoin\":false");
    }
    private org.springframework.test.web.servlet.ResultActions update(Cookie cookie, long id, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/posts/" + id).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    @Test void savesConditionsAndSynchronizesAvailableSeats() throws Exception {
        String name = newNickname(); Cookie host = login(name);
        Long id = createLolPost(host, "TOP");
        update(host, id, settings(LOL_MODE_2, "MID", "TOP", "JUNGLE", "ADC", "SUPPORT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.title").value("수정한 방")).andExpect(jsonPath("$.voice").value("NO_VOICE"))
                .andExpect(jsonPath("$.hostPosition").value("MID")).andExpect(jsonPath("$.allowAutoJoin").value(false));
        assertThat(positionOf(id, userIdOf(name))).isEqualTo("MID");
        assertThat(redisTemplate.opsForSet().members(needsKey(id))).containsExactlyInAnyOrder("TOP", "JUNGLE", "ADC", "SUPPORT");
        assertThat(redisTemplate.getExpire(needsKey(id))).isPositive();
        enterRoom(login(newNickname()), id, "JUNGLE").andExpect(status().isCreated());
        update(host, id, settings(LOL_MODE_2, "MID", "TOP", "JUNGLE", "ADC", "SUPPORT")).andExpect(status().isOk());
        assertThat(redisTemplate.opsForSet().members(needsKey(id))).doesNotContain("JUNGLE");
    }
    @Test void rejectsNonHostAndRollsBackDatabaseAndRedis() throws Exception {
        Cookie host = login(newNickname()), other = login(newNickname());
        Long id = createFivePersonLolPost(host);
        String body = settings(LOL_MODE_2, "JUNGLE", FIVE_PERSON_LOL_WANTED);
        update(other, id, body).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_HOST"));
        mockMvc.perform(get("/api/v1/posts/" + id).cookie(host)).andExpect(jsonPath("$.title").value("같이 하실 분"));
        assertThat(redisTemplate.opsForSet().members(needsKey(id))).containsExactlyInAnyOrder(FIVE_PERSON_LOL_WANTED);
    }
    @Test void rejectsOccupiedPositionAndTooSmallCapacityWithoutPartialWrites() throws Exception {
        Cookie host = login(newNickname()); Long id = createFivePersonLolPost(host);
        enterRoom(login(newNickname()), id, "TOP").andExpect(status().isCreated());
        enterRoom(login(newNickname()), id, "MID").andExpect(status().isCreated());
        update(host, id, settings(LOL_MODE_2, "TOP", "JUNGLE", "MID", "ADC", "SUPPORT"))
                .andExpect(status().isBadRequest());
        update(host, id, settings(LOL_MODE, "JUNGLE", FIVE_PERSON_LOL_WANTED)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/posts/" + id).cookie(host)).andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.hostPosition").value("JUNGLE")).andExpect(jsonPath("$.voice").value("REQUIRED"));
        assertThat(redisTemplate.opsForSet().members(needsKey(id))).containsExactlyInAnyOrder("ADC", "SUPPORT");
    }
    @Test void shrinkingToCurrentCountConfirmsAndAllowsOnlyMetadataAfterwards() throws Exception {
        Cookie host = login(newNickname()); Long id = createFivePersonLolPost(host);
        enterRoom(login(newNickname()), id, "TOP").andExpect(status().isCreated());
        String body = settings(LOL_MODE, "JUNGLE", "TOP");
        update(host, id, body).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        update(host, id, body.replace("수정한 방", "마감 후 새 제목").replace("NO_VOICE", "REQUIRED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("마감 후 새 제목"));
        update(host, id, settings(LOL_MODE_2, "JUNGLE", FIVE_PERSON_LOL_WANTED)).andExpect(status().isBadRequest());
        update(host, id, body.replace("즐겁게", "변경할 수 없음"))
                .andExpect(status().isBadRequest());
    }
    @Test void currentHostAfterSuccessionCanEditMetadataButFormerHostCannot() throws Exception {
        Cookie host = login(newNickname()), next = login(newNickname());
        Long id = createLolPost(host, "TOP"); enterRoom(next, id, "TOP").andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/rooms/" + id + "/members/me").cookie(host)).andExpect(status().isNoContent());
        String body = settings(LOL_MODE, "JUNGLE", "TOP").replace("\"allowAutoJoin\":false", "\"allowAutoJoin\":true");
        update(host, id, body).andExpect(status().isForbidden());
        update(next, id, body).andExpect(status().isOk()).andExpect(jsonPath("$.voice").value("NO_VOICE"));
    }
    @Test void closedRoomDoesNotSaveAndStaleAutoJoinCandidateCannotEnter() throws Exception {
        Cookie host = login(newNickname()); Long id = createFivePersonLolPost(host);
        var previous = store.find(id).orElseThrow().getUpdatedAt();
        update(host, id, settings(LOL_MODE_2, "JUNGLE", FIVE_PERSON_LOL_WANTED)).andExpect(status().isOk());
        String guest = newNickname(); login(guest);
        assertThatThrownBy(() -> memberService.enter(String.valueOf(id), String.valueOf(userIdOf(guest)), "TOP", previous))
                .hasMessageContaining("모집 중");
        closeRoom(id);
        update(host, id, settings(LOL_MODE_2, "JUNGLE", FIVE_PERSON_LOL_WANTED).replace("수정한 방", "저장 안 됨"))
                .andExpect(status().isNotFound());
        assertThat(store.find(id).orElseThrow().getTitle()).isEqualTo("수정한 방");
    }
    @Test void entryAndCapacityChangeAreSerialized() throws Exception {
        Cookie host = login(newNickname()); Long id = createFivePersonLolPost(host);
        enterRoom(login(newNickname()), id, "TOP").andExpect(status().isCreated());
        Cookie guest = login(newNickname()); CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var edit = pool.submit(() -> { start.await(); return update(host, id, settings(LOL_MODE, "JUNGLE", "TOP", "MID")).andReturn().getResponse().getStatus(); });
            var entry = pool.submit(() -> { start.await(); return enterRoom(guest, id, "MID").andReturn().getResponse().getStatus(); });
            start.countDown(); int edited = edit.get(10, TimeUnit.SECONDS), entered = entry.get(10, TimeUnit.SECONDS);
            if (edited == 200) { assertThat(entered).isEqualTo(409); assertThat(memberIds(id)).hasSize(2); }
            else { assertThat(edited).isEqualTo(400); assertThat(entered).isEqualTo(201); assertThat(store.find(id).orElseThrow().getCapacity()).isEqualTo(5); }
        }
    }
}
