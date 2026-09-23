package com.queuemate.platform.account.service;

import com.queuemate.platform.account.domain.User;
import com.queuemate.platform.account.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <b>{@code account} 밖에서 사용자를 읽는 창구.</b> 다른 도메인({@code social} · 다음 단계의 모집 글)은 {@code account} 의 테이블을
 * JOIN 하지 않고(크로스 스키마 JOIN 금지 — CLAUDE.md §3.5) 리포지토리도 직접 쓰지 않는다 — 여기와 {@link GameProfileReader} 만 부른다.
 * 나중에 {@code account} 가 다른 배포 단위로 떨어져 나가도 바뀌는 곳이 이 두 클래스다.
 *
 * <p>공개 사용자 탐색이 아니다 — 컨트롤러에 그대로 내놓지 마라(CLAUDE.md §1). 이미 아는 사용자 번호의 닉네임을 붙이는 데만 쓴다.
 * 로그인 아이디로 사람을 찾는 창구는 없다 — 로그인 아이디는 로그인에만 쓴다.
 */
@Component
@RequiredArgsConstructor
public class UserReader {

    private final UserRepository userRepository;

    /**
     * 사용자 번호 → 닉네임. <b>쿼리 한 번이다.</b> 없는 사용자는 결과에 없다 — "그런 사용자가 있는가"도 이것으로 본다.
     * <b>입력 검증용이다</b>(차단 대상이 있는 사람인가 등) — 불변식을 이것으로 지키지 마라(CLAUDE.md §5).
     */
    @Transactional(readOnly = true)
    public Map<Long, String> findNicknames(Collection<Long> userIds)
    {
        if(userIds == null || userIds.isEmpty())
        {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }
}
