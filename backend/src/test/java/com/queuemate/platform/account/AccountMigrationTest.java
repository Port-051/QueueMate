package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 마이그레이션({@code V1__schema.sql}) — 테이블이 생겼는지, <b>DB 가 스스로</b> 불변식을 지키는지. 앱을 거치지 않고 SQL 로 직접 본다.
 * 테이블은 전부 {@code public} 하나에 있다(2026-09-26 소유자 결정 — 옛 {@code account} · {@code social} · {@code party} 스키마를 합쳤다).
 * PostgreSQL 에서만 의미가 있다 — H2 는 {@code ~} 정규식 CHECK 를 재현하지 못한다.
 *
 * <p>사용자의 식별자는 <b>사용자 번호</b>({@code id}, bigint identity) 하나다(2026-09-26 소유자 결정 — 로그인은 소셜뿐이라 로그인 아이디 ·
 * 비밀번호의 칸이 없다). 번호는 DB 가 매기므로 INSERT 에 주지 않고 {@code RETURNING} 으로 받는다.
 */
class AccountMigrationTest extends ApiTestSupport {

    @Test
    @DisplayName("public 스키마에 테이블 열셋이 있고(Flyway 의 기록 테이블 말고), 옛 스키마 셋은 없다")
    void schemaAndTablesExist()
    {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' "
                        + "and table_name <> 'flyway_schema_history' order by table_name",
                String.class);
        List<String> oldSchemas = jdbcTemplate.queryForList(
                "select nspname from pg_namespace where nspname in ('account', 'social', 'party')", String.class);

