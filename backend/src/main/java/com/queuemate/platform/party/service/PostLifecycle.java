package com.queuemate.platform.party.service;

import com.queuemate.platform.party.board.BoardSignalPublisher;
import com.queuemate.platform.party.repository.PartyRecordRepository;
import com.queuemate.platform.party.repository.RecruitPostRepository;
import com.queuemate.platform.social.service.RecentPlayerRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * <b>방이 끝났을 때의 글 쪽 정리</b> — {@code room} 의 나가기 · 접속 확인({@code RoomMemberService})이 방이 없어진 뒤에 부르는 창구이고,
 * 목록 · 단건의 옮겨 적기({@link PostStore#applyObservations})도 파티 닫기({@link #closeParty})를 여기서 빌려 쓴다.
 * <ul>
 *   <li><b>확정 전의 방</b>이 없어지면 글이 만료된다(2026-09-25 소유자 결정 — "확정 전에는 방과 글이 같이 끝난다")</li>
 *   <li><b>확정한 방</b>이 없어지면 <b>파티가 닫힌다</b>(2026-09-26 소유자 결정) — {@code parties.status = 'CLOSED'} · {@code closed_at} 을 적고
 *       그 순간 파티원끼리 서로를 최근 함께한 사람에 적는다({@link RecentPlayerRecorder}). 글은 {@code CONFIRMED} 그대로다</li>
 * </ul>
 *
 * <p><b>어느 쪽인지는 DB 가 가른다</b> — 글이 모집 중이면(확정 전) 만료의 조건부 UPDATE 가, 파티가 열려 있으면(확정 뒤) 닫기의 조건부 UPDATE 가 1줄을 받는다.
 * 한 글이 두 상태일 수는 없어서 둘을 차례로 해 보면 맞는 쪽 하나만 걸린다. 방의 스크립트에게 "확정한 방이었나" 를 묻지 않는다 — 스크립트의 반환값으로는
 * 방장 혼자 있던 확정 전의 방과 넘길 사람이 없어 닫힌 확정한 방이 똑같이 {@code {2, 방장}} 이다.
 *
 * <p>목록 · 단건의 옮겨 적기는 그대로 남는다 — 수명이 다해 저절로 사라진 방(전원이 말없이 사라졌다)에는 돌아가는 코드가 없어서다.
 *
 * <p><b>{@code PostService} · {@link PostStore} 를 물지 않는 따로 선 빈이다</b> — {@link PostStore} 가 {@code RoomService} 를 물고
 * {@code RoomMemberService} 가 이것을 문다. {@link PostEntryGate} 와 같은 까닭이고, 이쪽은 {@code RoomService} 조차 필요 없어 리포지토리 · 신호 ·
 * {@code social} 의 창구만 문다. 그래서 {@link PostStore} 가 이것을 물어도 고리가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostLifecycle {

    private final RecruitPostRepository postRepository;
    private final PartyRecordRepository partyRecordRepository;
    private final RecentPlayerRecorder recentPlayerRecorder;
    private final BoardSignalPublisher boardSignal;

    /**
     * 방이 없어진 글을 정리한다 — 모집 중이면 만료로 바꾸고, 확정된 글의 파티가 열려 있으면 닫는다. 둘 다 조건부 UPDATE 라 이미 정리됐으면 아무것도 하지 않는다.
     *
     * <p><b>부르기 전에 방이 정말 없어진 것을 알았어야 한다</b> — 나가기 스크립트가 방을 지웠거나({@code ROOM_CLOSED}), 접속 확인 스크립트가 방장 키도
     * 확정 표시 키도 없는 것을 봤다({@code ROOM_CLOSED}). 확정한 방은 방장이 나가도 승계라 나가기의 결과가 {@code LEFT} 이고 여기 오지 않는다(D-23).
     *
     * @param roomId 방 번호 = 글 번호. 그런 글이 없으면 둘 다 0줄이다
     * @return 이 호출이 <b>글을 만료시켜</b> 게시판 신호를 예약했으면 {@code true}(커밋 뒤에 나간다). 부르는 쪽은 방의 신호를 따로 내지 않는다.
     *         파티를 닫은 것은 신호를 내지 않는다 — 글 한 줄에 달라지는 것이 없다(글은 {@code CONFIRMED} 그대로이고 확정된 글은 멤버를 비워 내려 준다)
     */
    @Transactional
    public boolean endByRoomClosed(Long roomId)
    {
        // PostService#now() 와 같은 정밀도(밀리초)로 적는다
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if(postRepository.expireIfRecruiting(roomId, now) == 1)
        {
            boardSignal.changed();
            log.info("모집 글 만료 postId={} reason=방장이 나가 방이 닫혔다", roomId);
            return true;
        }
        closeParty(roomId, now);
        return false;
    }

    /**
     * <b>파티 닫기</b>(2026-09-26 소유자 결정 — 확정된 방이 없어질 때 파티가 닫힌다). 길이 둘이다 — ① 마지막 사람의 나가기 · 접속 확인이 방이 없어진 것을
     * 그 자리에서 안다({@link #endByRoomClosed}) ② 전원이 말없이 사라져 키가 수명으로 없어졌으면 목록 · 단건이 방 키를 읽다 발견한다({@link PostStore#applyObservations}).
     *
     * <p><b>멱등이다</b> — 파티가 {@code ACTIVE} 일 때만 닫는 조건부 UPDATE 가 1줄을 받은 호출 하나만 최근 함께한 사람을 적는다. 두 길이 동시에 와도
     * 다른 하나는 그 줄 잠금에서 기다렸다가 0줄을 받는다(CLAUDE.md §5 "불변식은 DB 가 강제한다"). 그 글의 파티가 없으면(확정 전) 0줄이다.
     *
     * <p>적는 사람은 <b>확정 순간의 파티원</b>({@code party_members})이다 — 방에 지금 누가 남아 있었는지가 아니다. 방장 확정의 기록과 같은 트랜잭션이 아니어도
     * 된다 — 파티원은 확정할 때 이미 적혔다.
     *
     * @return 이 호출이 닫았으면 {@code true}
     */
    @Transactional
    public boolean closeParty(Long postId, Instant now)
    {
        if(partyRecordRepository.closeIfActive(postId, now) == 0)
        {
            return false;
        }
        Long partyId = partyRecordRepository.findPartyIdByPostId(postId).orElseThrow(
                () -> new IllegalStateException("방금 닫은 파티가 없다 postId=" + postId));
        int rows = recentPlayerRecorder.recordParty(partyId, now);
        log.info("파티 닫힘 postId={} partyId={} recentPlayerRows={}", postId, partyId, rows);
        return true;
    }
}
