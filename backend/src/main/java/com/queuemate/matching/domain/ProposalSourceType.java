package com.queuemate.matching.domain;

/** 제안이 어디서 나왔는지. 실시간과 예약은 같은 수락 모델을 공유한다 (docs/04 §8). */
public enum ProposalSourceType {

    REALTIME,
    RESERVATION
}
