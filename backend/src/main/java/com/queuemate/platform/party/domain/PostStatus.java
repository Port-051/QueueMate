package com.queuemate.platform.party.domain;

/**
 * 모집 글의 상태. DB 의 CHECK({@code recruit_posts_status_check})도 같은 세 이름을 건다.
 *
 * <p><b>{@code RECRUITING} 에서만 나갈 수 있고 되돌아오지 않는다</b> — 확정은 되돌릴 수 없고(docs/11 D-21) 만료된 글을 다시 열지 않는다.
 * 그래서 상태를 바꾸는 UPDATE 는 전부 {@code … WHERE status = 'RECRUITING'} 이다({@code RecruitPostRepository}).
 */
public enum PostStatus {

    /** 모집 중. 입장권은 이 상태의 글에만 내준다 */
    RECRUITING,

    /** 방장이 확정했다 — {@code room} 의 확정 표시 키를 이 앱이 읽어 기록한 것이다. 끝까지 이 상태다(D-23 — 방장 키가 없어도 만료시키지 않는다) */
    CONFIRMED,

    /** 방장이 지웠거나 방이 사라졌다. 글은 지우지 않고 이 상태로 남긴다(CLAUDE.md §7.1) */
    EXPIRED
}
