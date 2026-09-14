package com.queuemate.common.party;
import java.util.UUID;
/** 실제 게임 시작이 아닌, 정원 전원의 준비 완료 버튼 확인이다. */
public record PartyReadyReached(UUID partyId) {}
