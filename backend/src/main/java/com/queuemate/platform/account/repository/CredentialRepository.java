package com.queuemate.platform.account.repository;

import com.queuemate.platform.account.domain.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, Long> {
}
