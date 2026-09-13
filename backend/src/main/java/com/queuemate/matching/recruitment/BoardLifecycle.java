package com.queuemate.matching.recruitment;

import com.queuemate.matching.domain.MatchingEvents;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.common.party.PartyReadyReached;
import com.queuemate.realtime.event.*;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.LoggerFactory;
import java.util.*;

@Component
public class BoardLifecycle {
    private final JdbcTemplate db;
    private final EntityManager em;
    private final BoardService service;
    private final RealtimeEventPublisher events;
    public BoardLifecycle(JdbcTemplate db, EntityManager em, BoardService service, RealtimeEventPublisher events) {
        this.db=db;this.em=em;this.service=service;this.events=events;
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void created(MatchingEvents.ProposalCreated event) {record(event.proposalId(),"PROPOSED");invalidate();}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void settled(MatchingEvents.ProposalSettled event) {
        record(event.proposalId(),event.status()==ProposalStatus.CONFIRMED?"MUTUALLY_ACCEPTED":"PROPOSAL_"+event.status());
        if(event.status()!=ProposalStatus.CONFIRMED) {
            var ids=db.queryForList("select source_request_id from proposal_members where proposal_id=?",UUID.class,event.proposalId());
            for(UUID id:ids) db.update("update recruitments set parent_id=null, requested_parent_id=null, version=version+1 where id=? or parent_id=? or requested_parent_id=?",id,id,id);
        }
        invalidate();
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void ready(PartyReadyReached event) {
        em.flush();
        db.update("""
            insert into recruitment_events(recruitment_id,event)
            select r.id,'READY' from recruitments r join proposal_members pm on pm.source_request_id=r.id
            join parties p on p.proposal_id=pm.proposal_id where p.id=?
            and not exists(select 1 from recruitment_events e where e.recruitment_id=r.id and e.event='READY')
            """,event.partyId());
    }
    private void record(UUID proposalId,String event) {
        em.flush();
        db.update("insert into recruitment_events(recruitment_id,event) select r.id,? from recruitments r join proposal_members pm on pm.source_request_id=r.id where pm.proposal_id=?",event,proposalId);
    }
    private void invalidate() {events.publishAfterCommit(List.of(),ServerEvent.of(EventType.RECRUITMENT_UPDATED,Map.of()));}
    @Scheduled(fixedDelayString="${queuemate.recruitment.group-sweep-ms:15000}",initialDelayString="${queuemate.recruitment.group-sweep-ms:15000}")
    public void fillGroups() {
        for(UUID id:db.queryForList("select distinct parent_id from recruitments where parent_id is not null and not closed",UUID.class)) {
            try {service.fillGroup(id);} catch(RuntimeException e) {LoggerFactory.getLogger(getClass()).warn("모집 빈자리 재검색 실패 id={}",id,e);}
        }
    }
}
