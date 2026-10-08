package com.queuemate.platform.account.service;

import com.queuemate.platform.account.repository.UserRepository;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.security.RefreshTokens;
import com.queuemate.platform.party.service.PostService;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.service.RoomMemberService;
import com.queuemate.platform.room.service.RoomService;
import com.queuemate.platform.social.domain.BlockRelationUnavailableException;
import com.queuemate.platform.social.service.BlockReader;
import com.queuemate.platform.social.service.BlockRelationRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

/**
 * <b>회원 탈퇴</b>({@code DELETE /api/v1/auth/account} — 2026-10-02 소유자 결정 · {@code contracts/platform-api.md} P-48. 처음에는 {@code DELETE /api/v1/users/me} 였다 —
 * refresh 쿠키가 실려 오게 같은 날 {@code /api/v1/auth} 아래로 옮겼다). 카카오 운영정책 · 디스코드 Developer Terms ·
 * 개인정보 보호법이 탈퇴(전부 파기) 수단을 요구한다. <b>그 사람의 데이터를 지체 없이 전부 지우고, 확정된 파티 기록만 남긴다</b> — 확정된 글은 작성자 칸만 비고
 * 다른 파티원의 줄과 파티는 그대로다(V9 의 {@code ON DELETE SET NULL}). 지우는 범위는 {@link UserRepository#deleteUserRow} 의 주석이다.
 *
 * <p><b>순서</b>(Claude 가 정한 세부 — 계약 P-48)
 * <ol>
 *   <li>사용자가 없으면 401 — 토큰은 멀쩡한데 이미 탈퇴했다(access 는 denylist 가 없어 만료까지 서명이 유효하다). 아무것도 건드리지 않는다</li>
 *   <li><b>매칭 대기 중이면 409 {@code ALREADY_QUEUED}</b> — 활성 요청 키는 {@code matching} 의 것이라 이 앱이 지울 수 없다(D-19). 사용자가 먼저 취소한다</li>
 *   <li><b>방 안이면 평소 나가기와 같은 규칙으로 나간다</b>({@link RoomMemberService#leave} — 그 콜백까지 그대로) — 확정 전 방의 방장이면 방이 닫히고 글이 만료되고,
 *       확정한 방이면 승계, 마지막 사람이면 파티가 닫힌다(최근 함께한 사람도 그때 적힌다 — 곧 그 사람의 줄은 지워진다)</li>
 *   <li>모집 중인 글이 남아 있으면 글 지우기와 같은 효과(만료 · 방 닫기 · {@code ROOM_CLOSED}) — {@link PostService#expireRecruitingOf}</li>
 *   <li><b>한 트랜잭션</b> — 사용자 줄을 잠그고({@code FOR UPDATE}) → <b>차단 관계인 상대 목록을 읽어 두고</b>({@link BlockReader#counterpartsOf} — 2026-10-02 · P-52)
 *       → 확정되지 않은 글을 지우고 → 사용자 줄을 지운다(나머지는 FK 가 정리한다 — {@code blocks} 의 줄도)</li>
 *   <li><b>차단 관계 사본(Redis)에서 그 사람을 지운다</b>({@link BlockRelationRedis#removeUser} — 그 사람의 집합을 지우고 상대들의 집합에서 번호를 뺀다).
 *       Redis 에 닿지 못해도 넘어간다 — 남는 것은 없는 사람(번호는 다시 쓰이지 않는다)을 향한 더 막기뿐이고 재구성이 치운다</li>
 *   <li><b>요청에 실려 온 refresh 를 Redis 에서 지운다</b>(2026-10-02 소유자 지시 "탈퇴 요청에도 refresh 토큰 실어서 버려") — 로그아웃과 같은 코드
 *       ({@link RefreshTokens#revoke(List)}). 쿠키가 없거나 Redis 가 죽었으면 넘어간다 — 탈퇴는 이미 끝났으니 실패시키지 않는다(로그아웃이 Redis 장애에도 204 인 것과 같다)</li>
 * </ol>
 * 쿠키를 지우는 것은 컨트롤러다({@code AuthController#deleteAccount} — 로그아웃과 같은 {@code Set-Cookie}).
 *
 * <p><b>Redis 를 먼저, DB 를 나중에</b> 한다. 방을 먼저 정리하고 DB 가 실패하면 "방에서 나왔지만 탈퇴는 안 됐다" 가 남는데, 다시 누르면 된다. 거꾸로면 지워진 사람이
 * 방의 멤버 HASH 에 남는다. <b>Redis 를 못 읽으면 503 {@code ROOM_STATE_UNAVAILABLE}</b>(①②) — 매칭 중인지 · 어느 방에 있는지 모르는 채 지우지 않는다.
 *
 * <p><b>잠금의 이유</b> — 확정되지 않은 글을 지운 뒤 사용자 줄을 지우기 전에 같은 사람의 글 쓰기가 끼면 SET NULL 이 CHECK({@code recruit_posts_host_id_check})에 걸려
 * 탈퇴가 500 으로 되돌려진다. 사용자 줄을 {@code FOR UPDATE} 로 잡으면 그 글 쓰기의 INSERT 가 FK 검사({@code FOR KEY SHARE})에서 기다렸다가, 사용자가 지워진 뒤
 * FK 위반 → 401 로 끝난다. 같은 사람의 탈퇴 둘이 겹쳐도 뒤의 것은 잠금에서 기다렸다가 줄이 없어 401 이다.
 *
 * <p><b>감수하는 것</b>(새 장치를 만들지 않는다 — 소유자 지시) — ① 매칭 대기를 확인한 뒤 · 방을 나간 뒤 탈퇴가 끝나기 전의 밀리초에 같은 사람이 매칭을 걸거나 방에 들어가는 경쟁
 * ② 탈퇴 뒤 남는 access 토큰(최대 15분)으로 Redis 만 쓰는 요청(방 입장 · {@code matching} 의 매칭 요청)은 통한다 — 나를 적는 요청(글 · 친구 요청 · 차단 · 신고 · 게임 계정)은 FK 위반이, 내 정보는 사용자 조회가 401 로 막는다
 * ③ <b>다른 기기</b>의 refresh 의 Redis 줄은 지우지 못한다 — 이 요청에 실려 온 이 브라우저의 값만 지운다(마지막 단계). 한 사용자의 refresh 를 찾는 길이 없다
 * ({@code KEYS}/{@code SCAN} 을 쓰지 않는다 · 모든 기기 로그아웃은 미정). 그 값으로 재발급을 부르면 사용자가 없어 401 {@code INVALID_REFRESH_TOKEN} 이고 그때 그 줄도 지워진다.
 * 남은 줄도 수명(7일)이 다하면 사라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final RoomService roomService;
    private final RoomMemberService roomMemberService;
    private final PostService postService;
    private final TransactionTemplate transactionTemplate;
    private final RefreshTokens refreshTokens;
    private final BlockReader blockReader;
    private final BlockRelationRedis blockRelationRedis;

    /**
     * 탈퇴시킨다. 성공하면 그 사용자 번호의 줄이 DB 에 없다.
     *
     * <p><b>{@code @Transactional} 이 없다 — 붙이면 안 된다.</b> 앞의 셋(매칭 확인 · 나가기 · 남은 글)은 Redis 를 부르고 저마다 짧은 트랜잭션으로 끝난다 —
     * 나가기의 콜백이 글을 만료시키거나 파티를 닫는 것도 그 자리에서 커밋돼야 알림 · 신호가 나간다. DB 를 지우는 마지막 토막만 한 트랜잭션이다.
     *
     * @param refreshTokens 요청에 실려 온 {@code qm_refresh} 쿠키의 값들({@link RefreshTokens#valuesIn}) — DB 를 지운 뒤 Redis 에서 지운다. 없으면 빈 목록
     * @throws ApiException 401 {@code UNAUTHENTICATED}(이미 없는 사용자) · 409 {@code ALREADY_QUEUED}(매칭 대기 중) · 503 {@code ROOM_STATE_UNAVAILABLE}(Redis)
     */
    public void delete(Long userId, List<String> refreshTokens)
    {
        if(!userRepository.existsById(userId))
        {
            // 토큰은 멀쩡한데 그 사용자가 DB 에 없다 — 이미 탈퇴했다
            throw ApiException.unauthenticated();
        }
        String me = String.valueOf(userId);
        if(roomService.queued(me))
        {
            throw RoomErrors.alreadyQueued("매칭 중에는 탈퇴할 수 없습니다. 매칭을 먼저 취소해 주세요");
        }
        String roomId = roomService.myRoom(me);
        if(roomId != null)
        {
            roomMemberService.leave(roomId, me);
        }
        postService.expireRecruitingOf(userId);
        Set<Long> blockCounterparts = transactionTemplate.execute(status -> {
            if(userRepository.lockById(userId).isEmpty())
            {
                // 같은 사람의 탈퇴가 겹쳐 앞의 것이 먼저 지웠다
                throw ApiException.unauthenticated();
            }
            // 사용자 줄을 지우면 CASCADE 가 blocks 의 줄을 지운다 — 사본에서 번호를 뺄 상대를 먼저 읽는다(잠근 뒤라 새 차단이 끼지 못한다)
            Set<Long> counterparts = blockReader.counterpartsOf(userId);
            postService.deleteUnconfirmedOf(userId);
            userRepository.deleteUserRow(userId);
            return counterparts;
        });
        forgetBlockRelations(userId, blockCounterparts == null ? Set.of() : blockCounterparts);
        // 탈퇴는 끝났다 — 이 브라우저의 refresh 를 버린다. 예외를 내지 않는다(Redis 가 죽었으면 그 줄은 수명까지 남고, 쓰면 사용자가 없어 401 이다)
        this.refreshTokens.revoke(refreshTokens);
        log.info("회원 탈퇴 userId={}", userId);
    }

    /**
     * 차단 관계 사본(Redis)에서 탈퇴자를 지운다 — 커밋 뒤라 <b>예외를 내지 않는다</b>(탈퇴는 이미 끝났다). 못 지우면 WARN 한 줄 — 남는 것은 없는 사람을 향한 더 막기뿐이고
     * 재구성(기동 때 · 주기 — {@code BlockRelationSync})이 표에 없는 사용자의 키와 번호를 치운다.
     */
    private void forgetBlockRelations(Long userId, Set<Long> counterparts)
    {
        try
        {
            blockRelationRedis.removeUser(userId, counterparts);
        }
        catch(BlockRelationUnavailableException e)
        {
            log.warn("탈퇴자를 차단 관계 사본에서 지우지 못했다 — 재구성이 치운다 userId={} 상대 {}명: {}", userId, counterparts.size(), e.getCause().toString());
        }
    }
}
