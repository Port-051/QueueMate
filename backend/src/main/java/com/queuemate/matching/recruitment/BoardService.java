package com.queuemate.matching.recruitment;

import com.queuemate.common.api.MatchConditionRequest;
import com.queuemate.common.domain.*;
import com.queuemate.common.error.*;
import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.app.*;
import com.queuemate.matching.domain.*;
import com.queuemate.matching.infra.*;
import com.queuemate.reservation.app.*;
import com.queuemate.reservation.domain.*;
import com.queuemate.reservation.infra.ReservationRepository;
import com.queuemate.realtime.event.*;
import com.queuemate.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static com.queuemate.matching.recruitment.BoardApi.*;

@Service
@Transactional
public class BoardService {
    private final BoardStore store;
    private final BoardPolicy policy;
    private final MatchRequestService realtime;
    private final ReservationService reservation;
    private final MatchRequestRepository requests;
    private final ReservationRepository reservations;
    private final MatchConditionCodec codec;
    private final GameModeConfigProvider modes;
    private final BlockLookupPort blocks;
    private final UserRepository users;
    private final RealtimeMatcher realtimeMatcher;
    private final ReservationMatcher reservationMatcher;
    private final MatchQueueRepository queue;
    private final RealtimeEventPublisher events;
    private final long bumpMinutes;
    public BoardService(BoardStore store, BoardPolicy policy, MatchRequestService realtime, ReservationService reservation,
                        MatchRequestRepository requests, ReservationRepository reservations, MatchConditionCodec codec,
                        GameModeConfigProvider modes, BlockLookupPort blocks, UserRepository users,
                        RealtimeMatcher realtimeMatcher, ReservationMatcher reservationMatcher, MatchQueueRepository queue,
                        RealtimeEventPublisher events, @Value("${queuemate.recruitment.bump-minutes:5}") long bumpMinutes) {
        this.store=store;this.policy=policy;this.realtime=realtime;this.reservation=reservation;
        this.requests=requests;this.reservations=reservations;this.codec=codec;this.modes=modes;this.blocks=blocks;
        this.users=users;this.realtimeMatcher=realtimeMatcher;this.reservationMatcher=reservationMatcher;
        this.queue=queue;this.events=events;this.bumpMinutes=bumpMinutes;
    }
    record Source(MatchCondition condition, String status, UUID proposalId, OffsetDateTime from, OffsetDateTime to, PlayAmount amount) {
        boolean waiting() { return status.equals("QUEUED") || status.equals("ACTIVE"); }
    }
    private Source source(BoardStore.Entry row) {
        if (row.type().equals("REALTIME")) {
            var r=requests.findById(row.id()).orElseThrow(BoardService::missing);
            return new Source(codec.fromJson(r.getConditionJson()),r.getStatus().name(),r.getProposalId(),null,null,null);
        }
        var r=reservations.findById(row.id()).orElseThrow(BoardService::missing);
        return new Source(codec.fromJson(r.getConditionJson()),r.getStatus().name(),r.getProposalId(),r.getAvailableFrom(),r.getAvailableTo(),r.getPlayAmount());
    }
    static MatchCondition condition(MatchConditionRequest r) {
        return new MatchCondition(r.game(),r.modeKey(),KeyCondition.of(r.game(),KeyConditionType.valueOf(r.keyCondition().type()),r.keyCondition().value()),r.voicePreference(),r.playPurpose());
    }
    static MatchConditionRequest wire(MatchCondition c) {
        return new MatchConditionRequest(c.game(),c.modeKey(),new MatchConditionRequest.KeyConditionRequest(c.keyCondition().type().name(),c.keyCondition().value()),c.voicePreference(),c.playPurpose());
    }
    private void validate(Write body) {
        var c=condition(body.condition());body.preferences().validate(c);
        modes.findActive(c.game(),c.modeKey()).orElseThrow(() -> new IllegalArgumentException("지원하지 않는 모드입니다"));
        if (body.type().equals("RESERVATION") && (body.availableFrom()==null || body.availableTo()==null || body.playAmount()==null))
            throw new IllegalArgumentException("예약 시간과 플레이 양을 선택해 주세요");
        if (body.type().equals("REALTIME") && (body.availableFrom()!=null || body.availableTo()!=null || body.playAmount()!=null))
            throw new IllegalArgumentException("실시간 모집에는 예약 시간을 넣을 수 없습니다");
    }
    public Row create(UUID user, Write body) {
        validate(body);var c=condition(body.condition());policy.lock(c.game(),c.modeKey());
        if (body.type().equals("RESERVATION") && !body.availableFrom().isAfter(OffsetDateTime.now()))
            throw new IllegalArgumentException("예약 시작 시각은 현재 이후여야 합니다");
        UUID id=body.type().equals("REALTIME") ? realtime.start(user,c).getId()
                : reservation.create(user,c,body.availableFrom(),body.availableTo(),body.playAmount()).getId();
        store.create(id,user,body.type(),c.game(),c.modeKey(),body.preferences(),body.description().trim(),body.autoMatch(),OffsetDateTime.now());
        syncQueue(entry(id));changed();return view(entry(id),user);
    }
    public Row edit(UUID user, UUID id, Write body) {
        validate(body);var row=lockedOwned(user,id);version(row,body.version());editable(row);
        if (!row.type().equals(body.type()) || row.game()!=body.condition().game() || !row.modeKey().equals(body.condition().modeKey()))
            throw conflict("게임·모드·모집 종류 변경은 현재 모집을 종료한 뒤 해 주세요");
        if (row.parentId()!=null || row.requestedParentId()!=null || store.hasChildren(id)) throw conflict("참여 중인 모집에서 나간 후 조건을 수정해 주세요");
        releaseChildren(id); // 신청 시점의 조건이 바뀌면 이전 신청은 다시 확인한다.
        var c=condition(body.condition());
        if (!c.equals(source(row).condition()) || !body.preferences().equals(row.preferences())) store.event(id,"CONDITIONS_CHANGED");
        if (row.type().equals("REALTIME")) realtime.edit(user,id,c);
        else reservation.edit(user,id,c,body.availableFrom(),body.availableTo(),body.playAmount());
        store.edit(id,body.preferences(),body.description().trim(),body.autoMatch(),OffsetDateTime.now());
        syncQueue(entry(id));changed();return view(entry(id),user);
    }
    public Row action(UUID user, UUID id, Action body) {
        var row=lockedOwned(user,id);version(row,body.version());editable(row);
        String action=body.action();
        if (action.equals("LEAVE")) { store.detach(id);store.event(id,"LEFT"); }
        else {
            if (row.parentId()!=null || row.requestedParentId()!=null) {
                if (!List.of("CLOSE", "PAUSE", "CONFIRM").contains(action))
                    throw conflict("참여 중인 모집에서는 활동 확인이나 나가기를 선택해 주세요");
            }
            if (action.equals("BUMP")) {
                OffsetDateTime last=row.bumpedAt()==null?row.createdAt():row.bumpedAt();
                if (last.plusMinutes(bumpMinutes).isAfter(OffsetDateTime.now())) throw new TooManyRequestsException("RECRUITMENT_BUMP_COOLDOWN","위로 올리기는 "+bumpMinutes+"분마다 할 수 있습니다");
            }
            if ((action.equals("PAUSE") || action.equals("CLOSE")) && row.parentId()!=null) store.detach(id);
            if (action.equals("PAUSE") || action.equals("CLOSE")) releaseChildren(id);
            if (action.equals("CLOSE")) {
                if (row.type().equals("REALTIME")) realtime.cancel(user,id); else reservation.cancel(user,id);
            }
            store.action(id,action,OffsetDateTime.now());
        }
        syncQueue(entry(id));changed();return view(entry(id),user);
    }
    public Row join(UUID user, UUID hostId, UUID ownId) {
        var host=entry(hostId);policy.lock(host.game(),host.modeKey());host=entry(hostId);
        var own=owned(user,ownId);editable(own);
        if (host.userId().equals(user) || !publicRow(host) || !publicRow(own) || store.hasChildren(ownId)) throw conflict("현재 참여할 수 없는 모집입니다");
        if (!host.type().equals(own.type()) || !fitsGroup(own,host)) throw conflict("서로의 조건이 맞지 않거나 모집 상태가 바뀌었습니다");
        store.request(ownId,hostId);syncQueue(entry(ownId));changed();return view(entry(hostId),user);
    }
    public Row respond(UUID user, UUID hostId, Respond body) {
        var host=lockedOwned(user,hostId);editable(host);
        var own=entry(body.applicantId());
        if (!hostId.equals(own.requestedParentId())) throw conflict("이미 처리된 신청입니다");
        if (!body.accept()) { store.detach(own.id());store.event(own.id(),"REJECTED");syncQueue(entry(own.id())); }
        else {
            if (!publicRow(host) || !available(own) || !fitsGroup(own,host)) throw conflict("모집 상태나 조건이 바뀌었습니다");
            store.join(own.id(),hostId);syncQueue(entry(hostId));
            var group=group(host);
            if (group.size()==target(host)) {
                if (propose(group).isEmpty()) throw conflict("다른 매칭이 먼저 진행되었습니다. 다시 확인해 주세요");
                // 정원이 찼을 때 남은 신청은 독립된 모집으로 돌아간다.
                for (var pending:store.applicants(hostId)) {store.detach(pending.id());syncQueue(entry(pending.id()));}
            }
        }
        changed();return view(entry(hostId),user);
    }
    public Page search(UUID user, Search query) {
        if (query.pageSize()!=5 && query.pageSize()!=10) throw new IllegalArgumentException("한 페이지에 5개 또는 10개를 볼 수 있습니다");
        var c=condition(query.condition());query.preferences().validate(c);
        if (query.type().equals("RESERVATION")) validateWindow(query.availableFrom(),query.availableTo(),query.playAmount());
        var matches=matching(user,query.type(),c,query.preferences(),query.availableFrom(),query.availableTo(),query.playAmount());
        Comparator<BoardStore.Entry> order=Comparator.comparing((BoardStore.Entry e)->e.bumpedAt()==null?e.createdAt():e.bumpedAt()).reversed().thenComparing(BoardStore.Entry::id);
        if (!"RECENT".equals(query.sort())) order=Comparator.comparingInt((BoardStore.Entry e)->preferred(c,source(e).condition())).thenComparingLong(BoardStore.Entry::cycleImpressions).thenComparing(order);
        matches.sort(order);
        int from=Math.min(matches.size(),query.page()*query.pageSize()),to=Math.min(matches.size(),from+query.pageSize());
        return new Page(matches.subList(from,to).stream().map(e->view(e,user)).toList(),matches.size(),query.page(),to<matches.size(),OffsetDateTime.now());
    }
    private int preferred(MatchCondition a, MatchCondition b) {
        return (a.playPurpose()==b.playPurpose()?0:1)+(a.voicePreference()==b.voicePreference()?0:1);
    }
    private List<BoardStore.Entry> matching(UUID user,String type,MatchCondition c,BoardPreferences p,OffsetDateTime from,OffsetDateTime to,PlayAmount amount) {
        List<BoardStore.Entry> result=new ArrayList<>();
        var pool = store.pool(type,c.game(),c.modeKey());
        var sourceIds = pool.stream().map(BoardStore.Entry::id).toList();
        if (type.equals("REALTIME")) requests.findAllById(sourceIds); else reservations.findAllById(sourceIds);
        users.findAllById(pool.stream().map(BoardStore.Entry::userId).distinct().toList());
        for(var row:pool) {
            if(row.userId().equals(user) || !publicRow(row) || group(row).stream().anyMatch(e->e.userId().equals(user))) continue;
            boolean fits=true;var windows=new ArrayList<TimeSlots.Window>();
            if(type.equals("RESERVATION")) windows.add(new TimeSlots.Window(from,to));
            for(var member:group(row)) {
                var s=source(member);
                if(!pair(user,c,p,member.userId(),s.condition(),member.preferences())) {fits=false;break;}
                if(type.equals("RESERVATION")) {
                    if(amount!=s.amount()) {fits=false;break;}
                    windows.add(new TimeSlots.Window(s.from(),s.to()));
                }
            }
            if(fits && (windows.isEmpty() || TimeSlots.earliestCommonSlot(windows,OffsetDateTime.now()).isPresent())) result.add(row);
        }
        return result;
    }
    private boolean pair(UUID user,MatchCondition c,BoardPreferences p,UUID other,MatchCondition oc,BoardPreferences op) {
        if(user.equals(other) || blocks.anyBlockBetween(List.of(user,other))) return false;
        var config=modes.findActive(c.game(),c.modeKey());
        return config.isPresent() && ConditionCompatibility.between(c,oc,config.get()).isPresent() && BoardPreferences.mutual(p,c,op,oc);
    }
    private boolean fitsGroup(BoardStore.Entry own,BoardStore.Entry host) {
        if(!own.type().equals(host.type()) || group(host).size()>=target(host)) return false;
        var all=new ArrayList<>(group(host));all.add(own);
        var os=source(own);
        for(var other:group(host)) {
            var s=source(other);
            if(!available(other) || !pair(own.userId(),os.condition(),own.preferences(),other.userId(),s.condition(),other.preferences())) return false;
            if(own.type().equals("RESERVATION") && os.amount()!=s.amount()) return false;
        }
        return !own.type().equals("RESERVATION") || TimeSlots.earliestCommonSlot(all.stream().map(e->{var s=source(e);return new TimeSlots.Window(s.from(),s.to());}).toList(),OffsetDateTime.now()).isPresent();
    }
    public Suggestions suggestions(UUID user,UUID id) {
        var row=owned(user,id);editable(row);var s=source(row);var p=row.preferences();var c=s.condition();
        var current=matching(user,row.type(),c,p,s.from(),s.to(),s.amount());
        var suggestions=new ArrayList<Suggestion>();
        if (!p.desiredKeys().isEmpty()) suggest(suggestions,"desiredKeys","상대 포지션을 넓히면",row,c,new BoardPreferences(p.ownTier(),p.minTier(),p.maxTier(),List.of(),p.purposeRequired()),current);
        if (p.minTier()!=null || p.maxTier()!=null) suggest(suggestions,"tierRange","상대 티어 범위를 넓히면",row,c,new BoardPreferences(p.ownTier(),null,null,p.desiredKeys(),p.purposeRequired()),current);
        if (c.voicePreference()!=VoicePreference.OPTIONAL) suggest(suggestions,"voicePreference","음성 조건을 무관으로 바꾸면",row,new MatchCondition(c.game(),c.modeKey(),c.keyCondition(),VoicePreference.OPTIONAL,c.playPurpose()),p,current);
        if (p.purposeRequired()) suggest(suggestions,"purposeRequired","플레이 목적을 선호 조건으로 바꾸면",row,c,new BoardPreferences(p.ownTier(),p.minTier(),p.maxTier(),p.desiredKeys(),false),current);
        suggestions.sort(Comparator.comparingInt(Suggestion::candidateCount).reversed());
        if (!suggestions.isEmpty()) store.event(id,"SUGGESTED");
        return new Suggestions(current.size(),suggestions,OffsetDateTime.now());
    }
    private void suggest(List<Suggestion> out,String field,String label,BoardStore.Entry row,MatchCondition c,BoardPreferences p,List<BoardStore.Entry> current) {
        var s=source(row);Set<UUID> already=new HashSet<>();current.forEach(e->already.add(e.id()));
        var candidates=matching(row.userId(),row.type(),c,p,s.from(),s.to(),s.amount()).stream().filter(e->!already.contains(e.id())).toList();
        if(!candidates.isEmpty()) out.add(new Suggestion(field,label,wire(c),p,candidates.size(),candidates.stream().limit(3).map(e->view(e,row.userId())).toList()));
    }
    public List<Row> mine(UUID user) { return store.mine(user).stream().map(e->view(e,user)).toList(); }
    public Row get(UUID user,UUID id) {
        var row=entry(id);
        var members=group(row);
        boolean related=row.userId().equals(user) || members.stream().anyMatch(e->e.userId().equals(user)) || store.applicants(id).stream().anyMatch(e->e.userId().equals(user));
        if(!related && (!publicRow(row) || members.stream().anyMatch(e->blocks.anyBlockBetween(List.of(user,e.userId()))))) throw missing();
        return view(row,user);
    }
    public void impressions(UUID user,List<UUID> ids) {
        for(UUID id:new HashSet<>(ids)) {
            var row=store.find(id).orElse(null);
            if(row!=null && !row.userId().equals(user) && publicRow(row)
                    && group(row).stream().noneMatch(e->blocks.anyBlockBetween(List.of(user,e.userId())))) store.impression(id,user);
        }
    }
    private boolean available(BoardStore.Entry row) {
        var s=source(row);return policy.fresh(row) && s.waiting() && (s.to()==null || s.to().isAfter(OffsetDateTime.now()));
    }
    private boolean publicRow(BoardStore.Entry row) {
        return row.parentId()==null && row.requestedParentId()==null && available(row)
                && group(row).size()<target(row) && group(row).stream().allMatch(this::available);
    }
    private List<BoardStore.Entry> group(BoardStore.Entry root) {var list=new ArrayList<BoardStore.Entry>();list.add(root);list.addAll(store.children(root.id()));return list;}
    private int target(BoardStore.Entry row) {return modes.findActive(row.game(),row.modeKey()).orElseThrow(BoardService::missing).targetPartySize();}
    private Person person(BoardStore.Entry row) {return new Person(row.id(),row.userId(),nickname(row.userId()),wire(source(row).condition()),row.preferences());}
    private String nickname(UUID user) {return users.findById(user).map(u->u.getNickname()).orElse("탈퇴한 사용자");}
    private Row view(BoardStore.Entry row,UUID viewer) {
        var s=source(row);String status;
        if(s.status().equals("PROPOSED") || s.status().equals("MATCHED")) status=s.status();
        else if(row.closed() || !s.waiting() || (s.to()!=null && !s.to().isAfter(OffsetDateTime.now()))) status="CLOSED";
        else if(row.paused()) status="PAUSED";
        else if(!policy.fresh(row)) status="STALE";
        else if(row.parentId()!=null) status="JOINED";
        else if(row.requestedParentId()!=null) status="REQUESTED";
        else status="OPEN";
        boolean owner=row.userId().equals(viewer);
        return new Row(row.id(),row.userId(),nickname(row.userId()),row.type(),wire(s.condition()),row.preferences(),row.description(),row.autoMatch(),
                s.from(),s.to(),s.amount(),status,row.createdAt(),row.confirmedAt(),row.bumpedAt(),row.parentId(),row.requestedParentId(),s.proposalId(),row.version(),target(row),
                (row.parentId()==null?group(row):group(entry(row.parentId()))).stream().map(this::person).toList(),owner?store.applicants(row.id()).stream().filter(this::available).map(this::person).toList():List.of(),owner?row.impressions():0,owner&&row.alertEnabled(),policy.timing(row,s.from(),bumpMinutes));
    }
    private BoardStore.Entry entry(UUID id) {return store.find(id).orElseThrow(BoardService::missing);}
    private BoardStore.Entry owned(UUID user,UUID id) {var row=entry(id);if(!row.userId().equals(user)) throw missing();return row;}
    private BoardStore.Entry lockedOwned(UUID user,UUID id) {var row=owned(user,id);policy.lock(row.game(),row.modeKey());return owned(user,id);}
    private void editable(BoardStore.Entry row) {if(row.closed() || !source(row).waiting()) throw conflict("진행 중인 제안이나 종료된 모집은 바꿀 수 없습니다");}
    private void version(BoardStore.Entry row,Long version) {if(version==null || row.version()!=version) throw conflict("다른 화면에서 모집이 변경되었습니다. 최신 상태를 확인해 주세요");}
    private static ConflictException conflict(String message) {return new ConflictException("RECRUITMENT_CONFLICT",message);}
    private static NotFoundException missing() {return new NotFoundException("RECRUITMENT_NOT_FOUND","모집을 찾을 수 없습니다");}
    private void validateWindow(OffsetDateTime from,OffsetDateTime to,PlayAmount amount) {
        if(from==null || to==null || amount==null || !from.isBefore(to) || !TimeSlots.isAligned(from) || !TimeSlots.isAligned(to)) throw new IllegalArgumentException("30분 단위의 예약 시간과 플레이 양을 선택해 주세요");
    }
    private Optional<UUID> propose(List<BoardStore.Entry> group) {
        var ids=group.stream().map(BoardStore.Entry::id).toList();
        return group.getFirst().type().equals("REALTIME")?realtimeMatcher.proposeSelected(ids):reservationMatcher.proposeSelected(ids);
    }
    private void releaseChildren(UUID id) {
        var released = new ArrayList<>(store.children(id));
        released.addAll(store.applicants(id));
        store.releaseChildren(id);
        released.forEach(row -> syncQueue(entry(row.id())));
    }
    private void syncQueue(BoardStore.Entry row) {
        if(!row.type().equals("REALTIME")) return;
        var s=source(row);var bucket=MatchBucket.of(s.condition());
        boolean eligible=s.waiting() && policy.automatic(row.id());
        AfterCommit.run(()->{if(eligible) queue.requeue(MatchingRedisKeys.queue(bucket),row.id(),row.createdAt().toInstant());else queue.removeStale(bucket,List.of(row.id()));});
    }
    private void changed() {events.publishAfterCommit(List.of(),ServerEvent.of(EventType.RECRUITMENT_UPDATED,Map.of()));}
    /** 자동 찾기를 켠 모집 그룹의 빈자리도 동일한 공개 모집 풀에서 채운다. */
    public void fillGroup(UUID id) {
        var root=entry(id);policy.lock(root.game(),root.modeKey());root=entry(id);
        if(!root.autoMatch() || !publicRow(root) || !store.hasChildren(id)) return;
        for(var candidate:store.pool(root.type(),root.game(),root.modeKey())) {
            if(candidate.id().equals(id) || !candidate.autoMatch() || !publicRow(candidate) || store.hasChildren(candidate.id()) || !fitsGroup(candidate,root)) continue;
            // 정원이 채워지는 조합일 때만 동의한 자동 후보를 그룹에 넣는다.
            var chosen=new ArrayList<>(group(root));chosen.add(candidate);
            for(var extra:store.pool(root.type(),root.game(),root.modeKey())) {
                if(chosen.size()>=target(root)) break;
                if(!extra.autoMatch() || !publicRow(extra) || store.hasChildren(extra.id()) || chosen.stream().anyMatch(e->e.userId().equals(extra.userId()))) continue;
                if(compatibleGroup(chosen,extra)) chosen.add(extra);
            }
            if(chosen.size()==target(root) && propose(chosen).isPresent()) {changed();return;}
        }
    }
    private boolean compatibleGroup(List<BoardStore.Entry> group,BoardStore.Entry extra) {
        var s=source(extra);
        for(var member:group) {var m=source(member);if(!pair(extra.userId(),s.condition(),extra.preferences(),member.userId(),m.condition(),member.preferences()) || s.amount()!=m.amount()) return false;}
        var all=new ArrayList<>(group);all.add(extra);
        return !extra.type().equals("RESERVATION") || TimeSlots.earliestCommonSlot(all.stream().map(e->{var a=source(e);return new TimeSlots.Window(a.from(),a.to());}).toList(),OffsetDateTime.now()).isPresent();
    }
}
