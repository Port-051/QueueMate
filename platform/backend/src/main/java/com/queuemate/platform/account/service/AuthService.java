package com.queuemate.platform.account.service;

import com.queuemate.platform.account.domain.User;
import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.repository.UserRepository;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.common.security.RefreshTokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 재발급 — refresh 토큰으로 "누구인가"까지만 낸다. 토큰을 찍고 쿠키에 싣는 것은 컨트롤러가 한다.
 *
 * <p><b>직접 가입 · 비밀번호 로그인은 없다</b>(2026-09-26 소유자 결정) — 가입 · 로그인은 소셜로만 하고({@link SocialLoginService}),
 * 사용자의 식별자는 사용자 번호({@code userId}) 하나 · 보여 주는 이름은 닉네임 하나다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 마이그레이션(V1__schema.sql)이 붙인 제약의 이름이다. 거기서 바꾸면 여기도 바꾼다 */
    static final String USERS_NICKNAME_UNIQUE = "users_nickname_key";

    private final UserRepository userRepository;
    private final RefreshTokens refreshTokens;

    /**
     * <b>재발급</b> — 쿠키의 refresh 토큰으로 "누구인가"까지만 낸다(새 토큰을 찍고 쿠키에 싣는 것은 컨트롤러다).
     * 2026-09-23 소유자 결정이고 계약은 {@code contracts/platform-api.md} "refresh 토큰" 이다.
     *
     * <p><b>쓴 값은 그 자리에서 버린다(rotation)</b> — {@link RefreshTokens#consume} 가 {@code GETDEL} 한 번으로 한다.
     * 그래서 같은 값으로 두 번째 재발급은 통하지 않는다.
     *
     * <p><b>실패의 이유를 가르지 않는다</b> — 쿠키가 없든 · 꼴이 아니든 · 이미 쓴 값이든 · 그 사용자가 사라졌든 · Redis 에 묻지 못했든
     * 전부 빈 값이고, 컨트롤러가 <b>글자까지 같은 401 {@code INVALID_REFRESH_TOKEN}</b> 으로 답한다. 어느 쪽인지 알려 주면 값이 살아 있는지가 새어 나간다.
     *
     * <p><b>트랜잭션이 없다</b> — Redis 한 번과 사용자 한 줄을 읽을 뿐이고 아무것도 쓰지 않는다(지우는 것은 Redis 쪽이다).
     */
    public Optional<AuthResponse> refresh(String refreshToken)
    {
        OptionalLong userId = refreshTokens.consume(refreshToken);
        if(userId.isEmpty())
        {
            // 토큰 값은 남기지 않는다 — 로그를 보는 사람이 남의 세션을 이을 수 있다
            log.warn("재발급 거절 — 쓸 수 없는 refresh 토큰이다");
            return Optional.empty();
        }
        Optional<User> user = userRepository.findById(userId.getAsLong());
        if(user.isEmpty())
        {
            // 값은 멀쩡했는데 그 사이에 계정이 없어졌다. 값은 이미 버려졌다
            log.warn("재발급 거절 — 없는 사용자다 userId={}", userId.getAsLong());
            return Optional.empty();
        }
        User found = user.orElseThrow();
        log.info("재발급 userId={}", found.getId());
        return Optional.of(new AuthResponse(found.getId(), found.getNickname()));
    }

    /** 제약의 이름으로 가른다. 모르는 위반은 그대로 던진다(500) — 아는 에러 코드로 둔갑시키지 않는다 */
    static RuntimeException translate(DataIntegrityViolationException e)
    {
        if(USERS_NICKNAME_UNIQUE.equals(ConstraintViolations.nameOf(e)))
        {
            return new ApiException(HttpStatus.CONFLICT, "NICKNAME_TAKEN", "이미 쓰고 있는 닉네임입니다");
        }
        return e;
    }
}
