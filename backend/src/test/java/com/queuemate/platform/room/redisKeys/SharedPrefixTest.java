package com.queuemate.platform.room.redisKeys;

import com.queuemate.platform.common.push.PushChannels;
import com.queuemate.platform.party.match.MatchPartyKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code matching} 과 나눠 쓰는 접두사가 그쪽 코드와 글자까지 같은지 본다 (docs/11 D-19).
 *
 * <p>어긋나도 컴파일과 나머지 테스트는 전부 통과한다 — 이 앱은 늘 "활성 요청 없음"을 보고 매칭 중인 사람을
 * 입장시키고, {@code matching} 은 방에 있는 사람의 매칭 요청을 받는다. 그래서 옆 폴더의 원본을 직접 읽어 비교한다.
 * <b>옆 폴더가 없는 컴퓨터(이 브랜치만 받은 경우)에서는 건너뛴다.</b>
 */
class SharedPrefixTest {

    /** 옆 폴더 matching 안의 원본 파일 — 이 폴더 기준 상대 경로 */
    private static final String SHARED_KEYS_IN_MATCHING = "backend/src/main/java/com/queuemate/matching/redisKeys/SharedKeys.java";

    /**
     * matching 폴더를 찾는 곳. gradle 은 테스트를 backend/ 에서 돌리므로 보통은 {@code ../../matching} 이다. 그런데 {@code qm-test} 는 backend/ 를
     * {@code /tmp/qm-build/…} 로 복사해 돌려서 상대 경로가 닿지 않는다 — 그래서 환경변수 {@code QM_MATCHING_DIR} 와 이 컴퓨터의 절대 경로도 본다
     */
    private static final List<Path> MATCHING_DIRS = candidates();

    private static List<Path> candidates()
    {
        List<Path> dirs = new ArrayList<>();
        String env = System.getenv("QM_MATCHING_DIR");
        if (env != null && !env.isBlank())
        {
            dirs.add(Path.of(env.trim()));
        }
        dirs.add(Path.of("../../matching"));
        dirs.add(Path.of("/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching"));
        return dirs;
    }

    @Test
    @DisplayName("활성 요청 키 접두사가 원본(matching 의 SharedKeys)과 같다")
    void activeRequestPrefixMatchesMatching() throws IOException
    {
        assertThat(matchingSharedKeys())
                .contains("ACTIVE_REQUEST_PREFIX = \"" + SharedKeys.ACTIVE_REQUEST_PREFIX + "\"");
    }

    @Test
    @DisplayName("입장 표시 키 접두사를 matching 이 똑같이 따라 적었다")
    void activeRoomPrefixMatchesMatching() throws IOException
    {
        assertThat(matchingSharedKeys())
                .contains("ACTIVE_ROOM_PREFIX = \"" + RoomKeys.ACTIVE_ROOM_PREFIX + "\"");
    }

    @Test
    @DisplayName("알림 채널 접두사가 원본(matching 의 SharedKeys)과 같다")
    void pushChannelPrefixMatchesMatching() throws IOException
    {
        assertThat(matchingSharedKeys())
                .contains("PUSH_CHANNEL_PREFIX = \"" + PushChannels.PUSH_CHANNEL_PREFIX + "\"");
    }

    @Test
    @DisplayName("확정된 파티 HASH 의 접두사가 원본(matching 의 SharedKeys)과 같다 — 이 앱은 읽기만 한다(D-42)")
    void partyPrefixMatchesMatching() throws IOException
    {
        assertThat(matchingSharedKeys())
                .contains("PARTY_PREFIX = \"" + MatchPartyKeys.PARTY_PREFIX + "\"");
    }

    private static String matchingSharedKeys() throws IOException
    {
        for (Path dir : MATCHING_DIRS)
        {
            Path file = dir.resolve(SHARED_KEYS_IN_MATCHING);
            if (Files.exists(file))
            {
                return Files.readString(file);
            }
        }
        assumeTrue(false, "옆 폴더 matching 이 없다: " + MATCHING_DIRS);
        return "";
    }
}
