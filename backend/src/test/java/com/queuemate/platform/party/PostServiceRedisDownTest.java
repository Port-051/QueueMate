package com.queuemate.platform.party;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import com.queuemate.platform.party.dto.PostResponse;
import com.queuemate.platform.party.dto.PostUpdateRequest;
import com.queuemate.platform.party.service.BoardProperties;
import com.queuemate.platform.party.service.PostEntryGate;
import com.queuemate.platform.party.service.PostService;
import com.queuemate.platform.party.service.PostStore;
import com.queuemate.platform.room.domain.RoomStateUnavailableException;
import com.queuemate.platform.room.service.RoomService;
import com.queuemate.platform.social.service.BlockReader;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * <b>방 키를 못 읽을 때</b> — 방의 상태를 읽는 창구({@link RoomService#states})만 실패하게 만든 {@link RoomService} 를 끼운 {@link PostService} ·
 * {@link PostEntryGate} 로 본다. 나머지(DB · 프로필 · 차단 · gameconfig)는 앱의 진짜 빈이다. 두 클래스에는 트랜잭션이 없어 손으로 만들어도 똑같이 돈다.
 *
 * <p>가장 중요한 것 — <b>방장 키를 "못 읽은 것"을 "방이 없다"로 읽지 않는다.</b> 그러면 Redis 가 흔들릴 때 멀쩡한 글이 전부 만료된다.
 * 그리고 <b>fail-open 인지 fail-closed 인지는 부르는 쪽이 정한다</b> — 목록 · 단건은 open, 고치기 · 입장 검사는 closed 다.
 */
class PostServiceRedisDownTest extends PostTestSupport {

    @Autowired
    PostStore postStore;

    @Autowired
    RoomService roomService;

    @Autowired
    GameProfileReader gameProfileReader;

    @Autowired
    BlockReader blockReader;

    @Autowired
    BoardProperties boardProperties;

    /** gameconfig 는 진짜 빈이다 — 여기서 죽이는 것은 방 키를 읽는 창구뿐이다 */
    @Autowired
    GameConfigReader gameConfig;

    /** 방의 상태 읽기만 실패한다 — 스크립트(만들기 · 확정)는 진짜다. RoomService 에는 트랜잭션 프록시가 없어 그대로 감쌀 수 있다 */
    private RoomService brokenStates()
    {
        RoomService broken = spy(roomService);
        doThrow(new RoomStateUnavailableException(new IllegalStateException("테스트 — Redis 가 죽었다")))
                .when(broken).states(any());
        return broken;
    }

    private PostService withBrokenRedis()
    {
        return new PostService(postStore, brokenStates(), gameProfileReader, blockReader, boardProperties, gameConfig);
    }

    @Test
    @DisplayName("목록 · 단건은 500 이 아니라 방 정보를 비운 채 글을 내려 주고, 어떤 글도 만료시키지 않는다 — 방이 사라진 글도")
    void listDegradesWithoutExpiring() throws Exception
    {
        String host = newNickname();
        String gone = newNickname();
        String viewer = newNickname();
        Cookie hostCookie = login(host);
        login(viewer);
        Long hostId = userIdOf(host);
        Long viewerId = userIdOf(viewer);
        Long seen = createLolPost(hostCookie);
        openRoom(seen, hostId, viewerId);
        // 방이 사라진 글 — 방장 키를 "못 읽으면" 만료시키기 딱 좋은 글이다. 멀쩡한 앱이라면 다음 목록에서 만료된다
        Long vanished = createLolPost(login(gone));
        closeRoom(vanished);

        PostService service = withBrokenRedis();
        PostResponse line = service.list(viewerId, Game.LOL, null, null).posts().stream()
                .filter(post -> post.postId().equals(seen)).findFirst().orElseThrow();

        assertThat(line.status()).isEqualTo("RECRUITING");
        assertThat(line.members()).isEmpty();
        assertThat(line.memberCount()).isZero();
        assertThat(line.full()).isFalse();
        assertThat(line.host().userId()).isEqualTo(hostId);
        assertThat(service.list(viewerId, Game.LOL, null, null).posts()).extracting(PostResponse::postId).contains(seen, vanished);
        assertThat(service.get(viewerId, vanished).status()).isEqualTo("RECRUITING");

        assertThat(statusOf(seen)).isEqualTo("RECRUITING");
        assertThat(statusOf(vanished)).isEqualTo("RECRUITING");
    }

    @Test
    @DisplayName("입장 검사는 503 ROOM_STATE_UNAVAILABLE(+ Retry-After 5) 이다 — 방 안 사람과 차단 대조를 못 했는데 들여보낼 수 없다(fail-closed)")
    void entryGateFailsClosed() throws Exception
    {
        String host = newNickname();
        String guest = newNickname();
        Long postId = createLolPost(login(host));
        login(guest);
        Long guestId = userIdOf(guest);

        PostEntryGate gate = new PostEntryGate(postStore, brokenStates(), blockReader);

        assertThatThrownBy(() -> gate.check(String.valueOf(postId), guestId))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("ROOM_STATE_UNAVAILABLE");
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(5L);
                });
        // 없는 글은 방 키를 읽기 전에 404 다 — Redis 가 죽어도 그 답은 줄 수 있다
        assertThatThrownBy(() -> gate.check(String.valueOf(NO_SUCH_POST), guestId))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("POST_NOT_FOUND"));
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");
    }

    @Test
    @DisplayName("고치기도 503 ROOM_STATE_UNAVAILABLE 이다 — 방에 누가 있는지 모르는데 고치게 하면 '사람이 있으면 못 고친다'가 뚫린다(fail-closed)")
    void editFailsClosed() throws Exception
    {
        String host = newNickname();
        Cookie hostCookie = login(host);
        Long hostId = userIdOf(host);
        Long postId = createLolPost(hostCookie);

        PostService service = withBrokenRedis();

        assertThatThrownBy(() -> service.edit(hostId, postId,
                new PostUpdateRequest(null, "고쳐 보자", null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("ROOM_STATE_UNAVAILABLE");
                });
        assertThat(jdbcTemplate.queryForObject("select title from recruit_posts where id = ?", String.class, postId))
                .isEqualTo("같이 하실 분");
    }

    @Test
    @DisplayName("방 정보가 없어도 방장과의 차단은 거른다 — Redis 가 죽었다고 숨겨진 글이 드러나지 않는다")
    void stillFiltersHostBlocks() throws Exception
    {
        String host = newNickname();
        String blocked = newNickname();
        Cookie hostCookie = login(host);
        login(blocked);
        Long hostId = userIdOf(host);
        Long blockedId = userIdOf(blocked);
        Long postId = createLolPost(hostCookie);
        block(hostCookie, blockedId);

        PostService service = withBrokenRedis();

        assertThat(service.list(blockedId, Game.LOL, null, null).posts()).extracting(PostResponse::postId).doesNotContain(postId);
        assertThat(service.list(hostId, Game.LOL, null, null).posts()).extracting(PostResponse::postId).contains(postId);
    }
}
