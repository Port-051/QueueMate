package com.queuemate.platform.social.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.common.push.PushPublisher;
import com.queuemate.platform.common.web.Ids;
import com.queuemate.platform.social.domain.FriendRequest;
import com.queuemate.platform.social.domain.FriendRequestStatus;
import com.queuemate.platform.social.domain.FriendRequestRow;
import com.queuemate.platform.social.domain.Friendship;
import com.queuemate.platform.social.dto.FriendListResponse;
import com.queuemate.platform.social.dto.FriendRequestListResponse;
import com.queuemate.platform.social.dto.FriendRequestResponse;
import com.queuemate.platform.social.dto.FriendResponse;
import com.queuemate.platform.social.repository.FriendRequestRepository;
import com.queuemate.platform.social.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 친구 요청(보내기 · 목록 · 수락 · 거절 · 거두기)과 친구(목록 · 끊기) ({@code contracts/platform-api.md} "친구 · 신고 · 최근 함께한 사람").
 *
 * <p><b>사람을 찾아보는 기능이 아니다</b> — 사용자 번호를 정확히 알아야 요청을 보낼 수 있고, 여기에 검색 · 추천 · 사용자 목록을 붙이지 않는다(CLAUDE.md §1).
 *
 * <p><b>불변식은 DB 가 지킨다</b>(CLAUDE.md §5) — "같은 방향의 대기 중 요청은 하나"는 partial unique index, "친구는 한 줄"은 PK,
 * "대기 중일 때만 응답한다"는 조건부 UPDATE 다. 아래의 조회들({@code ALREADY_FRIENDS} 등)은 친절한 에러를 위한 것이고 빠져도 데이터가 깨지지 않는다.
 *
 * <p>친구를 끊어도 · 거절해도 · 거둬도 상대에게 알리지 않는다. <b>차단은 친구 관계를 건드리지 않는다</b>(미정 그대로 — CLAUDE.md §7.1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    /** 마이그레이션(V1__schema.sql)이 붙인 이름이다. 거기서 바꾸면 여기도 바꾼다 */
    static final String ONE_PENDING = "friend_requests_one_pending";
    static final String REQUEST_NOT_SELF = "friend_requests_not_self";
    /** 보낸 사람(나)의 칸의 FK. 받는 사람의 칸이 깨지면 없는 사용자라 404 다({@code ConstraintViolations#isMissingUser}) */
    static final String REQUESTER_FKEY = "friend_requests_requester_id_fkey";

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final BlockReader blockReader;
    private final PushPublisher pushPublisher;

    /**
     * 친구 요청을 보낸다. <b>같은 방향의 대기 중 요청이 이미 있는지는 조회하지 않는다</b> — INSERT 한 뒤 partial unique index 의 위반을 409 로 옮긴다.
     * 같은 요청이 동시에 여러 번 와도 하나만 통과한다.
     *
     * <p><b>없는 사용자와 차단 관계는 글자까지 같은 404 다</b> — 다르면 "저 사람이 나를 차단했다"가 새어 나간다. 내가 차단한 경우도 같다
     * (어느 방향이든 — 계약). 숫자가 아닌 번호도 같다(있을 수 없는 사용자다). 그래서 셋을 한 곳({@link #userNotFound})에서 만든다.
     * <b>있는 사용자인지는 조회하지 않고 FK 가 본다</b> — 받는 사람의 칸({@code friend_requests_receiver_id_fkey})이 깨지면 404 다.
     * 차단 관계는 FK 가 대신 못 하므로 INSERT 전에 {@link BlockReader} 로 본다. 보낸 사람(나)의 칸이 깨지면 토큰은 멀쩡한데 내가 DB 에 없는 것이라 401 이다.
     *
     * <p>{@code ALREADY_FRIENDS} · {@code FRIEND_REQUEST_ALREADY_RECEIVED} 는 <b>조회로 거르는 친절한 에러다</b> — 서로 동시에 보내면 둘 다 이 조회를 지나
     * 양방향 PENDING 이 둘 생길 수 있다. 그래도 깨지는 것이 없다 — 어느 쪽을 수락하든 친구는 한 줄이고 반대 방향도 같이 닫힌다({@link #accept}).
     */
    @Transactional
    public FriendRequestResponse send(Long me, String target)
    {
        Long targetUserId = Ids.parse(target).orElseThrow(FriendService::userNotFound);
        if(me.equals(targetUserId))
        {
            throw cannotFriendSelf();
        }
        if(!blockReader.findBlockedEitherWay(me, List.of(targetUserId)).isEmpty())
        {
            throw userNotFound();
        }
        if(friendshipRepository.existsById(Friendship.Key.of(me, targetUserId)))
        {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_FRIENDS", "이미 친구입니다");
        }
        if(friendRequestRepository.existsByRequesterIdAndReceiverIdAndStatus(targetUserId, me, FriendRequestStatus.PENDING))
        {
            throw new ApiException(HttpStatus.CONFLICT, "FRIEND_REQUEST_ALREADY_RECEIVED",
                    "상대가 이미 친구 요청을 보냈습니다. 받은 요청을 수락하세요");
        }

        Instant now = now();
        FriendRequest request;
        try
        {
            request = friendRequestRepository.saveAndFlush(new FriendRequest(me, targetUserId, now));
        }
        catch(DataIntegrityViolationException e)
        {
            String constraint = ConstraintViolations.nameOf(e);
            if(ONE_PENDING.equals(constraint))
            {
                throw new ApiException(HttpStatus.CONFLICT, "FRIEND_REQUEST_ALREADY_SENT", "이미 친구 요청을 보냈습니다");
            }
            if(REQUEST_NOT_SELF.equals(constraint))
            {
                throw cannotFriendSelf();
            }
            if(REQUESTER_FKEY.equals(constraint))
            {
                // 토큰은 멀쩡한데 그 사용자가 DB 에 없다
                throw ApiException.unauthenticated();
            }
            if(ConstraintViolations.isMissingUser(constraint))
            {
                throw userNotFound();
            }
            throw e;
        }

        // 데이터(닉네임 등)는 싣지 않는다 — 받은 쪽이 GET /friend-requests 를 다시 부른다. 칸의 순서는 계약의 것이다
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.getId());
        payload.put("fromUserId", me);
        pushPublisher.publishAfterCommit(targetUserId, PushEventType.FRIEND_REQUEST_RECEIVED, payload);

        log.info("친구 요청 requestId={} requesterId={} receiverId={}", request.getId(), me, targetUserId);
        // 방금 이 트랜잭션이 넣은 줄이다 — 반드시 있다(양쪽이 있는 사용자라는 것도 FK 가 확인했다)
        return friendRequestRepository.findRowById(request.getId()).map(FriendRequestRow::toResponse)
                .orElseThrow(() -> new IllegalStateException("방금 넣은 친구 요청이 없다 requestId=" + request.getId()));
    }

    /** 대기 중인 것만, 새것이 먼저. {@code received} 가 참이면 내가 받은 것, 아니면 내가 보낸 것이다. 쿼리 한 번이다 — 닉네임은 JOIN 으로 붙인다 */
    @Transactional(readOnly = true)
    public FriendRequestListResponse listPending(Long me, boolean received)
    {
        List<FriendRequestRow> rows = received
                ? friendRequestRepository.findPendingReceivedRows(me)
                : friendRequestRepository.findPendingSentRows(me);
        return new FriendRequestListResponse(rows.stream().map(FriendRequestRow::toResponse).toList());
    }

    /**
     * 수락한다. <b>조건부 UPDATE 가 1줄을 바꿨을 때만</b> 친구를 넣는다 — 0줄이면 없는 요청이거나 · 내가 받은 것이 아니거나 · 이미 처리됐고,
     * 셋을 가르지 않고 404 다(남의 요청이 있는지를 알려 주지 않는다). 같은 수락이 동시에 와도 1 을 받는 것은 하나다 — 200 은 하나, 친구는 한 줄이다.
     *
     * <p>친구 INSERT 는 {@code ON CONFLICT DO NOTHING} 이다 — 이미 친구였어도(조회를 비껴간 요청이 남아 있었다) 위반 없이 지나간다.
     * <b>반대 방향의 대기 중 요청도 같은 트랜잭션에서 {@code ACCEPTED} 로 닫는다</b> — 안 닫으면 이미 친구인 사람의 요청이 목록에 남는다.
     */
    @Transactional
    public FriendResponse accept(Long me, Long requestId)
    {
        Instant now = now();
        if(friendRequestRepository.respondIfPending(requestId, me, FriendRequestStatus.ACCEPTED, now) != 1)
        {
            throw requestNotFound();
        }
        // 방금 이 트랜잭션이 바꾼 줄이다 — 반드시 있다. 보낸 사람이 누구인지는 여기서 안다(요청 본문에 없다)
        Long requesterId = friendRequestRepository.findById(requestId).orElseThrow(FriendService::requestNotFound).getRequesterId();

        Friendship.Key key = Friendship.Key.of(me, requesterId);
        friendshipRepository.insertIfAbsent(key.getUserLowId(), key.getUserHighId(), now);
        friendRequestRepository.acceptPendingBetween(me, requesterId, now);
        // 이미 친구였으면 since 는 그때의 시각이다 — 그래서 넣은 값이 아니라 읽은 값을 쓴다. 상대의 닉네임도 같이 읽는다(JOIN)
        FriendResponse friend = friendshipRepository.findFriend(me, requesterId)
                .orElseThrow(() -> new IllegalStateException("방금 넣은 친구가 없다 requesterId=" + requesterId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("userId", me);
        pushPublisher.publishAfterCommit(requesterId, PushEventType.FRIEND_REQUEST_ACCEPTED, payload);

        log.info("친구 요청 수락 requestId={} requesterId={} receiverId={}", requestId, requesterId, me);
        return friend;
    }

    /** 거절한다. 보낸 사람에게 알리지 않는다. 거절된 뒤에는 같은 사람이 다시 요청할 수 있다(처리된 줄은 partial unique index 에 없다) */
    @Transactional
    public void decline(Long me, Long requestId)
    {
        if(friendRequestRepository.respondIfPending(requestId, me, FriendRequestStatus.DECLINED, now()) != 1)
        {
            throw requestNotFound();
        }
        log.info("친구 요청 거절 requestId={} receiverId={}", requestId, me);
    }

    /** 보낸 사람이 거둔다. 받은 사람에게 알리지 않는다 — 받은 목록을 다시 받으면 없어져 있다 */
    @Transactional
    public void cancel(Long me, Long requestId)
    {
        if(friendRequestRepository.cancelIfPending(requestId, me, now()) != 1)
        {
            throw requestNotFound();
        }
        log.info("친구 요청 거둠 requestId={} requesterId={}", requestId, me);
    }

    /** 친구 목록 — 닉네임순(대소문자를 가리지 않는다. 같으면 사용자 번호순). 쿼리 한 번이다 — 닉네임은 JOIN 으로 붙이고 정렬은 DB 가 한다 */
    @Transactional(readOnly = true)
    public FriendListResponse listFriends(Long me)
    {
        return new FriendListResponse(friendshipRepository.findFriendsOf(me));
    }

    /** 친구를 끊는다. 친구가 아니어도 · 없는 사용자여도 성공이다 — 두 번 눌러도 결과가 같다. 상대에게 알리지 않는다 */
    @Transactional
    public void unfriend(Long me, Long otherUserId)
    {
        if(me.equals(otherUserId))
        {
            return;
        }
        Friendship.Key key = Friendship.Key.of(me, otherUserId);
        if(friendshipRepository.deleteByLowAndHigh(key.getUserLowId(), key.getUserHighId()) > 0)
        {
            log.info("친구 끊음 userId={} otherUserId={}", me, otherUserId);
        }
    }

    private static Instant now()
    {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /** 없는 사용자와 차단 관계가 <b>같은 응답</b>이어야 한다 — 그래서 만드는 곳이 하나다 */
    private static ApiException userNotFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "없는 사용자입니다");
    }

    private static ApiException requestNotFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, "FRIEND_REQUEST_NOT_FOUND", "없는 친구 요청입니다");
    }

    private static ApiException cannotFriendSelf()
    {
        return new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_FRIEND_SELF", "자기 자신에게는 친구 요청을 보낼 수 없습니다");
    }
}
