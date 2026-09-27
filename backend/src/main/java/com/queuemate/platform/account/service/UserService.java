package com.queuemate.platform.account.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import com.queuemate.platform.account.domain.SocialIdentity;
import com.queuemate.platform.account.domain.SocialProvider;
import com.queuemate.platform.account.domain.User;
import com.queuemate.platform.account.dto.GameAccountRequest;
import com.queuemate.platform.account.dto.GameProfileResponse;
import com.queuemate.platform.account.dto.UserResponse;
import com.queuemate.platform.account.repository.GameAccountRepository;
import com.queuemate.platform.account.repository.SocialIdentityRepository;
import com.queuemate.platform.account.repository.UserRepository;
import com.queuemate.platform.account.stats.GameStatsRefresher;
import com.queuemate.platform.account.stats.LolStatsProvider;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 내 프로필과 게임 계정. 전부 "로그인한 나"의 것만 다룬다 — 남의 프로필을 번호로 조회하는 요청은 없다
 * (공개 사용자 탐색은 만들지 않는다 — CLAUDE.md §1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** 마이그레이션(V1__schema.sql)이 붙인 제약의 이름이다 */
    static final String GAME_ACCOUNTS_USER_FK = "game_accounts_user_id_fkey";

    private final UserRepository userRepository;
    private final GameAccountRepository gameAccountRepository;
    private final SocialIdentityRepository socialIdentityRepository;
    private final GameStatsRefresher gameStatsRefresher;
    private final GameConfigReader gameConfig;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public UserResponse me(Long userId)
    {
        return response(requireUser(userId));
    }

    /** 닉네임을 바꾼다. 중복은 소셜 가입과 같은 방식으로 안다 — 먼저 조회하지 않고 UPDATE 의 제약 위반을 409 로 옮긴다 */
    @Transactional
    public UserResponse changeNickname(Long userId, String nickname)
    {
        User user = requireUser(userId);
        user.changeNickname(nickname, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        try
        {
            userRepository.saveAndFlush(user);
        }
        catch(DataIntegrityViolationException e)
        {
            throw AuthService.translate(e);
        }
        log.info("닉네임 변경 userId={}", userId);
        return response(user);
    }

    /**
     * 게임 계정을 연결한다 — 없으면 만들고 있으면 바꾼다. 게임마다 하나다. 게임에 따라 길이 둘이다(2026-09-27 소유자 결정).
     * <ul>
     *   <li><b>LoL — 티어 · 전적은 Riot 에서 채운다.</b> 본문은 {@code gameNickname}(이름#태그)과 선택인 {@code mainPosition} 이고 {@code tier} · {@code server} 를 보내면 400 이다.
     *       주 포지션은 "지금 하고 싶은 포지션"이라 사용자가 정한다(Riot 의 최근 경기에서 뽑지 않는다 — 같은 날 소유자 결정).
     *       <b>저장하기 전에 Riot 을 긁는다</b>(동기 · 상한 30초) — 티어는 솔로랭크에서 채우고 응답에 {@code stats} 까지 들어 있다.
     *       이름#태그가 Riot 에 없으면 404 {@code RIOT_ID_NOT_FOUND}, Riot 을 못 부르면 503 — <b>둘 다 저장하지 않는다</b>({@link GameStatsRefresher#link})</li>
     *   <li><b>VALORANT · PUBG — 자기신고.</b> 게임사 API 가 없어 적은 대로 저장하고 긁지 않는다</li>
     * </ul>
     *
     * <p><b>{@code @Transactional} 이 없다 — 붙이면 안 된다.</b> LoL 은 최대 30초를 기다리므로 그동안 DB 커넥션을 붙잡으면 커넥션 풀이 마른다.
     * 저장은 짧은 트랜잭션으로 따로 한다(LoL 은 {@code stats.GameStatsStore#link}, 자기신고는 {@link #saveSelfReported}).
     */
    public GameProfileResponse putGameAccount(Long userId, Game game, GameAccountRequest request)
    {
        try
        {
            if(game == Game.LOL)
            {
                return GameProfileResponse.from(linkFromRiot(userId, request));
            }
            return saveSelfReported(userId, game, request);
        }
        catch(DataIntegrityViolationException e)
        {
            // 토큰은 멀쩡한데 그 사용자가 DB 에 없다 — 먼저 조회해서 확인하지 않고 FK 위반으로 안다
            if(GAME_ACCOUNTS_USER_FK.equals(ConstraintViolations.nameOf(e)))
            {
                throw ApiException.unauthenticated();
            }
            throw e;
        }
    }

    /** LoL — 요청은 이름#태그와 (선택) 주 포지션이다. 티어 · 서버를 보내면 400(전부 Riot 을 부르기 전에 거른다) */
    private GameAccountWithStats linkFromRiot(Long userId, GameAccountRequest request)
    {
        rejectForRiot("tier", request.tier());
        rejectForRiot("server", request.server());
        if(!Game.LOL.allowsPosition(request.mainPosition()))
        {
            throw ApiException.validationFailed("mainPosition", Game.LOL.name() + " 의 포지션이 아닙니다");
        }
        if(!LolStatsProvider.isRiotId(request.gameNickname()))
        {
            throw ApiException.validationFailed("gameNickname", "LOL 은 '이름#태그' 여야 합니다");
        }
        return gameStatsRefresher.link(userId, Game.LOL, request.gameNickname(), request.mainPosition());
    }

    private static void rejectForRiot(String field, String value)
    {
        if(value != null)
        {
            throw ApiException.validationFailed(field, "LOL 은 Riot 에서 채웁니다 — 보내지 마세요");
        }
    }

    /** VALORANT · PUBG — 자기신고를 검증해 그대로 적는다. 명령 하나(gameconfig)는 DB 에 쓰기 전이다 */
    private GameProfileResponse saveSelfReported(Long userId, Game game, GameAccountRequest request)
    {
        // 티어는 gameconfig 의 사다리에 있는 이름이어야 한다 (2026-09-24 소유자 결정 — contracts/platform-api.md "gameconfig 를 읽는 것").
        // 자기신고라 안 적을 수 있다 — 값이 있을 때만 본다
        if(request.tier() != null && !gameConfig.hasTier(game, request.tier()))
        {
            throw ApiException.validationFailed("tier", game.name() + " 의 티어가 아닙니다");
        }
        if(!game.allowsPosition(request.mainPosition()))
        {
            throw ApiException.validationFailed("mainPosition", game.positions().isEmpty()
                    ? game.name() + " 에는 포지션이 없습니다"
                    : game.name() + " 의 포지션이 아닙니다");
        }
        if(!game.allowsServer(request.server()))
        {
            throw ApiException.validationFailed("server", game.servers().isEmpty()
                    ? game.name() + " 에는 서버가 없습니다"
                    : "STEAM · KAKAO 가운데 하나여야 합니다");
        }
        return transactionTemplate.execute(status -> {
            gameAccountRepository.upsert(userId, game.name(), request.gameNickname(), request.tier(),
                    request.mainPosition(), request.server(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
            // 요청에서 되짚어 만들지 않고 다시 읽는다 — verified · stats 는 요청에 없는 칸이라 DB 에만 있다
            return gameAccountRepository.findWithStatsByUserIdAndGame(userId, game)
                    .map(GameProfileResponse::from)
                    .orElseThrow(() -> new IllegalStateException(
                            "방금 넣은 게임 계정이 없다 userId=" + userId + " game=" + game));
        });
    }

    /**
     * <b>전적 갱신</b> — 지금 게임사 API 에서 다시 받아 적고 <b>갱신된 게임 프로필</b>을 준다(2026-09-24 소유자 결정 ·
     * {@code contracts/platform-api.md} "전적을 긁는 것"). 게임 계정을 저장할 때만 갱신되던 것을 사용자가 원할 때 하는 길이다.
     *
     * <p><b>{@code @Transactional} 이 없다 — 붙이면 안 된다.</b> 최대 30초를 기다리므로 그동안 DB 커넥션을 붙잡으면 커넥션 풀이 마른다.
     * 읽고 쓰는 것은 {@code stats} 쪽이 각자 짧은 트랜잭션으로 한다({@code stats.GameStatsStore}).
     * 거절과 상한은 {@link GameStatsRefresher} 가 정한다 — 이 메서드는 게임 이름만 보고 넘긴다.
     */
    public GameProfileResponse refreshGameStats(Long userId, Game game)
    {
        return GameProfileResponse.from(gameStatsRefresher.refresh(userId, game));
    }

    /** 게임 계정 연결을 끊는다. 없어도 성공이다 — 두 번 눌러도 결과가 같다 */
    @Transactional
    public void deleteGameAccount(Long userId, Game game)
    {
        gameAccountRepository.deleteByUserIdAndGame(userId, game);
    }

    /** {@code GET · PATCH /users/me} 가 같은 모양을 내려 준다 — 소셜 연결 · 게임 프로필을 붙인다 */
    private UserResponse response(User user)
    {
        List<SocialProvider> socialProviders = socialIdentityRepository.findByUserId(user.getId()).stream()
                .map(identity -> identity.getId().getProvider())
                .toList();
        return UserResponse.of(user, socialProviders,
                gameAccountRepository.findWithStatsByUserId(user.getId()));
    }

    /**
     * 토큰의 사용자가 DB 에 없으면 401 이다 — 지금은 탈퇴가 없어 일어나지 않지만, access 토큰은 스스로 검증되는 것이라
     * (denylist 가 없다 — CLAUDE.md §5.1 (라)) 계정이 없어진 뒤에도 만료까지 서명은 유효하다.
     */
    private User requireUser(Long userId)
    {
        return userRepository.findById(userId).orElseThrow(ApiException::unauthenticated);
    }

}
