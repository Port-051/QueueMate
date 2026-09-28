package com.queuemate.platform.party.service;

import com.queuemate.platform.party.match.MatchParty;
import com.queuemate.platform.party.repository.PartyRecordRepository;
import com.queuemate.platform.social.service.RecentPlayerRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;

/**
 * <b>자동 매칭 파티의 DB 쪽</b> — 기록과 닫기(2026-09-27 소유자 결정 — docs/11 D-42. 클래스의 이름 · 자리 · {@code is_host} 규칙은 Claude 가 정한 세부다).
 * 메서드 하나가 트랜잭션 하나다. Redis(파티 HASH 읽기 · 방의 스크립트)는 여기 없다 — {@link MatchPartyService} 가 트랜잭션 밖에서 한다
 * ({@link PostService} 와 {@link PostStore} 를 나눈 것과 같은 이유다 — 트랜잭션 안에서 Redis 를 기다리지 않는다).
 *
 * <p><b>{@code PostService} · {@link PostStore} 를 물지 않는 따로 선 빈이다</b> — {@code room} 의 나가기 · 접속 확인({@code RoomMemberService})이 UUID 방이
 * 없어진 뒤 {@link #closeByRoomClosed} 를 부른다. {@link PostLifecycle} 과 같은 까닭이고 리포지토리와 {@code social} 의 창구만 문다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchPartyStore {

    private final PartyRecordRepository partyRecordRepository;
    private final RecentPlayerRecorder recentPlayerRecorder;

    /**
     * 확정된 자동 매칭 파티를 {@code parties} 에, <b>부른 사람 한 명</b>을 {@code party_members} 에 적는다. <b>멱등이다</b> — 파티는
     * {@code UNIQUE (match_party_id)} 위의 {@code INSERT … ON CONFLICT DO NOTHING}, 파티원은 PK 위의 같은 것이라 파티원 전원이 동시에 불러도
     * 파티 하나 · 한 사람에 한 줄이다.
     *
     * <p><b>{@code party_members} 는 HASH 의 {@code member:*} 전원이 아니라 이 요청으로 실제로 방에 들어온 사람만이다</b>(2026-09-28 소유자 결정 —
     * 그 전에는 HASH 의 파티원 전부를 첫 호출에서 적었다). {@code party_members} 의 뜻은 "실제로 방에 들어온 사람" 이다 — 게시판 파티가 확정 순간
     * 방 안에 있던 사람만 적는 것, {@code recent_players} 가 실제로 함께한 사람만 적는 것과 같은 뜻이다. 자격(403 {@code NOT_PARTY_MEMBER})은 여전히
     * HASH 의 {@code member:*} 로 본다({@link MatchPartyService}) — 여기는 자격이 확인된 사람이 들어올 때마다 한 줄씩 더하는 자리다.
     *
     * <p><b>{@code is_host} 는 파티 줄을 만든 호출의 사용자다</b>(= 이 앱을 처음 부른 파티원). 자동 매칭에는 글을 쓴 사람이 없어 방장 자리가 원래 없다 —
     * 방의 방장 키는 스크립트를 먼저 탄 사람이 되는데 그것은 이 기록 뒤의 일이라 여기서 알 수 없고, 알아도 확정한 방은 방장이 바뀐다(D-23). 그래서 DB 의
     * {@code is_host} 는 "기록을 남긴 사람" 하나로 정한다. 이미 있던 파티면 부른 사람을 {@code false} 로 더한다.
     *
     * <p>가입하지 않은 번호는 적지 않는다 — {@code party_members.user_id} 의 FK 를 {@code INSERT … WHERE EXISTS (users)} 가 피한다
     * ({@link PartyRecordRepository#insertMemberIfAbsent}).
     *
     * @return {@code parties.id}
     */
    @Transactional
    public Long record(MatchParty party, Long me, Instant now)
    {
        boolean created = partyRecordRepository.insertMatchPartyIfAbsent(party.partyId(), party.game().name(), now) == 1;
        Long partyId = partyRecordRepository.findPartyIdByMatchPartyId(party.partyId()).orElseThrow(
                () -> new IllegalStateException("방금 기록한 자동 매칭 파티가 없다 matchPartyId=" + party.partyId()));
        // 파티원은 들어온 사람만이다(2026-09-28 소유자 결정). 최근 함께한 사람은 여기서 적지 않는다 — 방에 실제로 들어간 뒤
        // 그 순간 방에 있던 사람과 적는다(recordEntry, MatchPartyService 가 Lua 결과를 보고 부른다)
        int added = partyRecordRepository.insertMemberIfAbsent(partyId, me, created, now);
        if(created)
        {
            // confirmed 는 HASH 의 파티원 수(정원)이고 party_members 에 적은 줄은 부른 사람 하나다 — 뒤에 들어오는 사람이 한 줄씩 더한다
            log.info("자동 매칭 파티 기록 matchPartyId={} partyId={} game={} confirmed={} userId={}", party.partyId(), partyId,
                    party.game(), party.memberIds().size(), me);
        }
        log.debug("자동 매칭 파티 입장 기록 partyId={} userId={} added={}", partyId, me, added);
        return partyId;
    }

    /**
     * <b>최근 함께한 사람 — 들어온 순간, 그때 방에 있던 사람과만</b>(2026-09-28 소유자 결정). 닫을 때 {@code party_members} 끼리 전부 짝을
     * 지으면(게시판 파티의 방식) 자동 매칭 방에서는 A 가 나간 뒤 들어온 C 까지 A 와 "함께한 사람"이 된다 — {@code party_members} 는 나간
     * 사람을 지우지 않기 때문이다. 그래서 스크립트가 돌려준 "먼저 있던 사람"({@code MatchRoomEntry#priorMembers})과만 양방향으로 적는다.
     * 먼저 들어온 사람은 나중 사람이 들어올 때 그 사람과 짝이 지어지므로 빠지지 않는다. 숫자가 아닌 번호와 {@code party_members} 에
     * 없는 번호(가입하지 않은 사람)는 짝에서 빠진다(FK).
     *
     * @return 적거나 갱신한 줄 수 (먼저 있던 사람이 n 명이면 2n)
     */
    @Transactional
    public int recordEntry(Long partyId, Long me, Collection<String> priorMemberIds, Instant now)
    {
        List<Long> others = new ArrayList<>();
        for(String id : priorMemberIds)
        {
            try
            {
                others.add(Long.parseLong(id));
            }
            catch(NumberFormatException e)
            {
                log.warn("방 멤버 SET 에 사용자 번호가 아닌 값이 있어 최근 함께한 사람에서 건너뛴다 partyId={}", partyId);
            }
        }
        if(others.isEmpty())
        {
            return 0;
        }
        int rows = recentPlayerRecorder.recordEntry(partyId, me, others, now);
        log.debug("최근 함께한 사람 기록 partyId={} userId={} with={} rows={}", partyId, me, others.size(), rows);
        return rows;
    }

    /**
     * <b>자동 매칭 파티 닫기</b> — 그 파티의 방({@code roomId} = {@code match_party_id})이 없어졌을 때 {@code room} 이 부른다. 게시판 파티의
     * {@link PostLifecycle#closeParty} 와 달리 <b>{@code CLOSED} · {@code closed_at} 만 적는다</b> — 최근 함께한 사람은 들어올 때 이미 적었다
     * ({@link #record} → {@link RecentPlayerRecorder#recordEntry}, 2026-09-28 소유자 결정). 조건부 UPDATE 라 몇 번 불려도 한 번만 닫힌다(멱등).
     * 그래서 전원이 말없이 사라져 이 메서드가 영영 안 불리는 파티는 {@code ACTIVE} 로 남지만, 기록(파티원 · 최근 함께한 사람)은 이미 다 있어 손해가 없다.
     *
     * <p>길은 하나다 — 마지막 사람의 나가기 · 남은 사람의 접속 확인. 게시판 파티의 길 ②(목록 · 단건이 사라진 방을 발견)는 글이 없어 여기 없다
     * (D-42 "아직 미정" — {@code RoomMemberService#endPostOf}).
     *
     * @return 이 호출이 닫았으면 {@code true}
     */
    @Transactional
    public boolean closeByRoomClosed(String matchPartyId)
    {
        // PostService#now() 와 같은 정밀도(밀리초)로 적는다
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if(partyRecordRepository.closeIfActiveByMatchPartyId(matchPartyId, now) == 0)
        {
            return false;
        }
        // 최근 함께한 사람은 들어올 때 이미 적었다(record) — 여기서 party_members 끼리 다시 짝을 지으면
        // A 가 나간 뒤 들어온 C 까지 A 와 "함께한 사람"이 된다. 닫힘은 상태만 바꾼다
        log.info("자동 매칭 파티 닫힘 matchPartyId={}", matchPartyId);
        return true;
    }
}
