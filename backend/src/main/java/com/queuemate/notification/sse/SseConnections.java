package com.queuemate.notification.sse;

import com.queuemate.notification.subscription.UserChannelSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

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

    /**
     * 하트비트 이벤트 이름. <b>프런트와 맞춘 계약 값이다</b> — 프런트가
     * {@code addEventListener("heartbeat", ...)} 로 받는다. 바꾸려면 프런트와 같이 바꾼다.
     *
     * <p><b>이름이 반드시 있어야 한다.</b> 이름 없이 data 만 보내면 프런트 {@code onmessage} 로
     * 들어가 알림 처리 코드가 JSON 으로 파싱하다 터진다.
     */
    public static final String HEARTBEAT_EVENT_NAME = "heartbeat";

    /**
     * 하트비트 이벤트의 data. 의미 없는 채움값이고 프런트는 읽지 않는다.
     *
     * <p><b>비워 두면 안 된다.</b> data 가 빈 이벤트는 브라우저가 디스패치하지 않아 주석 줄과
     * 똑같이 프런트에 닿지 않는다.
     */
    static final String HEARTBEAT_DATA = "heartbeat";

    private final UserChannelSubscriber subscriber;
    private final ObjectMapper objectMapper;

    private final Map<String, Set<SseEmitter>> connections = new ConcurrentHashMap<>();

    /**
     * 연결을 등록한다. 그 사용자의 첫 연결이면 같은 compute 안에서 구독도 건다.
     */
    public void add(String userId, SseEmitter emitter) {
        connections.compute(userId, (id, sseEmitters) -> {
            if (sseEmitters == null) {
                sseEmitters = ConcurrentHashMap.newKeySet();
                subscriber.subscribe(id);
            }
            sseEmitters.add(emitter);
            return sseEmitters;
        });
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
    }

    /**
     * 연결을 뺀다. 그 사용자의 마지막 연결이면 같은 compute 안에서 구독도 푼다.
     */
    public void remove(String userId, SseEmitter emitter) {
        connections.computeIfPresent(userId, (id, sseEmitters) -> {
            sseEmitters.remove(emitter);
            if (sseEmitters.isEmpty()) {
                subscriber.unsubscribe(id);
                return null;
            }
            return sseEmitters;
        });
    }

    /**
     * 한 사용자의 모든 연결에 메시지를 보낸다. 전송 풀 스레드에서 불린다.
     *
     * <p>본문은 열어 보지 않고 SSE {@code data:} 에 그대로 싣는다. {@code eventId} 만 꺼내
     * {@code id:} 필드에 싣는다. 보내기 실패는 그 연결만 버리고 예외를 밖으로 올리지 않는다.
     */
    public void send(String userId, String json) {
        Set<SseEmitter> sseEmitters = connections.get(userId);
        if (sseEmitters == null) {
            return;
        }
        String eventId = eventIdOf(json);
        sseEmitters.forEach(sseEmitter -> {
            SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event().data(json);
            if (eventId != null) {
                eventBuilder.id(eventId);
            }
            try {
                sseEmitter.send(eventBuilder);
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
        });
    }

    /**
     * 살아 있는 모든 연결에 하트비트를 보낸다. {@link SseHeartbeat} 가 주기적으로 부른다.
     *
     * <p>SSE 주석 줄이 아니라 <b>이름 있는 이벤트</b>로 보낸다. 주석 줄은 브라우저
     * {@code EventSource} 가 자바스크립트에 올려 주지 않아, 프런트가 "연결이 조용히 죽었다"
     * (공유기 재부팅, 인터넷만 끊긴 와이파이, 절전 복귀, 서버 기계가 통째로 죽음처럼 종료 신호 없이
     * 길만 사라진 경우)를 알아챌 재료가 없다. 브라우저는 읽기만 하므로 쓰기 실패로 알 수도 없다.
     * 이벤트로 보내면 프런트가 마지막 하트비트 시각을 적어 두다가 한동안 안 오면 닫고 다시 연다.
     * 프록시 유휴 타임아웃을 피하고 서버가 죽은 연결을 발견하는 역할은 그대로다 — 바이트가
     * 흐르는 것은 같다.
     *
     * <p>알림({@link #send})은 계속 <b>이름 없는 이벤트</b>다. 이름이 있는 것은 하트비트뿐이다.
     *
     * <p>보내기 실패는 그 연결만 버리고 예외를 밖으로 올리지 않는다.
     */
    public void broadcastHeartbeat() {
        connections.forEach((userId, sseEmitters) -> {
            for (SseEmitter sseEmitter : sseEmitters) {
                try {
                    sseEmitter.send(SseEmitter.event().name(HEARTBEAT_EVENT_NAME).data(HEARTBEAT_DATA));
                } catch (Exception e) {
                    sseEmitter.completeWithError(e);
                }
            }
        });
    }

    /**
     * 열려 있는 연결을 전부 정상 종료한다. 앱이 꺼질 때 {@link SseShutdown} 이 부른다.
     *
     * <p>맵은 여기서 비우지 않는다. {@code complete()} 가 {@code onCompletion} 콜백을 부르고, 그
     * 콜백이 {@link #remove} 로 맵에서 빼고 마지막 연결이면 구독도 푼다. 같은 일을 두 군데서 하면
     * 구독 해제가 빠지거나 두 번 불린다.
     *
     * @return 닫기를 시도한 연결 수
     */
    public int closeAll() {
        int closed = 0;
        for (Set<SseEmitter> sseEmitters : connections.values()) {
            for (SseEmitter sseEmitter : sseEmitters) {
                try {
                    sseEmitter.complete();
                } catch (Exception e) {
                    // 하나가 실패해도 나머지는 닫는다. 어차피 곧 프로세스가 끝난다
                    log.debug("종료 중 SSE 연결 닫기 실패: {}", e.toString());
                }
                closed++;
            }
        }
        return closed;
    }

    /**
     * 봉투에서 {@code eventId} 하나만 꺼낸다. 다른 필드는 보지 않는다 (CLAUDE.md §2).
     * JSON 이 깨졌거나 필드가 없으면 {@code null} 이다. 그래도 본문은 그대로 전달한다.
     */
    private String eventIdOf(String json) {
        try {
            return objectMapper.readTree(json).path("eventId").asString(null);
        } catch (Exception e) {
            return null;
        }
    }
}
