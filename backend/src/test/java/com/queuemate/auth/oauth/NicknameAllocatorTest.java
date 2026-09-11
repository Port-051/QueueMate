package com.queuemate.auth.oauth;

import com.queuemate.common.error.ConflictException;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 제공자 닉네임은 우리 제약(2~16자, unique)을 모른다. 여기서 맞춰주지 않으면
 * 소셜 가입이 DB 제약에서 막히는데, 사용자에게는 고칠 입력란이 없다.
 */
class NicknameAllocatorTest {

    private Set<String> taken;
    private NicknameAllocator allocator;

    @BeforeEach
    void setUp() {
        taken = new HashSet<>();
        UserRepository users = mock(UserRepository.class);
        when(users.existsByNickname(anyString())).thenAnswer(i -> taken.contains(i.getArgument(0)));
        allocator = new NicknameAllocator(users);
    }

    @Test
    void 비어_있으면_제공자_닉네임을_그대로_쓴다() {
        assertEquals("칼바람장인", allocator.allocate("칼바람장인"));
    }

    @Test
    void 이미_쓰는_닉네임이면_접미사를_붙인다() {
        taken.add("칼바람장인");
        String allocated = allocator.allocate("칼바람장인");

        assertNotEquals("칼바람장인", allocated);
        assertTrue(allocated.startsWith("칼바람장인_"), allocated);
        assertTrue(allocated.length() <= 16, allocated);
    }

    @Test
    void 열여섯자를_넘으면_자른다() {
        String allocated = allocator.allocate("아주아주아주아주아주아주아주긴닉네임입니다");

        assertEquals(16, allocated.length());
    }

    @Test
    void 잘라도_접미사_자리를_남긴다() {
        String preferred = "아주아주아주아주아주아주아주긴닉네임입니다";
        taken.add(allocator.allocate(preferred));

        String allocated = allocator.allocate(preferred);

        assertTrue(allocated.length() <= 16, allocated);
        assertTrue(allocated.contains("_"), allocated);
    }

    @Test
    void 쓸_수_없는_닉네임이면_기본값으로_바꾼다() {
        // 이모지는 Java가 char 두 개로 세고 Postgres는 하나로 세서 길이 검사가 어긋난다.
        assertEquals("플레이어", allocator.allocate("😀"));
        assertEquals("플레이어", allocator.allocate("!"));
        assertEquals("플레이어", allocator.allocate(null));
    }

    @Test
    void 후보를_다_써버리면_실패로_알린다() {
        // 모든 닉네임이 이미 있는 극단 상황. 무한 재시도 대신 멈춘다.
        UserRepository all = mock(UserRepository.class);
        when(all.existsByNickname(anyString())).thenReturn(true);

        assertThrows(ConflictException.class, () -> new NicknameAllocator(all).allocate("아무개"));
    }
}
