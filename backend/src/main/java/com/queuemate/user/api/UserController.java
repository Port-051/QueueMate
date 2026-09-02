package com.queuemate.user.api;

import com.queuemate.common.security.CurrentUser;
import com.queuemate.user.api.UserDtos.CreateGameAccountRequest;
import com.queuemate.user.api.UserDtos.GameAccountView;
import com.queuemate.user.api.UserDtos.UpdateUserRequest;
import com.queuemate.user.api.UserDtos.UserProfileResponse;
import com.queuemate.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserProfileResponse me(CurrentUser currentUser) {
        return UserProfileResponse.from(userService.getById(currentUser.userId()));
    }

    @PatchMapping
    public UserProfileResponse update(CurrentUser currentUser,
                                      @Valid @RequestBody UpdateUserRequest request) {
        return UserProfileResponse.from(userService.update(currentUser.userId(), request));
    }

    @GetMapping("/game-accounts")
    public List<GameAccountView> gameAccounts(CurrentUser currentUser) {
        return userService.listGameAccounts(currentUser.userId()).stream()
                .map(GameAccountView::from)
                .toList();
    }

    @PostMapping("/game-accounts")
    public ResponseEntity<GameAccountView> linkGameAccount(
            CurrentUser currentUser, @Valid @RequestBody CreateGameAccountRequest request) {
        GameAccountView view = GameAccountView.from(
                userService.linkGameAccount(currentUser.userId(), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @DeleteMapping("/game-accounts/{id}")
    public ResponseEntity<Void> unlinkGameAccount(CurrentUser currentUser, @PathVariable UUID id) {
        userService.unlinkGameAccount(currentUser.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
