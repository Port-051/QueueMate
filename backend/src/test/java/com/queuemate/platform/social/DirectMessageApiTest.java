package com.queuemate.platform.social;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DirectMessageApiTest extends ApiTestSupport {
    @Test
    void deliveryPersistsAndRetriesDoNotDuplicateOrLeakToThirdUser() throws Exception {
        String aName = newNickname(), bName = newNickname(), cName = newNickname();
        var a = login(aName); var b = login(bName); var c = login(cName);
        long aId = userIdOf(aName), bId = userIdOf(bName);
        String message = "{\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"text\":\"다음 판 같이 해요\"}";
        for (int i = 0; i < 2; i++) mockMvc.perform(post("/api/v1/messages/" + bId).cookie(a).contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("다음 판 같이 해요"));
        mockMvc.perform(get("/api/v1/messages/" + aId).cookie(b)).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1));
        mockMvc.perform(get("/api/v1/messages/" + aId).cookie(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0));
        mockMvc.perform(get("/api/v1/messages").cookie(c)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/messages/" + aId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/messages/" + userIdOf(cName)).cookie(a).contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isConflict());
    }

    @Test
    void blockedRecipientAndInvalidTextCannotReceiveMessages() throws Exception {
        String aName = newNickname(), bName = newNickname(); var a = login(aName); var b = login(bName);
        long aId = userIdOf(aName), bId = userIdOf(bName);
        mockMvc.perform(post("/api/v1/messages/" + bId).cookie(a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/blocks").cookie(b).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + aId + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/messages/" + bId).cookie(a)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/messages/" + bId).cookie(a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"text\":\"안녕하세요\"}"))
                .andExpect(status().isNotFound());
    }
}
