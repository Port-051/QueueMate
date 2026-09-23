package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2 · V3 마이그레이션 — 스키마와 테이블이 생겼는지, <b>DB 가 스스로</b> 불변식을 지키는지. 앱을 거치지 않고 SQL 로 직접 본다.
 * PostgreSQL 에서만 의미가 있다 — H2 는 {@code ~} 정규식 CHECK 를 재현하지 못한다.
 *
 * <p>사용자의 식별자가 둘이다 — <b>사용자 번호</b>({@code id}, bigint identity)와 <b>로그인 아이디</b>({@code login_id})다
 * (2026-09-22 소유자 결정). 번호는 DB 가 매기므로 INSERT 에 주지 않고 {@code RETURNING} 으로 받는다.
 */
class AccountMigrationTest extends ApiTestSupport {

    @Test
    @DisplayName("account 스키마에 테이블 다섯이 있다")
    void schemaAndTablesExist()
    {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'account' order by table_name",
                String.class);

        assertThat(tables).containsExactly("credentials", "game_account_stats", "game_accounts", "social_identities", "users");
    }

    @Test
    @DisplayName("사용자의 번호는 bigint 이고 로그인 아이디는 varchar(20) 이다 — 번호는 DB 가 매긴다")
    void userIdentifierColumns()
    {
        assertThat(dataTypeOf("users", "id")).isEqualTo("bigint");
        // identity 라 INSERT 에 주지 않는다 — DB 가 매긴다
        assertThat(jdbcTemplate.queryForObject(
                "select is_identity::text from information_schema.columns "
                        + "where table_schema = 'account' and table_name = 'users' and column_name = 'id'",
                String.class)).isEqualTo("YES");
        assertThat(dataTypeOf("users", "login_id")).isEqualTo("character varying");
        assertThat(jdbcTemplate.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_schema = 'account' and table_name = 'users' and column_name = 'login_id'",
                Integer.class)).isEqualTo(20);
        // 다른 테이블이 사용자 · 게임 계정을 가리키는 칸도 전부 bigint 다
        assertThat(dataTypeOf("credentials", "user_id")).isEqualTo("bigint");
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
                        + "where n.nspname = 'account' and c.contype in ('p', 'u', 'c', 'f')", String.class);

        assertThat(constraints).contains("users_pkey", "users_login_id_key", "users_nickname_key", "users_login_id_format",
                "credentials_pkey", "credentials_user_id_fkey",
                "game_accounts_pkey", "game_accounts_user_id_fkey", "game_accounts_user_id_game_key",
                "game_accounts_game_check",
                // V3
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
        String loginId = newLoginId();
        Long userId = insertUser(loginId, nicknameOf(loginId));
        insertGameAccount(userId, "LOL");
        insertGameAccount(userId, "PUBG");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update account.game_accounts set server = 'STEAM' where user_id = ? and game = 'LOL'", userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_accounts_server_check");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update account.game_accounts set server = 'XBOX' where user_id = ? and game = 'PUBG'", userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_accounts_server_check");
        assertThat(jdbcTemplate.update(
                "update account.game_accounts set server = 'KAKAO' where user_id = ? and game = 'PUBG'", userId)).isEqualTo(1);
        // 새 칸의 기본값 — 인증되지 않았고 게임사 쪽 식별자가 없다
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.game_accounts where user_id = ? and verified = false and external_id is null",
                Integer.class, userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("전적은 게임 계정에 딸려 지워지고, 음수 · 모르는 출처는 DB 가 거절한다")
    void statsConstraintsAndCascade()
    {
        String loginId = newLoginId();
        Long userId = insertUser(loginId, nicknameOf(loginId));
        insertGameAccount(userId, "LOL");
        Long accountId = jdbcTemplate.queryForObject(
                "select id from account.game_accounts where user_id = ? and game = 'LOL'", Long.class, userId);

        assertThatThrownBy(() -> insertStats(accountId, 10, -1, 0, "API"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_counts_check");
        assertThatThrownBy(() -> insertStats(accountId, -1, 0, 0, "API"))
                .as("판 수도 음수일 수 없다")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_counts_check");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into account.game_account_stats (game_account_id, games, wins, losses, win_streak, source, synced_at) "
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

        jdbcTemplate.update("delete from account.game_accounts where id = ?", accountId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.game_account_stats where game_account_id = ?", Integer.class, accountId)).isZero();
    }

    @Test
    @DisplayName("판 수만 NOT NULL 이다 — PUBG 처럼 승/패 · 연승이 없는 줄이 들어가고, 승만 있고 패가 없으면 DB 가 거절한다")
    void statsNullableColumns()
    {
        String loginId = newLoginId();
        Long userId = insertUser(loginId, nicknameOf(loginId));
        insertGameAccount(userId, "PUBG");
        Long accountId = jdbcTemplate.queryForObject(
                "select id from account.game_accounts where user_id = ? and game = 'PUBG'", Long.class, userId);

        // 세 게임이 보여 주는 것이 다르다 — PUBG 는 "승"이 치킨(1위)이라 승/패가 없고 연승의 개념도 약하다
        assertThat(isNullable("game_account_stats", "games")).isEqualTo("NO");
        assertThat(isNullable("game_account_stats", "wins")).isEqualTo("YES");
        assertThat(isNullable("game_account_stats", "losses")).isEqualTo("YES");
        assertThat(isNullable("game_account_stats", "win_streak")).isEqualTo("YES");
        assertThat(dataTypeOf("game_account_stats", "games")).isEqualTo("integer");
        // 옛 기본값(win_streak DEFAULT 0)은 없어졌다 — 비어 있는 것과 0 은 다른 뜻이다
        assertThat(jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns where table_schema = 'account' "
                        + "and table_name = 'game_account_stats' and column_name = 'win_streak'", String.class)).isNull();

        jdbcTemplate.update("insert into account.game_account_stats "
                + "(game_account_id, games, avg_kills, avg_deaths, detail, source, synced_at) "
                + "values (?, 120, 4.2, 3.1, '{\"chickenRate\": 7}'::jsonb, 'API', now())", accountId);

        assertThat(jdbcTemplate.queryForObject("select games from account.game_account_stats where game_account_id = ?",
                Integer.class, accountId)).isEqualTo(120);
        assertThat(jdbcTemplate.queryForObject("select count(*) from account.game_account_stats where game_account_id = ? "
                        + "and wins is null and losses is null and win_streak is null",
                Integer.class, accountId)).isEqualTo(1);

        // 승만 있고 패가 없으면 승률을 계산할 수 없다 — 둘은 같이 있거나 같이 없다
        jdbcTemplate.update("delete from account.game_account_stats where game_account_id = ?", accountId);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into account.game_account_stats (game_account_id, games, wins, source, synced_at) "
                        + "values (?, 10, 5, 'API', now())", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_wins_losses_together_check");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into account.game_account_stats (game_account_id, games, losses, source, synced_at) "
                        + "values (?, 10, 5, 'API', now())", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_account_stats_wins_losses_together_check");
        // games 는 비울 수 없다
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into account.game_account_stats (game_account_id, source, synced_at) values (?, 'API', now())",
                accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("소셜 계정 하나는 사용자 하나에만, 한 사용자는 제공자마다 하나만 이어진다. 사용자를 지우면 연결도 지워진다")
    void socialIdentityConstraintsAndCascade()
    {
        String firstLoginId = newLoginId();
        String secondLoginId = newLoginId();
        Long first = insertUser(firstLoginId, nicknameOf(firstLoginId));
        Long second = insertUser(secondLoginId, nicknameOf(secondLoginId));
        String providerUserId = "mig-" + firstLoginId;
        insertSocialIdentity("KAKAO", providerUserId, first);

        assertThatThrownBy(() -> insertSocialIdentity("KAKAO", providerUserId, second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("social_identities_pkey");
        assertThatThrownBy(() -> insertSocialIdentity("KAKAO", providerUserId + "-2", first))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("social_identities_user_id_provider_key");
        assertThatThrownBy(() -> insertSocialIdentity("GOOGLE", providerUserId, second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("social_identities_provider_check");
        // 같은 회원 번호라도 제공자가 다르면 다른 계정이다
        insertSocialIdentity("DISCORD", providerUserId, first);

        jdbcTemplate.update("delete from account.users where id = ?", first);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.social_identities where user_id = ?", Integer.class, first)).isZero();
    }

    @Test
    @DisplayName("DB 의 CHECK 가 형식이 아닌 로그인 아이디의 INSERT 를 거절한다 — 앱의 검증을 거치지 않아도 막힌다")
    void idFormatCheck()
    {
        for(String badId : new String[]{"UPPER_CASE", "abc", "has:colon", "has/slash", "a".repeat(21)})
        {
            assertThatThrownBy(() -> insertUser(badId, "n_" + Math.abs(badId.hashCode())))
                    .as(badId)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("게임은 셋뿐이고, 한 사용자에 게임마다 한 줄이다. 사용자를 지우면 딸린 줄도 지워진다")
    void gameAccountConstraintsAndCascade()
    {
        String loginId = newLoginId();
        Long userId = insertUser(loginId, nicknameOf(loginId));
        jdbcTemplate.update("insert into account.credentials (user_id, password_hash, updated_at) values (?, 'x', now())", userId);
        insertGameAccount(userId, "LOL");

        assertThatThrownBy(() -> insertGameAccount(userId, "LOL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_accounts_user_id_game_key");
        assertThatThrownBy(() -> insertGameAccount(userId, "OVERWATCH"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_accounts_game_check");

        jdbcTemplate.update("delete from account.users where id = ?", userId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.credentials where user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.game_accounts where user_id = ?", Integer.class, userId)).isZero();
    }

    private String dataTypeOf(String table, String column)
    {
        return jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'account' and table_name = ? and column_name = ?",
                String.class, table, column);
    }

    /** 사용자를 직접 넣고 <b>DB 가 매긴 번호</b>를 받는다 — {@code id} 는 identity 라 INSERT 에 주지 않는다 */
    private Long insertUser(String loginId, String nickname)
    {
        return jdbcTemplate.queryForObject(
                "insert into account.users (login_id, nickname, created_at, updated_at) "
                        + "values (?, ?, now(), now()) returning id",
                Long.class, loginId, nickname);
    }

    private void insertStats(Long gameAccountId, int games, int wins, int losses, String source)
    {
        jdbcTemplate.update("insert into account.game_account_stats "
                        + "(game_account_id, games, wins, losses, win_streak, source, synced_at) "
                        + "values (?, ?, ?, ?, 0, ?, now())", gameAccountId, games, wins, losses, source);
    }

    private String isNullable(String table, String column)
    {
        return jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_schema = 'account' and table_name = ? and column_name = ?",
                String.class, table, column);
    }

    private void insertSocialIdentity(String provider, String providerUserId, Long userId)
    {
        jdbcTemplate.update("insert into account.social_identities (provider, provider_user_id, user_id, created_at) "
                + "values (?, ?, ?, now())", provider, providerUserId, userId);
    }

    private void insertGameAccount(Long userId, String game)
    {
        jdbcTemplate.update("insert into account.game_accounts (user_id, game, game_nickname, created_at, updated_at) "
                + "values (?, ?, 'x', now(), now())", userId, game);
    }
}
