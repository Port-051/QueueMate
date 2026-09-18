package com.queuemate.notification.sse;

import com.queuemate.notification.subscription.UserChannelSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 살아 있는 SSE 연결을 {@code userId → 연결 목록} 으로 들고 있는다.
 *
 * <p>한 사용자가 여러 탭을 열 수 있으므로 값은 집합이다. 같은 메시지를 전부에 보낸다.
 * Redis 구독은 연결 수가 아니라 <b>사용자 단위로 하나</b>다 (CLAUDE.md §5).
 *
 * <p><b>첫/마지막 연결 판단과 구독/해제를 {@code ConcurrentHashMap.compute} 안에서
 * 함께 한다.</b> "비었나 확인 → 구독" 을 두 단계로 나누면 같은 사용자의 탭 두 개가 동시에
 * 붙을 때 둘 다 첫 연결이라고 믿고 구독을 두 번 걸거나, 하나가 끊기며 구독을 풀어 버리는
 * 경쟁이 생긴다. compute 는 키 하나에 대해 직렬로 돌므로 그 안에서 판단과 구독을 같이 끝낸다.
 *
 * <p>정리는 {@code onCompletion} / {@code onTimeout} / {@code onError} 셋 모두에서
 * {@link #remove} 를 불러야 한다. 하나라도 빠뜨리면 죽은 연결과 구독이 조용히 쌓인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseConnections {

    private final UserChannelSubscriber subscriber;

    private final Map<String, Set<SseEmitter>> connections = new ConcurrentHashMap<>();

    /**
     * 연결을 등록한다. 그 사용자의 첫 연결이면 같은 compute 안에서 구독도 건다.
     */
    public void add(String userId, SseEmitter emitter) {
        // TODO: compute 안에서 집합에 넣고, 새로 만든 집합이면 subscriber.subscribe(userId) 를 부른다
    }

    /**
     * 연결을 뺀다. 그 사용자의 마지막 연결이면 같은 compute 안에서 구독도 푼다.
     */
    public void remove(String userId, SseEmitter emitter) {
        // TODO: compute 안에서 집합에서 빼고, 비면 subscriber.unsubscribe(userId) 를 부르고 null 을 돌려줘 키를 지운다
    }

    /**
     * 한 사용자의 모든 연결에 메시지를 보낸다. 전송 풀 스레드에서 불린다.
     *
     * <p>본문은 열어 보지 않고 SSE {@code data:} 에 그대로 싣는다. {@code eventId} 만 꺼내
     * {@code id:} 필드에 싣는다. 보내기 실패는 그 연결만 버리고 예외를 밖으로 올리지 않는다.
     */
    public void send(String userId, String json) {
        // TODO: json 에서 eventId 만 꺼내 id: 로, json 전체를 data: 로 각 emitter 에 보낸다. 실패한 emitter 는 completeWithError 후 remove
    }

    /**
     * 모든 연결에 SSE 주석 줄({@code :} 로 시작)을 보낸다. 하트비트가 쓴다.
     * 브라우저 {@code EventSource} 는 주석 줄을 이벤트로 올리지 않는다.
     */
    public void broadcastComment(String comment) {
        // TODO: 모든 emitter 에 SseEmitter.event().comment(comment) 를 보내고, 실패한 연결은 remove
    }
}
