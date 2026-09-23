package com.queuemate.platform.social;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V6 마이그레이션 — 친구 요청 · 친구 · 신고 · 최근 함께한 사람의 테이블이 <b>DB 스스로 불변식을 지키는지</b>(CLAUDE.md §3.5 · §5).
 * 앱을 거치지 않고 SQL 로 직접 본다. <b>{@code id} 는 identity 라 넣을 때 주지 않는다.</b>
 *
 * <p>PostgreSQL 에서만 의미가 있다 — H2 는 partial unique index 를 재현하지 못한다(docs/11 D-3).
 * 롤 · GRANT 는 보지 않는다 — 스키마별 DB 롤은 두지 않는다(2026-09-22 소유자 결정. {@link BlockMigrationTest} 도 같다).
 */
class SocialMigrationTest extends ApiTestSupport {

    @Test
    @DisplayName("앱이 에러 코드로 옮기는 제약 · 인덱스가 그 이름으로 있고, 사람 · 파티 · 글을 가리키는 칸은 전부 bigint 다. social 에서 밖으로 나가는 FK 는 없다 — 크로스 스키마 FK 금지")
    void namedConstraintsAndIndexes()
    {
        List<String> constraints = jdbcTemplate.queryForList(
                "select conname from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                        + "where n.nspname = 'social'", String.class);
        Integer foreignKeys = jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                        + "where n.nspname = 'social' and c.contype = 'f'", Integer.class);
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'social'", String.class);
        String onePending = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where schemaname = 'social' and indexname = 'friend_requests_one_pending'", String.class);
        // 사용자 번호 · 파티 · 글의 id 는 전부 bigint 다(2026-09-22 소유자 결정) — 문자열이던 때의 varchar 가 한 칸이라도 남아 있으면 안 된다
        List<Map<String, Object>> idColumns = jdbcTemplate.queryForList(
                "select table_name, column_name, data_type from information_schema.columns "
                        + "where table_schema = 'social' and (column_name = 'id' or column_name like '%\\_id') "
                        + "order by table_name, column_name");

        assertThat(constraints).contains(
                "friend_requests_pkey", "friend_requests_status_check", "friend_requests_not_self", "friend_requests_responded_at_check",
                "friendships_pkey", "friendships_ordered",
                "reports_pkey", "reports_reason_check", "reports_status_check", "reports_not_self",
                "recent_players_pkey", "recent_players_not_self");
        assertThat(foreignKeys).isZero();
        assertThat(indexes).contains("friend_requests_one_pending", "friend_requests_receiver_pending_idx",
                "friendships_user_high_id_idx", "recent_players_user_recent_idx");
        assertThat(onePending).contains("UNIQUE").contains("(requester_id, receiver_id)").contains("PENDING");
        assertThat(idColumns).isNotEmpty();
        assertThat(idColumns).extracting(column -> column.get("data_type")).containsOnly("bigint");
    }

