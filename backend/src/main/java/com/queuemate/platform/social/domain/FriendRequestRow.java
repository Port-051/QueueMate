package com.queuemate.platform.social.domain;

import com.queuemate.platform.social.dto.FriendRequestResponse;
import com.queuemate.platform.social.dto.FriendUser;

import java.time.Instant;

/**
 * 친구 요청 한 줄과 양쪽 닉네임을 <b>한 쿼리로</b> 읽어 온 것({@code users} 를 두 번 JOIN — {@code FriendRequestRepository}).
 * JPQL 의 {@code select new} 는 생성자 안에 또 {@code new} 를 쓸 수 없어서 납작한 모양으로 받고 {@link #toResponse()} 로 응답 모양을 만든다.
 */
public record FriendRequestRow(Long requestId, Long requesterId, String requesterNickname,
                               Long receiverId, String receiverNickname, Instant createdAt) {

    public FriendRequestResponse toResponse()
    {
        return new FriendRequestResponse(requestId, new FriendUser(requesterId, requesterNickname),
                new FriendUser(receiverId, receiverNickname), createdAt);
    }
}
