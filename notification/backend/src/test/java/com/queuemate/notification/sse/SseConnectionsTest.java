package com.queuemate.notification.sse;

import com.queuemate.notification.subscription.UserChannelSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * {@link SseConnections} 의 연결 목록·구독 수명·전송 격리를 서블릿 없이 검증한다.
 *
 * <p>여기 있는 케이스 중 여럿은 <b>이 프로젝트에서 실제로 났던 버그</b>를 못 박아 둔 것이다.
 * 케이스를 지우거나 느슨하게 고치기 전에 {@code @DisplayName} 옆의 설명을 읽어라.
 *
 * <p><b>서블릿 컨테이너가 없으면 {@code completeWithError} 를 불러도
 * {@code onCompletion} / {@code onError} 콜백이 실행되지 않는다</b> (handler 가 초기화되지
 * 않았기 때문). 그래서 "전송 실패 → 자동 remove" 는 여기서 기대하지 않고, 콜백이 하는 일은
 * {@link SseConnections#remove} 를 직접 불러 흉내 낸다.
 */
class SseConnectionsTest {

    private static final String JSON_U1 =
            "{\"type\":\"MATCH_CONFIRMED\",\"eventId\":\"e-1\",\"occurredAt\":\"2026-01-01T00:00:00.000Z\",\"payload\":{}}";

    /** 하트비트 하나가 SSE 로 나가는 글자 그대로. 실제로 찍어 보고 적은 값이다 */
    private static final String HEARTBEAT_WIRE = "event:heartbeat\ndata:heartbeat\n\n";

    private UserChannelSubscriber subscriber;
    private SseConnections connections;

    @BeforeEach
    void setUp() {
        subscriber = mock(UserChannelSubscriber.class);
        connections = new SseConnections(subscriber, JsonMapper.builder().build());
    }

    @Nested
    @DisplayName("구독 수명 - 사용자 단위로 하나")
    class 구독_수명 {

        @Test
        @DisplayName("같은 사용자의 첫 연결에만 subscribe 가 불린다")
        void 같은_사용자의_첫_연결에만_구독한다() {
            connections.add("u1", new RecordingEmitter());
            connections.add("u1", new RecordingEmitter());

            verify(subscriber, times(1)).subscribe("u1");
        }

        @Test
        @DisplayName("탭 두 개 중 하나만 remove 하면 unsubscribe 가 불리지 않는다 (있었던 버그: 무조건 unsubscribe)")
        void 탭_두_개_중_하나만_빠지면_구독을_풀지_않는다() {
            RecordingEmitter tab1 = new RecordingEmitter();
            RecordingEmitter tab2 = new RecordingEmitter();
            connections.add("u1", tab1);
            connections.add("u1", tab2);

            connections.remove("u1", tab1);

            verify(subscriber, never()).unsubscribe("u1");
            // 남은 탭은 계속 알림을 받아야 한다
            connections.send("u1", JSON_U1);
            assertThat(tab1.sent).isEmpty();
            assertThat(tab2.sent).hasSize(1);
        }

        @Test
        @DisplayName("마지막 연결을 remove 하면 unsubscribe 가 정확히 1번 불린다")
        void 마지막_연결이_빠지면_구독을_한_번_푼다() {
            RecordingEmitter tab1 = new RecordingEmitter();
            RecordingEmitter tab2 = new RecordingEmitter();
            connections.add("u1", tab1);
            connections.add("u1", tab2);

            connections.remove("u1", tab1);
            connections.remove("u1", tab2);

            verify(subscriber, times(1)).unsubscribe("u1");
        }

        @Test
        @DisplayName("같은 emitter 를 두 번 remove 해도 unsubscribe 는 1번뿐이고 예외가 없다 (onTimeout → onCompletion)")
        void 같은_연결을_두_번_빼도_구독은_한_번만_푼다() {
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            assertThatCode(() -> {
                connections.remove("u1", emitter);
                connections.remove("u1", emitter);
            }).doesNotThrowAnyException();

            verify(subscriber, times(1)).unsubscribe("u1");
        }

        @Test
        @DisplayName("탭이 남아 있을 때 이미 빠진 emitter 를 또 remove 해도 구독을 풀지 않는다")
        void 이미_빠진_연결을_또_빼도_남은_탭의_구독은_유지된다() {
            RecordingEmitter tab1 = new RecordingEmitter();
            RecordingEmitter tab2 = new RecordingEmitter();
            connections.add("u1", tab1);
            connections.add("u1", tab2);

            connections.remove("u1", tab1);
            connections.remove("u1", tab1);

            verify(subscriber, never()).unsubscribe("u1");
        }

        @Test
        @DisplayName("전부 remove 한 뒤 다시 add 하면 subscribe 가 다시 불린다 (있었던 버그: 빈 집합이 맵에 남아 재구독 누락)")
        void 전부_빠진_뒤_재접속하면_다시_구독한다() {
            RecordingEmitter first = new RecordingEmitter();
            connections.add("u1", first);
            connections.remove("u1", first);

            RecordingEmitter second = new RecordingEmitter();
            connections.add("u1", second);

            verify(subscriber, times(2)).subscribe("u1");
            verify(subscriber, times(1)).unsubscribe("u1");
            connections.send("u1", JSON_U1);
            assertThat(second.sent).hasSize(1);
        }

        @Test
        @DisplayName("서로 다른 사용자는 각각 구독된다")
        void 서로_다른_사용자는_각각_구독한다() {
            RecordingEmitter u1 = new RecordingEmitter();
            connections.add("u1", u1);
            connections.add("u2", new RecordingEmitter());

            verify(subscriber, times(1)).subscribe("u1");
            verify(subscriber, times(1)).subscribe("u2");

            // 한 사용자가 나가도 다른 사용자의 구독은 그대로다
            connections.remove("u1", u1);
            verify(subscriber, times(1)).unsubscribe("u1");
            verify(subscriber, never()).unsubscribe("u2");
        }

        @Test
        @DisplayName("등록된 적 없는 사용자를 remove 해도 예외가 없고 unsubscribe 도 없다")
        void 모르는_사용자를_빼도_아무_일_없다() {
            assertThatCode(() -> connections.remove("nobody", new RecordingEmitter()))
                    .doesNotThrowAnyException();

            verifyNoMoreInteractions(subscriber);
        }
    }

    @Nested
    @DisplayName("send - 본문을 해석하지 않고 그대로 싣는다")
    class 전송 {

        @Test
        @DisplayName("그 사용자의 모든 emitter 에 전달되고 다른 사용자 emitter 에는 안 간다")
        void 그_사용자의_모든_연결에만_보낸다() {
            RecordingEmitter tab1 = new RecordingEmitter();
            RecordingEmitter tab2 = new RecordingEmitter();
            RecordingEmitter other = new RecordingEmitter();
            connections.add("u1", tab1);
            connections.add("u1", tab2);
            connections.add("u2", other);

            connections.send("u1", JSON_U1);

            assertThat(tab1.sent).hasSize(1);
            assertThat(tab2.sent).hasSize(1);
            assertThat(other.sent).isEmpty();
        }

        @Test
        @DisplayName("본문 JSON 이 글자 하나 안 바뀌고 data 로 나가고 eventId 가 id 로 실린다")
        void 본문은_그대로_data_로_eventId_는_id_로_나간다() {
            // 키 순서, 공백, 유니코드 이스케이프, 한글까지 다시 직렬화되지 않고 그대로여야 한다
            String json = "{ \"payload\" : {\"nick\":\"큐메이트\",\"esc\":\"\\u00e9\"},  \"eventId\":\"abc-123\", \"type\":\"MATCH_CONFIRMED\" }";
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            connections.send("u1", json);

            assertThat(emitter.sent).containsExactly("data:" + json + "\nid:abc-123\n\n");
        }

        @Test
        @DisplayName("알림에는 event: 줄도 retry: 줄도 없다 (이름 없는 이벤트여야 프런트 onmessage 로 간다 / retry 는 연결할 때 한 번만)")
        void 알림은_이름_없는_이벤트이고_retry_를_싣지_않는다() {
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            connections.send("u1", JSON_U1);

            // 줄 단위로 본다. 본문 JSON 안에 우연히 "event:" 같은 글자가 있어도 흔들리지 않게 한다
            assertThat(emitter.sent).hasSize(1);
            assertThat(emitter.sent.get(0).split("\n"))
                    .noneMatch(line -> line.startsWith("event:"))
                    .noneMatch(line -> line.startsWith("retry:"));
        }

        @Test
        @DisplayName("eventId 가 없는 JSON 도 전달된다, id 줄만 없다 (있었던 버그: eventId 가 null 이면 안 보냄 / id 가 \"null\" 로 나감)")
        void eventId_가_없어도_보내고_id_는_싣지_않는다() {
            String json = "{\"type\":\"MATCH_CANCELLED\",\"payload\":{}}";
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            connections.send("u1", json);

            assertThat(emitter.sent).containsExactly("data:" + json + "\n\n");
            assertThat(emitter.sent.get(0)).doesNotContain("id:").doesNotContain("null");
        }

        @Test
        @DisplayName("eventId 가 JSON null 이어도 전달되고 id 가 문자열 \"null\" 로 나가지 않는다")
        void eventId_가_JSON_null_이면_id_를_싣지_않는다() {
            String json = "{\"type\":\"MATCH_CANCELLED\",\"eventId\":null}";
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            connections.send("u1", json);

            assertThat(emitter.sent).containsExactly("data:" + json + "\n\n");
        }

        @Test
        @DisplayName("깨진 문자열(hello)도 그대로 전달된다")
        void JSON_이_아닌_문자열도_그대로_보낸다() {
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            connections.send("u1", "hello");

            assertThat(emitter.sent).containsExactly("data:hello\n\n");
        }

        @Test
        @DisplayName("중간에 잘린 JSON 도 그대로 전달된다")
        void 잘린_JSON_도_그대로_보낸다() {
            String broken = "{\"type\":\"MATCH_CONFIRMED\",\"eventId\":\"e-9";
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            connections.send("u1", broken);

            assertThat(emitter.sent).containsExactly("data:" + broken + "\n\n");
        }

        @Test
        @DisplayName("모르는 type 도 그대로 전달된다 (type 분기 없음)")
        void 모르는_type_도_그대로_보낸다() {
            String json = "{\"type\":\"SOMETHING_FROM_THE_FUTURE\",\"eventId\":\"e-2\",\"payload\":{\"x\":1}}";
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            connections.send("u1", json);

            assertThat(emitter.sent).containsExactly("data:" + json + "\nid:e-2\n\n");
        }

        @Test
        @DisplayName("연결이 없는 사용자에게 send 해도 예외가 없다")
        void 연결이_없는_사용자에게_보내도_아무_일_없다() {
            assertThatCode(() -> connections.send("nobody", JSON_U1)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("전부 나간 사용자에게 send 해도 예외가 없고 나간 emitter 에는 안 간다")
        void 전부_나간_사용자에게_보내도_아무_일_없다() {
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);
            connections.remove("u1", emitter);

            assertThatCode(() -> connections.send("u1", JSON_U1)).doesNotThrowAnyException();
            assertThat(emitter.sent).isEmpty();
        }
    }

    @Nested
    @DisplayName("broadcastHeartbeat - 프런트에 닿는 이름 있는 이벤트")
    class 하트비트 {

        @Test
        @DisplayName("모든 사용자의 모든 emitter 에 event:heartbeat 이벤트가 나간다")
        void 모든_연결에_하트비트_이벤트를_보낸다() {
            RecordingEmitter u1tab1 = new RecordingEmitter();
            RecordingEmitter u1tab2 = new RecordingEmitter();
            RecordingEmitter u2 = new RecordingEmitter();
            connections.add("u1", u1tab1);
            connections.add("u1", u1tab2);
            connections.add("u2", u2);

            connections.broadcastHeartbeat();

            assertThat(u1tab1.sent).containsExactly(HEARTBEAT_WIRE);
            assertThat(u1tab2.sent).containsExactly(HEARTBEAT_WIRE);
            assertThat(u2.sent).containsExactly(HEARTBEAT_WIRE);
        }

        @Test
        @DisplayName("주석 줄이 아니다 (EventSource 는 주석 줄을 자바스크립트에 올리지 않아 프런트가 죽은 연결을 알 수 없다)")
        void 하트비트는_주석_줄이_아니다() {
            assertThat(하트비트_줄들()).noneMatch(line -> line.startsWith(":"));
        }

        @Test
        @DisplayName("이벤트 이름이 있다 (이름이 없으면 프런트 onmessage 로 들어가 알림 JSON 파싱이 터진다)")
        void 하트비트에는_이벤트_이름이_있다() {
            assertThat(하트비트_줄들()).containsOnlyOnce("event:" + SseConnections.HEARTBEAT_EVENT_NAME);
        }

        @Test
        @DisplayName("이벤트 이름은 프런트와 맞춘 계약 값 heartbeat 다")
        void 이벤트_이름은_heartbeat_다() {
            assertThat(SseConnections.HEARTBEAT_EVENT_NAME).isEqualTo("heartbeat");
        }

        @Test
        @DisplayName("data 가 비어 있지 않다 (data 가 빈 이벤트는 브라우저가 디스패치하지 않는다)")
        void 하트비트의_data_는_비어_있지_않다() {
            List<String> dataLines = 하트비트_줄들().stream()
                    .filter(line -> line.startsWith("data:"))
                    .toList();

            assertThat(dataLines).hasSize(1);
            assertThat(dataLines.get(0).substring("data:".length())).isNotBlank();
        }

        @Test
        @DisplayName("연결이 하나도 없어도 예외가 없다")
        void 연결이_없어도_아무_일_없다() {
            assertThatCode(() -> connections.broadcastHeartbeat()).doesNotThrowAnyException();
        }

        private List<String> 하트비트_줄들() {
            RecordingEmitter emitter = new RecordingEmitter();
            connections.add("u1", emitter);

            connections.broadcastHeartbeat();

            assertThat(emitter.sent).hasSize(1);
            return List.of(emitter.sent.get(0).split("\n"));
        }
    }

    @Nested
    @DisplayName("전송 실패 격리 - 실패한 연결만 버린다")
    class 실패_격리 {

        @Test
        @DisplayName("send: 한 emitter 가 IOException 을 던져도 다른 emitter 는 받고 예외가 밖으로 안 나온다")
        void send_IOException_은_그_연결만_버린다() {
            전송_실패는_그_연결만_버린다(new IOException("Broken pipe"));
        }

        @Test
        @DisplayName("send: 한 emitter 가 IllegalStateException 을 던져도 다른 emitter 는 받고 예외가 밖으로 안 나온다")
        void send_IllegalStateException_은_그_연결만_버린다() {
            // 이미 완료된 emitter 에 send 하면 ResponseBodyEmitter 가 이 예외를 던진다
            전송_실패는_그_연결만_버린다(new IllegalStateException("ResponseBodyEmitter has already completed"));
        }

        private void 전송_실패는_그_연결만_버린다(Exception failure) {
            // 집합의 순회 순서는 정해져 있지 않다. 실패하는 연결이 몇 번째에 오든 나머지는 받아야 한다
            RecordingEmitter healthy1 = new RecordingEmitter();
            RecordingEmitter broken = new RecordingEmitter(failure);
            RecordingEmitter healthy2 = new RecordingEmitter();
            RecordingEmitter otherUser = new RecordingEmitter();
            connections.add("u1", healthy1);
            connections.add("u1", broken);
            connections.add("u1", healthy2);
            connections.add("u2", otherUser);

            assertThatCode(() -> connections.send("u1", JSON_U1)).doesNotThrowAnyException();

            assertThat(healthy1.sent).hasSize(1);
            assertThat(healthy2.sent).hasSize(1);
            assertThat(broken.sent).isEmpty();
            assertThat(broken.completedWithErrors).containsExactly(failure);
            assertThat(healthy1.completedWithErrors).isEmpty();
            assertThat(healthy2.completedWithErrors).isEmpty();
            assertThat(otherUser.completedWithErrors).isEmpty();
        }

        @Test
        @DisplayName("broadcastHeartbeat: 한 emitter 가 IOException 을 던져도 같은 사용자·다른 사용자 emitter 는 받는다")
        void broadcastHeartbeat_IOException_은_그_연결만_버린다() {
            브로드캐스트_실패는_그_연결만_버린다(new IOException("Broken pipe"));
        }

        @Test
        @DisplayName("broadcastHeartbeat: 한 emitter 가 IllegalStateException 을 던져도 같은 사용자·다른 사용자 emitter 는 받는다")
        void broadcastHeartbeat_IllegalStateException_은_그_연결만_버린다() {
            브로드캐스트_실패는_그_연결만_버린다(new IllegalStateException("ResponseBodyEmitter has already completed"));
        }

        private void 브로드캐스트_실패는_그_연결만_버린다(Exception failure) {
            RecordingEmitter healthy1 = new RecordingEmitter();
            RecordingEmitter broken = new RecordingEmitter(failure);
            RecordingEmitter healthy2 = new RecordingEmitter();
            RecordingEmitter otherUser = new RecordingEmitter();
            connections.add("u1", healthy1);
            connections.add("u1", broken);
            connections.add("u1", healthy2);
            connections.add("u2", otherUser);

            assertThatCode(() -> connections.broadcastHeartbeat()).doesNotThrowAnyException();

            assertThat(healthy1.sent).hasSize(1);
            assertThat(healthy2.sent).hasSize(1);
            assertThat(otherUser.sent).hasSize(1);
            assertThat(broken.sent).isEmpty();
            assertThat(broken.completedWithErrors).containsExactly(failure);
        }

        @Test
        @DisplayName("실패한 emitter 의 콜백이 remove 를 부른 뒤에는 남은 emitter 만 받는다")
        void 실패한_연결이_빠진_뒤에는_남은_연결만_받는다() {
            RecordingEmitter healthy = new RecordingEmitter();
            RecordingEmitter broken = new RecordingEmitter(new IOException("Broken pipe"));
            connections.add("u1", healthy);
            connections.add("u1", broken);

            connections.send("u1", JSON_U1);
            // 실제 서버에서는 completeWithError → onError/onCompletion 콜백이 이 remove 를 부른다
            connections.remove("u1", broken);
            connections.send("u1", JSON_U1);

            assertThat(healthy.sent).hasSize(2);
            assertThat(broken.completedWithErrors).hasSize(1);
            verify(subscriber, never()).unsubscribe("u1");
        }
    }

    @Nested
    @DisplayName("closeAll - 앱 종료 전에 연결을 먼저 닫는다")
    class 전부_닫기 {

        @Test
        @DisplayName("모든 사용자의 모든 emitter 가 정상 종료(complete)되고 닫은 수를 돌려준다")
        void 모든_연결을_정상_종료한다() {
            RecordingEmitter u1tab1 = new RecordingEmitter();
            RecordingEmitter u1tab2 = new RecordingEmitter();
            RecordingEmitter u2tab1 = new RecordingEmitter();
            connections.add("u1", u1tab1);
            connections.add("u1", u1tab2);
            connections.add("u2", u2tab1);

            int closed = connections.closeAll();

            assertThat(closed).isEqualTo(3);
            assertThat(u1tab1.completedCount).hasValue(1);
            assertThat(u1tab2.completedCount).hasValue(1);
            assertThat(u2tab1.completedCount).hasValue(1);
            // 에러 종료가 아니다 - 의도한 정상 종료다
            assertThat(u1tab1.completedWithErrors).isEmpty();
        }

        @Test
        @DisplayName("한 emitter 의 complete 가 예외를 던져도 나머지는 닫고 예외가 밖으로 안 나온다")
        void 하나가_실패해도_나머지를_닫는다() {
            RecordingEmitter broken = new RecordingEmitter();
            broken.failOnComplete = true;
            RecordingEmitter healthy = new RecordingEmitter();
            connections.add("u1", broken);
            connections.add("u2", healthy);

            assertThatCode(() -> connections.closeAll()).doesNotThrowAnyException();

            assertThat(healthy.completedCount).hasValue(1);
        }

        @Test
        @DisplayName("맵을 직접 비우지 않는다 - 정리는 onCompletion 콜백이 remove 로 한다 (구독 해제가 한 곳에서만 일어나게)")
        void 구독_해제는_콜백에_맡긴다() {
            connections.add("u1", new RecordingEmitter());

            connections.closeAll();

            // 서블릿이 없는 단위 테스트에서는 콜백이 돌지 않는다. closeAll 이 스스로 unsubscribe 를
            // 부르지 않는다는 것만 확인한다. 콜백 배선은 EventStreamControllerTest 가 본다
            verify(subscriber, never()).unsubscribe("u1");
        }

        @Test
        @DisplayName("연결이 하나도 없어도 예외 없이 0 을 돌려준다")
        void 연결이_없어도_된다() {
            assertThat(connections.closeAll()).isZero();
        }
    }

    /**
     * 서블릿 없이 쓰는 테스트용 emitter. 소켓에 쓰는 대신 SSE 로 나갈 글자를 그대로 기록한다.
     *
     * <p>{@code failure} 를 주면 {@code send} 가 그 예외를 던진다 — 끊긴 클라이언트 흉내다.
     */
    static class RecordingEmitter extends SseEmitter {

        final List<String> sent = new CopyOnWriteArrayList<>();
        final List<Throwable> completedWithErrors = new CopyOnWriteArrayList<>();
        final AtomicInteger completedCount = new AtomicInteger();
        private final Exception failure;
        /** true 면 complete() 가 예외를 던진다 */
        boolean failOnComplete;

        RecordingEmitter() {
            this(null);
        }

        RecordingEmitter(Exception failure) {
            this.failure = failure;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failure instanceof IOException ioException) {
                throw ioException;
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            StringBuilder wire = new StringBuilder();
            for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
                wire.append(part.getData());
            }
            sent.add(wire.toString());
        }

        @Override
        public void completeWithError(Throwable ex) {
            completedWithErrors.add(ex);
        }

        @Override
        public void complete() {
            completedCount.incrementAndGet();
            if (failOnComplete) {
                throw new IllegalStateException("complete 실패 흉내");
            }
        }
    }
}
