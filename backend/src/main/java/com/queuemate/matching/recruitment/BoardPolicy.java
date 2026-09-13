package com.queuemate.matching.recruitment;

import com.queuemate.common.domain.GameKey;
import com.queuemate.matching.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class BoardPolicy implements MatchPoolPolicy {
    private final BoardStore store;
    private final long staleMinutes;
    @Value("${queuemate.recruitment.confirm-minutes:10}") private long confirmMinutes;
    @Value("${queuemate.recruitment.suggestion-minutes:3}") private long suggestionMinutes;
    @Value("${queuemate.recruitment.reservation-prompt-minutes:30}") private long reservationPromptMinutes;
    public BoardPolicy(BoardStore store, @Value("${queuemate.recruitment.stale-minutes:12}") long staleMinutes) {
        this.store=store; this.staleMinutes=staleMinutes;
    }
    private record Snapshot(Set<UUID> ids, Map<UUID,BoardStore.Entry> rows, Set<UUID> parents) {}
    @Override public void prepare(Collection<UUID> ids) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        var values = new HashMap<UUID,BoardStore.Entry>();
        store.findAll(ids).forEach(row -> values.put(row.id(),row));
        var snapshot = new Snapshot(Set.copyOf(ids),values,store.parentsWithChildren(ids));
        if (TransactionSynchronizationManager.hasResource(this)) TransactionSynchronizationManager.unbindResource(this);
        else TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(BoardPolicy.this); }
        });
        TransactionSynchronizationManager.bindResource(this,snapshot);
    }
    private Optional<BoardStore.Entry> entry(UUID id) {
        var snapshot = (Snapshot)TransactionSynchronizationManager.getResource(this);
        return snapshot!=null && snapshot.ids().contains(id) ? Optional.ofNullable(snapshot.rows().get(id)) : store.find(id);
    }
    private boolean hasChildren(UUID id) {
        var snapshot = (Snapshot)TransactionSynchronizationManager.getResource(this);
        return snapshot!=null && snapshot.ids().contains(id) ? snapshot.parents().contains(id) : store.hasChildren(id);
    }
    @Override public void lock(GameKey game, String mode) { store.lock(game,mode); }
    public BoardApi.Timing timing(BoardStore.Entry row, OffsetDateTime from, long bumpMinutes) {
        boolean realtime = row.type().equals("REALTIME");
        return new BoardApi.Timing(realtime ? row.confirmedAt().plusMinutes(confirmMinutes) : from.minusMinutes(reservationPromptMinutes),
                realtime ? row.confirmedAt().plusMinutes(staleMinutes) : null,
                realtime ? row.createdAt().plusMinutes(suggestionMinutes) : from.minusMinutes(reservationPromptMinutes),
                (row.bumpedAt()==null ? row.createdAt() : row.bumpedAt()).plusMinutes(bumpMinutes));
    }
    public boolean fresh(BoardStore.Entry row) {
        return !row.closed() && !row.paused() && (!row.type().equals("REALTIME")
                || row.confirmedAt().plusMinutes(staleMinutes).isAfter(OffsetDateTime.now()));
    }
    @Override public boolean automatic(UUID id) {
        return entry(id).map(row -> fresh(row) && row.autoMatch()
                && row.parentId()==null && row.requestedParentId()==null && !hasChildren(id)).orElse(true);
    }
    @Override public boolean compatible(UUID a, MatchCondition ca, UUID b, MatchCondition cb) {
        var pa=entry(a).map(BoardStore.Entry::preferences).orElse(BoardPreferences.ANY);
        var pb=entry(b).map(BoardStore.Entry::preferences).orElse(BoardPreferences.ANY);
        return BoardPreferences.mutual(pa,ca,pb,cb);
    }
}