        assertThat(tables).containsExactly("blocks", "friend_requests", "friendships", "game_account_stats",
                "game_accounts", "parties", "party_members", "recent_players", "recruit_post_positions", "recruit_posts",
                "reports", "social_identities", "users");
        assertThat(oldSchemas).isEmpty();
    }

    @Test
    @DisplayName("사용자를 지우면 딸린 줄이 전부 같이 지워진다 — 차단 · 친구 요청 · 친구 · 신고 · 최근 함께한 사람 · 글 · 파티 · 파티원(ON DELETE CASCADE)")
    void deletingUserCascades()
    {
        Long gone = insertUser();
        Long other = insertUser();
        Long low = Math.min(gone, other);
        Long high = Math.max(gone, other);
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now()), (?, ?, now())",
                gone, other, other, gone);
        jdbcTemplate.update("insert into friend_requests (requester_id, receiver_id, status, created_at) values (?, ?, 'PENDING', now())",
                other, gone);
        jdbcTemplate.update("insert into friendships (user_low_id, user_high_id, created_at) values (?, ?, now())", low, high);
        jdbcTemplate.update("insert into reports (reporter_id, target_user_id, reason, created_at) values (?, ?, 'ABUSE', now())",
                other, gone);
        // 지워지는 사람이 방장인 글과 그 글의 파티 — 다른 사람도 파티원이다
        Long postId = jdbcTemplate.queryForObject("insert into recruit_posts "
                + "(host_id, game, mode, title, voice, status, created_at, updated_at, confirmed_at) "
                + "values (?, 'LOL', 'RANKED_SOLO', 't', 'REQUIRED', 'CONFIRMED', now(), now(), now()) returning id", Long.class, gone);
        Long partyId = jdbcTemplate.queryForObject("insert into parties (source, post_id, game, status, created_at) "
                + "values ('BOARD', ?, 'LOL', 'ACTIVE', now()) returning id", Long.class, postId);
        jdbcTemplate.update("insert into party_members (party_id, user_id, is_host, joined_at) values (?, ?, true, now()), (?, ?, false, now())",
                partyId, gone, partyId, other);
        // 남는 사람의 "최근 함께한 사람" 에 지워지는 사람이 있다 — 줄이 딸려 지워진다
        jdbcTemplate.update("insert into recent_players (user_id, other_user_id, last_party_id, last_played_at) "
                + "values (?, ?, ?, now()), (?, ?, ?, now())", other, gone, partyId, gone, other, partyId);

        jdbcTemplate.update("delete from users where id = ?", gone);

        assertThat(count("select count(*) from blocks where blocker_id = ? or blocked_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from friend_requests where requester_id = ? or receiver_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from friendships where user_low_id = ? and user_high_id = ?", low, high)).isZero();
        assertThat(count("select count(*) from reports where reporter_id = ? or target_user_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from recent_players where user_id = ? or other_user_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from recruit_posts where id = ?", postId)).isZero();
        // 글이 지워지면 그 글의 파티와 파티원(남는 사람 것까지)도 딸려 지워진다
        assertThat(count("select count(*) from parties where id = ?", partyId)).isZero();
        assertThat(count("select count(*) from party_members where party_id = ?", partyId)).isZero();
        // 남는 사람은 그대로다
        assertThat(count("select count(*) from users where id = ?", other)).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자의 칸은 번호 · 닉네임 · 시각 둘뿐이다 — 번호는 bigint identity 이고 DB 가 매긴다")
    void userIdentifierColumns()
    {
        assertThat(dataTypeOf("users", "id")).isEqualTo("bigint");
        // identity 라 INSERT 에 주지 않는다 — DB 가 매긴다
        assertThat(jdbcTemplate.queryForObject(
                "select is_identity::text from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'users' and column_name = 'id'",
                String.class)).isEqualTo("YES");
        // 로그인 아이디의 칸이 없다 — 식별자는 번호 하나, 보여 주는 이름은 닉네임 하나다(2026-09-26)
        assertThat(jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'users' order by column_name",
                String.class)).containsExactly("created_at", "id", "nickname", "updated_at");
        // 다른 테이블이 사용자 · 게임 계정을 가리키는 칸도 전부 bigint 다
        assertThat(dataTypeOf("social_identities", "user_id")).isEqualTo("bigint");
        assertThat(dataTypeOf("game_accounts", "user_id")).isEqualTo("bigint");
        assertThat(dataTypeOf("game_account_stats", "game_account_id")).isEqualTo("bigint");
    }

    @Test
    @DisplayName("앱이 에러 코드로 옮기는 제약이 그 이름으로 있다")
    void namedConstraintsExist()
    {
        List<String> constraints = jdbcTemplate.queryForList(
                "select conname from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                        + "where n.nspname = 'public' and c.contype in ('p', 'u', 'c', 'f')", String.class);

        assertThat(constraints).contains("users_pkey", "users_nickname_key",
                "game_accounts_pkey", "game_accounts_user_id_fkey", "game_accounts_user_id_game_key",
                "game_accounts_game_check",
                "game_accounts_server_check",
                "game_account_stats_pkey", "game_account_stats_game_account_id_fkey",
                "game_account_stats_source_check", "game_account_stats_counts_check",
                "game_account_stats_wins_losses_together_check",
                "social_identities_pkey", "social_identities_user_id_provider_key",
                "social_identities_user_id_fkey", "social_identities_provider_check");
    }

    @Test
    @DisplayName("서버는 PUBG 의 STEAM · KAKAO 뿐이다 — 다른 게임의 서버도 PUBG 의 모르는 서버도 DB 가 거절한다")
    void serverCheck()
    {
        Long userId = insertUser();
        insertGameAccount(userId, "LOL");
        insertGameAccount(userId, "PUBG");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update game_accounts set server = 'STEAM' where user_id = ? and game = 'LOL'", userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_accounts_server_check");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update game_accounts set server = 'XBOX' where user_id = ? and game = 'PUBG'", userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_accounts_server_check");
        assertThat(jdbcTemplate.update(
                "update game_accounts set server = 'KAKAO' where user_id = ? and game = 'PUBG'", userId)).isEqualTo(1);
        // 새 칸의 기본값 — 인증되지 않았고 게임사 쪽 식별자가 없다
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from game_accounts where user_id = ? and verified = false and external_id is null",
                Integer.class, userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("전적은 게임 계정에 딸려 지워지고, 음수 · 모르는 출처는 DB 가 거절한다")
    void statsConstraintsAndCascade()
    {
        Long userId = insertUser();
        insertGameAccount(userId, "LOL");
        Long accountId = jdbcTemplate.queryForObject(
                "select id from game_accounts where user_id = ? and game = 'LOL'", Long.class, userId);

        assertThatThrownBy(() -> insertStats(accountId, 10, -1, 0, "API"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_counts_check");
        assertThatThrownBy(() -> insertStats(accountId, -1, 0, 0, "API"))
                .as("판 수도 음수일 수 없다")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_counts_check");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into game_account_stats (game_account_id, games, wins, losses, win_streak, source, synced_at) "
                        + "values (?, 10, 5, 5, -1, 'API', now())", accountId))
                .as("연승도 음수일 수 없다")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_counts_check");
        assertThatThrownBy(() -> insertStats(accountId, 2, 1, 1, "OPGG"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_source_check");
        insertStats(accountId, 2, 1, 1, "API");
        // 게임 계정 하나에 전적 한 줄이다
        assertThatThrownBy(() -> insertStats(accountId, 4, 2, 2, "API"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_pkey");

        jdbcTemplate.update("delete from game_accounts where id = ?", accountId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from game_account_stats where game_account_id = ?", Integer.class, accountId)).isZero();
    }

    @Test
    @DisplayName("판 수만 NOT NULL 이다 — PUBG 처럼 승/패 · 연승이 없는 줄이 들어가고, 승만 있고 패가 없으면 DB 가 거절한다")
    void statsNullableColumns()
    {
        Long userId = insertUser();
        insertGameAccount(userId, "PUBG");
        Long accountId = jdbcTemplate.queryForObject(
                "select id from game_accounts where user_id = ? and game = 'PUBG'", Long.class, userId);

        // 세 게임이 보여 주는 것이 다르다 — PUBG 는 "승"이 치킨(1위)이라 승/패가 없고 연승의 개념도 약하다
        assertThat(isNullable("game_account_stats", "games")).isEqualTo("NO");
        assertThat(isNullable("game_account_stats", "wins")).isEqualTo("YES");
        assertThat(isNullable("game_account_stats", "losses")).isEqualTo("YES");
        assertThat(isNullable("game_account_stats", "win_streak")).isEqualTo("YES");
        assertThat(dataTypeOf("game_account_stats", "games")).isEqualTo("integer");
        // 옛 기본값(win_streak DEFAULT 0)은 없어졌다 — 비어 있는 것과 0 은 다른 뜻이다
        assertThat(jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns where table_schema = 'public' "
                        + "and table_name = 'game_account_stats' and column_name = 'win_streak'", String.class)).isNull();

        jdbcTemplate.update("insert into game_account_stats "
                + "(game_account_id, games, avg_kills, avg_deaths, detail, source, synced_at) "
                + "values (?, 120, 4.2, 3.1, '{\"chickenRate\": 7}'::jsonb, 'API', now())", accountId);

        assertThat(jdbcTemplate.queryForObject("select games from game_account_stats where game_account_id = ?",
                Integer.class, accountId)).isEqualTo(120);
        assertThat(jdbcTemplate.queryForObject("select count(*) from game_account_stats where game_account_id = ? "
                        + "and wins is null and losses is null and win_streak is null",
                Integer.class, accountId)).isEqualTo(1);

        // 승만 있고 패가 없으면 승률을 계산할 수 없다 — 둘은 같이 있거나 같이 없다
        jdbcTemplate.update("delete from game_account_stats where game_account_id = ?", accountId);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into game_account_stats (game_account_id, games, wins, source, synced_at) "
                        + "values (?, 10, 5, 'API', now())", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_wins_losses_together_check");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into game_account_stats (game_account_id, games, losses, source, synced_at) "
                        + "values (?, 10, 5, 'API', now())", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_wins_losses_together_check");
        // games 는 비울 수 없다
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into game_account_stats (game_account_id, source, synced_at) values (?, 'API', now())",
                accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("소셜 계정 하나는 사용자 하나에만, 한 사용자는 제공자마다 하나만 이어진다. 사용자를 지우면 연결도 지워진다")
    void socialIdentityConstraintsAndCascade()
    {
        Long first = insertUser();
        Long second = insertUser();
        String providerUserId = "mig-" + UUID.randomUUID();
        insertSocialIdentity("KAKAO", providerUserId, first);

        assertThatThrownBy(() -> insertSocialIdentity("KAKAO", providerUserId, second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("social_identities_pkey");
        assertThatThrownBy(() -> insertSocialIdentity("KAKAO", providerUserId + "-2", first))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("social_identities_user_id_provider_key");
        // 제공자는 셋뿐이다 — 구글은 V3 로 더했다(2026-09-29)
        assertThatThrownBy(() -> insertSocialIdentity("NAVER", providerUserId, second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("social_identities_provider_check");
        // 같은 회원 번호라도 제공자가 다르면 다른 계정이다
        insertSocialIdentity("DISCORD", providerUserId, first);

        jdbcTemplate.update("delete from users where id = ?", first);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from social_identities where user_id = ?", Integer.class, first)).isZero();
    }

    @Test
    @DisplayName("V3 — 구글도 제공자이고(CHECK 이름은 그대로), 제공자 쪽 회원 번호는 255자까지 받는다(구글의 sub)")
    void googleProviderAndLongProviderUserId()
    {
        Long userId = insertUser();
        // 앞의 40자(mig- + UUID)가 겹치지 않게 한다 — 사용자를 지우면 연결도 지워진다
        String longest = ("mig-" + UUID.randomUUID() + "x".repeat(255)).substring(0, 255);
        insertSocialIdentity("GOOGLE", longest, userId);

        assertThat(jdbcTemplate.queryForObject(
                "select provider_user_id from social_identities where user_id = ? and provider = 'GOOGLE'", String.class, userId))
                .hasSize(255);
        assertThatThrownBy(() -> insertSocialIdentity("GOOGLE", longest + "y", insertUser()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("varying(255)");
        assertThat(jdbcTemplate.queryForObject("select character_maximum_length from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'social_identities' and column_name = 'provider_user_id'",
                Integer.class)).isEqualTo(255);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conname = 'social_identities_provider_check'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("V4 — 게임 계정에 주 포지션 칸이 없다(2026-09-29 소유자 결정 — P-35). 나머지 칸은 그대로다")
    void gameAccountHasNoMainPosition()
    {
        assertThat(jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'game_accounts' order by column_name",
                String.class)).containsExactly("created_at", "external_id", "game", "game_nickname", "id", "server",
                "tier", "updated_at", "user_id", "verified");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '4' and success", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("게임은 셋뿐이고, 한 사용자에 게임마다 한 줄이다. 사용자를 지우면 딸린 줄도 지워진다")
    void gameAccountConstraintsAndCascade()
    {
        Long userId = insertUser();
        insertGameAccount(userId, "LOL");

        assertThatThrownBy(() -> insertGameAccount(userId, "LOL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_accounts_user_id_game_key");
        assertThatThrownBy(() -> insertGameAccount(userId, "OVERWATCH"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_accounts_game_check");

        jdbcTemplate.update("delete from users where id = ?", userId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from game_accounts where user_id = ?", Integer.class, userId)).isZero();
    }

    private int count(String sql, Object... args)
    {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String dataTypeOf(String table, String column)
    {
        return jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, table, column);
    }

    private void insertStats(Long gameAccountId, int games, int wins, int losses, String source)
    {
        jdbcTemplate.update("insert into game_account_stats "
                        + "(game_account_id, games, wins, losses, win_streak, source, synced_at) "
                        + "values (?, ?, ?, ?, 0, ?, now())", gameAccountId, games, wins, losses, source);
    }

    private String isNullable(String table, String column)
    {
        return jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, table, column);
    }

    private void insertSocialIdentity(String provider, String providerUserId, Long userId)
    {
        jdbcTemplate.update("insert into social_identities (provider, provider_user_id, user_id, created_at) "
                + "values (?, ?, ?, now())", provider, providerUserId, userId);
    }

    private void insertGameAccount(Long userId, String game)
    {
        jdbcTemplate.update("insert into game_accounts (user_id, game, game_nickname, created_at, updated_at) "
                + "values (?, ?, 'x', now(), now())", userId, game);
    }
}
