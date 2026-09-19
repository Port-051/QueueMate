package com.queuemate.notification.redisKeys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PushChannelsTest {

    /**
     * 작업 디렉터리({@code notification/backend}) 기준으로 본 옆 폴더 {@code matching} 의 원본 파일.
     * CI 나 Docker 빌드에는 옆 폴더가 없다 — 그때는 비교 테스트를 건너뛴다.
     */
    private static final Path MATCHING_SHARED_KEYS = Path.of("..", "..", "matching", "backend", "src", "main", "java",
            "com", "queuemate", "matching", "redisKeys", "SharedKeys.java");

    private static final Pattern PREFIX_DECLARATION =
            Pattern.compile("String\\s+PUSH_CHANNEL_PREFIX\\s*=\\s*\"([^\"]*)\"");

    // 이 값은 matching 의 SharedKeys.PUSH_CHANNEL_PREFIX 와 같아야 한다.
    // 바꾸면 알림이 조용히 전부 끊긴다 - 발행 쪽은 구독자 0명을 실패로 보지 않고 버리기 때문이다.
    // 이 테스트가 깨졌다면 테스트를 고치지 말고 matching 과 같이 바꾸는 것인지 먼저 확인하라 (CLAUDE.md §3)
    @Test
    @DisplayName("PUSH_CHANNEL_PREFIX 는 리터럴 \"qm:pubsub:push:\" 다")
    void 접두사는_정해진_리터럴이다() {
        assertThat(PushChannels.PUSH_CHANNEL_PREFIX).isEqualTo("qm:pubsub:push:");
    }

    @Test
    @DisplayName("PUSH_CHANNEL_PREFIX 는 matching 의 SharedKeys.PUSH_CHANNEL_PREFIX 와 같다 (옆 폴더가 없으면 건너뛴다)")
    void 접두사는_matching_원본과_같다() throws IOException {
        assumeTrue(Files.isRegularFile(MATCHING_SHARED_KEYS),
                "옆 폴더 matching 이 없어 건너뛴다: " + MATCHING_SHARED_KEYS.toAbsolutePath().normalize());

        String source = Files.readString(MATCHING_SHARED_KEYS, StandardCharsets.UTF_8);
        Matcher matcher = PREFIX_DECLARATION.matcher(source);

        assertThat(matcher.find())
                .as("matching 의 SharedKeys.java 에서 PUSH_CHANNEL_PREFIX 선언을 찾지 못했다")
                .isTrue();
        assertThat(PushChannels.PUSH_CHANNEL_PREFIX).isEqualTo(matcher.group(1));
    }

    @Test
    @DisplayName("pushChannel 은 접두사 뒤에 userId 를 붙인다")
    void pushChannel_은_접두사에_userId_를_붙인다() {
        assertThat(PushChannels.pushChannel("u123")).isEqualTo("qm:pubsub:push:u123");
    }

    @Test
    @DisplayName("pushChannel 과 userIdOf 는 왕복한다")
    void pushChannel_과_userIdOf_는_왕복한다() {
        for (String userId : new String[]{"u1", "123", "a:b:c", "한글유저", "7f9c2b1e-0000-4000-8000-000000000000"}) {
            assertThat(PushChannels.userIdOf(PushChannels.pushChannel(userId))).isEqualTo(userId);
        }
    }

    @Test
    @DisplayName("userIdOf 는 접두사가 다른 채널에 null 을 돌려준다")
    void 접두사가_다른_채널은_null_이다() {
        assertThat(PushChannels.userIdOf("qm:party:u1")).isNull();
        assertThat(PushChannels.userIdOf("qm:pubsub:pushX:u1")).isNull();
        assertThat(PushChannels.userIdOf("x" + PushChannels.pushChannel("u1"))).isNull();
        assertThat(PushChannels.userIdOf("")).isNull();
    }

    @Test
    @DisplayName("userIdOf 는 null 입력에 null 을 돌려준다")
    void null_입력은_null_이다() {
        assertThat(PushChannels.userIdOf(null)).isNull();
    }
}
