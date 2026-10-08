package com.queuemate.platform.social;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.account.repository.UserRepository;
import com.queuemate.platform.account.service.AccountDeletionService;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.security.RefreshTokens;
import com.queuemate.platform.party.service.PostService;
import com.queuemate.platform.room.service.RoomMemberService;
import com.queuemate.platform.room.service.RoomService;
import com.queuemate.platform.social.redisKeys.BlockKeys;
import com.queuemate.platform.social.repository.BlockRepository;
import com.queuemate.platform.social.service.BlockReader;
import com.queuemate.platform.social.service.BlockRelationRedis;
import com.queuemate.platform.social.service.BlockRelationSync;
import com.queuemate.platform.social.service.BlockService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>차단 관계 사본</b> — Redis SET {@code qm:user:block-rel:{userId}}(2026-10-02 소유자 결정 "차단 관계를 Redis 로 — matching 이 DB 를 안 쓰게" ·
 * docs/11 D-57 · {@code contracts/platform-api.md} P-52). {@code matching} 이 합류 스크립트에서 {@code SISMEMBER} 로 읽으므로 이 앱이 적은 모양이 곧 약속이다 —
 * 대칭(A 가 B 를 차단하면 둘 다) · 번호의 십진 문자열 · 수명 없음.
 *
 * <p>가장 중요한 것 — <b>"DB 에는 있는데 사본에는 없다"(덜 막기)가 생기지 않는다.</b> 사본이 못 따라오면 차단 · 해제가 503 으로 DB 째 되돌아가고,
 * 해제의 커밋이 실패하면 사본에 되돌려 넣고, 재구성은 차단 · 해제와 한 줄로 선다.
 *
 * <p>Redis 가 죽은 경우는 앱 전체의 Redis 를 죽일 수 없어 <b>아무도 듣지 않는 포트의 Redis 를 끼운 사본 쓰기</b>로 본다({@code AccountDeletionTest} 와 같다).
 * 손으로 만든 서비스에는 트랜잭션 프록시가 없어 {@link TransactionTemplate} 으로 감싼다({@code FriendPushRedisDownTest} 와 같다).
 */
class BlockRelationRedisTest extends ApiTestSupport {

    @Autowired
    BlockService blockService;

    @Autowired
    BlockRepository blockRepository;

    @Autowired
    BlockReader blockReader;

    @Autowired
    BlockRelationRedis blockRelationRedis;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    RedisScript<Long> addBlockRelScript;

    @Autowired
    RedisScript<Long> removeBlockRelScript;

    @Autowired
    RedisScript<Long> replaceBlockRelScript;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoomService roomService;

    @Autowired
    RoomMemberService roomMemberService;

    @Autowired
    PostService postService;

    @Autowired
    RefreshTokens refreshTokens;

    /** 아무도 듣지 않는 포트(1)의 Redis — 사본 쓰기만 죽인다 */
    private LettuceConnectionFactory dead;

    /** 사용자가 아닌 번호의 키(재구성이 지워야 하는 것) — 남으면 끝에 지운다 */
    private String ghostKey;

    @BeforeEach
    void openDeadRedis()
    {
        dead = new LettuceConnectionFactory("localhost", 1);
        dead.afterPropertiesSet();
    }

    @AfterEach
    void closeDeadRedis()
    {
        dead.destroy();
        if(ghostKey != null)
        {
            redisTemplate.delete(ghostKey);
        }
    }

    // ---- 차단 · 해제가 사본을 고친다 ----

