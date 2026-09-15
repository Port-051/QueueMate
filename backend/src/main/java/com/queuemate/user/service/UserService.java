package com.queuemate.user.service;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.user.api.UserDtos.CreateGameAccountRequest;
import com.queuemate.user.api.UserDtos.UpdateUserRequest;
import com.queuemate.user.domain.GameAccount;
import com.queuemate.user.domain.User;
import com.queuemate.user.avatar.AvatarService;
import com.queuemate.user.repository.GameAccountRepository;
import com.queuemate.user.riot.RiotRankService;
import com.queuemate.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final GameAccountRepository gameAccounts;
    private final AvatarService avatars;
    private final RiotRankService riotRanks;

    public UserService(UserRepository users, GameAccountRepository gameAccounts,
                       AvatarService avatars, RiotRankService riotRanks) {
        this.users = users;
        this.gameAccounts = gameAccounts;
        this.avatars = avatars;
        this.riotRanks = riotRanks;
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
        String replacedAvatarUrl = null;
        if (request.avatarUrlPresent()) {
            replacedAvatarUrl = user.getAvatarUrl();
            user.changeAvatarUrl(request.avatarUrl());
        }
        User saved;
        try {
            saved = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("NICKNAME_ALREADY_IN_USE", "이미 사용 중인 닉네임이다");
        }
        // 프리셋으로 바꾸거나 지워도 직전에 업로드했던 파일은 남는다. 여기서 치운다.
        avatars.discardReplaced(replacedAvatarUrl, saved.getAvatarUrl());
        return saved;
    }

    /**
     * 목록을 보여주는 김에 오래된 티어를 다시 읽는다.
     *
     * <p>readOnly가 아닌 이유가 이것이다. 조회 경로에서 쓰기가 일어나는 것은 예외적이지만,
     * 대안은 아무도 보지 않는 계정까지 갱신하는 스케줄러이고 외부 API에는 호출 한도가 있다.
     * Riot을 못 읽으면 아무것도 쓰지 않으므로 평소에는 읽기만 하는 것과 같다.
     */
    @Transactional
    public List<GameAccount> listGameAccounts(UUID userId) {
        List<GameAccount> accounts = gameAccounts.findAllByUserId(userId);
        if (riotRanks.refreshStale(accounts, OffsetDateTime.now())) {
            gameAccounts.saveAll(accounts);
        }
        return accounts;
    }

    @Transactional
    public GameAccount linkGameAccount(UUID userId, CreateGameAccountRequest request) {
        if (gameAccounts.existsByUserIdAndProviderGameAndExternalGameId(
                userId, request.game(), request.externalGameId())) {
            throw new ConflictException("GAME_ACCOUNT_ALREADY_LINKED", "이미 연결된 게임 계정이다");
        }
        GameAccount account;
        try {
            account = gameAccounts.saveAndFlush(
                    GameAccount.create(userId, request.game(), request.externalGameId(), request.region()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("GAME_ACCOUNT_ALREADY_LINKED", "이미 연결된 게임 계정이다");
        }
        // 연결하자마자 티어를 읽어 둔다. 실패는 삼킨다. Riot이 죽었다고 연결이 막히면 안 된다.
        riotRanks.syncNewlyLinked(account, OffsetDateTime.now());
        return gameAccounts.saveAndFlush(account);
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
