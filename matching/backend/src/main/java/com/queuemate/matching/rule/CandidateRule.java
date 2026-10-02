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
 *   VALORANT  — 역할군 중복 금지(늘 true). 경쟁전은 합류할 때마다 파티 티어 범위를
 *               "지금 범위 ∩ 들어온 사람 줄"로 좁힌다 (ValorantCandidateRule)
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
     * 중복을 금지하는 모드는 <b>남은 사람이 맡지 않은 값 전부</b>(파티 HASH 의 member: 필드로 구한다),
     * 허용하는 모드는 전부 되돌린다. "내가 비운 값만" 되돌리면 안 된다 — 정원이 차면 합류 스크립트가
     * 모든 줄에서 파티를 내리므로, 한 명이 빠진 뒤 그 값 한 줄만 올리면 아무도 안 맡았던 값의 줄은
     * 영영 돌아오지 않는다. 정원이 안 찼던 경우에는 같은 점수로 다시 넣어도 바뀌지 않아 무해하다.
     *
     * @param active            Redis에 저장돼 있던 활성 요청
     * @param expectedRequestId 클라이언트가 아는 요청 id. 저장된 값과 같을 때만 지운다
     */
    CancelResult leave(ActiveRequest active, String expectedRequestId);
}
