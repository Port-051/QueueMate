package com.queuemate.matching.rule;

import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.dto.CreateMatchRequestCommand;

/**
 * 게임별 파티 배정 규칙.
 *
 * 파티 정보와 모드 설정은 Redis에서 키로 찾는다.
 * 판단 기준이 게임마다 다르다.
 *   LoL       — 모드 설정 positionUniqueness가 true면 이미 찬 포지션은 거부
 *   VALORANT  — 미구현
 *   PUBG      — 플랫폼(STEAM/KAKAO) 중복 허용(같은 플랫폼 여럿이 한 파티).
 *               단 플랫폼 자체는 hard 조건이라 플랫폼이 다르면 애초에 다른 색인이다
 *
 * 공통 추상 클래스는 없다. 게임마다 이 인터페이스를 직접 구현한다(LoL 은 LolCandidateRule).
 * 호출부(MatchTrigger · MatchCancelService)가 supports(GameKey) 로 구현체를 고른다.
 */
public interface CandidateRule {

    boolean supports(GameKey game);

    /** 들어갈 파티를 찾아 넣거나, 없으면 만들어서 넣는다. */
    void canJoin(CreateMatchRequestCommand command);

    /**
     * 파티에서 빼고 비워진 자리를 색인에 되돌린다.
     *
     * 되돌릴 자리를 고르는 규칙은 canJoin의 등록 규칙과 짝을 이룬다.
     * 중복을 금지하는 모드는 내가 비운 값만, 허용하는 모드는 정원이 찼다가
     * 풀린 경우 전부 되돌린다.
     *
     * @param active            Redis에 저장돼 있던 활성 요청
     * @param expectedRequestId 클라이언트가 아는 요청 id. 저장된 값과 같을 때만 지운다
     */
    CancelResult leave(ActiveRequest active, String expectedRequestId);
}
