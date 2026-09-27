package com.queuemate.party.room;

import com.queuemate.common.domain.GameKey;
import java.time.*;
import java.util.*;
import static com.queuemate.party.room.RoomApi.*;

/** Recruitment rules for the pilot; external game queue eligibility is separate. */
final class RoomRules {
    static List<String> roles(GameKey game) {
        return switch(game) {
            case LOL -> List.of("TOP","JUNGLE","MID","ADC","SUPPORT");
            case VALORANT -> List.of("DUELIST","INITIATOR","CONTROLLER","SENTINEL");
            case PUBG -> List.of("AGGRESSIVE","BALANCED","SURVIVAL");
        };
    }
    static List<String> tiers(GameKey game) {
        return switch(game) {
            case LOL -> List.of("IRON","BRONZE","SILVER","GOLD","PLATINUM","EMERALD","DIAMOND","MASTER","GRANDMASTER","CHALLENGER");
            case VALORANT -> List.of("IRON","BRONZE","SILVER","GOLD","PLATINUM","DIAMOND","ASCENDANT","IMMORTAL","RADIANT");
            case PUBG -> List.of("BRONZE","SILVER","GOLD","PLATINUM","DIAMOND","MASTER");
        };
    }
    static boolean noRoles(Settings s) { return s.game()==GameKey.LOL && s.modeKey().equals("ARAM"); }
    static List<String> canonical(GameKey game,List<String> values) {
        if (values.stream().anyMatch(v -> !v.equals("ANY") && !roles(game).contains(v))) fail("포지션을 확인해 주세요");
        return values.contains("ANY") ? roles(game) : roles(game).stream().filter(values::contains).toList();
    }
    static void validate(Settings s,Profile p) {
        int max=switch(s.game()) {
            case LOL -> switch(s.modeKey()) { case "SOLO_DUO_RANKED" -> 2; case "FLEX_RANKED","NORMAL_DRAFT","SWIFTPLAY","ARAM" -> 5; default -> 0; };
            case VALORANT -> List.of("COMPETITIVE","UNRATED").contains(s.modeKey()) ? 5 : 0;
            case PUBG -> s.modeKey().equals("DUO") ? 2 : s.modeKey().equals("SQUAD") ? 4 : 0;
        };
        if (s.capacity()>max || s.modeKey().equals("FLEX_RANKED") && s.capacity()==4) fail("게임 모드에 맞는 인원을 선택해 주세요");
        var own=canonical(s.game(),p.roles());var desired=canonical(s.game(),s.desiredRoles());
        if (!noRoles(s) && own.isEmpty()) fail("내 포지션을 선택해 주세요");
        if (s.game()==GameKey.LOL && s.capacity()==5 && !noRoles(s)
                && (own.size()!=1 || desired.size()!=4 || desired.contains(own.getFirst()))) fail("5인 방은 내 포지션을 제외한 4개 포지션을 선택해 주세요");
        var r=s.desiredTierRange();var tiers=tiers(s.game());
        if (r!=null && (r.minTier()!=null && !tiers.contains(r.minTier()) || r.maxTier()!=null && !tiers.contains(r.maxTier())
                || r.minTier()!=null && r.maxTier()!=null && tiers.indexOf(r.minTier())>tiers.indexOf(r.maxTier()))) fail("티어 범위를 확인해 주세요");
        if (s.type().equals("RESERVATION")) {
            if (s.availableFrom()==null) fail("시작 시간을 선택해 주세요");
            try {
                var t=OffsetDateTime.parse(s.availableFrom());
                if (!t.isAfter(OffsetDateTime.now()) || t.getMinute()!=0 || t.getSecond()!=0 || t.getNano()!=0) fail("미래의 정시를 선택해 주세요");
            } catch (java.time.format.DateTimeParseException e) { fail("시작 시간을 확인해 주세요"); }
        } else if (s.availableFrom()!=null) fail("지금 시작하는 방에는 예약 시간이 필요하지 않아요");
    }
    static boolean tierFits(Settings s,String tier) {
        var r=s.desiredTierRange();if(r==null || r.minTier()==null && r.maxTier()==null) return true;
        int i=tiers(s.game()).indexOf(tier==null ? "" : tier);
        return i>=0 && (r.minTier()==null || i>=tiers(s.game()).indexOf(r.minTier())) && (r.maxTier()==null || i<=tiers(s.game()).indexOf(r.maxTier()));
    }
    static void fail(String message) { throw new IllegalArgumentException(message); }
}
