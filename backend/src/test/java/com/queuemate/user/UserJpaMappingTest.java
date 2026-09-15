package com.queuemate.user;

import com.queuemate.common.domain.GameKey;
import com.queuemate.user.domain.GameAccount;
import com.queuemate.user.domain.User;
import com.queuemate.user.domain.UserStatus;
import com.queuemate.user.repository.GameAccountRepository;
import com.queuemate.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 엔티티 매핑이 V1 스키마와 어긋나지 않는지 검증한다.
 * ddl-auto=validate라 컬럼 이름/타입이 틀리면 컨텍스트 로딩부터 실패한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class UserJpaMappingTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate")
                    .withUsername("queuemate")
                    .withPassword("queuemate");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository users;

    @Autowired
    private GameAccountRepository gameAccounts;

    @Autowired
    private EntityManager em;

    @Test
    void savesAndReadsUserWithDbGeneratedTimestamps() {
        User saved = users.saveAndFlush(User.create("a@queuemate.test", "hash", "nick"));
        em.clear();

        User found = users.findByEmail("a@queuemate.test").orElseThrow();
        assertEquals(saved.getId(), found.getId());
        assertEquals(UserStatus.ACTIVE, found.getStatus());
        assertTrue(found.isActive());
        // created_at/updated_at은 DB default와 트리거가 채운다.
        assertTrue(found.getCreatedAt() != null && found.getUpdatedAt() != null);
    }

    @Test
    void rejectsDuplicateEmail() {
        users.saveAndFlush(User.create("dup@queuemate.test", "hash", "nickA"));

        // 운영 코드와 같은 경로로 저장해야 Spring이 DataIntegrityViolationException으로 변환한다.
        User duplicate = User.create("dup@queuemate.test", "hash", "nickB");
        assertThrows(DataIntegrityViolationException.class, () -> users.saveAndFlush(duplicate));
    }

    @Test
    void rejectsDuplicateNickname() {
        users.saveAndFlush(User.create("x@queuemate.test", "hash", "sameNick"));

        User duplicate = User.create("y@queuemate.test", "hash", "sameNick");
        assertThrows(DataIntegrityViolationException.class, () -> users.saveAndFlush(duplicate));
    }

    @Test
    void existsChecksMatchSavedRows() {
        users.saveAndFlush(User.create("e@queuemate.test", "hash", "nickE"));

        assertTrue(users.existsByEmail("e@queuemate.test"));
        assertTrue(users.existsByNickname("nickE"));
    }

    @Test
    void savesGameAccountAndBlocksDuplicateLink() {
        UUID userId = users.saveAndFlush(User.create("g@queuemate.test", "hash", "nickG")).getId();

        gameAccounts.saveAndFlush(GameAccount.create(userId, GameKey.LOL, "Hide on bush#KR1", "KR"));
        em.clear();

        assertEquals(1, gameAccounts.findAllByUserId(userId).size());
        assertTrue(gameAccounts.existsByUserIdAndProviderGameAndExternalGameId(
                userId, GameKey.LOL, "Hide on bush#KR1"));

        GameAccount duplicate = GameAccount.create(userId, GameKey.LOL, "Hide on bush#KR1", "KR");
        assertThrows(DataIntegrityViolationException.class, () -> gameAccounts.saveAndFlush(duplicate));
    }
}
