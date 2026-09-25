package com.queuemate.platform.party.service;

import com.queuemate.platform.party.board.BoardSignalPublisher;
import com.queuemate.platform.party.repository.RecruitPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * <b>방이 끝났을 때의 글 쪽 정리</b> — {@code room} 의 나가기({@code RoomMemberService#leave})가 방장이 나가 방이 닫힌 뒤에 부르는 창구다
 * (2026-09-25 소유자 결정 — "확정 전에는 방과 글이 같이 끝난다". 창구의 이름과 자리는 Claude 가 정했다).
 *
 * <p>전에는 방이 닫혀도 글을 건드리지 않고 다음 목록 · 단건이 "방장 키 없음 → 만료" 로 옮겨 적기를 기다렸다. 그 사이에 방장이 새 글을 쓰면
 * 409 {@code ALREADY_RECRUITING} 이었다 — 방은 없는데 글이 모집 중으로 남아 "모집 중인 글은 한 사람에 하나" 에 걸렸다. 이제 같은 요청에서 만료시킨다.
 * 목록 · 단건의 옮겨 적기는 그대로 남는다 — 수명이 다해 저절로 사라진 방(방장이 말없이 사라졌다)에는 돌아가는 코드가 없어서다.
 *
 * <p><b>{@code PostService} · {@link PostStore} 를 물지 않는 따로 선 빈이다</b> — {@link PostStore} 가 {@code RoomService} 를 물고
 * {@code RoomMemberService} 가 이것을 문다. {@link PostEntryGate} 와 같은 까닭이고, 이쪽은 {@code RoomService} 조차 필요 없어 리포지토리와 신호만 문다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostLifecycle {

    private final RecruitPostRepository postRepository;
    private final BoardSignalPublisher boardSignal;

    /**
     * 방이 닫힌 글을 만료로 바꾼다 — <b>모집 중일 때만</b>(조건부 UPDATE). 이미 만료 · 확정이면 0줄이라 아무것도 하지 않는다 —
     * 확정한 방은 방장이 나가도 승계라 여기 오지 않고, 넘길 사람이 없어 닫혀도 글은 {@code CONFIRMED} 그대로다(D-23).
     *
     * @param roomId 방 번호 = 글 번호. 그런 글이 없으면 0줄이다
     * @return 이 호출이 만료시켰으면 {@code true} — 그때만 게시판 신호를 예약했다(커밋 뒤에 나간다). 부르는 쪽은 방의 신호를 따로 내지 않는다
     */
    @Transactional
    public boolean expireByRoomClosed(Long roomId)
    {
        // PostService#now() 와 같은 정밀도(밀리초)로 적는다
        if(postRepository.expireIfRecruiting(roomId, Instant.now().truncatedTo(ChronoUnit.MILLIS)) == 1)
        {
            boardSignal.changed();
            log.info("모집 글 만료 postId={} reason=방장이 나가 방이 닫혔다", roomId);
            return true;
        }
        return false;
    }
}
