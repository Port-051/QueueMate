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
 * {@code blocks} 테이블({@code V1__schema.sql}) — {@code blocks} 가 <b>{@code matching} 이 읽는 모양 그대로</b>인지, DB 가 스스로 불변식을 지키는지
 * (CLAUDE.md §3.5 · docs/11 D-4). 앱을 거치지 않고 SQL 로 직접 본다.
 *
 * <p>PostgreSQL 에서만 의미가 있다 — H2 는 제약을 그대로 재현하지 못한다(docs/11 D-3).
 *
 * <p>롤 · GRANT 는 보지 않는다 — 스키마별 DB 롤은 두지 않는다(2026-09-22 소유자 결정. {@code matching} 은 별도 롤 없이 이 테이블을 읽는다).
 */
class BlockMigrationTest extends ApiTestSupport {

    @Test
    @DisplayName("컬럼의 이름과 자료형이 matching 이 읽어야 하는 모양과 맞는다 — id · blocker_id · blocked_id 전부 bigint")
    void columnsMatchWhatMatchingReads()
    {
        // matching 의 block/Block.java — @Table(schema = "social", name = "blocks"), Long id, blocker_id, blocked_id.
        // ** 그쪽은 아직 blocker_id · blocked_id 를 String 으로 읽고, 스키마도 social 로 적혀 있다 — 2026-09-22 에 bigint 가 됐고
        //    2026-09-26 에 public 으로 옮겼다(소유자 결정). matching 폴더에서 둘 다 고쳐야 한다. 고치기 전까지 matching 은 이 테이블을 읽다가 런타임에 깨진다.
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "select column_name, data_type, is_nullable, is_identity from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'blocks' order by ordinal_position");

        assertThat(columns).extracting(column -> column.get("column_name"))
                .containsExactly("id", "blocker_id", "blocked_id", "created_at");
        assertThat(columns).extracting(column -> column.get("data_type"))
                .containsExactly("bigint", "bigint", "bigint", "timestamp with time zone");
        assertThat(columns).extracting(column -> column.get("is_nullable")).containsOnly("NO");
        // 채번은 DB 가 한다 — 앱이 id 를 주지 않는다
        assertThat(columns.get(0).get("is_identity")).isEqualTo("YES");
    }

    @Test
    @DisplayName("앱이 에러 코드로 옮기는 제약이 그 이름으로 있고, 두 칸 다 users 로 FK 가 있으며 ON DELETE CASCADE 다(2026-09-26)")
    void namedConstraints()
    {
        List<String> constraints = jdbcTemplate.queryForList(
                "select conname from pg_constraint where conrelid = 'public.blocks'::regclass", String.class);
        List<String> foreignKeys = jdbcTemplate.queryForList(
                "select c.conname || ' ' || c.confrelid::regclass::text || ' ' || c.confdeltype::text from pg_constraint c "
                        + "where c.conrelid = 'public.blocks'::regclass and c.contype = 'f'", String.class);
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public' and tablename = 'blocks'", String.class);

        assertThat(constraints).contains("blocks_pkey", "blocks_blocker_blocked_key", "blocks_not_self");
        assertThat(foreignKeys).containsExactlyInAnyOrder("blocks_blocker_id_fkey users c", "blocks_blocked_id_fkey users c");
        assertThat(indexes).contains("blocks_blocked_id_idx");
    }

    @Test
    @DisplayName("DB 가 같은 차단 두 번 · 자기 자신 차단 · 없는 사용자 차단을 거절한다 — 앱을 거치지 않아도 막힌다")
    void uniqueAndNotSelf()
    {
        Long blocker = insertUser();
        Long blocked = insertUser();
        insertBlock(blocker, blocked);

        assertThatThrownBy(() -> insertBlock(blocker, blocked))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("blocks_blocker_blocked_key");
        assertThatThrownBy(() -> insertBlock(blocker, blocker))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("blocks_not_self");
        // 반대 방향은 다른 줄이다
        insertBlock(blocked, blocker);
        // 없는 사용자는 FK 가 거절한다 — 앱은 이 위반을 404 USER_NOT_FOUND 로 옮긴다
        assertThatThrownBy(() -> insertBlock(blocker, unknownUserId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("blocks_blocked_id_fkey");
    }

    /** {@code id} 는 주지 않는다 — identity 라 DB 가 매긴다 */
    private void insertBlock(Long blockerId, Long blockedId)
    {
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())",
                blockerId, blockedId);
    }
}
