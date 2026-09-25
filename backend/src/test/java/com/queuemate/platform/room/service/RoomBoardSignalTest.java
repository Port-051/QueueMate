package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.CreateResult;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.HeartbeatResult;
import com.queuemate.platform.room.domain.KickResult;
import com.queuemate.platform.room.domain.LeaveResult;
import com.queuemate.platform.room.notification.BoardSubscriber;
import com.queuemate.platform.party.board.BoardChannels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시판 채널 신호({@code BOARD_CHANGED}). 방의 인원이 <b>실제로 바뀌었을 때만</b> {@code qm:pubsub:board} 에 나가는지를
 * 그 채널을 구독해서 확인한다 (docs/11 D-20 · D-22 · D-23). 발행은 실패해도 조용하므로 "불렀다"가 아니라 "도착했다"로 본다.
 *
 * <p>수명을 2초로 줄여 실제로 기다리는 테스트가 있다 —
 * {@code ./gradlew test --tests '*RoomBoardSignalTest'}
 */
@TestPropertySource(properties = "platform.room.ttl-seconds=2")
class RoomBoardSignalTest extends RoomTestSupport {

    @BeforeEach
    void warmUpScripts()
    {
        warmUp();
    }

    /** 수명이 2초라 JVM 이 막 떴을 때의 첫 실행이 방의 수명을 넘긴다 — 한 바퀴 돌려 데운다 ({@link RoomServiceConfirmTest} 와 같다) */
    private void warmUp()
    {
        roomService.create(r("warm-up"), u("warm-up-host"));
        roomMemberService.enter(r("warm-up"), u("warm-up-member"));
        roomMemberService.heartbeat(r("warm-up"), u("warm-up-host"));
        roomMemberService.leave(r("warm-up"), u("warm-up-host"));
    }

    @Test
    @DisplayName("채널 이름은 notification 과의 약속이다 — qm:pubsub:board. 방의 신호도 게시판 글의 신호와 같은 상수로 나간다")
    void channelName()
    {
        assertThat(BoardChannels.BOARD_CHANNEL).isEqualTo("qm:pubsub:board");
    }

    @Test
    @DisplayName("방을 만들면 신호가 한 번 나간다. 봉투는 네 칸이고 type 은 BOARD_CHANGED, payload 는 빈 객체다 — roomId 도 싣지 않는다")
    void createSendsOneSignalWithAnEmptyPayload() throws Exception
    {
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomService.create(r("r1"), u("host"))).isEqualTo(CreateResult.CREATED);

