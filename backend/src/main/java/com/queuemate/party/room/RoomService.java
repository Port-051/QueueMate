package com.queuemate.party.room;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.error.*;
import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.common.ratelimit.RateLimiter;
import com.queuemate.realtime.event.*;
import com.queuemate.user.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.queuemate.party.room.RoomApi.*;

@Service
@Transactional
@ConditionalOnProperty(name="queuemate.rooms.enabled", havingValue="true")
public class RoomService {
    private final JdbcClient db;
    private final ObjectMapper json;
    private final UserRepository users;
    private final GameAccountRepository accounts;
    private final BlockLookupPort blocks;
    private final RealtimeEventPublisher events;
    private final StringRedisTemplate redis;
    private final RateLimiter limiter;
    private final MeterRegistry metrics;
    public RoomService(JdbcClient db,ObjectMapper json,UserRepository users,GameAccountRepository accounts,
                       BlockLookupPort blocks,RealtimeEventPublisher events,StringRedisTemplate redis,RateLimiter limiter,MeterRegistry metrics) {
        this.db=db;this.json=json;this.users=users;this.accounts=accounts;this.blocks=blocks;this.events=events;this.redis=redis;this.limiter=limiter;this.metrics=metrics;
    }
    private record Row(UUID id,UUID owner,Settings settings,String status,long created) {}
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private <T> T decode(String value,Class<T> type) { try { return json.readValue(value,type); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> object(String value) { try { return json.readValue(value,new TypeReference<>() {}); } catch(Exception e) { throw new IllegalStateException(e); } }
    private List<Row> rows(String predicate,Object... args) {
        return db.sql("select * from rooms "+predicate).params(args).query((rs,n)->new Row(rs.getObject("id",UUID.class),rs.getObject("owner_id",UUID.class),decode(rs.getString("settings"),Settings.class),rs.getString("status"),rs.getLong("created_at"))).list();
    }
    private Row room(UUID id) { return rows("where id=?",id).stream().findFirst().orElseThrow(RoomService::missing); }
    private static NotFoundException missing() { return new NotFoundException("ROOM_NOT_FOUND","방에 접근할 수 없어요"); }
    private static ConflictException conflict(String message) { return new ConflictException("ROOM_CONFLICT",message); }
    // Small opt-in pilot: serialize mutations across rooms so transfer, join and kick have one lock order.
    private void lock() { db.sql("select pg_advisory_xact_lock(71027001)").query().listOfRows(); }
    private void requireRedis() {
        try { redis.hasKey("qm:room-pilot:health"); }
        catch (org.springframework.dao.DataAccessException e) { throw new ServiceUnavailableException("ROOM_SERVICE_UNAVAILABLE","매칭 서버에 연결할 수 없어요",e); }
    }
    private UUID active(UUID user) { return db.sql("select room_id from room_members where user_id=?").param(user).query(UUID.class).optional().orElse(null); }
    private List<Map<String,Object>> members(UUID id) { return db.sql("select profile::text from room_members where room_id=? order by joined_at,user_id").param(id).query(String.class).list().stream().map(this::object).toList(); }
    private List<UUID> memberIds(UUID id) { return db.sql("select user_id from room_members where room_id=?").param(id).query(UUID.class).list(); }
    private void requireMember(UUID roomId,UUID user) { if (!roomId.equals(active(user))) throw missing(); }
    private void changed() { events.publishAfterCommit(List.of(),ServerEvent.of(EventType.ROOMS_UPDATED,Map.of())); }
    private Map<String,Object> view(Row row,UUID viewer) {
        var result=object(encode(row.settings()));result.put("id",row.id());result.put("ownerId",row.owner());result.put("status",row.status());result.put("createdAt",row.created());result.put("autoCloseAt",null);
        var roster=members(row.id());result.put("members",roster);
        result.put("messages",roster.stream().anyMatch(m->viewer.toString().equals(m.get("id"))) ? messages(row.id()) : List.of());return result;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> list(UUID user) { return rows("order by created_at desc,id").stream().map(r->view(r,user)).toList(); }
    private Map<String,Object> profile(UUID user,Settings s,Profile p,String selectedRole) {
        var u=users.findById(user).orElseThrow(RoomService::missing);
        var account=accounts.findAllByUserId(user).stream().filter(a->a.getProviderGame()==s.game()).findFirst().orElse(null);
        var rank=account==null ? null : s.modeKey().equals("FLEX_RANKED") ? account.getFlexRankCode() : account.getRankCode();
        String tier=null;Integer division=null;
        if (rank!=null) {
            var parts=rank.split("[_ ]");if(RoomRules.tiers(s.game()).contains(parts[0])) tier=parts[0];
            if(parts.length>1) division=switch(parts[1]) {case "I","1"->1;case "II","2"->2;case "III","3"->3;case "IV","4"->4;case "5"->5;default->null;};
        }
        var result=new LinkedHashMap<String,Object>();result.put("id",user.toString());result.put("nickname",u.getNickname());result.put("avatarUrl",u.getAvatarUrl());
        result.put("tier",tier);result.put("division",division);result.put("winRate",null);result.put("kda",null);result.put("champions",List.of());
        result.put("roles",RoomRules.noRoles(s) ? List.of() : selectedRole==null ? RoomRules.canonical(s.game(),p.roles()) : List.of(selectedRole));
        result.put("bio",p.bio().trim());result.put("voice",s.voice());return result;
    }
    private void add(UUID id,UUID user,Map<String,Object> profile) {
        db.sql("insert into room_members(room_id,user_id,profile,joined_at) values(?,?,?::jsonb,?)").params(id,user,encode(profile),System.currentTimeMillis()).update();
    }
    public Map<String,Object> create(UUID user,Create body) {
        lock();requireRedis();
        var old=rows("where id=?",body.requestId());if(!old.isEmpty()) { if(!old.getFirst().owner().equals(user)) throw conflict("방 ID가 이미 사용 중이에요");return view(old.getFirst(),user); }
        if(active(user)!=null) throw conflict("참여 중인 방에서 먼저 나와 주세요");
        RoomRules.validate(body.input(),body.profile());
        db.sql("insert into rooms(id,owner_id,settings,created_at) values(?,?,?::jsonb,?)").params(body.requestId(),user,encode(body.input()),System.currentTimeMillis()).update();
        add(body.requestId(),user,profile(user,body.input(),body.profile(),null));system(body.requestId(),"방이 열렸어요. 먼저 인사해 보세요.");
        changed();metrics.counter("queuemate.room.actions","action","create").increment();return view(room(body.requestId()),user);
    }
    public Map<String,Object> join(UUID user,UUID id,Join body) {
        lock();requireRedis();var r=room(id);var current=active(user);
        if(id.equals(current)) return view(r,user);
        if(!Objects.equals(current,body.fromRoomId())) throw conflict("참여 중인 방이 바뀌었어요. 다시 확인해 주세요");
        var members=members(id);var s=r.settings();
        if(!r.status().equals("OPEN") || members.size()>=s.capacity()) throw conflict("이미 모집이 마감된 방이에요");
        if(s.availableFrom()!=null && !OffsetDateTime.parse(s.availableFrom()).isAfter(OffsetDateTime.now())) throw conflict("예약 시간이 지난 방이에요");
        RoomRules.canonical(s.game(),body.profile().roles());
        if(!RoomRules.noRoles(s)) {
            var desired=RoomRules.canonical(s.game(),s.desiredRoles());var allowed=new ArrayList<>(desired.isEmpty()?RoomRules.roles(s.game()):desired);
            if(s.game()==com.queuemate.common.domain.GameKey.LOL) for(var m:members) {
                if(s.capacity()<5 && r.owner().toString().equals(m.get("id"))) continue;
                var occupied=(List<?>)m.get("roles");if(occupied.size()==1) allowed.remove(occupied.getFirst());
            }
            if(body.role()==null || !allowed.contains(body.role())) throw conflict("선택한 포지션은 더 이상 모집하지 않아요");
        }
        var ids=new ArrayList<>(memberIds(id));ids.add(user);if(blocks.anyBlockBetween(ids)) throw conflict("이 방에는 참여할 수 없어요");
        var profile=profile(user,s,body.profile(),body.role());
        if(!RoomRules.tierFits(s,(String)profile.get("tier"))) throw conflict("방에서 찾는 티어 범위와 맞지 않아요");
        if(current!=null) depart(room(current),user,false);
        add(id,user,profile);system(id,profile.get("nickname")+" 님이 들어왔어요.");
        if(members.size()+1==s.capacity()) { db.sql("update rooms set status='CONFIRMED' where id=?").param(id).update();system(id,"정원이 모두 차서 모집을 마감했어요."); }
        changed();metrics.counter("queuemate.room.actions","action","join").increment();return view(room(id),user);
    }
    private void depart(Row r,UUID user,boolean kicked) {
        db.sql("delete from room_members where room_id=? and user_id=?").params(r.id(),user).update();var remaining=memberIds(r.id());
        if(remaining.isEmpty()) { db.sql("delete from rooms where id=?").param(r.id()).update();return; }
        if(r.owner().equals(user)) db.sql("update rooms set owner_id=? where id=?").params(remaining.getFirst(),r.id()).update();
        system(r.id(),users.findById(user).map(u->u.getNickname()).orElse("참여자")+(kicked?" 님을 내보냈어요.":" 님이 나갔어요."));
    }
    public void action(UUID user,UUID id,Action body) {
        lock();var r=room(id);requireMember(id,user);
        if(body.action().equals("LEAVE")) depart(r,user,false);
        else {
            if(!r.owner().equals(user)) throw missing();
            if(body.action().equals("KICK")) {
                if(user.equals(body.memberId()) || body.memberId()==null) throw conflict("방장은 나가기 버튼을 이용해 주세요");
                requireMember(id,body.memberId());depart(r,body.memberId(),true);
            } else if(body.action().equals("REOPEN")) {
                if(r.status().equals("CONFIRMED")) {
                    requireRedis();
                    if(memberIds(id).size()>=r.settings().capacity()) throw conflict("빈자리가 생기면 모집을 다시 열 수 있어요");
                    if(r.settings().availableFrom()!=null && !OffsetDateTime.parse(r.settings().availableFrom()).isAfter(OffsetDateTime.now())) throw conflict("예약 시간이 지난 방은 다시 열 수 없어요");
                    db.sql("update rooms set status='OPEN' where id=?").param(id).update();
                    system(id,"방장이 모집을 다시 열었어요.");
                }
            } else {
                if(r.status().equals("OPEN")) { db.sql("update rooms set status='CONFIRMED' where id=?").param(id).update();system(id,"방장이 모집을 마감했어요. 대화는 계속할 수 있어요."); }
            }
        }
        changed();metrics.counter("queuemate.room.actions","action",body.action().toLowerCase()).increment();
    }
    private Map<String,Object> messageRow(UUID id,UUID author,String text,long at) {
        var m=new LinkedHashMap<String,Object>();m.put("id",id);m.put("authorId",author);m.put("text",text);m.put("createdAt",at);return m;
    }
    private List<Map<String,Object>> messages(UUID id) {
        return db.sql("select * from (select * from room_messages where room_id=? order by created_at desc,id desc limit 200) m order by created_at,id").param(id)
            .query((rs,n)->messageRow(rs.getObject("id",UUID.class),rs.getObject("author_id",UUID.class),rs.getString("body"),rs.getLong("created_at"))).list();
    }
    private void system(UUID id,String text) { insertMessage(id,null,UUID.randomUUID(),text); }
    private Map<String,Object> insertMessage(UUID room,UUID author,UUID clientId,String text) {
        UUID id=UUID.randomUUID();long at=System.currentTimeMillis();
        db.sql("insert into room_messages(id,room_id,author_id,client_message_id,body,created_at) values(?,?,?,?,?,?)").params(id,room,author,clientId,text,at).update();return messageRow(id,author,text,at);
    }
    public Map<String,Object> send(UUID user,UUID id,Message body) {
        lock();requireMember(id,user);
        var old=db.sql("select * from room_messages where room_id=? and author_id=? and client_message_id=?").params(id,user,body.clientMessageId())
            .query((rs,n)->messageRow(rs.getObject("id",UUID.class),user,rs.getString("body"),rs.getLong("created_at"))).optional();
        if(old.isPresent()) return old.get();
        if(!limiter.tryAcquire("room-chat",user.toString(),30,Duration.ofSeconds(10),RateLimiter.OnUnavailable.REJECT)) throw new TooManyRequestsException("ROOM_MESSAGE_RATE_LIMIT","잠시 후 다시 보내 주세요");
        var message=insertMessage(id,user,body.clientMessageId(),body.text().trim());
        events.publishAfterCommit(memberIds(id),ServerEvent.of(EventType.ROOM_MESSAGES_UPDATED,Map.of("roomId",id)));
        metrics.counter("queuemate.room.actions","action","message").increment();return message;
    }
}
