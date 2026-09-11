package com.queuemate.auth.repository;

import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.auth.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    List<UserIdentity> findByUserId(UUID userId);
}
