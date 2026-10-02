package com.queuemate.platform.social.controller;

import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.common.web.Ids;
import com.queuemate.platform.social.service.DirectMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class DirectMessageController {
    private final DirectMessageService service;
    public record Send(@NotNull UUID clientMessageId, @NotBlank @Size(max = 2000) String text) {}

    @GetMapping
    public List<DirectMessageService.Thread> threads(@CurrentUserId Long me) { return service.threads(me); }

    @GetMapping("/{userId}")
    public DirectMessageService.Conversation conversation(@CurrentUserId Long me, @PathVariable String userId,
                                                          @RequestParam(required = false) Long before) {
        return service.conversation(me, Ids.parse(userId).orElse(null), before);
    }

    @PostMapping("/{userId}")
    public DirectMessageService.Message send(@CurrentUserId Long me, @PathVariable String userId, @Valid @RequestBody Send body) {
        return service.send(me, Ids.parse(userId).orElse(null), body.clientMessageId(), body.text());
    }
}
