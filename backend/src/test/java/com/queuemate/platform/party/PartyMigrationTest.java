package com.queuemate.platform.party;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5 마이그레이션 — {@code party} 스키마가 <b>스스로 불변식을 지키는지</b>를 앱을 거치지 않고 SQL 로 직접 본다(CLAUDE.md §5).
 * PostgreSQL 에서만 의미가 있다 — 부분 UNIQUE 인덱스는 H2 에 없다.
 *
 * <p><b>모든 식별자가 {@code bigint} 다</b>(2026-09-22 소유자 결정) — 글의 id 는 DB 가 매기고 그것이 곧 {@code roomId} 이며,
 * 파티의 id 는 글의 id 와 따로 매겨진다. 그래서 여기서 직접 넣을 때는 {@code id} 를 주지 않는다.
 */
class PartyMigrationTest extends ApiTestSupport {

    @Test
    @DisplayName("식별자가 전부 bigint identity 이고, 앱이 에러 코드로 옮기는 인덱스 · 제약이 그 이름으로 있고, party 에서 다른 스키마로 가는 FK 는 없다 — 크로스 스키마 FK 금지")
    void namedConstraintsAndNoCrossSchemaForeignKeys()
    {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'party'", String.class);
        // 사용자 번호 · 글 번호 · 파티 번호가 모두 bigint 다 — 하나라도 문자열이면 여기서 드러난다
        List<String> notBigint = jdbcTemplate.queryForList(
                "select table_name || '.' || column_name from information_schema.columns "
                        + "where table_schema = 'party' and column_name in ('id', 'host_id', 'post_id', 'party_id', 'user_id') "
                        + "and data_type <> 'bigint'", String.class);
        List<String> identities = jdbcTemplate.queryForList(
                "select table_name from information_schema.columns where table_schema = 'party' "
                        + "and column_name = 'id' and is_identity = 'YES'", String.class);
        List<String> unnamed = jdbcTemplate.queryForList(
                "select conname from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                        + "where n.nspname = 'party' and conname ~ '_(check|key|fkey)[0-9]+$'", String.class);
        Integer crossSchemaForeignKeys = jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint c "
                        + "join pg_class source on source.oid = c.conrelid join pg_namespace sn on sn.oid = source.relnamespace "
                        + "join pg_class target on target.oid = c.confrelid join pg_namespace tn on tn.oid = target.relnamespace "
                        + "where c.contype = 'f' and (sn.nspname = 'party') <> (tn.nspname = 'party')", Integer.class);

        // recruit_posts_game_id_idx 는 정렬이 id 내림차순 하나가 되며 옛 (game, status, created_at) 인덱스를 대신한 것이다 (2026-09-24 · V7)
        assertThat(indexes).contains("recruit_posts_one_recruiting_per_host", "recruit_posts_game_id_idx",
                        "party_members_user_id_idx", "parties_post_id_key")
                .doesNotContain("recruit_posts_game_status_created_idx");
        assertThat(notBigint).isEmpty();
        assertThat(identities).containsExactlyInAnyOrder("recruit_posts", "parties");
        assertThat(unnamed).isEmpty();
        assertThat(crossSchemaForeignKeys).isZero();
    }

    @Test
    @DisplayName("DB 가 '모집 중인 글은 한 사람에 하나'를 지킨다 — 만료 · 확정된 글은 몇 개든 된다")
    void oneRecruitingPostPerHost()
    {
        Long host = unknownUserId();
        insertPost(host, "RECRUITING");

        assertThatThrownBy(() -> insertPost(host, "RECRUITING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("recruit_posts_one_recruiting_per_host");
        insertPost(host, "EXPIRED");
        insertPost(host, "EXPIRED");
        insertPost(host, "CONFIRMED");
        insertPost(unknownUserId(), "RECRUITING");
    }

    @Test
    @DisplayName("상태와 시각이 어긋난 줄 · 모르는 이름을 DB 가 받지 않는다")
    void checks()
    {
        Long host = unknownUserId();

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
        assertThatThrownBy(() -> jdbcTemplate.update("insert into party.parties (source, post_id, game, status, created_at) "
                + "values ('BOARD', null, 'LOL', 'ACTIVE', now())"))
                .hasMessageContaining("parties_board_has_post_check");
        assertThatThrownBy(() -> jdbcTemplate.update("insert into party.parties (source, post_id, game, status, created_at) "
                + "values ('MATCH', ?, 'LOL', 'ACTIVE', now())", postId))
                .hasMessageContaining("parties_board_has_post_check");
        assertThatThrownBy(() -> jdbcTemplate.update("insert into party.parties (source, post_id, game, status, created_at) "
                + "values ('BOARD', ?, 'LOL', 'CLOSED', now())", postId))
                .hasMessageContaining("parties_closed_at_check");

        Long partyId = insertBoardParty(postId);
        // 한 글에 파티 하나 — 파티의 id 는 DB 가 따로 매기므로 PK 가 아니라 UNIQUE (post_id) 가 지킨다
        assertThatThrownBy(() -> insertBoardParty(postId))
                .hasMessageContaining("parties_post_id_key");
        jdbcTemplate.update("insert into party.party_members (party_id, user_id, is_host, joined_at) values (?, ?, true, now())",
                partyId, host);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into party.party_members (party_id, user_id, is_host, joined_at) values (?, ?, false, now())", partyId, host))
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
        return "insert into party.recruit_posts (host_id, game, title, voice, purpose, status, created_at, updated_at, "
                + "confirmed_at, expired_at) values (?, 'LOL', 't', 'REQUIRED', 'FUN', " + status + ", now(), now(), "
                + confirmedAt + ", " + expiredAt + ")";
    }

    /** 파티 번호도 DB 가 매긴다 — 글 번호와 다른 값이다 */
    private Long insertBoardParty(Long postId)
    {
        return jdbcTemplate.queryForObject("insert into party.parties (source, post_id, game, status, created_at) "
                + "values ('BOARD', ?, 'LOL', 'ACTIVE', now()) returning id", Long.class, postId);
    }
}
