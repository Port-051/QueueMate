package com.queuemate.platform.account.repository;

import com.queuemate.platform.account.domain.SocialIdentity;
import com.queuemate.platform.account.domain.SocialIdentityId;
import com.queuemate.platform.account.domain.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * <b>{@code existsById} 로 "이미 연결된 소셜 계정인지" 먼저 확인하고 넣지 마라</b> — 같은 소셜 계정의 두 번째 연결은
 * {@code saveAndFlush} 의 PK 위반으로 안다 ({@code SocialLoginService#signup}). 콜백에서 {@code findById} 로 읽는 것은
 * "로그인인가 가입인가"를 가르는 조회일 뿐이다.
 */
public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, SocialIdentityId> {

    List<SocialIdentity> findByUserId(Long userId);

    Optional<SocialIdentity> findByUserIdAndIdProvider(Long userId, SocialProvider provider);

    long countByUserId(Long userId);

    /** 끊기 — "마지막 하나인가"는 부르는 쪽이 사용자 줄을 잠근 뒤 본다({@code SocialLoginService#unlink}) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from social_identities where user_id = :userId and provider = :provider", nativeQuery = true)
    int deleteByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);
}
