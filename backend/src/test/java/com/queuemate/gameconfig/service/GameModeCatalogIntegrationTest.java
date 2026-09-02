package com.queuemate.gameconfig.service;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.error.ServiceUnavailableException;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.infra.SeedGameModeConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 카탈로그가 실제 DB를 읽는지 검증한다.
 *
 * <p>상수로 기대값을 들고 비교하면 migration이 비어 있어도 통과한다. 그래서 시드가
 * 실제로 들어갔는지를 Flyway가 적용된 PostgreSQL에서 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameModeCatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate")
                    .withUsername("queuemate")
                    .withPassword("queuemate");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired GameModeCatalog catalog;
    @Autowired JdbcClient jdbc;

    @AfterEach
    void restoreSeed() {
        jdbc.sql("UPDATE game_mode_configs SET active = TRUE").update();
        catalog.reload();
    }

    @Test
    @DisplayName("세 게임 모두 활성 모드가 하나 이상 있다")
    void everyGameHasAtLeastOneActiveMode() {
        for (GameKey game : GameKey.values()) {
            assertThat(catalog.activeModes(game))
                    .as("%s의 활성 모드", game)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("모든 모드의 정원이 2 이상이다")
    void everyModeHasPartySizeOfAtLeastTwo() {
        for (GameKey game : GameKey.values()) {
            assertThat(catalog.activeModes(game))
                    .allSatisfy(config -> assertThat(config.targetPartySize()).isGreaterThanOrEqualTo(2));
        }
    }

    @Test
    @DisplayName("정원과 역할 중복 규칙이 docs/02의 값과 같다")
    void seedMatchesDocumentedValues() {
        assertThat(catalog.findActive(GameKey.LOL, "SOLO_DUO_RANKED"))
                .get()
                .satisfies(config -> {
                    assertThat(config.targetPartySize()).isEqualTo(2);
                    assertThat(config.roleUniqueness()).isTrue();
                });
        assertThat(catalog.findActive(GameKey.VALORANT, "COMPETITIVE"))
                .get()
                .satisfies(config -> {
                    assertThat(config.targetPartySize()).isEqualTo(5);
                    assertThat(config.roleUniqueness()).isFalse();
                });
        assertThat(catalog.findActive(GameKey.PUBG, "SQUAD"))
                .get()
                .satisfies(config -> assertThat(config.targetPartySize()).isEqualTo(4));
    }

    @Test
    @DisplayName("DB 시드와 단위 테스트 fixture가 어긋나지 않는다")
    void seedDoesNotDriftFromUnitTestFixture() {
        SeedGameModeConfigProvider fixture = new SeedGameModeConfigProvider();
        for (GameKey game : GameKey.values()) {
            assertThat(catalog.activeModes(game))
                    .as("%s 모드 목록", game)
                    .containsExactlyInAnyOrderElementsOf(fixture.activeModes(game));
        }
    }

    @Test
    @DisplayName("다른 게임의 모드 키를 붙이면 거절한다")
    void rejectsModeKeyFromAnotherGame() {
        assertThat(catalog.findActive(GameKey.LOL, "SQUAD")).isEmpty();
        assertThat(catalog.findActive(GameKey.PUBG, "SOLO_DUO_RANKED")).isEmpty();
    }

    @Test
    @DisplayName("모드를 내리면 그 모드로는 매칭을 시작할 수 없다")
    void deactivatedModeDisappearsAfterReload() {
        jdbc.sql("UPDATE game_mode_configs SET active = FALSE WHERE mode_key = 'SQUAD'").update();
        catalog.reload();

        assertThat(catalog.findActive(GameKey.PUBG, "SQUAD")).isEmpty();
        assertThat(catalog.activeModes(GameKey.PUBG))
                .extracting(GameModeConfig::modeKey)
                .containsExactly("DUO");
    }

    @Test
    @DisplayName("활성 모드가 하나도 없으면 어떤 요청도 통과하지 않는다")
    void emptyTableRejectsEverything() {
        jdbc.sql("UPDATE game_mode_configs SET active = FALSE").update();
        catalog.reload();

        for (GameKey game : GameKey.values()) {
            assertThat(catalog.activeModes(game)).isEmpty();
        }
        assertThat(catalog.findActive(GameKey.LOL, "SOLO_DUO_RANKED")).isEmpty();
    }

    @Test
    @DisplayName("한 번도 읽지 못했으면 비어 있는 것이 아니라 503이다")
    void neverLoadedIsUnavailableNotEmpty() {
        // 적재를 한 번도 돌리지 않은 카탈로그다. 의존성에 손대지 않으므로 null로 충분하다.
        GameModeCatalog neverLoaded = new GameModeCatalog(null, null);

        assertThatThrownBy(() -> neverLoaded.activeModes(GameKey.LOL))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("매칭을 받을 수 없다");
    }
}
