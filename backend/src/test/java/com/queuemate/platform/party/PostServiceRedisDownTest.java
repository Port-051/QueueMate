package com.queuemate.platform.party;

import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.account.stats.GameStatsSync;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.party.dto.PostResponse;
import com.queuemate.platform.party.room.RoomStateReader;
import com.queuemate.platform.party.room.RoomStateUnavailableException;
import com.queuemate.platform.party.service.BoardProperties;
import com.queuemate.platform.party.service.PostService;
import com.queuemate.platform.party.service.PostStore;
import com.queuemate.platform.party.service.RoomTicketIssuer;
import com.queuemate.platform.social.service.BlockReader;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>Redis 가 죽었을 때</b> — 방 키를 읽는 창구({@link RoomStateReader})를 실패하는 구현으로 갈아 끼운 {@link PostService} 로 본다.
 * 나머지(DB · 프로필 · 차단 · 입장권)는 앱의 진짜 빈이다. {@link PostService} 에는 트랜잭션이 없어 손으로 만들어도 똑같이 돈다.
 *
 * <p>가장 중요한 것 — <b>방장 키를 "못 읽은 것"을 "방이 없다"로 읽지 않는다.</b> 그러면 Redis 가 흔들릴 때 멀쩡한 글이 전부 만료된다.
 */
class PostServiceRedisDownTest extends PostTestSupport {

    @Autowired
    PostStore postStore;

    @Autowired
    GameProfileReader gameProfileReader;

    @Autowired
    BlockReader blockReader;

    @Autowired
    RoomTicketIssuer roomTicketIssuer;

    @Autowired
    BoardProperties boardProperties;

    @Autowired
    GameStatsSync gameStatsSync;

    private PostService withBrokenRedis()
    {
        RoomStateReader broken = roomIds -> {
            throw new RoomStateUnavailableException(new IllegalStateException("테스트 — Redis 가 죽었다"));
        };
        return new PostService(postStore, broken, gameProfileReader, blockReader, roomTicketIssuer, boardProperties,
                gameStatsSync);
    }

    @Test
    @DisplayName("목록 · 단건은 500 이 아니라 방 정보를 비운 채 글을 내려 주고, 어떤 글도 만료시키지 않는다 — 봤던 방의 글도, 10분이 지난 글도")
    void listDegradesWithoutExpiring() throws Exception
    {
        String host = newLoginId();
        String oldHost = newLoginId();
        String viewer = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        signupAndLogin(viewer);
        Long hostId = userIdOf(host);
        Long viewerId = userIdOf(viewer);
        Long seen = createLolPost(hostCookie);
        Long old = createLolPost(signupAndLogin(oldHost));
        // 멀쩡한 앱이 방을 한 번 봤다 — room_seen_at 이 있다. 이 상태에서 방장 키를 "못 읽으면" 만료시키기 딱 좋은 글이다
        openRoom(seen, hostId, viewerId);
        list(hostCookie, "LOL");
        jdbcTemplate.update("update party.recruit_posts set created_at = now() - interval '11 minutes' where id = ?", old);

        PostService service = withBrokenRedis();
        PostResponse line = service.list(viewerId, "LOL").posts().stream()
                .filter(post -> post.postId().equals(seen)).findFirst().orElseThrow();

        assertThat(line.status()).isEqualTo("RECRUITING");
        assertThat(line.members()).isEmpty();
        assertThat(line.memberCount()).isZero();
        assertThat(line.full()).isFalse();
        assertThat(line.host().userId()).isEqualTo(hostId);
        assertThat(service.list(viewerId, null).posts()).extracting(PostResponse::postId).contains(seen, old);
        assertThat(service.get(viewerId, seen).status()).isEqualTo("RECRUITING");

        assertThat(statusOf(seen)).isEqualTo("RECRUITING");
        assertThat(statusOf(old)).isEqualTo("RECRUITING");
    }

    @Test
    @DisplayName("입장권 · confirm 은 503 ROOM_STATE_UNAVAILABLE 이다 — 차단 대조를 못 했는데 내줄 수 없다(fail-closed)")
    void ticketFailsClosed() throws Exception
    {
        String host = newLoginId();
        String guest = newLoginId();
        Long postId = createLolPost(signupAndLogin(host));
        signupAndLogin(guest);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        openRoom(postId, hostId);
        confirmRoom(postId);

        PostService service = withBrokenRedis();

        assertThatThrownBy(() -> service.ticket(guestId, postId))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("ROOM_STATE_UNAVAILABLE");
                });
        assertThatThrownBy(() -> service.confirm(hostId, postId))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("ROOM_STATE_UNAVAILABLE"));
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");
    }

    @Test
    @DisplayName("방 정보가 없어도 방장과의 차단은 거른다 — Redis 가 죽었다고 숨겨진 글이 드러나지 않는다")
    void stillFiltersHostBlocks() throws Exception
    {
        String host = newLoginId();
        String blocked = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        signupAndLogin(blocked);
        Long hostId = userIdOf(host);
        Long blockedId = userIdOf(blocked);
        Long postId = createLolPost(hostCookie);
        block(hostCookie, blockedId);

        PostService service = withBrokenRedis();

        assertThat(service.list(blockedId, "LOL").posts()).extracting(PostResponse::postId).doesNotContain(postId);
        assertThat(service.list(hostId, "LOL").posts()).extracting(PostResponse::postId).contains(postId);
    }
}
