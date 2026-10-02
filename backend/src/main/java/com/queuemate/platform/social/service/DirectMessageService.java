package com.queuemate.platform.social.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.common.push.PushPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** 방 채팅과 분리된 개인 메시지. 모든 조회는 로그인한 사람과 상대의 쌍으로 한정한다. */
@Service
@RequiredArgsConstructor
public class DirectMessageService {
    private final JdbcTemplate jdbc;
    private final BlockReader blocks;
    private final PushPublisher push;

    public record Person(Long userId, String nickname) {}
    public record Message(Long id, Long senderId, Long recipientId, String text, Instant sentAt) {}
    public record Conversation(Person user, List<Message> messages, Long nextCursor) {}
    public record Thread(Person user, Message lastMessage) {}
    private static final RowMapper<Message> MESSAGE = (r, n) -> new Message(r.getLong("id"), r.getLong("sender_id"),
            r.getLong("recipient_id"), r.getString("body"), r.getTimestamp("sent_at").toInstant());

    private Person target(Long me, Long other) {
        if (other == null || other.equals(me) || !blocks.findBlockedEitherWay(me, List.of(other)).isEmpty()) throw unavailable();
        return jdbc.query("SELECT id, nickname FROM users WHERE id = ?", (r, n) -> new Person(r.getLong(1), r.getString(2)), other)
                .stream().findFirst().orElseThrow(DirectMessageService::unavailable);
    }

    @Transactional(readOnly = true)
    public List<Thread> threads(Long me) {
        var found = jdbc.query("""
                SELECT m.*, u.nickname FROM (
                  SELECT DISTINCT ON (other_id) * FROM (
                    SELECT *, CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END AS other_id
                    FROM direct_messages WHERE sender_id = ? OR recipient_id = ?
                  ) visible ORDER BY other_id, id DESC
                ) m JOIN users u ON u.id = m.other_id
                ORDER BY m.id DESC LIMIT 100
                """, (r, n) -> new Thread(new Person(r.getLong("other_id"), r.getString("nickname")), MESSAGE.mapRow(r, n)), me, me, me);
        var hidden = blocks.findBlockedEitherWay(me, found.stream().map(t -> t.user().userId()).toList());
        return found.stream().filter(t -> !hidden.contains(t.user().userId())).toList();
    }

    @Transactional(readOnly = true)
    public Conversation conversation(Long me, Long other, Long before) {
        Person user = target(me, other);
        var rows = jdbc.query("""
                SELECT * FROM direct_messages WHERE ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))
                AND id < ? ORDER BY id DESC LIMIT 51
                """, MESSAGE, me, other, other, me, before == null ? Long.MAX_VALUE : before);
        boolean more = rows.size() > 50;
        var page = new ArrayList<>(rows.subList(0, Math.min(50, rows.size())));
        Long next = more ? page.getLast().id() : null;
        Collections.reverse(page);
        return new Conversation(user, List.copyOf(page), next);
    }

    @Transactional
    public Message send(Long me, Long other, UUID clientId, String text) {
        target(me, other);
        // 사용자 행을 잠가 탈퇴와 경합해도 외래키 오류가 노출되지 않게 한다.
        var locked = jdbc.queryForList("SELECT id FROM users WHERE id IN (?, ?) ORDER BY id FOR KEY SHARE", Long.class, me, other);
        if (!locked.contains(me)) throw ApiException.unauthenticated();
        if (!locked.contains(other)) throw unavailable();
        String body = text.strip();
        var inserted = jdbc.query("""
                INSERT INTO direct_messages(sender_id, recipient_id, client_message_id, body)
                VALUES (?, ?, ?, ?) ON CONFLICT (sender_id, client_message_id) DO NOTHING RETURNING *
                """, MESSAGE, me, other, clientId, body);
        if (inserted.isEmpty()) {
            Message previous = jdbc.query("SELECT * FROM direct_messages WHERE sender_id = ? AND client_message_id = ?", MESSAGE, me, clientId).getFirst();
            if (!previous.recipientId().equals(other) || !previous.text().equals(body))
                throw new ApiException(HttpStatus.CONFLICT, "MESSAGE_RETRY_CONFLICT", "다른 메시지에 사용한 전송 번호입니다");
            return previous;
        }
        Message message = inserted.getFirst();
        push.publishAfterCommit(other, PushEventType.DIRECT_MESSAGE_RECEIVED, Map.of("fromUserId", String.valueOf(me)));
        push.publishAfterCommit(me, PushEventType.DIRECT_MESSAGE_RECEIVED, Map.of("fromUserId", String.valueOf(other)));
        return message;
    }

    private static ApiException unavailable() { return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "대화할 수 없는 사용자입니다"); }
}
