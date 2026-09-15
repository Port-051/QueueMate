package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.queuemate.common.domain.GameKey;
import com.queuemate.matching.app.RealtimeMatcher;
import com.queuemate.reservation.app.ReservationMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties={"queuemate.matching.auto-trigger=false","queuemate.matching.sweep-ms=3600000","queuemate.recruitment.group-sweep-ms=3600000","queuemate.proposal.sweep-ms=3600000"})
class RecruitmentBoardIntegrationTest extends ApiContractTestSupport {
    @Autowired RealtimeMatcher matcher;
    @Autowired ReservationMatcher reservationMatcher;
    @Autowired com.queuemate.matching.recruitment.BoardService board;
    private static String prefs(String own,String min,String max,String keys) {
        return "{\"ownTier\":"+own+",\"minTier\":"+min+",\"maxTier\":"+max+",\"desiredKeys\":"+keys+",\"purposeRequired\":false}";
    }
    private static String any() {return prefs("null","null","null","[]");}
    private String write(String role,String prefs,boolean auto) {
        return "{\"type\":\"REALTIME\",\"condition\":"+lolCondition(role)+",\"preferences\":"+prefs+",\"description\":\"같이 한 판 해요\",\"autoMatch\":"+auto+"}";
    }
    private JsonNode create(UUID user,String role,String prefs,boolean auto) {
        var response=post("/api/v1/recruitments",user,write(role,prefs,auto));assertStatus(response,201);return body(response);
    }
    private JsonNode search(UUID user,String role,String prefs) {
        var response=post("/api/v1/recruitments/search",user,"{\"type\":\"REALTIME\",\"condition\":"+lolCondition(role)+",\"preferences\":"+prefs+",\"pageSize\":10}");
        assertStatus(response,200);return body(response);
    }
    private String id(JsonNode node) {return node.path("id").asText();}
    @Test void requiresAuthAndPreservesUniqueSource() {
        assertStatus(post("/api/v1/recruitments",null,write("TOP",any(),false)),401);
        UUID user=alpha();var row=create(user,"TOP",any(),false);
        assertEquals(id(row),body(get("/api/v1/match-requests/"+id(row),user)).path("id").asText());
        assertStatus(post("/api/v1/recruitments",user,write("JUNGLE",any(),false)),409);
        assertEquals(1,body(get("/api/v1/recruitments/mine",user)).size());
    }
    @Test void bothSidesTierAndRoleAreCheckedAndUnknownDoesNotPass() {
        UUID a=alpha(), b=bravo();
        create(a,"TOP",prefs("\"GOLD\"","\"GOLD\"","\"PLATINUM\"","[\"JUNGLE\"]"),false);
        assertEquals(0,search(b,"JUNGLE",any()).path("total").asInt());
        assertEquals(1,search(b,"JUNGLE",prefs("\"GOLD\"","null","null","[]")).path("total").asInt());
        assertEquals(0,search(b,"MID",prefs("\"GOLD\"","null","null","[]")).path("total").asInt());
        assertEquals(0,search(b,"JUNGLE",prefs("\"GOLD\"","\"DIAMOND\"","null","[]")).path("total").asInt());
    }
    @Test void optInAutomaticUsesSamePreferences() {
        UUID a=alpha(),b=bravo();
        var one=create(a,"TOP",any(),false);var two=create(b,"JUNGLE",any(),true);
        assertTrue(matcher.tryMatch(GameKey.LOL,"SOLO_DUO_RANKED").isEmpty());
        assertStatus(post("/api/v1/recruitments/"+id(one)+"/actions",a,"{\"action\":\"AUTO_ON\",\"version\":0}"),200);
        assertTrue(matcher.tryMatch(GameKey.LOL,"SOLO_DUO_RANKED").isPresent());
        assertEquals("PROPOSED",body(get("/api/v1/recruitments/"+id(two),b)).path("status").asText());
    }
    @Test void directApplicationUsesFullAcceptanceAndRecordsReady() {
        UUID a=alpha(),b=bravo();var one=create(a,"TOP",any(),false);var two=create(b,"JUNGLE",any(),false);
        var join=post("/api/v1/recruitments/"+id(one)+"/join",b,"{\"sourceId\":\""+id(two)+"\"}");assertStatus(join,200);
        assertEquals(0,body(join).path("applicants").size());
        assertEquals(1,body(get("/api/v1/recruitments/"+id(one),a)).path("applicants").size());
        assertEquals(0,jdbc.sql("select count(*) from parties").query(Integer.class).single());
        var accepted=post("/api/v1/recruitments/"+id(one)+"/respond",a,"{\"applicantId\":\""+id(two)+"\",\"accept\":true}");assertStatus(accepted,200);
        String proposal=body(accepted).path("proposalId").asText();
        assertStatus(post("/api/v1/proposals/"+proposal+"/accept",a,null),200);
        assertEquals(0,jdbc.sql("select count(*) from parties").query(Integer.class).single());
        var finalAccept=post("/api/v1/proposals/"+proposal+"/accept",b,null);assertStatus(finalAccept,200);
        String party=body(finalAccept).path("partyId").asText();
        assertStatus(post("/api/v1/parties/"+party+"/ready",a,"{\"ready\":true}"),200);
        assertStatus(post("/api/v1/parties/"+party+"/ready",b,"{\"ready\":true}"),200);
        assertEquals(2,jdbc.sql("select count(*) from recruitment_events where event='READY'").query(Integer.class).single());
    }
    @Test void bumpIsRateLimitedAndDoesNotCreateAnotherRoom() {
        UUID a=alpha();var row=create(a,"TOP",any(),false);String path="/api/v1/recruitments/"+id(row)+"/actions";
        assertStatus(post(path,a,"{\"action\":\"BUMP\",\"version\":0}"),429);
        jdbc.sql("update recruitments set created_at=created_at-interval '6 minutes' where id=:id").param("id",UUID.fromString(id(row))).update();
        var bump=post(path,a,"{\"action\":\"BUMP\",\"version\":0}");assertStatus(bump,200);
        assertStatus(post(path,a,"{\"action\":\"PAUSE\",\"version\":0}"),409);
        assertEquals(1,jdbc.sql("select count(*) from recruitment_events where event='CREATED'").query(Integer.class).single());
        assertEquals(1,body(bump).path("version").asInt());
    }
    @Test void staleHiddenAndConfirmationRestoresVisibility() {
        UUID a=alpha(),b=bravo();var row=create(a,"TOP",any(),false);
        jdbc.sql("update recruitments set confirmed_at=confirmed_at-interval '13 minutes'").update();
        assertEquals(0,search(b,"JUNGLE",any()).path("total").asInt());
        assertEquals("STALE",body(get("/api/v1/recruitments/"+id(row),a)).path("status").asText());
        assertStatus(post("/api/v1/recruitments/"+id(row)+"/actions",a,"{\"action\":\"CONFIRM\",\"version\":0}"),200);
        assertEquals(1,search(b,"JUNGLE",any()).path("total").asInt());
    }
    @Test void counterfactualChangesOnlyOneFieldAndUsesActualCandidates() {
        UUID a=alpha(),b=bravo();var row=create(a,"TOP",prefs("null","null","null","[\"MID\"]"),false);
        create(b,"JUNGLE",any(),false);
        var response=get("/api/v1/recruitments/"+id(row)+"/suggestions",a);assertStatus(response,200);
        var results=body(response);assertEquals(0,results.path("currentCount").asInt());
        assertEquals(1,results.path("suggestions").size());
        assertEquals("desiredKeys",results.path("suggestions").get(0).path("field").asText());
        assertEquals(1,results.path("suggestions").get(0).path("candidateCount").asInt());
        assertEquals(1,body(get("/api/v1/recruitments/"+id(row),a)).path("preferences").path("desiredKeys").size());
    }
    @Test void impressionsAreDeduplicatedAndForeignWritesDenied() {
        UUID a=alpha(),b=bravo();var row=create(a,"TOP",any(),false);
        for(int i=0;i<2;i++) assertStatus(post("/api/v1/recruitments/impressions",b,"{\"ids\":[\""+id(row)+"\"]}"),204);
        assertEquals(1,body(get("/api/v1/recruitments/"+id(row),a)).path("impressions").asInt());
        assertStatus(post("/api/v1/recruitments/"+id(row)+"/actions",b,"{\"action\":\"CLOSE\",\"version\":0}"),404);
    }
    @Test void reservationSurvivesOfflineAndRequiresOverlappingTime() {
        UUID a=alpha(),b=bravo();var from=OffsetDateTime.now().plusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0);
        String write=write("TOP",any(),false).replace("REALTIME","RESERVATION").replaceFirst("}$",",\"availableFrom\":\""+from+"\",\"availableTo\":\""+from.plusHours(1)+"\",\"playAmount\":\"ONE_GAME\"}");
        var response=post("/api/v1/recruitments",a,write);assertStatus(response,201);
        jdbc.sql("update recruitments set confirmed_at=confirmed_at-interval '2 days'").update();
        String query="{\"type\":\"RESERVATION\",\"condition\":"+lolCondition("JUNGLE")+",\"preferences\":"+any()+",\"availableFrom\":\""+from+"\",\"availableTo\":\""+from.plusHours(1)+"\",\"playAmount\":\"ONE_GAME\",\"pageSize\":10}";
        var found=post("/api/v1/recruitments/search",b,query);assertStatus(found,200);assertEquals(1,body(found).path("total").asInt());
        var later=post("/api/v1/recruitments/search",b,query.replace(from.toString(),from.plusHours(1).toString()).replace(from.plusHours(1).toString(),from.plusHours(2).toString()));
        assertStatus(later,400);
    }
    @Test void blockIsRespectedBySearchSuggestionsAndDirectJoin() {
        UUID a=alpha(),b=bravo();var host=create(a,"TOP",any(),false);var own=create(b,"JUNGLE",any(),false);
        assertStatus(post("/api/v1/blocks",a,"{\"targetUserId\":\""+b+"\"}"),201);
        assertEquals(0,search(b,"JUNGLE",any()).path("total").asInt());
        assertStatus(get("/api/v1/recruitments/"+id(host),b),404);
        assertStatus(post("/api/v1/recruitments/"+id(host)+"/join",b,"{\"sourceId\":\""+id(own)+"\"}"),409);
    }
    @Test void competingAcceptancesCannotExceedCapacity() throws Exception {
        UUID a=alpha(),b=bravo(),c=charlie();var host=create(a,"TOP",any(),false);var one=create(b,"JUNGLE",any(),false);var two=create(c,"MID",any(),false);
        for(var r:java.util.List.of(one,two)) assertStatus(post("/api/v1/recruitments/"+id(host)+"/join",UUID.fromString(r.path("userId").asText()),"{\"sourceId\":\""+id(r)+"\"}"),200);
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var results=executor.invokeAll(java.util.List.of(
                ()->post("/api/v1/recruitments/"+id(host)+"/respond",a,"{\"applicantId\":\""+id(one)+"\",\"accept\":true}").getStatusCode().value(),
                ()->post("/api/v1/recruitments/"+id(host)+"/respond",a,"{\"applicantId\":\""+id(two)+"\",\"accept\":true}").getStatusCode().value()));
            var codes=results.stream().map(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).toList();
            assertTrue(codes.contains(200));assertTrue(codes.contains(409));
        }
        assertEquals(1,jdbc.sql("select count(*) from match_proposals").query(Integer.class).single());
        assertEquals(2,jdbc.sql("select count(*) from proposal_members").query(Integer.class).single());
        assertEquals(0,jdbc.sql("select count(*) from parties").query(Integer.class).single());
    }
    @Test void fourPersonRecruitmentWaitsForCapacityAndAutomaticFillsRemainingSlots() {
        UUID a=alpha(),b=bravo(),c=charlie(),d=user("delta");
        var ids=new java.util.ArrayList<String>();
        for(UUID user:java.util.List.of(a,b,c,d)) {
            String value=write("TOP",any(),true).replace("LOL","PUBG").replace("SOLO_DUO_RANKED","SQUAD").replace("POSITION","PLAY_STYLE").replace("TOP","BALANCED");
            var response=post("/api/v1/recruitments",user,value);assertStatus(response,201);ids.add(id(body(response)));
        }
        assertStatus(post("/api/v1/recruitments/"+ids.get(0)+"/join",b,"{\"sourceId\":\""+ids.get(1)+"\"}"),200);
        assertStatus(post("/api/v1/recruitments/"+ids.get(0)+"/respond",a,"{\"applicantId\":\""+ids.get(1)+"\",\"accept\":true}"),200);
        assertEquals(0,jdbc.sql("select count(*) from match_proposals").query(Integer.class).single());
        assertEquals(2,body(get("/api/v1/recruitments/"+ids.get(1),b)).path("members").size());
        assertTrue(matcher.tryMatch(GameKey.PUBG,"SQUAD").isEmpty(),"모집에 들어간 사람은 개별 자동 매칭에서 빠져야 한다");
        board.fillGroup(UUID.fromString(ids.get(0)));
        assertEquals(4,jdbc.sql("select count(*) from proposal_members").query(Integer.class).single());
        assertEquals(0,jdbc.sql("select count(*) from parties").query(Integer.class).single());
        String proposal=body(get("/api/v1/recruitments/"+ids.get(0),a)).path("proposalId").asText();
        assertStatus(post("/api/v1/proposals/"+proposal+"/decline",a,null),204);
        assertNull(body(get("/api/v1/recruitments/"+ids.get(1),b)).path("parentId").textValue());
    }

}
