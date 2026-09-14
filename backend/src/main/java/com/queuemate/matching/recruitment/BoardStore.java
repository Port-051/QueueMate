package com.queuemate.matching.recruitment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.domain.GameKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class BoardStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public BoardStore(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }
    public record Entry(UUID id, UUID userId, String type, GameKey game, String modeKey,
                        BoardPreferences preferences, String description, boolean autoMatch,
                        boolean paused, boolean closed, OffsetDateTime createdAt, OffsetDateTime confirmedAt,
                        OffsetDateTime bumpedAt, UUID parentId, UUID requestedParentId,
                        long version, long impressions, long cycleImpressions, boolean alertEnabled) {}
    private final RowMapper<Entry> mapper = (r, i) -> new Entry(r.getObject("id", UUID.class),
            r.getObject("user_id", UUID.class), r.getString("type"), GameKey.valueOf(r.getString("game")),
            r.getString("mode_key"), read(r.getString("preferences")), r.getString("description"),
            r.getBoolean("auto_match"), r.getBoolean("paused"), r.getBoolean("closed"),
            r.getObject("created_at", OffsetDateTime.class), r.getObject("confirmed_at", OffsetDateTime.class),
            r.getObject("bumped_at", OffsetDateTime.class), r.getObject("parent_id", UUID.class),
            r.getObject("requested_parent_id", UUID.class), r.getLong("version"), r.getLong("impressions"), r.getLong("impressions")-r.getLong("impression_baseline"), r.getBoolean("alert_enabled"));
    public Optional<Entry> find(UUID id) { return db.query("select * from recruitments where id=?", mapper, id).stream().findFirst(); }
    public List<Entry> mine(UUID user) { return db.query("select * from recruitments where user_id=? order by created_at desc limit 100", mapper, user); }
    public List<Entry> pool(String type, GameKey game, String mode) {
        return db.query("""
            select r.* from recruitments r
            left join match_requests m on r.type='REALTIME' and m.id=r.id
            left join reservations s on r.type='RESERVATION' and s.id=r.id
            where r.type=? and r.game=? and r.mode_key=? and not r.closed
            and (m.status='QUEUED' or (s.status='ACTIVE' and s.available_to > CURRENT_TIMESTAMP))
            order by r.created_at, r.id
            """, mapper, type, game.name(), mode);
    }
    public List<Entry> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return db.query("select * from recruitments where id in ("+String.join(",", Collections.nCopies(ids.size(),"?"))+")", mapper, ids.toArray());
    }
    public Set<UUID> parentsWithChildren(Collection<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(db.queryForList("select distinct parent_id from recruitments where not closed and parent_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",UUID.class,ids.toArray()));
    }
    public List<Entry> children(UUID id) { return db.query("select * from recruitments where parent_id=? and not closed order by created_at, id", mapper, id); }
    public List<Entry> applicants(UUID id) { return db.query("select * from recruitments where requested_parent_id=? and not closed order by created_at, id", mapper, id); }
    public boolean hasChildren(UUID id) { return !children(id).isEmpty(); }
    public void create(UUID id, UUID user, String type, GameKey game, String mode, BoardPreferences prefs, String text, boolean auto, OffsetDateTime now) {
        db.update("insert into recruitments(id,user_id,type,game,mode_key,preferences,description,auto_match,created_at,confirmed_at) values(?,?,?,?,?,?::jsonb,?,?,?,?)",
                id,user,type,game.name(),mode,write(prefs),text,auto,now,now);
        event(id,"CREATED");
    }
    public void edit(UUID id, BoardPreferences prefs, String text, boolean auto, OffsetDateTime now) {
        db.update("update recruitments set preferences=?::jsonb, description=?, auto_match=?, confirmed_at=?, version=version+1 where id=?", write(prefs),text,auto,now,id);
        event(id,"EDITED");
    }
    public void action(UUID id, String action, OffsetDateTime now) {
        String assignment = switch (action) {
            case "CONFIRM", "RESUME" -> "confirmed_at=?, paused=false";
            case "BUMP" -> "confirmed_at=?, bumped_at=?, impression_baseline=impressions";
            case "PAUSE" -> "paused=true";
            case "CLOSE" -> "closed=true";
            case "AUTO_ON" -> "auto_match=true";
            case "AUTO_OFF" -> "auto_match=false";
            case "ALERT_ON" -> "alert_enabled=true";
            case "ALERT_OFF" -> "alert_enabled=false";
            default -> throw new IllegalArgumentException("지원하지 않는 모집 동작입니다");
        };
        List<Object> args = new ArrayList<>();
        if (action.equals("CONFIRM") || action.equals("RESUME") || action.equals("BUMP")) args.add(now);
        if (action.equals("BUMP")) args.add(now);
        args.add(id);
        db.update("update recruitments set " + assignment + ", version=version+1 where id=?", args.toArray());
        event(id,action);
    }
    public void request(UUID id, UUID parent) {
        db.update("update recruitments set requested_parent_id=?, version=version+1 where id=?",parent,id);
        event(id,"APPLIED");
    }
    public void join(UUID id, UUID parent) {
        db.update("update recruitments set parent_id=?, requested_parent_id=null, version=version+1 where id=?",parent,id);
        event(id,"JOINED");
    }
    public void detach(UUID id) { db.update("update recruitments set parent_id=null, requested_parent_id=null, version=version+1 where id=?",id); }
    public void releaseChildren(UUID id) {
        db.update("update recruitments set parent_id=null, requested_parent_id=null, version=version+1 where parent_id=? or requested_parent_id=?",id,id);
    }
    public void event(UUID id, String name) { db.update("insert into recruitment_events(recruitment_id,event) values(?,?)",id,name); }
    public void impression(UUID id, UUID viewer) {
        int added = db.update("insert into recruitment_impressions(recruitment_id,viewer_id) values(?,?) on conflict do nothing",id,viewer);
        if (added > 0) { db.update("update recruitments set impressions=impressions+1, last_exposed_at=CURRENT_TIMESTAMP where id=?",id); event(id,"IMPRESSION"); }
    }
    public void lock(GameKey game, String mode) {
        // 모드별로 선발과 모집 수정을 직렬화한다. 다른 게임/모드는 동시에 처리한다.
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "qm:pool:"+game+":"+mode);
    }
    private BoardPreferences read(String value) { try { return json.readValue(value, BoardPreferences.class); } catch(Exception e) { throw new IllegalStateException(e); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
}
