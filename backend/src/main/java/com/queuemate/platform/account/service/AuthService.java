package com.queuemate.platform.account.service;

import com.queuemate.platform.account.domain.Credential;
import com.queuemate.platform.account.domain.User;
import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.dto.LoginRequest;
import com.queuemate.platform.account.dto.SignupRequest;
import com.queuemate.platform.account.repository.CredentialRepository;
import com.queuemate.platform.account.repository.UserRepository;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.common.security.RefreshTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 가입과 로그인. 토큰을 찍고 쿠키에 싣는 것은 컨트롤러가 한다 — 여기는 "이 사람이 맞는가"까지다.
 *
 * <p>로그인 아이디({@code loginId})는 <b>여기서만</b> 사람을 찾는 열쇠다 — 토큰 · 채널 · URL 등 그 밖의 모든 곳은 사용자 번호({@code userId})를 쓴다
 * (2026-09-22 소유자 결정 — CLAUDE.md §3.5).
 */
@Slf4j
@Service
public class AuthService {

    /** 마이그레이션(V1__schema.sql)이 붙인 제약의 이름이다. 거기서 바꾸면 여기도 바꾼다 */
    static final String USERS_LOGIN_ID_UNIQUE = "users_login_id_key";
    static final String USERS_NICKNAME_UNIQUE = "users_nickname_key";

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile(User.LOGIN_ID_PATTERN);
    /** BCrypt 가 보는 최대 길이(바이트). 이보다 긴 비밀번호는 가입에서 걸러지므로 맞을 수가 없다 */
    private static final int BCRYPT_MAX_BYTES = 72;
    /** 너무 긴 비밀번호 대신 비교에 넣는 값. 빈 문자열이면 인코더가 비교를 건너뛸 수 있어 시간이 달라진다 */
    private static final String TOO_LONG_STAND_IN = "too-long-password";

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle loginThrottle;
    private final RefreshTokens refreshTokens;
    /** 없는 아이디로 로그인할 때 대신 비교하는 해시. 어떤 비밀번호와도 맞지 않는다 — 원문을 버렸다 */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, CredentialRepository credentialRepository,
                       PasswordEncoder passwordEncoder, LoginThrottle loginThrottle, RefreshTokens refreshTokens)
    {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginThrottle = loginThrottle;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 가입. <b>중복은 DB 가 막는다</b> — 있는지 먼저 조회하지 않고 INSERT 한 뒤 제약 위반을 409 로 옮긴다.
     * 동시에 같은 로그인 아이디로 두 요청이 와도 UNIQUE 가 하나만 통과시킨다. 사용자 번호는 DB 가 매긴다(IDENTITY).
     */
    @Transactional
    public AuthResponse signup(SignupRequest request)
    {
        // 밀리초까지만 남긴다 — 알림 봉투의 occurredAt 과 같은 정밀도다 (CLAUDE.md §3.2)
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String passwordHash = passwordEncoder.encode(request.password());
        User user;
        try
        {
            // id 가 null 인 새 엔티티라 persist 다 — 반드시 INSERT 가 나가고 그때 번호를 받는다. flush 로 위반을 지금 드러낸다
            user = userRepository.saveAndFlush(new User(request.loginId(), request.nickname(), now));
            credentialRepository.saveAndFlush(new Credential(user.getId(), passwordHash, now));
        }
        catch(DataIntegrityViolationException e)
        {
            // 여기서 던지는 ApiException 이 트랜잭션을 되돌린다 — 위반이 난 트랜잭션은 어차피 더 쓸 수 없다
            throw translate(e);
        }
        log.info("가입 userId={} loginId={}", user.getId(), user.getLoginId());
        return new AuthResponse(user.getId(), user.getLoginId(), user.getNickname());
    }

    /**
     * 로그인. 아이디가 없든 비밀번호가 틀리든 <b>같은 401 {@code INVALID_CREDENTIALS}</b> 다 — 어느 아이디가 있는지 알려 주지 않는다.
     *
     * <p>응답 시간으로도 새지 않게 한다 — 아이디가 없을 때도 해시 비교를 한 번 한다. BCrypt 비교는 일부러 느려서(수십 ms)
     * 건너뛰면 "없는 아이디"의 응답만 눈에 띄게 빠르다.
     *
     * <p><b>실패 제한</b>({@link LoginThrottle}) — 로그인 아이디 단위로 센다(사용자를 찾기 전에 세야 한다 — 없는 아이디도 센다). 잠긴 동안은
     * <b>비밀번호를 비교하지 않고</b> 429 다(맞는 비밀번호여도 그렇다 — 비교해 주면 잠금이 추측을 늦추지 못한다). <b>없는 아이디에도 똑같이 센다</b> —
     * 잠기는지로 아이디의 존재가 새지 않게 한다. 형식이 틀린 아이디는 세지 않는다 — 있을 수 없는 계정이고, 아무 문자열로나 Redis 키를 만들게 두지 않는다.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request)
    {
        // 형식이 아닌 아이디는 DB 에 있을 수 없다(CHECK). 조회만 건너뛰고 비교는 똑같이 한다
        boolean wellFormedId = LOGIN_ID_PATTERN.matcher(request.loginId()).matches();
        if(wellFormedId)
        {
            OptionalLong lockedFor = loginThrottle.lockedForSeconds(request.loginId());
            if(lockedFor.isPresent())
            {
                log.warn("로그인 거절(잠금) loginId={} retryAfter={}s", request.loginId(), lockedFor.getAsLong());
                throw ApiException.retryAfter(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_LOGIN_ATTEMPTS",
                        "로그인 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요", lockedFor.getAsLong());
            }
        }
        // 소셜로만 가입한 사람은 users 는 있고 credentials 가 없다 — 없는 아이디와 같은 길로 간다
        Optional<User> user = wellFormedId ? userRepository.findByLoginId(request.loginId()) : Optional.empty();
        Optional<Credential> credential = user.flatMap(found -> credentialRepository.findById(found.getId()));

        // 72바이트를 넘는 비밀번호는 맞을 수가 없다. 인코더에 그대로 넘기지 않고(길이를 이유로 예외를 던질 수 있다) 시간만 맞춘다
        boolean tooLong = request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
        String hash = credential.map(Credential::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(tooLong ? TOO_LONG_STAND_IN : request.password(), hash);

        if(credential.isEmpty() || tooLong || !matches)
        {
            // 아이디는 남기고 비밀번호는 절대 남기지 않는다. 형식이 아닌 아이디는 값도 남기지 않는다 —
            // 아이디 칸에 비밀번호를 친 것일 수 있고, 줄바꿈이 든 값은 로그를 어지럽힌다
            log.warn("로그인 실패 loginId={}", wellFormedId ? request.loginId() : "(형식이 아닌 아이디)");
            if(wellFormedId)
            {
                loginThrottle.recordFailure(request.loginId());
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다");
        }

        User found = user.orElseThrow();
        loginThrottle.clear(found.getLoginId());
        log.info("로그인 userId={} loginId={}", found.getId(), found.getLoginId());
        return new AuthResponse(found.getId(), found.getLoginId(), found.getNickname());
    }

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
     * <b>로그인 실패 제한을 걸지 않는다</b> — 그것은 로그인 아이디 단위로 세는 것이고, 여기에는 아이디가 없다.
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
        return Optional.of(new AuthResponse(found.getId(), found.getLoginId(), found.getNickname()));
    }

    /** 제약의 이름으로 가른다. 모르는 위반은 그대로 던진다(500) — 아는 에러 코드로 둔갑시키지 않는다 */
    static RuntimeException translate(DataIntegrityViolationException e)
    {
        String constraint = ConstraintViolations.nameOf(e);
        if(USERS_LOGIN_ID_UNIQUE.equals(constraint))
        {
            return new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_TAKEN", "이미 쓰고 있는 아이디입니다");
        }
        if(USERS_NICKNAME_UNIQUE.equals(constraint))
        {
            return new ApiException(HttpStatus.CONFLICT, "NICKNAME_TAKEN", "이미 쓰고 있는 닉네임입니다");
        }
        return e;
    }
}