            JsonNode envelope = board.next();
            assertThat(envelope).isNotNull();
            assertThat(envelope.propertyNames()).containsExactlyInAnyOrder("type", "eventId", "occurredAt", "payload");
            assertThat(envelope.get("type").asString()).isEqualTo("BOARD_CHANGED");
            assertThat(envelope.get("payload").isObject()).isTrue();
            assertThat(envelope.get("payload").isEmpty()).isTrue();
            // 방 번호는 18자리라 우연히 겹칠 일이 없다. 사용자 번호는 짧아서 eventId · occurredAt 의 숫자와 우연히 겹칠 수 있다 —
            // 빈 payload 를 위에서 봤으니 그것으로 충분하다
            assertThat(envelope.toString()).doesNotContain(r("r1"));
            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("이미 있는 방을 또 만들려 하면 신호가 나가지 않는다")
    void rejectedCreateSendsNothing() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomService.create(r("r1"), u("u1"))).isEqualTo(CreateResult.ROOM_EXISTS);

            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("입장하면 한 번 나간다. 재입장(이미 들어와 있다)과 거절(없는 방)에는 나가지 않는다")
    void enter() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ENTERED);
            assertThat(typeOf(board.next())).isEqualTo("BOARD_CHANGED");
            assertThat(board.nothingMore()).isTrue();

            assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ALREADY_ENTERED);
            assertThat(roomMemberService.enter(r("no-such-room"), u("u2"))).isEqualTo(EnterResult.ROOM_NOT_FOUND);
            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("멤버가 나가면 한 번 나간다. 방에 없는 사람의 나가기에는 나가지 않는다")
    void memberLeaves() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.LEFT);
            assertThat(typeOf(board.next())).isEqualTo("BOARD_CHANGED");
            assertThat(board.nothingMore()).isTrue();

            assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.NOT_IN_ROOM);
            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("방장이 나가 방이 없어지면 한 번 나간다")
    void roomClosed() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.ROOM_CLOSED);

            assertThat(typeOf(board.next())).isEqualTo("BOARD_CHANGED");
            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("확정한 방의 방장이 나가 방장이 넘어가도 한 번 나간다 — 인원이 줄었다")
    void hostHandover() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.confirm(r("r1"), u("host"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.LEFT);

            assertThat(typeOf(board.next())).isEqualTo("BOARD_CHANGED");
            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("강퇴하면 한 번 나간다. 방장이 아닌 사람의 강퇴 시도에는 나가지 않는다")
    void kick() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomMemberService.kick(r("r1"), u("u1"), u("u2"))).isEqualTo(KickResult.NOT_HOST);
            assertThat(board.nothingMore()).isTrue();

            assertThat(roomMemberService.kick(r("r1"), u("host"), u("u2"))).isEqualTo(KickResult.KICKED);
            assertThat(typeOf(board.next())).isEqualTo("BOARD_CHANGED");
            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("확정하면 한 번 나간다. 이미 확정된 방의 확정과 혼자서의 확정 시도에는 나가지 않는다")
    void confirm() throws Exception
    {
        roomService.create(r("alone"), u("solo"));
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomService.confirm(r("alone"), u("solo")).result()).isEqualTo(ConfirmResult.NOT_ENOUGH_MEMBERS);
            assertThat(board.nothingMore()).isTrue();

            assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.CONFIRMED);
            assertThat(typeOf(board.next())).isEqualTo("BOARD_CHANGED");
            assertThat(board.nothingMore()).isTrue();

            assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.ALREADY_CONFIRMED);
            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("접속 확인은 아무도 빠지지 않았으면 신호를 내지 않는다 — 1분마다 오는 요청이다")
    void plainHeartbeatSendsNothing() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);
            assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).isEqualTo(HeartbeatResult.ALIVE);

            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("방장의 접속 확인이 유령을 빼면 한 번 나간다 — 몇 명을 뺐든 한 번이다")
    void ghostRemoval() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            // 방장만 0.5초마다 신호를 보낸다. u1 · u2 는 말없이 사라졌다 — 수명(2초)이 지나면 둘의 입장 표시가 만료된다
            for (int i = 0; i < 6; i++)
            {
                Thread.sleep(500);
                assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);
            }

            assertThat(members("r1")).containsExactly("host");
            assertThat(typeOf(board.next())).isEqualTo("BOARD_CHANGED");
            assertThat(board.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("방장이 말없이 사라져 수명이 다한 방은, 남아 있던 멤버의 접속 확인이 '방이 없어졌다'를 받을 때 신호를 낸다")
    void expiredRoomIsSignalledByTheNextMemberHeartbeat() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        try (BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper))
        {
            // u1 만 신호를 보낸다. 방장 키의 수명(2초)이 다하면 방이 없어지고, 그 뒤의 첫 신호가 그것을 알아챈다
            HeartbeatResult last = HeartbeatResult.ALIVE;
            for (int i = 0; i < 8 && last == HeartbeatResult.ALIVE; i++)
            {
                Thread.sleep(500);
                last = roomMemberService.heartbeat(r("r1"), u("u1"));
            }

            assertThat(last).isEqualTo(HeartbeatResult.ROOM_CLOSED);
            assertThat(typeOf(board.next())).isEqualTo("BOARD_CHANGED");
            assertThat(board.nothingMore()).isTrue();
        }
    }

    private static String typeOf(JsonNode envelope)
    {
        assertThat(envelope).as("게시판 신호가 도착하지 않았다").isNotNull();
        return envelope.get("type").asString();
    }
}
