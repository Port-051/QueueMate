package com.queuemate.user.service;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.user.api.UserDtos.CreateGameAccountRequest;
import com.queuemate.user.api.UserDtos.UpdateUserRequest;
import com.queuemate.user.domain.GameAccount;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.GameAccountRepository;
import com.queuemate.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final GameAccountRepository gameAccounts;

    public UserService(UserRepository users, GameAccountRepository gameAccounts) {
        this.users = users;
        this.gameAccounts = gameAccounts;
    }

    @Transactional(readOnly = true)
    public User getById(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없다"));
    }

    @Transactional
    public User update(UUID userId, UpdateUserRequest request) {
        User user = getById(userId);
        // 닉네임은 비울 수 없다. 키를 보냈는데 값이 null이면 요청이 잘못된 것이다.
        if (request.nicknamePresent()) {
            if (request.nickname() == null) {
                throw new IllegalArgumentException("nickname은 비울 수 없다");
            }
            if (!request.nickname().equals(user.getNickname())) {
                if (users.existsByNickname(request.nickname())) {
                    throw new ConflictException("NICKNAME_ALREADY_IN_USE", "이미 사용 중인 닉네임이다");
                }
                user.changeNickname(request.nickname());
            }
        }
        // 아바타는 비울 수 있다. 키를 보냈으면 값 그대로 반영한다. null이면 삭제다.
        if (request.avatarUrlPresent()) {
            user.changeAvatarUrl(request.avatarUrl());
        }
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("NICKNAME_ALREADY_IN_USE", "이미 사용 중인 닉네임이다");
        }
    }

    @Transactional(readOnly = true)
    public List<GameAccount> listGameAccounts(UUID userId) {
        return gameAccounts.findAllByUserId(userId);
    }

    @Transactional
    public GameAccount linkGameAccount(UUID userId, CreateGameAccountRequest request) {
        if (gameAccounts.existsByUserIdAndProviderGameAndExternalGameId(
                userId, request.game(), request.externalGameId())) {
            throw new ConflictException("GAME_ACCOUNT_ALREADY_LINKED", "이미 연결된 게임 계정이다");
        }
        try {
            return gameAccounts.saveAndFlush(
                    GameAccount.create(userId, request.game(), request.externalGameId(), request.region()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("GAME_ACCOUNT_ALREADY_LINKED", "이미 연결된 게임 계정이다");
        }
    }

    /** 남의 계정을 지울 수 없도록 소유자까지 함께 조회한다 (docs/13 Authorization). */
    @Transactional
    public void unlinkGameAccount(UUID userId, UUID gameAccountId) {
        GameAccount account = gameAccounts.findByIdAndUserId(gameAccountId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "GAME_ACCOUNT_NOT_FOUND", "게임 계정을 찾을 수 없다"));
        gameAccounts.delete(account);
    }
}