    @Test
    @DisplayName("차단하면 두 사람의 집합에 서로의 번호가 들어간다 — 대칭이고 값은 사용자 번호의 십진 문자열 · 수명 없음")
    void blockAddsBothWays() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);
        Long targetId = insertUser();

        block(myCookie, targetId).andExpect(status().isCreated());

        assertThat(copyOf(myId)).containsExactly(String.valueOf(targetId));
        assertThat(copyOf(targetId)).containsExactly(String.valueOf(myId));
        assertThat(redisTemplate.getExpire(BlockKeys.key(myId))).isEqualTo(-1L);
        assertThat(redisTemplate.getExpire(BlockKeys.key(targetId))).isEqualTo(-1L);
    }

    @Test
    @DisplayName("해제하면 두 집합에서 다 빠진다(빈 집합은 키째 없어진다). 차단한 적 없는 해제는 204 이고 사본을 건드리지 않는다")
    void unblockRemovesBothWays() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);
        Long targetId = insertUser();
        Long otherId = insertUser();
        block(myCookie, targetId).andExpect(status().isCreated());
        block(myCookie, otherId).andExpect(status().isCreated());

        unblock(myCookie, targetId).andExpect(status().isNoContent());

        assertThat(copyOf(myId)).containsExactly(String.valueOf(otherId));
        assertThat(redisTemplate.hasKey(BlockKeys.key(targetId))).isFalse();
        assertThat(copyOf(otherId)).containsExactly(String.valueOf(myId));

        // 이미 푼 차단을 또 풀어도 204 — 지운 줄이 없으니 사본도 그대로다
        unblock(myCookie, targetId).andExpect(status().isNoContent());
        assertThat(copyOf(myId)).containsExactly(String.valueOf(otherId));
    }

    @Test
    @DisplayName("상대도 나를 차단했으면(반대 방향 줄) 내가 풀어도 관계가 남아 사본은 그대로다 — 상대까지 풀어야 빠진다")
    void reverseRowKeepsTheRelation() throws Exception
    {
        String alice = newNickname();
        String bob = newNickname();
        Cookie aliceCookie = login(alice);
        Cookie bobCookie = login(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        block(aliceCookie, bobId).andExpect(status().isCreated());
        block(bobCookie, aliceId).andExpect(status().isCreated());

        unblock(aliceCookie, bobId).andExpect(status().isNoContent());

        assertThat(blockRows(aliceId, bobId)).isZero();
        assertThat(blockRows(bobId, aliceId)).isEqualTo(1);
        assertThat(copyOf(aliceId)).containsExactly(String.valueOf(bobId));
        assertThat(copyOf(bobId)).containsExactly(String.valueOf(aliceId));

        unblock(bobCookie, aliceId).andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey(BlockKeys.key(aliceId))).isFalse();
        assertThat(redisTemplate.hasKey(BlockKeys.key(bobId))).isFalse();
    }

    @Test
    @DisplayName("거절된 차단(자기 자신 400 · 없는 사용자 404 · 이미 차단 409)은 사본을 건드리지 않는다")
    void rejectedBlocksLeaveTheCopyAlone() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);
        Long targetId = insertUser();
        Long nobody = unknownUserId();

        block(myCookie, myId).andExpect(status().isBadRequest());
        block(myCookie, nobody).andExpect(status().isNotFound());
        assertThat(redisTemplate.hasKey(BlockKeys.key(myId))).isFalse();
        assertThat(redisTemplate.hasKey(BlockKeys.key(nobody))).isFalse();

        block(myCookie, targetId).andExpect(status().isCreated());
        block(myCookie, targetId).andExpect(status().isConflict());
        assertThat(copyOf(myId)).containsExactly(String.valueOf(targetId));
        assertThat(copyOf(targetId)).containsExactly(String.valueOf(myId));
    }

    // ---- Redis 에 닿지 못하면 — 덜 막기가 생기지 않는다 ----

    @Test
    @DisplayName("사본에 못 넣으면 차단은 503 BLOCK_STATE_UNAVAILABLE(+ Retry-After 5)이고 DB 에도 줄이 없다 — DB 에만 있는 차단이 생기지 않는다")
    void blockFailsClosedWhenRedisIsDown()
    {
        Long me = insertUser();
        Long target = insertUser();
        BlockService broken = new BlockService(blockRepository, deadRelations());

        assertThatThrownBy(() -> transactionTemplate.execute(status -> broken.block(me, String.valueOf(target))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("BLOCK_STATE_UNAVAILABLE");
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(5L);
                });
        assertThat(blockRows(me, target)).isZero();
        assertThat(redisTemplate.hasKey(BlockKeys.key(me))).isFalse();
    }

    @Test
    @DisplayName("사본에서 못 빼면 해제도 503 이고 DB 의 줄이 되돌아온다 — 사본(그대로 차단)과 DB 가 같은 말을 한다")
    void unblockFailsClosedWhenRedisIsDown() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);
        Long targetId = insertUser();
        block(myCookie, targetId).andExpect(status().isCreated());
        BlockService broken = new BlockService(blockRepository, deadRelations());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> broken.unblock(myId, targetId)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("BLOCK_STATE_UNAVAILABLE");
                });
        assertThat(blockRows(myId, targetId)).isEqualTo(1);
        assertThat(copyOf(myId)).containsExactly(String.valueOf(targetId));
        assertThat(copyOf(targetId)).containsExactly(String.valueOf(myId));
    }

    @Test
    @DisplayName("해제가 사본에서 뺀 뒤 커밋되지 못하면(롤백) 사본에 되돌려 넣는다 — DB 에 남은 차단이 사본에서 빠진 채 남지 않는다")
    void unblockThatDoesNotCommitPutsTheRelationBack() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);
        Long targetId = insertUser();
        block(myCookie, targetId).andExpect(status().isCreated());

        transactionTemplate.executeWithoutResult(status -> {
            blockService.unblock(myId, targetId);
            // 커밋 전 — 사본에서는 빠졌다
            assertThat(redisTemplate.hasKey(BlockKeys.key(myId))).isFalse();
            status.setRollbackOnly();
        });

        assertThat(blockRows(myId, targetId)).isEqualTo(1);
        assertThat(copyOf(myId)).containsExactly(String.valueOf(targetId));
        assertThat(copyOf(targetId)).containsExactly(String.valueOf(myId));
    }

    @Test
    @DisplayName("사본을 바꾸는 트랜잭션은 한 줄로 선다 — 재구성이 줄을 잡고 옛 계산(빈 집합)을 쓰는 동안 들어온 차단은 기다렸다가 그 뒤에 적힌다(덮여 지워지지 않는다)")
    void blockWaitsWhileTheCopyIsBeingRebuilt() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);
        Long targetId = insertUser();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try
        {
            Future<Integer> blocked = transactionTemplate.execute(status -> {
                // 재구성처럼 줄을 먼저 잡는다 — 이 트랜잭션은 표를 이미 읽었고 그때는 차단이 없었다
                blockRepository.lockRelationCopy();
                Future<Integer> request = pool.submit(() -> block(myCookie, targetId).andReturn().getResponse().getStatus());
                try
                {
                    Thread.sleep(500);
                }
                catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                assertThat(request).isNotDone();
                // 옛 계산을 쓴다 — 줄을 서지 않았다면 이 DEL 이 이미 들어간 차단을 지웠을 것이다
                redisTemplate.delete(List.of(BlockKeys.key(myId), BlockKeys.key(targetId)));
                return request;
            });

            assertThat(blocked.get(10, TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(copyOf(myId)).containsExactly(String.valueOf(targetId));
            assertThat(copyOf(targetId)).containsExactly(String.valueOf(myId));
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    // ---- 회원 탈퇴 ----

    @Test
    @DisplayName("탈퇴하면 그 사람의 키가 없어지고 상대들의 집합에서 그 번호가 빠진다 — 다른 관계는 그대로다")
    void deletionForgetsTheUser() throws Exception
    {
        String leaver = newNickname();
        String alice = newNickname();
        Cookie leaverCookie = login(leaver);
        Cookie aliceCookie = login(alice);
        Long leaverId = userIdOf(leaver);
        Long aliceId = userIdOf(alice);
        Long blockedByLeaver = insertUser();
        Long blockedByAlice = insertUser();
        block(aliceCookie, leaverId).andExpect(status().isCreated());
        block(leaverCookie, blockedByLeaver).andExpect(status().isCreated());
        block(aliceCookie, blockedByAlice).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/auth/account").cookie(leaverCookie)).andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey(BlockKeys.key(leaverId))).isFalse();
        assertThat(copyOf(aliceId)).containsExactly(String.valueOf(blockedByAlice));
        assertThat(redisTemplate.hasKey(BlockKeys.key(blockedByLeaver))).isFalse();
        assertThat(copyOf(blockedByAlice)).containsExactly(String.valueOf(aliceId));
    }

    @Test
    @DisplayName("탈퇴 때 사본을 못 고쳐도(Redis 장애) 탈퇴는 끝난다 — 남은 번호는 없는 사람을 향한 더 막기이고 재구성이 치운다")
    void deletionSurvivesRedisDownAndRebuildCleansUp() throws Exception
    {
        String leaver = newNickname();
        String alice = newNickname();
        login(leaver);
        Cookie aliceCookie = login(alice);
        Long leaverId = userIdOf(leaver);
        Long aliceId = userIdOf(alice);
        block(aliceCookie, leaverId).andExpect(status().isCreated());
        AccountDeletionService service = new AccountDeletionService(userRepository, roomService, roomMemberService, postService,
                transactionTemplate, refreshTokens, blockReader, deadRelations());

        assertThatCode(() -> service.delete(leaverId, List.of())).doesNotThrowAnyException();

        assertThat(jdbcTemplate.queryForObject("select count(*) from users where id = ?", Long.class, leaverId)).isZero();
        assertThat(blockRows(aliceId, leaverId)).isZero();
        // 못 지운 것이 남았다 — 표에 없는 사람이다
        assertThat(copyOf(aliceId)).containsExactly(String.valueOf(leaverId));
        assertThat(copyOf(leaverId)).containsExactly(String.valueOf(aliceId));

        blockRelationRedis.rebuild();

        assertThat(redisTemplate.hasKey(BlockKeys.key(aliceId))).isFalse();
        assertThat(redisTemplate.hasKey(BlockKeys.key(leaverId))).isFalse();
    }

    // ---- 재구성 ----

    @Test
    @DisplayName("재구성은 표에서 집합을 다시 만든다 — 없던 키를 만들고, 틀린 번호를 빼고, 표에 없는 사용자의 키를 지운다")
    void rebuildMatchesTheTable()
    {
        Long alice = insertUser();
        Long bob = insertUser();
        Long carol = insertUser();
        // SQL 로 넣은 차단 — 사본에는 없다(차단 요청을 거치지 않았다)
        insertBlockRow(alice, bob);
        insertBlockRow(carol, alice);
        Long ghost = unknownUserId();
        ghostKey = BlockKeys.key(ghost);
        // 표에 없는 사람의 키와, 있는 사람의 집합에 섞인 틀린 번호
        redisTemplate.opsForSet().add(ghostKey, String.valueOf(alice));
        redisTemplate.opsForSet().add(BlockKeys.key(bob), String.valueOf(ghost));

        BlockRelationRedis.Rebuilt rebuilt = blockRelationRedis.rebuild();

        assertThat(copyOf(alice)).containsExactlyInAnyOrder(String.valueOf(bob), String.valueOf(carol));
        assertThat(copyOf(bob)).containsExactly(String.valueOf(alice));
        assertThat(copyOf(carol)).containsExactly(String.valueOf(alice));
        assertThat(redisTemplate.hasKey(ghostKey)).isFalse();
        assertThat(rebuilt.users()).isGreaterThanOrEqualTo(3);
        assertThat(rebuilt.removedKeys()).isGreaterThanOrEqualTo(1);
        // 두 번 해도 같다
        blockRelationRedis.rebuild();
        assertThat(copyOf(alice)).containsExactlyInAnyOrder(String.valueOf(bob), String.valueOf(carol));
    }

    @Test
    @DisplayName("기동 때의 재구성은 Redis 에 닿지 못해도 예외를 내지 않는다 — 기동을 막지 않고 다음 주기에 다시 한다")
    void startupRebuildDoesNotFailWithoutRedis()
    {
        BlockRelationSync sync = new BlockRelationSync(deadRelations(), new BlockProperties(Duration.ofMinutes(5)));

        assertThatCode(() -> sync.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주기는 BLOCK_REDIS_SYNC_INTERVAL 이다 — PT0S 면 주기적 재구성을 걸지 않고, 그 밖에는 첫 실행도 한 주기 뒤다")
    void periodicRebuildFollowsTheInterval()
    {
        ScheduledTaskRegistrar off = new ScheduledTaskRegistrar();
        new BlockRelationSync(blockRelationRedis, new BlockProperties(Duration.ZERO)).configureTasks(off);
        assertThat(off.getFixedDelayTaskList()).isEmpty();

        ScheduledTaskRegistrar on = new ScheduledTaskRegistrar();
        new BlockRelationSync(blockRelationRedis, new BlockProperties(Duration.ofMinutes(5))).configureTasks(on);
        assertThat(on.getFixedDelayTaskList()).singleElement().satisfies(task -> {
            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(5));
            assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(5));
        });
    }

    // ---- 도우미 ----

    /** 아무도 듣지 않는 Redis 에 쓰는 사본 쓰기 — 스크립트와 DB(재구성의 표 읽기)는 진짜다 */
    private BlockRelationRedis deadRelations()
    {
        return new BlockRelationRedis(new StringRedisTemplate(dead), addBlockRelScript, removeBlockRelScript, replaceBlockRelScript, blockRepository);
    }

    private Set<String> copyOf(Long userId)
    {
        return redisTemplate.opsForSet().members(BlockKeys.key(userId));
    }

    private long blockRows(Long blockerId, Long blockedId)
    {
        Long rows = jdbcTemplate.queryForObject("select count(*) from blocks where blocker_id = ? and blocked_id = ?",
                Long.class, blockerId, blockedId);
        return rows == null ? 0 : rows;
    }

    private void insertBlockRow(Long blockerId, Long blockedId)
    {
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", blockerId, blockedId);
    }

    private ResultActions block(Cookie cookie, Long target) throws Exception
    {
        return mockMvc.perform(post("/api/v1/blocks").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("userId", target)));
    }

    private ResultActions unblock(Cookie cookie, Long target) throws Exception
    {
        return mockMvc.perform(delete("/api/v1/blocks/" + target).cookie(cookie));
    }
}
