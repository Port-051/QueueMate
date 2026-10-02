package com.queuemate.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱이 뜨는지 본다 — 뜨려면 PostgreSQL 에 붙어 Flyway 가 돌아야 한다.
 *
 * <p><b>돌리는 법.</b> 테스트용 PostgreSQL(5433)과 Redis(6380)를 먼저 띄운다 — 명령은 폴더 루트의 {@code START_HERE.md} §6.
 * 안 띄우고 돌리면 <b>건너뛰지 않고 실패한다</b>(접속 거부). 건너뛰는 경우는 아래 하나뿐이다.
 *
 * <p><b>5432 · 6379 에는 붙지 않는다.</b> 그 둘은 이 컴퓨터의 다른 프로젝트({@code queuemate-v2-*}) 것이다(CLAUDE.md §9).
 * {@code DB_PORT=5432} 이거나 {@code REDIS_PORT=6379} 면 클래스를 통째로 건너뛴다 — 컨텍스트를 띄우는 것부터가
 * 그 DB 에 Flyway 를 돌리는 일이라, 테스트 메서드 안의 {@code assumeTrue} 로는 늦다. <b>건너뛴 것을 통과로 읽지 마라.</b>
 */
@SpringBootTest
@DisabledIf(value = "pointsAtForeignPorts",
		disabledReason = "DB_PORT=5432 또는 REDIS_PORT=6379 다 — 다른 프로젝트의 것이다. 테스트용을 5433 · 6380 으로 띄워라")
class PlatformApplicationTests {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	StringRedisTemplate redisTemplate;

	/** application.yaml 의 기본값과 같아야 한다 — DB_PORT 5433, REDIS_PORT 6380. */
	static boolean pointsAtForeignPorts() {
		return "5432".equals(envOrDefault("DB_PORT", "5433"))
				|| "6379".equals(envOrDefault("REDIS_PORT", "6380"));
	}

	private static String envOrDefault(String name, String defaultValue) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? defaultValue : value.trim();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void flywayRanAgainstPostgres() {
		Integer applied = jdbcTemplate.queryForObject(
				"select count(*) from flyway_schema_history where success", Integer.class);

		assertThat(applied).isGreaterThanOrEqualTo(1);
	}

	@Test
	void redisAnswers() {
		// Redis 연결은 처음 쓸 때 맺는다 — 컨텍스트가 뜬 것만으로는 Redis 에 붙었는지 알 수 없다
		String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());

		assertThat(pong).isEqualTo("PONG");
	}

}