    @Test
    @DisplayName("친구 요청 — 같은 방향의 PENDING 은 하나뿐이다. 처리된 줄은 여러 개여도 되고 반대 방향은 다른 줄이다. 자기 자신 · 모르는 상태 · 상태와 안 맞는 responded_at 은 거절된다")
    void friendRequestConstraints()
    {
        Long a = unknownUserId();
        Long b = unknownUserId();
        insertRequest(a, b, "PENDING", false);

        assertThatThrownBy(() -> insertRequest(a, b, "PENDING", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("friend_requests_one_pending");
        // 처리된 줄은 인덱스에 없다 — 거절된 뒤 다시 요청할 수 있는 이유다
        insertRequest(a, b, "DECLINED", true);
        insertRequest(a, b, "DECLINED", true);
        insertRequest(a, b, "CANCELED", true);
        // 반대 방향은 다른 줄이다 — DB 는 양방향 PENDING 을 막지 않는다(수락할 때 앱이 둘 다 닫는다)
        insertRequest(b, a, "PENDING", false);

        assertThatThrownBy(() -> insertRequest(a, a, "PENDING", false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("friend_requests_not_self");
        assertThatThrownBy(() -> insertRequest(b, a, "BLOCKED", true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("friend_requests_status_check");
        assertThatThrownBy(() -> insertRequest(b, a, "ACCEPTED", false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("friend_requests_responded_at_check");
        assertThatThrownBy(() -> insertRequest(unknownUserId(), unknownUserId(), "PENDING", true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("friend_requests_responded_at_check");
    }

    @Test
    @DisplayName("친구 — (작은 쪽, 큰 쪽) 한 줄뿐이다. 뒤집힌 순서 · 자기 자신 · 같은 쌍 두 번은 거절된다. 순서는 번호의 크기다")
    void friendshipConstraints()
    {
        Long first = unknownUserId();
        Long second = unknownUserId();
        Long low = Math.min(first, second);
        Long high = Math.max(first, second);
        insertFriendship(low, high);

        assertThatThrownBy(() -> insertFriendship(low, high))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("friendships_pkey");
        assertThatThrownBy(() -> insertFriendship(high, low))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("friendships_ordered");
        assertThatThrownBy(() -> insertFriendship(low, low))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("friendships_ordered");

        // 두 칸이 bigint 라 DB 의 비교가 자바의 Long 비교와 늘 같다 — 정렬 규칙을 맞출 것이 없다
        assertThat(jdbcTemplate.queryForList("select data_type from information_schema.columns "
                + "where table_schema = 'social' and table_name = 'friendships' "
                + "and column_name in ('user_low_id', 'user_high_id')", String.class)).containsOnly("bigint");
        // 자릿수가 다른 번호 — 문자열로 비교하면 "9…" 가 "10…" 보다 커서 뒤집히지만, 숫자 비교는 뒤집히지 않는다.
        // 어느 쪽이 먼저 요청했든 (작은 쪽, 큰 쪽) 한 줄이다
        List<Long> tricky = List.of(90_000_000_000_001L, 900_000_000_000_002L, 1_000_000_000_000_003L, 9_000_000_000_000_004L);
        for(Long x : tricky)
        {
            for(Long y : tricky)
            {
                if(x < y)
                {
                    insertFriendship(x, y);
                }
            }
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from social.friendships where user_low_id in (?, ?, ?, ?)",
                Integer.class, tricky.get(0), tricky.get(1), tricky.get(2), tricky.get(3))).isEqualTo(6);
        jdbcTemplate.update("delete from social.friendships where user_low_id in (?, ?, ?, ?)",
                tricky.get(0), tricky.get(1), tricky.get(2), tricky.get(3));
    }

    @Test
    @DisplayName("신고 — 자기 자신 · 모르는 사유 · 모르는 상태는 거절된다. 상태의 기본값은 RECEIVED 이고 같은 사람을 여러 번 신고할 수 있다")
    void reportConstraints()
    {
        Long reporter = unknownUserId();
        Long target = unknownUserId();
        insertReport(reporter, target, "ABUSE");
        insertReport(reporter, target, "ABUSE");

        assertThat(jdbcTemplate.queryForList("select distinct status from social.reports where reporter_id = ?", String.class, reporter))
                .containsExactly("RECEIVED");
        assertThatThrownBy(() -> insertReport(reporter, reporter, "ABUSE"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("reports_not_self");
        assertThatThrownBy(() -> insertReport(reporter, target, "RUDE"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("reports_reason_check");
        assertThatThrownBy(() -> jdbcTemplate.update("update social.reports set status = 'BANNED' where reporter_id = ?", reporter))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("reports_status_check");
    }

    @Test
    @DisplayName("최근 함께한 사람 — 한 사람에 한 줄이다. 자기 자신은 거절된다")
    void recentPlayerConstraints()
    {
        Long user = unknownUserId();
        Long other = unknownUserId();
        insertRecent(user, other);
        // 반대 방향은 다른 줄이다 — 각자의 목록이다
        insertRecent(other, user);

        assertThatThrownBy(() -> insertRecent(user, other))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("recent_players_pkey");
        assertThatThrownBy(() -> insertRecent(user, user))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("recent_players_not_self");
    }

    private void insertRequest(Long requesterId, Long receiverId, String status, boolean responded)
    {
        jdbcTemplate.update("insert into social.friend_requests (requester_id, receiver_id, status, created_at, responded_at) "
                + "values (?, ?, ?, now(), " + (responded ? "now()" : "null") + ")", requesterId, receiverId, status);
    }

    private void insertFriendship(Long lowId, Long highId)
    {
        jdbcTemplate.update("insert into social.friendships (user_low_id, user_high_id, created_at) values (?, ?, now())", lowId, highId);
    }

    private void insertReport(Long reporterId, Long targetUserId, String reason)
    {
        jdbcTemplate.update("insert into social.reports (reporter_id, target_user_id, reason, created_at) values (?, ?, ?, now())",
                reporterId, targetUserId, reason);
    }

    /** 파티의 id 도 번호다 — 없는 파티여도 된다(FK 가 없다) */
    private void insertRecent(Long userId, Long otherUserId)
    {
        jdbcTemplate.update("insert into social.recent_players (user_id, other_user_id, last_party_id, last_played_at) "
                + "values (?, ?, 930001, now())", userId, otherUserId);
    }
}
