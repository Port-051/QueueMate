package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="queuemate.rooms.enabled=true")
class SharedRoomIntegrationTest extends ApiContractTestSupport {
    private String settings(String title,int capacity) {
        return """
          {"game":"LOL","modeKey":"NORMAL_DRAFT","type":"REALTIME","title":"%s","capacity":%d,
           "desiredRoles":["JUNGLE","MID","ADC","SUPPORT"],"voice":"NO_VOICE","availableFrom":null}
          """.formatted(title,capacity);
    }
    private String createBody(UUID key,String title,int capacity) { return "{\"requestId\":\""+key+"\",\"input\":"+settings(title,capacity)+",\"profile\":{\"roles\":[\"TOP\"],\"bio\":\"같이 해요\"}}"; }
    private String create(UUID user,String title,int capacity) { var r=post("/api/v1/rooms",user,createBody(UUID.randomUUID(),title,capacity));assertStatus(r,201);return body(r).path("id").asText(); }
    private String joinBody(String role,String from) { return "{\"role\":\""+role+"\",\"fromRoomId\":"+(from==null?"null":"\""+from+"\"")+",\"profile\":{\"roles\":[\""+role+"\"],\"bio\":\"안녕하세요\"}}"; }
    private JsonNode room(UUID user,String id) { for(var row:body(get("/api/v1/rooms",user))) if(row.path("id").asText().equals(id)) return row;throw new AssertionError("missing room"); }
    @Test void sharedCreationChatPrivacyIdempotencyAndClosedRoomConversation() {
        UUID a=alpha(),b=bravo(),c=charlie();UUID key=UUID.randomUUID();String request=createBody(key,"함께 게임해요",2);
        assertStatus(post("/api/v1/rooms",null,request),401);
        assertStatus(post("/api/v1/rooms",a,request),201);assertStatus(post("/api/v1/rooms",a,request),201);
        assertEquals(1,body(get("/api/v1/rooms",b)).size());assertEquals(0,room(b,key.toString()).path("messages").size());
        assertStatus(post("/api/v1/rooms/"+key+"/join",b,joinBody("SUPPORT",null)),200);
        assertEquals("CONFIRMED",room(a,key.toString()).path("status").asText());
        String message="{\"clientMessageId\":\""+UUID.randomUUID()+"\",\"text\":\"실제 메시지\"}";
        var first=post("/api/v1/rooms/"+key+"/messages",a,message);assertStatus(first,200);
        assertEquals(body(first).path("id"),body(post("/api/v1/rooms/"+key+"/messages",a,message)).path("id"));
        assertTrue(room(b,key.toString()).path("messages").toString().contains("실제 메시지"));
        assertEquals(0,room(c,key.toString()).path("messages").size());
        assertStatus(post("/api/v1/rooms/"+key+"/messages",c,message),404);
        assertStatus(post("/api/v1/rooms/"+key+"/actions",b,"{\"action\":\"KICK\",\"memberId\":\""+a+"\"}"),404);
        assertStatus(post("/api/v1/rooms/"+key+"/actions",a,"{\"action\":\"KICK\",\"memberId\":\""+b+"\"}"),204);
        assertStatus(post("/api/v1/rooms/"+key+"/messages",b,message),404);
        assertEquals(0,room(b,key.toString()).path("messages").size());
    }
    @Test void ownerCanCloseAloneAndReopenWithoutLosingMembersOrChat() {
        UUID a=alpha(),b=bravo();String id=create(a,"모집 관리",3);
        String actions="/api/v1/rooms/"+id+"/actions";
        assertStatus(post(actions,a,"{\"action\":\"CONFIRM\"}"),204);
        assertEquals("CONFIRMED",room(a,id).path("status").asText());
        assertStatus(post("/api/v1/rooms/"+id+"/join",b,joinBody("SUPPORT",null)),409);
        assertStatus(post(actions,b,"{\"action\":\"REOPEN\"}"),404);
        assertStatus(post(actions,a,"{\"action\":\"REOPEN\"}"),204);
        int messages=room(a,id).path("messages").size();
        assertStatus(post(actions,a,"{\"action\":\"REOPEN\"}"),204);
        assertEquals(messages,room(a,id).path("messages").size());
        assertStatus(post("/api/v1/rooms/"+id+"/join",b,joinBody("SUPPORT",null)),200);
        assertStatus(post(actions,b,"{\"action\":\"CONFIRM\"}"),404);
        assertStatus(post(actions,a,"{\"action\":\"CONFIRM\"}"),204);
        assertStatus(post(actions,b,"{\"action\":\"REOPEN\"}"),404);
        String message="{\"clientMessageId\":\""+UUID.randomUUID()+"\",\"text\":\"마감 중에도 대화\"}";
        assertStatus(post("/api/v1/rooms/"+id+"/messages",b,message),200);
        assertStatus(post(actions,a,"{\"action\":\"REOPEN\"}"),204);
        assertEquals(2,room(a,id).path("members").size());
        assertTrue(room(a,id).path("messages").toString().contains("마감 중에도 대화"));
        assertStatus(post(actions,a,"{\"action\":\"CONFIRM\"}"),204);
        messages=room(a,id).path("messages").size();
        assertStatus(post(actions,a,"{\"action\":\"CONFIRM\"}"),204);
        assertEquals(messages,room(a,id).path("messages").size());
    }
    @Test void fullRoomAutomaticallyReopensAfterDeparture() {
        UUID a=alpha(),b=bravo(),c=charlie();String id=create(a,"정원 확인",2);
        String actions="/api/v1/rooms/"+id+"/actions";
        assertStatus(post("/api/v1/rooms/"+id+"/join",b,joinBody("SUPPORT",null)),200);
        assertStatus(post(actions,a,"{\"action\":\"REOPEN\"}"),409);
        assertStatus(post(actions,b,"{\"action\":\"LEAVE\"}"),204);
        assertEquals("OPEN",room(a,id).path("status").asText());
        assertStatus(post("/api/v1/rooms/"+id+"/join",c,joinBody("MID",null)),200);
        assertEquals("CONFIRMED",room(a,id).path("status").asText());
    }
    @Test void fiveMemberRoomReopensAfterKickOwnerLeaveAndTransferWithoutLosingChat() {
        UUID a=alpha(),b=bravo(),c=charlie(),d=user("delta"),e=user("echo"),f=user("foxtrot");
        String id=create(a,"다섯 명 모집",5),actions="/api/v1/rooms/"+id+"/actions";
        List<UUID> peers=List.of(b,c,d,e);List<String> roles=List.of("JUNGLE","MID","ADC","SUPPORT");
        for(int i=0;i<peers.size();i++) assertStatus(post("/api/v1/rooms/"+id+"/join",peers.get(i),joinBody(roles.get(i),null)),200);
        assertEquals(5,room(a,id).path("members").size());
        assertEquals("CONFIRMED",room(a,id).path("status").asText());
        assertStatus(post("/api/v1/rooms/"+id+"/join",f,joinBody("SUPPORT",null)),409);
        assertStatus(post("/api/v1/rooms/"+id+"/messages",e,"{\"clientMessageId\":\""+UUID.randomUUID()+"\",\"text\":\"다섯 명 대화\"}"),200);
        assertStatus(post(actions,b,"{\"action\":\"KICK\",\"memberId\":\""+e+"\"}"),404);
        assertStatus(post(actions,a,"{\"action\":\"KICK\",\"memberId\":\""+e+"\"}"),204);
        assertEquals(4,room(a,id).path("members").size());
        assertEquals("OPEN",room(a,id).path("status").asText());
        assertTrue(room(a,id).path("messages").toString().contains("다섯 명 대화"));
        assertStatus(post("/api/v1/rooms/"+id+"/join",e,joinBody("SUPPORT",null)),200);
        assertEquals("CONFIRMED",room(a,id).path("status").asText());
        assertStatus(post(actions,a,"{\"action\":\"LEAVE\"}"),204);
        UUID nextOwner=UUID.fromString(room(b,id).path("ownerId").asText());
        assertNotEquals(a,nextOwner);
        assertEquals("OPEN",room(b,id).path("status").asText());
        assertStatus(post("/api/v1/rooms/"+id+"/join",a,joinBody("TOP",null)),200);
        assertEquals("CONFIRMED",room(b,id).path("status").asText());
        String target=create(f,"이동할 방",2);
        assertStatus(post("/api/v1/rooms/"+target+"/join",e,joinBody("SUPPORT",id)),200);
        assertEquals("OPEN",room(b,id).path("status").asText());
        assertEquals(4,room(b,id).path("members").size());
        assertStatus(post(actions,nextOwner,"{\"action\":\"CONFIRM\"}"),204);
        UUID departing=List.of(a,b,c,d).stream().filter(u->!u.equals(nextOwner)).findFirst().orElseThrow();
        assertStatus(post(actions,departing,"{\"action\":\"LEAVE\"}"),204);
        assertEquals("OPEN",room(nextOwner,id).path("status").asText());
    }
    @Test void recruitmentCloseIsIndependentOfGameStartAndExpiredReservationCannotReopen() {
        UUID a=alpha(),b=bravo(),c=charlie();String id=create(a,"자유 랭크",5);
        assertStatus(post("/api/v1/rooms/"+id+"/join",b,joinBody("SUPPORT",null)),200);
        assertStatus(post("/api/v1/rooms/"+id+"/join",c,joinBody("MID",null)),200);
        // A fourth member and FLEX_RANKED mode exercise the former queue-start guard.
        UUID fourth=user("delta");
        assertStatus(post("/api/v1/rooms/"+id+"/join",fourth,joinBody("JUNGLE",null)),200);
        jdbc.sql("update rooms set settings=jsonb_set(settings,'{modeKey}','\"FLEX_RANKED\"') where id=?").param(UUID.fromString(id)).update();
        String actions="/api/v1/rooms/"+id+"/actions";
        assertStatus(post(actions,a,"{\"action\":\"CONFIRM\"}"),204);
        jdbc.sql("update rooms set settings=settings || '{\"type\":\"RESERVATION\",\"availableFrom\":\"2020-01-01T12:00:00+09:00\"}'::jsonb where id=?").param(UUID.fromString(id)).update();
        assertStatus(post(actions,a,"{\"action\":\"REOPEN\"}"),409);
        assertStatus(post(actions,b,"{\"action\":\"LEAVE\"}"),204);
        assertEquals("CONFIRMED",room(a,id).path("status").asText());
    }
    @Test void concurrentLastSeatOnlyAllowsOneAndNoDuplicateMembership() throws Exception {
        UUID a=alpha(),b=bravo(),c=charlie();String id=create(a,"마지막 자리",2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{start.await();return post("/api/v1/rooms/"+id+"/join",b,joinBody("SUPPORT",null)).getStatusCode().value();});
            var two=pool.submit(()->{start.await();return post("/api/v1/rooms/"+id+"/join",c,joinBody("MID",null)).getStatusCode().value();});start.countDown();
            var codes=new ArrayList<>(List.of(one.get(),two.get()));Collections.sort(codes);assertEquals(List.of(200,409),codes);
        }
        assertEquals(2,room(a,id).path("members").size());
    }
    @Test void failedTransferKeepsOldRoomAndSuccessfulTransferIsAtomic() {
        UUID a=alpha(),b=bravo(),c=charlie();String old=create(a,"기존 방",2),target=create(b,"새 방",3);
        assertStatus(post("/api/v1/rooms/"+target+"/join",a,joinBody("TOP",old)),409);
        assertEquals(a.toString(),room(a,old).path("ownerId").asText());
        assertStatus(post("/api/v1/rooms/"+target+"/join",a,joinBody("SUPPORT",old)),200);
        assertEquals(1,body(get("/api/v1/rooms",c)).size());assertEquals(2,room(a,target).path("members").size());
        assertStatus(post("/api/v1/rooms/"+target+"/actions",b,"{\"action\":\"LEAVE\"}"),204);
        assertEquals(a.toString(),room(a,target).path("ownerId").asText());
    }
    @Test void blockAndTierConditionsAreServerValidated() {
        UUID a=alpha(),b=bravo();String id=create(a,"조건 확인",3);
        jdbc.sql("insert into blocks(blocker_id,blocked_id) values(?,?)").params(a,b).update();
        assertStatus(post("/api/v1/rooms/"+id+"/join",b,joinBody("SUPPORT",null)),409);
        jdbc.sql("delete from blocks").update();
        jdbc.sql("update rooms set settings=jsonb_set(settings,'{desiredTierRange}','{\"minTier\":\"GOLD\",\"maxTier\":\"GOLD\"}') where id=?").param(UUID.fromString(id)).update();
        assertStatus(post("/api/v1/rooms/"+id+"/join",b,joinBody("SUPPORT",null)),409);
    }
    @Test void roomRulesAndIdentityCannotBeForged() {
        UUID a=alpha();String body=createBody(UUID.randomUUID(),"5인 구성",5).replace("\"SUPPORT\"", "\"TOP\"");
        assertStatus(post("/api/v1/rooms",a,body),400);
        String id=create(a,"프로필 검증",2);assertEquals("alpha",room(a,id).path("members").get(0).path("nickname").asText());
        assertTrue(room(a,id).path("members").get(0).path("tier").isNull());
        assertStatus(post("/api/v1/rooms/"+id+"/actions",a,"{\"action\":\"LEAVE\"}"),204);
        assertEquals(0,jdbc.sql("select count(*) from room_messages").query(Integer.class).single());
    }
}
