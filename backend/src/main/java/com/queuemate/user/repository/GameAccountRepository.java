package com.queuemate.user.repository;

import com.queuemate.common.domain.GameKey;
import com.queuemate.user.domain.GameAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameAccountRepository extends JpaRepository<GameAccount, UUID> {

    List<GameAccount> findAllByUserId(UUID userId);

    Optional<GameAccount> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndProviderGameAndExternalGameId(
            UUID userId, GameKey providerGame, String externalGameId);
}
