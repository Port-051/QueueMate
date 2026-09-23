package com.queuemate.platform.account.repository;

import com.queuemate.platform.account.domain.SocialIdentity;
import com.queuemate.platform.account.domain.SocialIdentityId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * <b>{@code existsById} 로 "이미 연결된 소셜 계정인지" 먼저 확인하고 넣지 마라</b> — 같은 소셜 계정의 두 번째 연결은
 * {@code saveAndFlush} 의 PK 위반으로 안다 ({@code SocialLoginService#signup}). 콜백에서 {@code findById} 로 읽는 것은
 * "로그인인가 가입인가"를 가르는 조회일 뿐이다.
 */
public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, SocialIdentityId> {

    List<SocialIdentity> findByUserId(Long userId);
}
