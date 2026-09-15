package com.queuemate.auth.oauth;

import com.queuemate.common.error.ConflictException;
import com.queuemate.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 제공자가 준 닉네임을 users 제약(2~16자, unique)에 맞춘다.
 *
 * 제공자 닉네임은 중복될 수 있고 길이 제한도 우리와 다르다. 그대로 넣으면 가입이 실패하는데,
 * 소셜 로그인에는 사용자가 고칠 입력란이 없어서 그냥 막힌다.
 */
@Component
public class NicknameAllocator {

    private static final int MAX_LENGTH = 16;
    private static final int MIN_LENGTH = 2;
    private static final String FALLBACK = "플레이어";
    private static final int ATTEMPTS = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;

    public NicknameAllocator(UserRepository users) {
        this.users = users;
    }

    public String allocate(String preferred) {
        String base = sanitize(preferred);
        if (!users.existsByNickname(base)) {
            return base;
        }
        for (int i = 0; i < ATTEMPTS; i++) {
            String suffix = String.format("%04d", RANDOM.nextInt(10_000));
            String candidate = truncate(base, MAX_LENGTH - suffix.length() - 1) + "_" + suffix;
            if (!users.existsByNickname(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("NICKNAME_ALLOCATION_FAILED", "닉네임을 만들지 못했다");
    }

    /**
     * 공백 정리와 길이 제한. 서로게이트 페어(이모지)는 버린다. Java는 char 두 개로 세고
     * Postgres char_length는 하나로 세기 때문에, 남겨두면 앱 검사를 통과한 값이 DB 제약에 걸린다.
     */
    private String sanitize(String preferred) {
        if (preferred == null) {
            return FALLBACK;
        }
        StringBuilder sb = new StringBuilder();
        preferred.codePoints()
                .filter(cp -> cp <= 0xFFFF)
                .filter(cp -> Character.isLetterOrDigit(cp) || cp == '_' || cp == ' ')
                .forEach(sb::appendCodePoint);
        String cleaned = truncate(sb.toString().trim().replaceAll("\\s+", " "), MAX_LENGTH);
        return cleaned.length() < MIN_LENGTH ? FALLBACK : cleaned;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
