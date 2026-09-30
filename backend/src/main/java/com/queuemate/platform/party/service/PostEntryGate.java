package com.queuemate.platform.party.service;

import com.queuemate.platform.party.domain.PostStatus;
import com.queuemate.platform.party.domain.RecruitPost;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.RoomState;
import com.queuemate.platform.room.domain.RoomStateUnavailableException;
import com.queuemate.platform.room.service.RoomService;
import com.queuemate.platform.social.service.BlockReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>입장의 게시판 쪽 검사</b> — {@code room} 의 입장({@code RoomMemberService#enter})이 스크립트를 부르기 <b>전에</b> 부르는 창구다
 * (2026-09-25 2단계 — 입장권을 없앴다. 소유자 결정 ①. 창구의 이름과 자리는 Claude 가 정했다).
 *
 * <p>두 앱이던 때 입장권 발급({@code POST /api/v1/posts/{postId}/ticket})이 서명하기 전에 하던 검사 그대로다 — 글을 읽고,
 * <b>차단으로 숨겨진 글이면 404 {@code POST_NOT_FOUND}</b>(없는 글과 똑같이. <b>상태보다 먼저</b> 본다 — 차단 관계인 사람에게는 "모집이 끝났다"도 알려 주지 않는다),
 * <b>모집 중이 아니면 409 {@code POST_NOT_RECRUITING}</b>. 차단은 <b>방장 + 그 순간 방 안 전원</b>과 본다(D-20 — 목록에서만 숨기고 입장은 되면 의미가 없다).
 * <b>확정된 글은 확정 순간의 파티원 전원도 같이 본다</b>(2026-09-30 — P-40. 목록 · 단건이 그들을 카드로 보여 주고 그들과 차단을 보므로 같은 범위다 — Claude 가 정한 세부).
 *
 * <p><b>{@code PostService} 를 물지 않는 따로 선 빈이다</b> — {@code PostService} 가 {@code RoomService} 를 부르고 {@code RoomMemberService} 가 이것을 부른다.
 * 이것이 {@code PostService} 를 물면 생성자 주입끼리 고리가 생길 수 있어 필요한 것({@link PostStore} · {@link RoomService} · {@link BlockReader})만 문다.
 *
 * <p><b>글에 옮겨 적지 않는다</b> — 입장권 발급은 방 키를 읽다 "방이 사라졌다 · 확정됐다"를 보면 글을 만료 · 확정으로 옮겨 적었다. 여기서는 하지 않는다:
 * 방장 키가 없으면 곧이어 스크립트가 404 {@code ROOM_NOT_FOUND} 로, 확정 표시 키가 있으면 409 {@code ROOM_CONFIRMED} 로 거절한다 — 거절의 결과가 같고,
 * 옮겨 적기는 목록 · 단건이 한다({@code PostService}). 입장이 DB 에 쓰는 일이 없어 입장은 트랜잭션 없이 돈다.
 */
@Component
@RequiredArgsConstructor
public class PostEntryGate {

    private final PostStore postStore;
    private final RoomService roomService;
    private final BlockReader blockReader;

    /**
     * 통과하면 <b>그 방의 정원</b>을 돌려준다(2026-09-30 — P-41. 글에 적힌 모드의 인원 · 옛 글은 5) — 입장 스크립트가 그 값으로 만석을 가른다.
     * 글을 이미 여기서 읽으므로 정원을 따로 읽지 않는다. 거절은 {@link com.queuemate.platform.common.error.ApiException} 이다.
     *
     * <p><b>이미 그 방에 들어와 있는 사람은 통과시킨다</b>(Claude 가 정한 세부) — 스크립트가 "이미 들어와 있다"(200)로 답하게 둔다. 확정된 방의 파티원이
     * 새로고침하면 글은 {@code CONFIRMED} 라 409 가 되고, 방 안에서 나중에 차단이 생긴 두 사람은 404 가 된다 — 새 사람을 막으려는 검사가
     * 이미 안에 있는 사람의 재시도를 거절할 이유가 없다(두 앱이던 때도 입장권은 들어오기 전에 한 번 받는 것이었다).
     *
     * @param roomId 경로의 {@code roomId} 글자 그대로 — 글의 번호다. 숫자가 아니면 그런 글이 없다(404)
     * @throws com.queuemate.platform.common.error.ApiException 404 {@code POST_NOT_FOUND} · 409 {@code POST_NOT_RECRUITING} ·
     *                                                          503 {@code ROOM_STATE_UNAVAILABLE}(방 안을 못 읽었다 — 차단 대조를 못 했는데 들여보낼 수 없다)
     */
    public int check(String roomId, Long me)
    {
        Long postId = postIdOf(roomId);
        RecruitPost post = postStore.find(postId).orElseThrow(PostStore::postNotFound);
        RoomState state;
        try
        {
            state = roomService.states(List.of(postId)).get(postId);
        }
        catch(RoomStateUnavailableException e)
        {
            throw RoomErrors.stateUnavailable();
        }
        if(state.members().contains(me))
        {
            return post.getCapacity();
        }
        Set<Long> shown = new HashSet<>(state.members());
        if(post.getStatus() == PostStatus.CONFIRMED)
        {
            // 확정된 글은 목록 · 단건이 확정 순간의 파티원 전원을 카드로 보여 주고 그들과 차단을 본다(2026-09-30 — P-40). 단건이 404 인 글에
            // 입장이 409 로 "모집이 끝났다" 를 알려 주지 않게 같은 범위로 본다 — 확정된 글에 들어오려는 드문 요청에만 쿼리 하나가 더 나간다
            BoardParty party = postStore.findBoardParties(List.of(postId)).get(postId);
            if(party != null)
            {
                party.seats().forEach(seat -> shown.add(seat.userId()));
            }
        }
        Set<Long> others = new HashSet<>(shown);
        others.add(post.getHostId());
        others.remove(me);
        if(PostService.isHidden(me, post, shown, blockReader.findBlockedEitherWay(me, others)))
        {
            throw PostStore.postNotFound();
        }
        if(post.getStatus() != PostStatus.RECRUITING)
        {
            throw PostStore.postNotRecruiting();
        }
        return post.getCapacity();
    }

    private static Long postIdOf(String roomId)
    {
        try
        {
            return Long.parseLong(roomId);
        }
        catch(NumberFormatException e)
        {
            throw PostStore.postNotFound();
        }
    }
}
