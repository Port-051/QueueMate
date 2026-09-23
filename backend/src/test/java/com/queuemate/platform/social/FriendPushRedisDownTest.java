package com.queuemate.platform.social;

import com.queuemate.platform.account.service.UserReader;
import com.queuemate.platform.common.push.PushPublisher;
import com.queuemate.platform.social.dto.FriendRequestResponse;
import com.queuemate.platform.social.dto.FriendResponse;
import com.queuemate.platform.social.repository.FriendRequestRepository;
import com.queuemate.platform.social.repository.FriendshipRepository;
import com.queuemate.platform.social.service.BlockReader;
import com.queuemate.platform.social.service.FriendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>Redis 가 죽었을 때</b> — 발행이 실패하는 {@link PushPublisher} 로 갈아 끼운 {@link FriendService} 로 본다. 나머지(DB · 닉네임 · 차단)는 앱의 진짜 빈이다.
 * <b>발행 실패가 본 작업을 뒤집으면 안 된다</b>(CLAUDE.md §3.2) — 알림은 놓쳐도 받은 목록을 다시 조회하면 복구된다.
 *
 * <p>손으로 만든 서비스에는 {@code @Transactional} 의 프록시가 없어서 {@link TransactionTemplate} 으로 감싼다 — 그래야 발행이 진짜처럼
 * <b>커밋 뒤({@code afterCommit})에</b> 일어난다. 거기서 예외가 새면 이미 커밋된 요청이 부른 쪽에는 실패로 보인다.
 */
class FriendPushRedisDownTest extends FriendTestSupport {

    @Autowired
    FriendRequestRepository friendRequestRepository;

    @Autowired
    FriendshipRepository friendshipRepository;

    @Autowired
    UserReader userReader;

    @Autowired
    BlockReader blockReader;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("발행이 실패해도 친구 요청과 수락은 성공이고 DB 에 남는다 — 예외가 밖으로 나오지 않는다")
    void publishFailureDoesNotUndoTheWork() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        signup(alice, PASSWORD, nicknameOf(alice)).andExpect(status().isCreated());
        signup(bob, PASSWORD, nicknameOf(bob)).andExpect(status().isCreated());
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        AtomicInteger attempts = new AtomicInteger();
        StringRedisTemplate broken = new StringRedisTemplate() {
            @Override
            public Long convertAndSend(String channel, Object message)
            {
                attempts.incrementAndGet();
                throw new RedisConnectionFailureException("테스트 — Redis 가 죽었다");
            }
        };
        FriendService service = new FriendService(friendRequestRepository, friendshipRepository, userReader, blockReader,
                new PushPublisher(broken, objectMapper));

        // 서비스는 본문의 userId 를 글자로 받아 스스로 Long 으로 판다 — 컨트롤러를 거치지 않으니 여기서도 글자로 넘긴다
        FriendRequestResponse request = transactionTemplate.execute(status -> service.send(aliceId, String.valueOf(bobId)));
        assertThat(request).isNotNull();
        assertThat(statusOf(request.requestId())).isEqualTo("PENDING");

        FriendResponse friend = transactionTemplate.execute(status -> service.accept(bobId, request.requestId()));
        assertThat(friend).isNotNull();
        assertThat(friend.userId()).isEqualTo(aliceId);
        assertThat(statusOf(request.requestId())).isEqualTo("ACCEPTED");
        assertThat(friendshipsBetween(aliceId, bobId)).isEqualTo(1);

        // 발행을 건너뛴 것이 아니다 — 두 번 다 시도했고 두 번 다 실패했다
        assertThat(attempts).hasValue(2);
    }
}
