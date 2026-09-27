package com.queuemate.party.room;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/rooms")
@ConditionalOnProperty(name="queuemate.rooms.enabled", havingValue="true")
public class RoomApi {
    public record TierRange(String minTier, String maxTier) {}
    public record Profile(@NotNull @Size(max=5) List<@NotBlank String> roles,
                          @NotNull @Size(max=120) String bio) {}
    public record Settings(@NotNull GameKey game, @NotBlank String modeKey,
                           @Pattern(regexp="REALTIME|RESERVATION") @NotNull String type,
                           @NotBlank @Size(max=120) String title, @Min(2) @Max(5) int capacity,
                           @NotNull @Size(max=5) List<@NotBlank String> desiredRoles,
                           TierRange desiredTierRange,
                           @NotNull @Pattern(regexp="REQUIRED|NO_VOICE") String voice, String availableFrom) {}
    public record Create(@NotNull UUID requestId, @NotNull @Valid Settings input, @NotNull @Valid Profile profile) {}
    public record Join(@NotNull @Valid Profile profile, String role, UUID fromRoomId) {}
    public record Action(@NotNull @Pattern(regexp="LEAVE|KICK|CONFIRM") String action, UUID memberId) {}
    public record Message(@NotNull UUID clientMessageId, @NotBlank @Size(max=2000) String text) {}
    private final RoomService service;
    public RoomApi(RoomService service) { this.service = service; }
    @GetMapping public List<Map<String,Object>> list(CurrentUser user) { return service.list(user.userId()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(CurrentUser user, @Valid @RequestBody Create body) { return service.create(user.userId(),body); }
    @PostMapping("/{id}/join")
    public Map<String,Object> join(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody Join body) { return service.join(user.userId(),id,body); }
    @PostMapping("/{id}/actions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void action(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody Action body) { service.action(user.userId(),id,body); }
    @PostMapping("/{id}/messages")
    public Map<String,Object> message(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody Message body) { return service.send(user.userId(),id,body); }
}
