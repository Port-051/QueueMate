package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.party.match.MatchParty;
import com.queuemate.platform.party.repository.PartyRecordRepository;
import com.queuemate.platform.social.service.RecentPlayerRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * DB 에 기록된 자동 매칭 파티 — 게임과 파티원({@link #findRecorded}).
     *
     * @param memberIds 가입한 사용자만이다({@code party_members} 의 FK). 사용자 번호순
     */
    record Recorded(Game game, List<Long> memberIds) {
    }

    /**
     * 기록된 자동 매칭 파티(2026-10-01 — 퀵 매칭 파티의 팀원 카드가 파티 HASH 가 사라진 뒤에 쓴다, {@link MatchPartyService#members}). 쿼리 한 번이다.
     *
     * @return 그 {@code match_party_id} 의 파티가 없으면 비어 있다. {@code parties.game} 을 이 앱의 {@link Game} 으로 팔 수 없어도 비어 있다(WARN)
     */
    @Transactional(readOnly = true)
    public Optional<Recorded> findRecorded(String matchPartyId)
    {
        List<Object[]> rows = partyRecordRepository.findMatchPartyMembers(matchPartyId);
        if(rows.isEmpty())
        {
            return Optional.empty();
        }
        Optional<Game> game = Game.fromName(String.valueOf(rows.getFirst()[0]));
        if(game.isEmpty())
        {
            log.warn("기록된 자동 매칭 파티의 게임을 모른다 — 없는 파티로 다룬다 matchPartyId={}", matchPartyId);
            return Optional.empty();
        }
        List<Long> members = new ArrayList<>();
        for(Object[] row : rows)
        {
            // 파티원이 한 명도 없는 파티는 LEFT JOIN 의 빈 줄 하나다
            if(row[1] != null)
            {
                members.add(((Number) row[1]).longValue());
            }
        }
        return Optional.of(new Recorded(game.get(), List.copyOf(members)));
    }

    /**
     * 확정된 자동 매칭 파티를 {@code parties} · {@code party_members} 에 적는다. <b>멱등이다</b> — 파티는 {@code UNIQUE (match_party_id)} 위의
     * {@code INSERT … ON CONFLICT DO NOTHING}, 파티원은 PK 위의 같은 것이라 파티원 전원이 동시에 불러도 파티 하나 · 파티원 한 벌이다.
     *
     * <p><b>{@code is_host} 는 파티 줄을 만든 호출의 사용자다</b>(= 이 앱을 처음 부른 파티원). 자동 매칭에는 글을 쓴 사람이 없어 방장 자리가 원래 없다 —
     * 방의 방장 키는 스크립트를 먼저 탄 사람이 되는데 그것은 이 기록 뒤의 일이라 여기서 알 수 없고, 알아도 확정한 방은 방장이 바뀐다(D-23). 그래서 DB 의
     * {@code is_host} 는 "기록을 남긴 사람" 하나로 정한다. 이미 있던 파티면 빠진 파티원만 {@code false} 로 채운다.
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
        for(Long member : party.memberIds())
        {
            partyRecordRepository.insertMemberIfAbsent(partyId, member, created && member.equals(me), now);
        }
        if(created)
        {
            log.info("자동 매칭 파티 기록 matchPartyId={} partyId={} game={} members={}", party.partyId(), partyId, party.game(),
                    party.memberIds().size());
        }
        return partyId;
    }

    /**
     * <b>자동 매칭 파티 닫기</b> — 그 파티의 방({@code roomId} = {@code match_party_id})이 없어졌을 때 {@code room} 이 부른다. 게시판 파티의
     * {@link PostLifecycle#closeParty} 와 같다 — {@code CLOSED} · {@code closed_at} 을 적고 그 순간 파티원끼리 서로를 최근 함께한 사람에 적는다
     * ({@link RecentPlayerRecorder}). 조건부 UPDATE 가 1줄을 받은 호출 하나만 적는다(멱등).
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
        Long partyId = partyRecordRepository.findPartyIdByMatchPartyId(matchPartyId).orElseThrow(
                () -> new IllegalStateException("방금 닫은 자동 매칭 파티가 없다 matchPartyId=" + matchPartyId));
        int rows = recentPlayerRecorder.recordParty(partyId, now);
        log.info("자동 매칭 파티 닫힘 matchPartyId={} partyId={} recentPlayerRows={}", matchPartyId, partyId, rows);
        return true;
    }
}
