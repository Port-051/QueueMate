package com.queuemate.social.api;

import com.queuemate.common.security.CurrentUser;
import com.queuemate.social.api.SocialDtos.CreateFriendRequest;
import com.queuemate.social.api.SocialDtos.Direction;
import com.queuemate.social.api.SocialDtos.FriendRequestView;
import com.queuemate.social.api.SocialDtos.FriendView;
import com.queuemate.social.domain.FriendRequest;
import com.queuemate.social.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FriendController {

    private final FriendService friendService;
    private final SocialViewAssembler assembler;

    public FriendController(FriendService friendService, SocialViewAssembler assembler) {
        this.friendService = friendService;
        this.assembler = assembler;
    }

    @GetMapping("/friends")
    public List<FriendView> friends(CurrentUser currentUser) {
        return assembler.toFriendViews(
                currentUser.userId(), friendService.listFriends(currentUser.userId()));
    }

    @DeleteMapping("/friends/{userId}")
    public ResponseEntity<Void> removeFriend(CurrentUser currentUser, @PathVariable UUID userId) {
        friendService.remove(currentUser.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/friend-requests")
    public List<FriendRequestView> requests(
            CurrentUser currentUser,
            @RequestParam(defaultValue = "RECEIVED") Direction direction) {
        List<FriendRequest> requests = friendService.listPending(
                currentUser.userId(), direction == Direction.RECEIVED);
        return assembler.toRequestViews(requests, direction);
    }

    @PostMapping("/friend-requests")
    public ResponseEntity<FriendRequestView> request(
            CurrentUser currentUser, @Valid @RequestBody CreateFriendRequest request) {
        FriendRequest created = friendService.request(currentUser.userId(), request.targetUserId());
        FriendRequestView view = assembler.toRequestViews(List.of(created), Direction.SENT).getFirst();
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PostMapping("/friend-requests/{id}/accept")
    public FriendView accept(CurrentUser currentUser, @PathVariable UUID id) {
        return assembler.toFriendView(
                currentUser.userId(), friendService.accept(currentUser.userId(), id));
    }

    @PostMapping("/friend-requests/{id}/decline")
    public ResponseEntity<Void> decline(CurrentUser currentUser, @PathVariable UUID id) {
        friendService.decline(currentUser.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/friend-requests/{id}")
    public ResponseEntity<Void> cancel(CurrentUser currentUser, @PathVariable UUID id) {
        friendService.cancel(currentUser.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
