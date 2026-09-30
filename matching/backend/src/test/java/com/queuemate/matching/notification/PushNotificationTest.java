package com.queuemate.matching.notification;

import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.MatchCancelService;
import com.queuemate.matching.service.MatchRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매칭 알림이 <b>실제로 Redis Pub/Sub 채널에 나갔는지</b>를 구독해서 확인한다.
 *
 * <p><b>왜 필요한가.</b> {@link PushPublisher#publish} 는 어떤 예외도 밖으로 내보내지
 * 않는다(의도된 설계다 — 알림은 휘발성이고, 여기서 터지면 이미 성립한 매칭이 503 으로
 * 뒤집힌다). 그래서 발행 코드가 틀려도 호출부는 아무 일 없이 지나간다. 발행된 메시지를
 * 받아 보는 것 말고는 검증할 방법이 없다.
 *
 * <p><b>동시성 테스트가 아니다.</b> {@code runConcurrently()} 를 쓰지 않는다 — 그쪽은
 * 스레드 안의 예외를 삼키므로 여기서 쓰면 두 겹으로 예외가 사라진다. 전부 단일 스레드
 * 순차 실행이고, 실패는 그대로 터진다. {@link ConcurrencyTestSupport} 는 Redis DB 15 /
 * 테스트마다 flush / LoL gameconfig 시드 / {@code command()} 헬퍼 때문에 상속한다.
 *
 * <p><b>구독 타이밍.</b> {@code SUBSCRIBE} 를 보낸 직후 publish 하면 등록 응답 전에
 * 지나가 간헐적으로 놓친다. {@link #awaitSubscriptionReady()} 가 probe 채널로 왕복
 * 한 번을 성사시켜 <b>배달이 실제로 되는 것</b>을 확인한 뒤에야 테스트 본문으로 넘어간다.
 */
class PushNotificationTest extends ConcurrencyTestSupport {

    private static final String CHANNEL_PREFIX = "qm:pubsub:push:";
    /** 구독이 붙었는지 확인하는 데만 쓰는 채널. 실제 userId 와 겹치지 않는 이름이어야 한다 */
    private static final String PROBE_CHANNEL = CHANNEL_PREFIX + "__subscription-probe__";

    /** 와야 할 메시지를 기다리는 시간 */
    private static final long ARRIVAL_TIMEOUT_MS = 3_000;
    /** "오지 않아야 한다" 를 판정하기 전에 기다리는 시간. 즉시 보면 안 온 것과 구별이 안 된다 */
    private static final long SILENCE_WINDOW_MS = 500;

    @Autowired
    private RedisConnectionFactory connectionFactory;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private MatchCancelService matchCancelService;
    @Autowired
    private List<CandidateRule> candidateRules;

    private RedisMessageListenerContainer container;

    /** 채널별 수신함. 채널 이름은 {@code qm:pubsub:push:{userId}} 통째로 쓴다 */
    private final Map<String, BlockingQueue<Map<String, Object>>> inbox = new ConcurrentHashMap<>();
    /** 채널을 가리지 않고 받은 전부. "다른 채널로는 아무것도 안 왔다" 를 보는 데 쓴다 */
    private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
    private final BlockingQueue<String> probes = new LinkedBlockingQueue<>();

    private record Received(String channel, Map<String, Object> envelope) {
    }

    // ── 구독 준비 ────────────────────────────────────────────────────────────

    @BeforeEach
    void subscribe() throws InterruptedException {
        inbox.clear();
        received.clear();
        probes.clear();

        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.afterPropertiesSet();
        // 사용자별 채널을 하나하나 구독하면 테스트마다 대상이 달라진다.
        // 패턴 하나로 받아 두면 "누구에게도 안 갔다" 까지 같은 자리에서 볼 수 있다.
        container.addMessageListener(this::record, new PatternTopic(CHANNEL_PREFIX + "*"));
        container.start();

        awaitSubscriptionReady();
    }

    @AfterEach
    void unsubscribe() throws Exception {
        if (container != null) {
            container.stop();
            container.destroy();
            container = null;
        }
    }

    private void record(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        if (PROBE_CHANNEL.equals(channel)) {
            probes.add(body);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = objectMapper.readValue(body, Map.class);
        received.add(new Received(channel, envelope));
        queueOf(channel).add(envelope);
    }

    /**
     * 구독이 실제로 붙을 때까지 기다린다.
     *
     * <p>{@code container.start()} 가 돌아왔다고 서버에 등록이 끝난 것이 아니다. 컨테이너는
     * 구독을 다른 스레드에서 건다. {@code PUBSUB NUMPAT} 을 보는 방법도 있지만, 그것은
     * "등록됐다" 까지만 말해 줄 뿐 <b>배달 경로</b>가 살아 있는지는 말해 주지 않는다.
     * 그래서 직접 왕복시킨다 — 내가 방금 보낸 토큰이 리스너에 도착해야 통과다.
     */
    private void awaitSubscriptionReady() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            String token = UUID.randomUUID().toString();
            redis.convertAndSend(PROBE_CHANNEL, token);

            String seen = probes.poll(100, TimeUnit.MILLISECONDS);
            while (seen != null && !token.equals(seen)) {   // 앞선 회차의 토큰은 버린다
                seen = probes.poll(50, TimeUnit.MILLISECONDS);
            }
            if (token.equals(seen)) {
                probes.clear();
                return;
            }
        }
        throw new IllegalStateException("Redis Pub/Sub 구독이 10초 안에 붙지 않았다");
    }

    // ── 테스트 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("새 파티를 만들면 MATCH_QUEUE_UPDATED 가 본인에게만 가고 memberNumber 는 1이다")
    void newPartyNotifiesOnlyTheCreator() throws InterruptedException {
        enqueue(command("u1", "RANKED_SOLO", "TOP"));

        Map<String, Object> envelope = awaitPush("u1");
        assertThat(type(envelope)).isEqualTo(PushEventType.MATCH_QUEUE_UPDATED.name());
        assertThat(memberNumber(envelope)).isEqualTo(1);
        // 대기 갱신에는 제안이 없다. partyId 가 실리면 클라이언트가 수락 창을 잘못 띄운다
        assertThat(payload(envelope)).doesNotContainKey("partyId");

        // 아직 나 혼자다. 다른 누구의 채널로도 나가면 안 된다
        assertNoPush("u2");
        assertThat(channelsSeen()).containsExactly(channel("u1"));
    }

    @Test
    @DisplayName("정원 미달 파티에 합류하면 먼저 들어와 있던 사람까지 전원이 받고 memberNumber 는 합류 후 인원이다")
    void joiningNotifiesEveryMemberWithPostJoinSize() throws InterruptedException {
        enqueue(command("u1", "ARAM_5", "NONE"));
        awaitPush("u1");
        drain();

        enqueue(command("u2", "ARAM_5", "NONE"));

        // 기존 파티원도 받아야 한다. 인원이 는 것을 알 다른 통로가 없다
        for (String userId : List.of("u1", "u2")) {
            Map<String, Object> envelope = awaitPush(userId);
            assertThat(type(envelope)).as("%s 가 받은 type", userId)
                    .isEqualTo(PushEventType.MATCH_QUEUE_UPDATED.name());
            // 합류 전 목록을 세면 1이 나온다. 스크립트가 나를 넣은 뒤 센 값이어야 한다
            assertThat(memberNumber(envelope)).as("%s 가 받은 memberNumber", userId).isEqualTo(2);
            assertThat(payload(envelope)).doesNotContainKey("partyId");
        }
        assertThat(channelsSeen()).containsExactlyInAnyOrder(channel("u1"), channel("u2"));
        drain();

        enqueue(command("u3", "ARAM_5", "NONE"));

        for (String userId : List.of("u1", "u2", "u3")) {
            Map<String, Object> envelope = awaitPush(userId);
            assertThat(memberNumber(envelope)).as("%s 가 받은 memberNumber", userId).isEqualTo(3);
        }
        assertThat(channelsSeen())
                .containsExactlyInAnyOrder(channel("u1"), channel("u2"), channel("u3"));
    }

    @Test
    @DisplayName("정원이 차면 MATCH_PROPOSAL_CREATED 가 파티 전원에게 가고 payload 에 partyId 가 있다")
    void partyFullNotifiesEveryMemberWithProposal() throws InterruptedException {
        enqueue(command("u1", "RANKED_SOLO", "TOP"));   // target 2
        awaitPush("u1");
        drain();

        enqueue(command("u2", "RANKED_SOLO", "JUNGLE"));

        String partyId = partyIdOf("u2");
        assertThat(partyId).isNotBlank();

        // 마지막에 들어온 u2 만 받고 끝나면 u1 은 수락 창을 못 띄운다. 실제로 있었던 버그다
        for (String userId : List.of("u1", "u2")) {
            Map<String, Object> envelope = awaitPush(userId);
            assertThat(type(envelope)).as("%s 가 받은 type", userId)
                    .isEqualTo(PushEventType.MATCH_PROPOSAL_CREATED.name());
            assertThat(memberNumber(envelope)).as("%s 가 받은 memberNumber", userId).isEqualTo(2);
            assertThat(payload(envelope)).as("%s 가 받은 payload", userId)
                    .containsEntry("partyId", partyId);
        }
        assertThat(channelsSeen()).containsExactlyInAnyOrder(channel("u1"), channel("u2"));
    }

    @Test
    @DisplayName("취소하면 MATCH_CANCELLED 가 남은 파티원에게만 가고 취소한 본인에게는 오지 않는다")
    void cancelNotifiesOnlyRemainingMembers() throws InterruptedException {
        enqueue(command("u1", "ARAM_5", "NONE"));
        awaitPush("u1");
        String requestId2 = enqueue(command("u2", "ARAM_5", "NONE"));
        awaitPush("u1");
        awaitPush("u2");
        enqueue(command("u3", "ARAM_5", "NONE"));
        awaitPush("u1");
        awaitPush("u2");
        awaitPush("u3");
        drain();

        assertThat(matchCancelService.cancel("u2", requestId2)).isEqualTo(CancelResult.CANCELLED);

        for (String userId : List.of("u1", "u3")) {
            Map<String, Object> envelope = awaitPush(userId);
            assertThat(type(envelope)).as("%s 가 받은 type", userId)
                    .isEqualTo(PushEventType.MATCH_CANCELLED.name());
            // 나간 뒤 남은 인원이다
            assertThat(memberNumber(envelope)).as("%s 가 받은 memberNumber", userId).isEqualTo(2);
        }
        // 본인은 REST 응답으로 이미 안다. 취소한 자기 채널로 오면 화면이 두 번 흔들린다
        assertNoPush("u2");
        assertThat(channelsSeen()).containsExactlyInAnyOrder(channel("u1"), channel("u3"));
    }

    @Test
    @DisplayName("봉투는 type/eventId/occurredAt/payload 네 칸이고 eventId 는 발행마다 다르다")
    void envelopeShapeIsFixedAndEventIdIsUnique() throws InterruptedException {
        enqueue(command("u1", "RANKED_SOLO", "TOP"));
        awaitPush("u1");
        enqueue(command("u2", "RANKED_SOLO", "JUNGLE"));
        awaitPush("u1");
        awaitPush("u2");

        List<Map<String, Object>> envelopes = envelopesSeen();
        assertThat(envelopes).hasSize(3);   // 생성 1건 + 정원 참 2건

        List<String> eventIds = new ArrayList<>();
        for (Map<String, Object> envelope : envelopes) {
            assertThat(envelope.keySet())
                    .containsExactlyInAnyOrder("type", "eventId", "occurredAt", "payload");
            assertThat(envelope.get("payload")).isInstanceOf(Map.class);
            // SSE 의 Last-Event-ID 가 된다. UUID 모양이어야 한다
            String eventId = (String) envelope.get("eventId");
            assertThat(UUID.fromString(eventId)).hasToString(eventId);
            // Instant 는 UTC 다. 서버 타임존이 섞이면 여기서 깨진다
            String occurredAt = (String) envelope.get("occurredAt");
            assertThat(occurredAt).endsWith("Z");
            assertThat(Instant.parse(occurredAt)).isNotNull();
            eventIds.add(eventId);
        }
        assertThat(eventIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("티어 모드도 같다 — 생성은 partyId 없는 MATCH_QUEUE_UPDATED, 정원 참은 partyId 있는 MATCH_PROPOSAL_CREATED")
    void tieredAssignerPublishesTheSameEnvelopes() throws InterruptedException {
        seedTieredMode("RANKED_SOLO_TIERED", 2);

        enqueue(tiered("u1", "RANKED_SOLO_TIERED", "TOP", "GOLD_2"));

        Map<String, Object> created = awaitPush("u1");
        assertThat(type(created)).isEqualTo(PushEventType.MATCH_QUEUE_UPDATED.name());
        assertThat(memberNumber(created)).isEqualTo(1);
        assertThat(payload(created)).doesNotContainKey("partyId");
        assertNoPush("u2");
        drain();

        enqueue(tiered("u2", "RANKED_SOLO_TIERED", "JUNGLE", "GOLD_2"));

        String partyId = partyIdOf("u2");
        assertThat(partyId).isNotBlank();
        for (String userId : List.of("u1", "u2")) {
            Map<String, Object> envelope = awaitPush(userId);
            assertThat(type(envelope)).as("%s 가 받은 type", userId)
                    .isEqualTo(PushEventType.MATCH_PROPOSAL_CREATED.name());
            assertThat(memberNumber(envelope)).as("%s 가 받은 memberNumber", userId).isEqualTo(2);
            assertThat(payload(envelope)).as("%s 가 받은 payload", userId)
                    .containsEntry("partyId", partyId);
        }
        assertThat(channelsSeen()).containsExactlyInAnyOrder(channel("u1"), channel("u2"));
    }

    // ── 요청을 넣는 길 ───────────────────────────────────────────────────────

    /** 활성 요청을 만들고 배정까지 태운다. 컨트롤러가 하는 두 단계와 같다 */
    private String enqueue(CreateMatchRequestCommand command) {
        String requestId = matchRequestService.join(command).accepted().orElseThrow(
                () -> new IllegalStateException("활성 요청 선점에 실패했다: " + command.getUserId())).requestId();
        lolRule().canJoin(command);
        return requestId;
    }

    private CandidateRule lolRule() {
        return candidateRules.stream()
                .filter(rule -> rule.supports(GameKey.LOL))
                .findFirst()
                .orElseThrow();
    }

    private CreateMatchRequestCommand tiered(String userId, String modeKey, String keyValue, String tier) {
        CreateMatchRequestCommand command = command(userId, modeKey, keyValue);
        command.setTier(tier);
        return command;
    }

    /**
     * gameconfig 는 Redis 에서 읽기만 한다 (CLAUDE.md §3). 테스트가 값을 넣어 준다.
     *
     * <p>티어를 보는 모드는 <b>두 가지</b>가 있어야 한다.
     * <ul>
     *   <li>티어 사다리 {@code qm:gameconfig:LOL:tier} — 배정 스크립트가 여기서 티어 이름을
     *       순번으로({@code ZRANK}) 바꾸고, 범위 안의 이름 목록을 꺼내({@code ZRANGE}) 칸 키를 만든다
     *   <li>허용 범위 표 {@code tier-range:{modeKey}} — 없으면 validator 가 거르고
     *       배정도 {@code -1} 로 끝난다
     * </ul>
     *
     * <p>실 시드({@code seed/gameconfig.redis})의 32칸 사다리 대신 이 테스트에 필요한
     * 만큼만 넣는다. 값이 데이터라 코드가 아는 티어 이름 같은 것은 없다.
     */
    private void seedTieredMode(String modeKey, int targetPartySize) {
        redis.opsForHash().putAll("qm:gameconfig:LOL:" + modeKey, Map.of(
                "targetPartySize", String.valueOf(targetPartySize),
                "positionUniqueness", "true",
                "tierRule", "EXIST"));

        // 낮은 것부터. score 가 사다리의 단계 번호다
        List<String> ladder = List.of("SILVER_1", "GOLD_4", "GOLD_3", "GOLD_2", "GOLD_1", "PLATINUM_4");
        for (int i = 0; i < ladder.size(); i++) {
            redis.opsForZSet().add("qm:gameconfig:LOL:tier", ladder.get(i), i);
        }
        // 사다리 전체를 받는 범위. 둘 다 GOLD_2 라 서로를 받기만 하면 된다
        ladder.forEach(tier -> redis.opsForHash()
                .put("qm:gameconfig:LOL:tier-range:" + modeKey, tier, "SILVER_1:PLATINUM_4"));
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get("qm:user:active-request:" + userId, "partyId");
    }

    // ── 수신 확인 ────────────────────────────────────────────────────────────

    private Map<String, Object> awaitPush(String userId) throws InterruptedException {
        Map<String, Object> envelope = queueOf(channel(userId))
                .poll(ARRIVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(envelope).as("%s 채널로 알림이 오지 않았다", userId).isNotNull();
        return envelope;
    }

    /**
     * 이 사용자 채널로는 아무것도 오지 않았음을 확인한다.
     *
     * <p>수신함만 보면 안 된다 — {@link #awaitPush} 가 poll 로 꺼내 가므로 이미 받은
     * 채널도 비어 보인다. {@link #channelsSeen()} 은 꺼내 가지 않는 누적 기록이라
     * "온 적이 없다" 를 실제로 말해 준다. 마지막 {@link #drain()} 이후를 기준으로 본다.
     */
    private void assertNoPush(String userId) throws InterruptedException {
        // 바로 보면 "안 왔다" 와 "아직 안 도착했다" 를 구별할 수 없다
        Thread.sleep(SILENCE_WINDOW_MS);
        assertThat(channelsSeen())
                .as("%s 채널로는 아무것도 오지 않아야 한다", userId)
                .doesNotContain(channel(userId));
        assertThat(queueOf(channel(userId)))
                .as("%s 채널 수신함이 비어 있어야 한다", userId)
                .isEmpty();
    }

    private BlockingQueue<Map<String, Object>> queueOf(String channel) {
        return inbox.computeIfAbsent(channel, key -> new LinkedBlockingQueue<>());
    }

    private List<String> channelsSeen() {
        synchronized (received) {
            return received.stream().map(Received::channel).distinct().toList();
        }
    }

    private List<Map<String, Object>> envelopesSeen() {
        synchronized (received) {
            return received.stream().map(Received::envelope).toList();
        }
    }

    private void drain() {
        inbox.values().forEach(Collection::clear);
        received.clear();
    }

    private String channel(String userId) {
        return CHANNEL_PREFIX + userId;
    }

    private String type(Map<String, Object> envelope) {
        return (String) envelope.get("type");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("payload");
    }

    private int memberNumber(Map<String, Object> envelope) {
        Object value = payload(envelope).get("memberNumber");
        assertThat(value).as("payload 에 memberNumber 가 없다").isInstanceOf(Number.class);
        return ((Number) value).intValue();
    }
}
