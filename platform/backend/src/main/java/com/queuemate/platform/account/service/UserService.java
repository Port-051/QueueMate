package com.queuemate.platform.account.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import com.queuemate.platform.account.domain.GameTiers;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 게임 계정을 연결한다 — 없으면 만들고 있으면 바꾼다. 게임마다 하나다. 게임에 따라 길이 둘이다(2026-09-27 · 2026-09-29 소유자 결정).
     * <ul>
     *   <li><b>LoL — 티어 · 전적은 Riot 에서 채운다.</b> 본문은 {@code gameNickname}(이름#태그) 하나이고 {@code tier} · {@code server} 를 보내면 400 이다.
     *       <b>저장하기 전에 Riot 을 긁는다</b>(동기 · 상한 30초) — 티어는 솔로랭크 · 자유랭크 두 사다리를 채우고(P-36) 응답에 {@code stats} 까지 들어 있다.
     *       이름#태그가 Riot 에 없으면 404 {@code RIOT_ID_NOT_FOUND}, Riot 을 못 부르면 503 — <b>둘 다 저장하지 않는다</b>({@link GameStatsRefresher#link})</li>
     *   <li><b>PUBG — 티어 · 전적은 PUBG API 에서 채운다</b>(2026-09-29 소유자 결정 "LoL 처럼 동기로" — P-36). 본문은 {@code gameNickname} · {@code server}(필수)이고
     *       {@code tier} 를 보내면 400 이다. 같은 길로 저장하기 전에 긁는다 — 그 서버에 그 닉네임이 없으면 404 {@code PUBG_PLAYER_NOT_FOUND}, 못 부르면 503.
     *       <b>둘 다 저장하지 않는다</b></li>
     *   <li><b>VALORANT — 자기신고.</b> 게임사 API 가 없어 적은 대로 저장하고 긁지 않는다. {@code tier} 는 그 게임의 사다리 하나({@code COMPETITIVE})에 적는다(P-36)</li>
     * </ul>
     * <b>주 포지션은 어느 게임도 받지 않는다</b>(2026-09-29 소유자 결정 — P-35). {@code mainPosition} 이 오면 이 메서드에 닿기 전에
     * {@code @Valid} 가 400 으로 거른다({@link GameAccountRequest}).
     *
     * <p><b>{@code @Transactional} 이 없다 — 붙이면 안 된다.</b> LoL · PUBG 는 최대 30초를 기다리므로 그동안 DB 커넥션을 붙잡으면 커넥션 풀이 마른다.
     * 저장은 짧은 트랜잭션으로 따로 한다(LoL · PUBG 는 {@code stats.GameStatsStore#link}, 자기신고는 {@link #saveSelfReported}).
     */
    public GameProfileResponse putGameAccount(Long userId, Game game, GameAccountRequest request)
    {
        try
        {
            if(game == Game.LOL)
            {
                return GameProfileResponse.from(linkFromRiot(userId, request));
            }
            if(game == Game.PUBG)
            {
                return GameProfileResponse.from(linkFromPubg(userId, request));
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

    /** LoL — 요청은 이름#태그 하나다. 티어 · 서버를 보내면 400(전부 Riot 을 부르기 전에 거른다) */
    private GameAccountWithStats linkFromRiot(Long userId, GameAccountRequest request)
    {
        rejectForRiot("tier", request.tier());
        rejectForRiot("server", request.server());
        if(!LolStatsProvider.isRiotId(request.gameNickname()))
        {
            throw ApiException.validationFailed("gameNickname", "LOL 은 '이름#태그' 여야 합니다");
        }
        return gameStatsRefresher.link(userId, Game.LOL, request.gameNickname(), null);
    }

    private static void rejectForRiot(String field, String value)
    {
        if(value != null)
        {
            throw ApiException.validationFailed(field, "LOL 은 Riot 에서 채웁니다 — 보내지 마세요");
        }
    }

    /**
     * PUBG — 요청은 닉네임 · 서버다(2026-09-29 — P-36). 티어를 보내면 400, 서버가 없거나 {@code STEAM} · {@code KAKAO} 가 아니면 400
     * (전부 PUBG 를 부르기 전에 거른다 — 서버가 곧 PUBG 의 shard 라 없으면 물어볼 수 없다). 닉네임은 대소문자까지 그대로 보낸다
     */
    private GameAccountWithStats linkFromPubg(Long userId, GameAccountRequest request)
    {
        if(request.tier() != null)
        {
            throw ApiException.validationFailed("tier", "PUBG 는 PUBG API 에서 채웁니다 — 보내지 마세요");
        }
        if(request.server() == null)
        {
            throw ApiException.validationFailed("server", "필요합니다");
        }
        if(!Game.PUBG.servers().contains(request.server()))
        {
            throw ApiException.validationFailed("server", "STEAM · KAKAO 가운데 하나여야 합니다");
        }
        return gameStatsRefresher.link(userId, Game.PUBG, request.gameNickname(), request.server());
    }

    /**
     * VALORANT — 자기신고를 검증해 그대로 적는다. 명령 하나(gameconfig)는 DB 에 쓰기 전이다.
     * <b>{@code tier} 는 그 게임의 사다리 하나에 적는다</b>(2026-09-29 — P-36. VALORANT 는 {@code COMPETITIVE} 하나다).
     * 사다리가 둘 이상인 게임은 요청 한 칸으로 어느 사다리인지 알 수 없어 받지 않는다(LoL · PUBG 는 애초에 여기 오지 않는다). {@code tier} 가 없으면 {@code tiers} 는 비운다({@code {}}).
     */
    private GameProfileResponse saveSelfReported(Long userId, Game game, GameAccountRequest request)
    {
        // 티어는 gameconfig 의 사다리에 있는 이름이어야 한다 (2026-09-24 소유자 결정 — contracts/platform-api.md "gameconfig 를 읽는 것").
        // 자기신고라 안 적을 수 있다 — 값이 있을 때만 본다
        if(request.tier() != null && !gameConfig.hasTier(game, request.tier()))
        {
            throw ApiException.validationFailed("tier", game.name() + " 의 티어가 아닙니다");
        }
        if(!game.allowsServer(request.server()))
        {
            throw ApiException.validationFailed("server", game.servers().isEmpty()
                    ? game.name() + " 에는 서버가 없습니다"
                    : "STEAM · KAKAO 가운데 하나여야 합니다");
        }
        String tiers = GameTiers.write(game, selfReportedTiers(game, request.tier()));
        return transactionTemplate.execute(status -> {
            gameAccountRepository.upsert(userId, game.name(), request.gameNickname(), tiers,
                    request.server(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
            // 요청에서 되짚어 만들지 않고 다시 읽는다 — verified · stats 는 요청에 없는 칸이라 DB 에만 있다
            return gameAccountRepository.findWithStatsByUserIdAndGame(userId, game)
                    .map(GameProfileResponse::from)
                    .orElseThrow(() -> new IllegalStateException(
                            "방금 넣은 게임 계정이 없다 userId=" + userId + " game=" + game));
        });
    }

    /** 자기신고 {@code tier} 를 그 게임의 사다리 하나에 — 사다리가 하나인 게임만 부른다({@code null} 이면 빈 맵) */
    private static Map<String, String> selfReportedTiers(Game game, String tier)
    {
        if(game.tierLadders().size() != 1)
        {
            throw new IllegalStateException(game + " 은 사다리가 하나가 아니라 자기신고 티어를 한 칸으로 받을 수 없다");
        }
        Map<String, String> tiers = new HashMap<>();
        tiers.put(game.tierLadders().get(0), tier);
        return tiers;
    }

    // (전적 갱신 refreshGameStats 는 2026-09-30 소유자 결정으로 없앴다 — 전적은 로그인 · 재발급 때 뒤에서 다시 받는다.
    //  stats.GameStatsLoginRefresher · contracts/platform-api.md P-42)

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
     * 토큰의 사용자가 DB 에 없으면 401 이다 — 탈퇴한 사람(2026-10-02 — {@link AccountDeletionService})의 남은 access 토큰이다. access 토큰은 스스로 검증되는 것이라
     * (denylist 가 없다 — CLAUDE.md §5.1 (라)) 계정이 없어진 뒤에도 만료(최대 15분)까지 서명은 유효하다.
     */
    private User requireUser(Long userId)
    {
        return userRepository.findById(userId).orElseThrow(ApiException::unauthenticated);
    }

}
