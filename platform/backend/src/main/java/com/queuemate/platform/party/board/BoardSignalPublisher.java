package com.queuemate.platform.party.board;

import com.queuemate.platform.common.push.PushEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 게시판 채널에 "바뀌었다" 신호를 발행한다 — 글이 생기거나 · 만료되거나 · 확정되거나 · 확정된 글의 파티가 닫힐 때다(글 응답의 {@code closed} — 2026-10-02 소유자 결정)
 * (CLAUDE.md §3.2 "게시판 채널". 글은 고칠 수 없다 — 2026-10-01 소유자 결정).
 * <b>이 앱의 몫은 PUBLISH 까지다</b> — 구독해서 SSE 로 흘려보내는 것은 {@code notification} 이다.
 *
 * <pre>
 * platform --PUBLISH qm:pubsub:board--> Redis --구독--> notification --SSE--> 게시판을 보고 있는 브라우저 --GET /api/v1/posts--> platform
 * </pre>
 *
 * <p><b>신호에는 데이터를 싣지 않는다</b> — {@code payload} 는 늘 {@code {}} 다. 방송은 사람별로 거를 수 없어서 {@code roomId} 하나만 실어도
 * 차단 때문에 그 방이 숨겨진 사용자에게 "그 방이 바뀌었다"가 새어 나간다(D-20 · D-22). 그래서 이 클래스에는 payload 를 받는 인자가 없다.
 *
 * <p>방의 인원이 바뀔 때도 이것으로 보낸다({@code room.service.RoomNotifier} — 2026-09-25 에 {@code room} 앱을 합쳤다). 봉투는 {@code {type, eventId, occurredAt, payload}} 네 칸이다.
 * 개인 알림({@code common.push.PushPublisher})과 같은 {@link PushEnvelope} 한 벌을 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardSignalPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * 게시판이 바뀌었다. <b>트랜잭션 안에서 부르면 커밋된 뒤에 발행한다</b> — 되돌려진 변경을 알리지 않고, 신호를 받은 프런트가 목록을 다시
     * 요청했을 때 그 변경이 이미 보인다. <b>한 트랜잭션에서 여러 번 불러도 신호는 한 번이다</b>(목록 조회 한 번에 여러 글이 만료될 수 있다).
     * 트랜잭션 밖에서 부르면 바로 발행한다.
     */
    public void changed()
    {
        if(!TransactionSynchronizationManager.isSynchronizationActive())
        {
            publish();
            return;
        }
        if(TransactionSynchronizationManager.hasResource(scheduledKey()))
        {
            return;
        }
        TransactionSynchronizationManager.bindResource(scheduledKey(), Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit()
            {
                publish();
            }

            @Override
            public void afterCompletion(int status)
            {
                // 커밋이든 롤백이든 표시를 걷는다 — 스레드는 다음 요청이 다시 쓴다
                TransactionSynchronizationManager.unbindResourceIfPossible(scheduledKey());
            }
        });
    }

    /**
     * 발행한다. <b>어떤 예외도 밖으로 내보내지 않는다</b> — 신호가 실패했다고 이미 커밋된 글 쓰기가 실패로 보이면 안 된다.
     * 신호는 놓쳐도 된다(클라이언트는 SSE 를 다시 연결한 직후 목록을 다시 받는다). 대가로 발행이 틀려도 조용하다 — 그래서 구독해서 확인하는 테스트를 둔다.
     */
    void publish()
    {
        try
        {
            PushEnvelope envelope = PushEnvelope.now(BoardEventType.BOARD_CHANGED.name(), Map.of());
            Long received = redis.convertAndSend(BoardChannels.BOARD_CHANNEL, objectMapper.writeValueAsString(envelope));
            // received == 0 은 실패가 아니다 — notification 이 떠 있지 않을 뿐이다
            log.debug("board signal type={} received={}", BoardEventType.BOARD_CHANGED, received);
        }
        catch(Exception e)
        {
            log.warn("게시판 신호 발행 실패 type={}: {}", BoardEventType.BOARD_CHANGED, e.toString());
        }
    }

    /** "이 트랜잭션에는 이미 신호를 예약했다"를 트랜잭션에 묶어 두는 열쇠. 빈은 하나뿐이라 자기 자신을 쓴다 */
    private Object scheduledKey()
    {
        return this;
    }
}
