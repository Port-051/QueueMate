package com.queuemate.platform.party.service;

import com.queuemate.platform.party.domain.RecruitPost;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 방 키를 읽고 알게 된 것 가운데 <b>글에 옮겨 적어야 하는 것</b> — {@code PostService} 가 모으고 {@code PostStore#applyObservations} 가 적는다.
 * 규칙은 {@code contracts/platform-api.md} 의 {@code room_seen_at} 대목과 "방장 확정의 기록" 길 ② 다.
 *
 * <p><b>방 키를 읽지 못했을 때는 이것을 만들지 않는다</b> — 못 읽은 것을 "방이 없다"로 옮기면 멀쩡한 글이 전부 만료된다.
 */
final class RoomObservations {

    /** 확정 표시 키가 있는 글과, 그 순간의 멤버 SET */
    record Confirmed(RecruitPost post, Set<Long> members) {
    }

    private final Set<Long> firstSeen = new LinkedHashSet<>();
    private final Set<Long> vanished = new LinkedHashSet<>();
    private final List<Confirmed> confirmed = new ArrayList<>();

    /** 방장 키를 처음 봤다 — {@code room_seen_at} 을 적는다 */
    void firstSeen(Long postId)
    {
        firstSeen.add(postId);
    }

    /** 방이 없다 — 봤던 방이 사라졌거나, 쓴 지 오래도록 방이 안 생겼다. 글을 만료로 바꾼다 */
    void vanished(Long postId)
    {
        vanished.add(postId);
    }

    /** DB 에는 모집 중인데 확정 표시 키가 있다(길 ②) — 확정을 기록한다 */
    void confirmed(RecruitPost post, Set<Long> members)
    {
        confirmed.add(new Confirmed(post, members));
    }

    Set<Long> firstSeen()
    {
        return firstSeen;
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
        return firstSeen.isEmpty() && vanished.isEmpty() && confirmed.isEmpty();
    }

    /** 상태가 바뀌었을 수 있는 글 — 적은 뒤에 다시 읽어야 한다. {@code room_seen_at} 만 적은 글은 보이는 것이 같아 넣지 않는다 */
    Set<Long> statusTouched()
    {
        Set<Long> touched = new LinkedHashSet<>(vanished);
        confirmed.forEach(one -> touched.add(one.post().getId()));
        return touched;
    }
}
