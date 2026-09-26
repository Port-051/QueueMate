package com.queuemate.platform.party.service;

import com.queuemate.platform.party.domain.RecruitPost;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 방 키를 읽고 알게 된 것 가운데 <b>글에 옮겨 적어야 하는 것</b> — {@code PostService} 가 모으고 {@code PostStore#applyObservations} 가 적는다.
 * 셋이다: 모집 중인 글의 방이 <b>사라졌다</b>(만료), 모집 중인 글의 방이 <b>확정됐다</b>(자가 치유 — {@code PostStore#confirmRoom} 의 커밋이 실패했던 글),
 * 확정된 글의 방이 <b>사라졌다</b>(파티 닫힘 — 2026-09-26 소유자 결정. 전원이 말없이 사라져 키가 수명으로 없어진 경우다).
 *
 * <p><b>방 키를 읽지 못했을 때는 이것을 만들지 않는다</b> — 못 읽은 것을 "방이 없다"로 옮기면 멀쩡한 글이 전부 만료된다.
 */
final class RoomObservations {

    /** 확정 표시 키가 있는 글과, 그 순간의 멤버 SET */
    record Confirmed(RecruitPost post, Set<Long> members) {
    }

    private final Set<Long> vanished = new LinkedHashSet<>();
    private final List<Confirmed> confirmed = new ArrayList<>();
    private final Set<Long> partyGone = new LinkedHashSet<>();

    /** 방이 없다 — 글을 만료로 바꾼다. 글 쓰기가 방을 같이 만들므로(2026-09-25 2단계) 모집 중인 글에 방이 없으면 사라진 것이다 */
    void vanished(Long postId)
    {
        vanished.add(postId);
    }

    /** DB 에는 모집 중인데 확정 표시 키가 있다(자가 치유) — 확정을 기록한다 */
    void confirmed(RecruitPost post, Set<Long> members)
    {
        confirmed.add(new Confirmed(post, members));
    }

    /**
     * 확정된 글인데 방이 없다 — 방장 키 · 멤버 SET · 확정 표시 키가 <b>셋 다</b> 없다. 파티를 닫는다. 글의 상태는 바뀌지 않는다({@code CONFIRMED} 그대로)
     */
    void partyGone(Long postId)
    {
        partyGone.add(postId);
    }

    Set<Long> partyGone()
    {
        return partyGone;
    }

    Set<Long> vanished()
    {
        return vanished;
    }

    List<Confirmed> confirmed()
    {
        return confirmed;
    }

    boolean isEmpty()
    {
        return vanished.isEmpty() && confirmed.isEmpty() && partyGone.isEmpty();
    }

    /** 상태가 바뀌었을 수 있는 글 — 적은 뒤에 다시 읽어야 한다. 파티 닫힘은 글의 상태를 바꾸지 않아 들지 않는다 */
    Set<Long> statusTouched()
    {
        Set<Long> touched = new LinkedHashSet<>(vanished);
        confirmed.forEach(one -> touched.add(one.post().getId()));
        return touched;
    }
}
