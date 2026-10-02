package com.queuemate.platform.party;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 파티 모집 게시판의 테이블({@code recruit_posts} · {@code recruit_post_positions} · {@code parties} · {@code party_members})이
 * <b>스스로 불변식을 지키는지</b>를 앱을 거치지 않고 SQL 로 직접 본다(CLAUDE.md §5). PostgreSQL 에서만 의미가 있다 — 부분 UNIQUE 인덱스는 H2 에 없다.
 *
 * <p><b>모든 식별자가 {@code bigint} 다</b>(2026-09-22 소유자 결정) — 글의 id 는 DB 가 매기고 그것이 곧 {@code roomId} 이며,
 * 파티의 id 는 글의 id 와 따로 매겨진다. 그래서 여기서 직접 넣을 때는 {@code id} 를 주지 않는다.
 *
 * <p><b>테이블은 {@code public} 하나에 있고 사용자 번호의 칸에는 {@code users(id)} 로 FK 가 있다</b>(2026-09-26 소유자 결정 — 옛 {@code party} 스키마를 합쳤다).
 * 그래서 방장 · 파티원은 {@link #insertUser()} 로 넣은 진짜 사용자여야 한다.
 */
class PartyMigrationTest extends ApiTestSupport {

    /** 이 테스트가 보는 테이블 — 옛 {@code party} 스키마에 있던 것들이다 */
    private static final String PARTY_TABLES = "('recruit_posts', 'recruit_post_positions', 'parties', 'party_members')";

    @Test
    @DisplayName("식별자가 전부 bigint identity 이고, 앱이 에러 코드로 옮기는 인덱스 · 제약이 그 이름으로 있다")
    void namedConstraintsAndIndexes()
    {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public' and tablename in " + PARTY_TABLES, String.class);
        // 사용자 번호 · 글 번호 · 파티 번호가 모두 bigint 다 — 하나라도 문자열이면 여기서 드러난다
        List<String> notBigint = jdbcTemplate.queryForList(
                "select table_name || '.' || column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name in " + PARTY_TABLES
                        + " and column_name in ('id', 'host_id', 'post_id', 'party_id', 'user_id') "
                        + "and data_type <> 'bigint'", String.class);
        List<String> identities = jdbcTemplate.queryForList(
                "select table_name from information_schema.columns where table_schema = 'public' and table_name in " + PARTY_TABLES
                        + " and column_name = 'id' and is_identity = 'YES'", String.class);
        List<String> unnamed = jdbcTemplate.queryForList(
                "select conname from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                        + "where n.nspname = 'public' and conname ~ '_(check|key|fkey)[0-9]+$'", String.class);

        // recruit_posts_game_id_idx 는 정렬이 id 내림차순 하나가 되며 옛 (game, status, created_at) 인덱스를 대신한 것이다 (2026-09-24)
        assertThat(indexes).contains("recruit_posts_one_recruiting_per_host", "recruit_posts_game_id_idx",
                        "party_members_user_id_idx", "parties_post_id_key")
                .doesNotContain("recruit_posts_game_status_created_idx");
        assertThat(notBigint).isEmpty();
        assertThat(identities).containsExactlyInAnyOrder("recruit_posts", "parties");
        assertThat(unnamed).isEmpty();
    }

    @Test
    @DisplayName("방장 · 파티원의 칸에서 users 로 FK 가 있다 — 파티원은 ON DELETE CASCADE, 방장은 ON DELETE SET NULL(V9 · 2026-10-02). 없는 사용자는 넣을 수 없다")
    void userForeignKeysCascade()
    {
        // 칸 → (가리키는 테이블.칸, 지울 때의 동작 — c 는 CASCADE · n 은 SET NULL). 방장 칸은 2026-10-02 에 SET NULL 이 됐다 — 탈퇴해도 확정된 파티 기록은 남긴다(P-48)
        List<String> foreignKeys = jdbcTemplate.queryForList(
                "select c.conname || ' ' || target.relname || '.' || a.attname || ' ' || c.confdeltype::text "
                        + "from pg_constraint c "
                        + "join pg_class target on target.oid = c.confrelid "
                        + "join pg_attribute a on a.attrelid = c.confrelid and a.attnum = c.confkey[1] "
                        + "where c.contype = 'f' and c.conname in ('recruit_posts_host_id_fkey', 'party_members_user_id_fkey')",
                String.class);

        assertThat(foreignKeys).containsExactlyInAnyOrder(
                "recruit_posts_host_id_fkey users.id n",
                "party_members_user_id_fkey users.id c");
        assertThatThrownBy(() -> insertPost(unknownUserId(), "RECRUITING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("recruit_posts_host_id_fkey");
    }

    @Test
    @DisplayName("V9 — 방장이 비는 글은 확정된 글뿐이다(recruit_posts_host_id_check). 비확정 글을 남긴 채 방장을 지우면 SET NULL 이 거절되고 아무것도 지워지지 않는다")
    void onlyConfirmedPostsLoseTheirHost()
    {
        Long host = insertUser();
        Long confirmed = insertPost(host, "CONFIRMED");
        Long partyId = insertBoardParty(confirmed);
        jdbcTemplate.update("insert into party_members (party_id, user_id, is_host, joined_at) values (?, ?, true, now())",
                partyId, host);
        try
        {
            // 방장 칸을 손으로 비울 수 있는 것도 확정된 글뿐이다
            assertThatThrownBy(() -> jdbcTemplate.update(insertPostSql("'RECRUITING'", "null", "null"), (Object) null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("recruit_posts_host_id_check");
            assertThatThrownBy(() -> jdbcTemplate.update(insertPostSql("'EXPIRED'", "null", "now()"), (Object) null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("recruit_posts_host_id_check");

            // 만료된 글이 남아 있으면 방장을 지울 수 없다 — 탈퇴는 비확정 글을 먼저 지운다(PostStore#deleteUnconfirmedOf)
            Long expired = insertPost(host, "EXPIRED");
            assertThatThrownBy(() -> jdbcTemplate.update("delete from users where id = ?", host))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("recruit_posts_host_id_check");
            assertThat(jdbcTemplate.queryForObject("select count(*) from users where id = ?", Integer.class, host)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("select host_id from recruit_posts where id = ?", Long.class, confirmed)).isEqualTo(host);

            jdbcTemplate.update("delete from recruit_posts where id = ?", expired);
            jdbcTemplate.update("delete from users where id = ?", host);

            // 확정된 글 · 파티는 남고 방장 칸만 빈다. 방장의 파티원 줄은 CASCADE 로 빠진다
            assertThat(jdbcTemplate.queryForObject("select host_id from recruit_posts where id = ?", Long.class, confirmed)).isNull();
            assertThat(jdbcTemplate.queryForObject("select count(*) from parties where id = ?", Integer.class, partyId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("select count(*) from party_members where party_id = ?", Integer.class, partyId)).isZero();
        }
        finally
        {
            // 방장이 빈 글은 뒷정리(사용자의 글을 지운다)가 찾지 못한다
            jdbcTemplate.update("delete from recruit_posts where id = ?", confirmed);
        }
    }

    @Test
    @DisplayName("V10 — allow_auto_join 은 boolean NOT NULL 이고 기본값이 true 다(옛 글은 합류 대상이었다 — 2026-10-02 · P-50). NULL 은 받지 않는다")
    void allowAutoJoinColumn()
    {
        Long host = insertUser();
        // 칸을 모르는 SQL 로 넣은 글 — V10 이 옛 글을 채운 값과 같은 true 다
        Long postId = insertPost(host, "EXPIRED");

        assertThat(jdbcTemplate.queryForObject("select allow_auto_join from recruit_posts where id = ?", Boolean.class, postId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select data_type || ' ' || is_nullable from information_schema.columns "
                + "where table_schema = 'public' and table_name = 'recruit_posts' and column_name = 'allow_auto_join'", String.class))
                .isEqualTo("boolean NO");
        assertThatThrownBy(() -> jdbcTemplate.update("update recruit_posts set allow_auto_join = null where id = ?", postId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("allow_auto_join");
    }

    @Test
    @DisplayName("room_seen_at 이 없다 — 글 쓰기가 방을 같이 만들어 '아직 안 만들어진 방' 을 가를 일이 없어졌다(2026-09-25 2단계)")
    void noRoomSeenAt()
    {
        List<String> columns = jdbcTemplate.queryForList("select column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name = 'recruit_posts'", String.class);

        assertThat(columns).doesNotContain("room_seen_at").contains("id", "host_id", "status", "created_at", "expired_at");
    }

    @Test
    @DisplayName("DB 가 '모집 중인 글은 한 사람에 하나'를 지킨다 — 만료 · 확정된 글은 몇 개든 된다")
    void oneRecruitingPostPerHost()
    {
        Long host = insertUser();
        insertPost(host, "RECRUITING");

        assertThatThrownBy(() -> insertPost(host, "RECRUITING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("recruit_posts_one_recruiting_per_host");
        insertPost(host, "EXPIRED");
        insertPost(host, "EXPIRED");
        insertPost(host, "CONFIRMED");
        insertPost(insertUser(), "RECRUITING");
    }

    @Test
    @DisplayName("상태와 시각이 어긋난 줄 · 모르는 이름을 DB 가 받지 않는다")
    void checks()
    {
        Long host = insertUser();

        assertThatThrownBy(() -> jdbcTemplate.update(insertPostSql("'CONFIRMED'", "null", "null"), host))
                .hasMessageContaining("recruit_posts_confirmed_at_check");
        assertThatThrownBy(() -> jdbcTemplate.update(insertPostSql("'RECRUITING'", "null", "now()"), host))
                .hasMessageContaining("recruit_posts_expired_at_check");
        assertThatThrownBy(() -> jdbcTemplate.update(insertPostSql("'DRAFT'", "null", "null"), host))
                .hasMessageContaining("recruit_posts_status_check");
        assertThatThrownBy(() -> jdbcTemplate.update(
                insertPostSql("'RECRUITING'", "null", "null").replace("'LOL'", "'OVERWATCH'"), host))
                .hasMessageContaining("recruit_posts_game_check");

        Long postId = insertPost(host, "CONFIRMED");
        // 게시판 파티는 글이 있어야 하고, 자동 매칭 파티는 글이 없어야 한다
        assertThatThrownBy(() -> jdbcTemplate.update("insert into parties (source, post_id, game, status, created_at) "
                + "values ('BOARD', null, 'LOL', 'ACTIVE', now())"))
                .hasMessageContaining("parties_board_has_post_check");
        assertThatThrownBy(() -> jdbcTemplate.update("insert into parties (source, post_id, game, status, created_at) "
                + "values ('MATCH', ?, 'LOL', 'ACTIVE', now())", postId))
                .hasMessageContaining("parties_board_has_post_check");
        assertThatThrownBy(() -> jdbcTemplate.update("insert into parties (source, post_id, game, status, created_at) "
                + "values ('BOARD', ?, 'LOL', 'CLOSED', now())", postId))
                .hasMessageContaining("parties_closed_at_check");

        Long partyId = insertBoardParty(postId);
        // 한 글에 파티 하나 — 파티의 id 는 DB 가 따로 매기므로 PK 가 아니라 UNIQUE (post_id) 가 지킨다
        assertThatThrownBy(() -> insertBoardParty(postId))
                .hasMessageContaining("parties_post_id_key");
        jdbcTemplate.update("insert into party_members (party_id, user_id, is_host, joined_at) values (?, ?, true, now())",
                partyId, host);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into party_members (party_id, user_id, is_host, joined_at) values (?, ?, false, now())", partyId, host))
                .hasMessageContaining("party_members_pkey");
    }

    /** 글 번호는 DB 가 매긴다 — 넣은 줄의 번호를 돌려받는다 */
    private Long insertPost(Long hostId, String status)
    {
        return jdbcTemplate.queryForObject(insertPostSql("'" + status + "'",
                        "CONFIRMED".equals(status) ? "now()" : "null", "EXPIRED".equals(status) ? "now()" : "null")
                        + " returning id", Long.class, hostId);
    }

    /** {@code id} 를 주지 않는다 — {@code GENERATED ALWAYS AS IDENTITY} 라 넣을 수도 없다 */
    private static String insertPostSql(String status, String confirmedAt, String expiredAt)
    {
        return "insert into recruit_posts (host_id, game, title, voice, status, created_at, updated_at, "
                + "confirmed_at, expired_at) values (?, 'LOL', 't', 'REQUIRED', " + status + ", now(), now(), "
                + confirmedAt + ", " + expiredAt + ")";
    }

    /** 파티 번호도 DB 가 매긴다 — 글 번호와 다른 값이다 */
    private Long insertBoardParty(Long postId)
    {
        return jdbcTemplate.queryForObject("insert into parties (source, post_id, game, status, created_at) "
                + "values ('BOARD', ?, 'LOL', 'ACTIVE', now()) returning id", Long.class, postId);
    }
}
