package com.queuemate.matching.service;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.rule.CandidateRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

// [실험용] 페일오버 재시도 - 제거 시 이 import 도 삭제
import com.queuemate.matching.failover.FailoverRetryCoordinator;

/**
 * 요청을 받아 게임에 맞는 규칙으로 파티 배정을 넘긴다.
 *
 * 톰캣 스레드를 붙잡지 않도록 별도 풀에서 실행한다.
 * 컨트롤러는 이 메서드를 부르고 바로 응답을 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchTrigger {

    private final List<CandidateRule> candidateRules;

    // [실험용] 페일오버 재시도 - 제거 시 이 필드만 삭제
    // ObjectProvider 라서 빈이 없어도 주입이 성립한다. 기능 플래그가 꺼져 있으면
    // com.queuemate.matching.failover 의 빈이 하나도 안 만들어지고 여기는 비어 있다.
    private final ObjectProvider<FailoverRetryCoordinator> failoverRetryCoordinator;

    @Async("matchingExecutor")
    public void trigger(CreateMatchRequestCommand command) {
        // [실험용] 페일오버 재시도 - 제거 시 이 블록만 삭제 (시작)
        // 켜져 있으면 배정을 코디네이터가 감싼다. Redis 장애로 실패한 건은 인메모리 큐로
        // 가고, Sentinel 의 +switch-master 를 받은 뒤 다시 배정된다.
        // Redis 장애가 아닌 예외는 코디네이터가 그대로 다시 던지므로, 그 경로의 동작은
        // 기능을 켜도 아래 else 와 같다 (AsyncConfig 의 uncaught 핸들러가 로그를 남긴다).
        FailoverRetryCoordinator coordinator = failoverRetryCoordinator.getIfAvailable();
        if (coordinator != null) {
            coordinator.execute(command, this::assign);
            return;
        }
        // [실험용] 페일오버 재시도 - 제거 시 이 블록만 삭제 (끝)

        assign(command);
    }

    /**
     * 원래 {@code trigger()} 의 본문이었다. 재시도 경로가 같은 일을 다시 부를 수 있도록
     * 메서드로 뺐을 뿐 내용은 그대로다.
     *
     * <p>[실험용] 페일오버 재시도를 제거할 때는 이 본문을 {@code trigger()} 안으로
     * 되돌리면 원상복구된다.
     */
    private void assign(CreateMatchRequestCommand command) {
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "파티 배정 규칙이 없는 게임: " + command.getGame()))
                .canJoin(command);
    }
}
