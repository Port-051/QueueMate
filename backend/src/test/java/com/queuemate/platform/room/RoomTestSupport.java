package com.queuemate.platform.room;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.room.service.RoomMemberService;
import com.queuemate.platform.room.service.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 방 안의 일의 테스트 바탕. 2026-09-25 에 {@code room} 앱을 합치며 그쪽의 테스트를 옮겨 온 자리다 — 이 앱의 {@link ApiTestSupport} 위에 선다
 * (진짜 PostgreSQL 5433 · Redis 6380, 쿠키 인증, 스프링 컨텍스트 공유).
 *
 * <p><b>이름표로 쓰고 진짜 번호로 돈다.</b> 옮겨 온 테스트는 {@code "host"} · {@code "u1"} · {@code "r1"} 같은 이름으로 적혀 있다 — 읽기 쉬워서 그대로 뒀다.
 * 그 이름을 {@link #u(String)} 가 <b>가입한 사용자의 번호</b>로, {@link #r(String)} 가 <b>겹치지 않는 방 번호</b>로 바꾼다(테스트마다 새로 짓는다).
 * 방 키에는 진짜 사용자 번호가 들어간다 — 게시판이 멤버 SET 을 사용자 번호로 읽기 때문이다. 거꾸로 Redis 에서 읽은 번호는 {@link #labelOf(String)} 가
 * 이름표로 되돌려 준다 — 그래서 {@code members("r1")} 이 {@code ["host", "u1"]} 로 비교된다.
 *
 * <p><b>{@code FLUSHDB} 를 쓰지 않는다</b>(이 앱의 테스트 규칙 — 같은 Redis 를 다른 테스트 · 앱이 쓴다). 합치기 전의 {@code room} 테스트는 DB 15 번을
 * 통째로 비웠다 — 여기서는 <b>이 테스트가 지은 방과 사용자의 키만</b> 보고({@link #ownKeys()}) 끝나면 그것만 지운다.
 * {@code redis.keys("qm:*")} 로 "아무것도 없다"를 보던 자리는 전부 {@link #ownKeys()} 로 바뀌었다.
 *
 * <p>사용자는 가입 API 가 아니라 SQL 로 바로 넣는다 — 방의 테스트는 사용자 번호만 있으면 되고, 동시성 테스트가 100명을 쓴다(가입은 bcrypt 라 느리다).
 * 로그인 아이디는 {@link #newNickname()} 로 지어 {@link ApiTestSupport} 가 끝에 지운다.
 */
public abstract class RoomTestSupport extends ApiTestSupport {

    private static final Pattern ROOM_KEY = Pattern.compile("^qm:room:([^:]+):(host|members|confirmed)$");
    private static final Pattern USER_KEY = Pattern.compile("^qm:user:(active-room|active-request):(.+)$");

    @Autowired
    protected RoomService roomService;

    @Autowired
    protected RoomMemberService roomMemberService;

    @Autowired
    protected RedisConnectionFactory connectionFactory;

    private final Map<String, String> userIds = new ConcurrentHashMap<>();
    private final Map<String, String> roomIds = new ConcurrentHashMap<>();
    private final Map<String, String> labels = new ConcurrentHashMap<>();

    /** 이름표의 사용자 번호(십진 문자열). 처음 부르면 사용자를 만든다 */
    protected String u(String label)
    {
        return userIds.computeIfAbsent(label, l -> {
            Long id = insertUser();
            String userId = String.valueOf(id);
            labels.put(userId, l);
            return userId;
        });
    }

    /**
     * 가입 API 로 만든 사용자(쿠키가 필요한 HTTP 테스트)를 이름표에 붙인다 — 그래야 그 사람의 입장 표시 키도 {@link #ownKeys()} 에 들고 끝나면 지워진다.
     * 돌려주는 것은 사용자 번호(십진 문자열)다
     */
    protected String adopt(String label, Long userId)
    {
        String id = String.valueOf(userId);
        userIds.put(label, id);
        labels.put(id, label);
        return id;
    }

    /**
     * 사용자를 미리 만든다 — {@code prefix + 0} … {@code prefix + (count - 1)}. 동시성 테스트는 출발선에 서기 전에 불러 둔다
     * (스레드 안에서 처음 부르면 INSERT 가 겹쳐 재려는 것이 흐려진다).
     */
    protected void users(String prefix, int count)
    {
        for(int i = 0; i < count; i++)
        {
            u(prefix + i);
        }
    }

    /**
     * 이름표의 방 번호. 이 앱에서 {@code roomId} 는 글의 번호라 숫자다 — 글 번호가 닿지 않을 만큼 큰 무작위 값으로 짓는다.
     *
     * <p><b>그 번호의 모집 글도 같이 넣는다</b>(2026-09-25 2단계) — 입장이 스크립트를 부르기 전에 글을 본다({@code PostEntryGate}: 없는 글은 404
     * {@code POST_NOT_FOUND}). 방의 테스트는 방을 {@code roomService.create} 로 직접 만들므로(글 쓰기를 거치지 않는다) 글은 여기서 SQL 로 넣는다 —
     * <b>모집 중 · 방장은 이 방만을 위해 새로 넣은 사용자</b>({@link #insertUser()} — {@code host_id} 에 {@code users(id)} 로 FK 가 있다)다.
     * 방의 멤버 누구와도 다른 사람이라 차단에 걸리지 않고, 입장의 갈래는 전부 스크립트가 가른다.
     * 번호를 앱이 아니라 테스트가 정하므로 {@code OVERRIDING SYSTEM VALUE} 로 넣는다(identity 의 순번은 건드리지 않는다). 끝나면 지운다
     */
    protected String r(String label)
    {
        return roomIds.computeIfAbsent(label, l -> {
            long id = ThreadLocalRandom.current().nextLong(3_000_000_000_000_000L, 4_000_000_000_000_000L);
            jdbcTemplate.update("insert into recruit_posts "
                    + "(id, host_id, game, mode, title, voice, purpose, conditions, status, created_at, updated_at) "
                    + "overriding system value values (?, ?, 'LOL', ?, '방의 테스트', 'REQUIRED', 'RANK_UP', '{}'::jsonb, 'RECRUITING', now(), now())",
                    id, insertUser(), LOL_MODE);
            String roomId = String.valueOf(id);
            labels.put(roomId, l);
            return roomId;
        });
    }

    /** Redis 에서 읽은 번호를 이름표로 되돌린다. 모르는 값이면 그대로다 */
    protected String labelOf(String id)
    {
        return id == null ? null : labels.getOrDefault(id, id);
    }

    /** 이름표로 적은 키({@code "qm:room:r1:host"} · {@code "qm:user:active-room:u1"})를 진짜 키로 바꾼다 */
    protected String key(String labelled)
    {
        Matcher room = ROOM_KEY.matcher(labelled);
        if(room.matches())
        {
            return "qm:room:" + r(room.group(1)) + ":" + room.group(2);
        }
        Matcher user = USER_KEY.matcher(labelled);
        if(user.matches())
        {
            return "qm:user:" + user.group(1) + ":" + u(user.group(2));
        }
        throw new IllegalArgumentException("이름표로 적은 방 · 사용자 키가 아니다: " + labelled);
    }

    /** 방의 멤버를 이름표로 */
    protected Set<String> members(String roomLabel)
    {
        return redisTemplate.opsForSet().members(key("qm:room:" + roomLabel + ":members")).stream()
                .map(this::labelOf).collect(Collectors.toCollection(TreeSet::new));
    }

    /** 사용자의 입장 표시(가리키는 방)를 이름표로. 없으면 {@code null} */
    protected String marker(String userLabel)
    {
        return labelOf(redisTemplate.opsForValue().get(key("qm:user:active-room:" + userLabel)));
    }

    /** 방장 키의 값(방장)을 이름표로. 없으면 {@code null} */
    protected String host(String roomLabel)
    {
        return labelOf(redisTemplate.opsForValue().get(key("qm:room:" + roomLabel + ":host")));
    }

    /**
     * 이 테스트가 지은 방과 사용자의 키 가운데 <b>지금 있는 것</b>을 이름표로 적어 돌려준다 — {@code "qm:room:r1:host"} 꼴이다.
     * 합치기 전의 {@code redis.keys("qm:*")} 자리다(그때는 테스트 전용 DB 를 통째로 비웠다).
     */
    protected Set<String> ownKeys()
    {
        Set<String> present = new TreeSet<>();
        for(String labelled : ownLabelledKeys())
        {
            if(Boolean.TRUE.equals(redisTemplate.hasKey(key(labelled))))
            {
                present.add(labelled);
            }
        }
        return present;
    }

    /** {@link #ownKeys()} 가운데 그 접두사로 시작하는 것 */
    protected Set<String> ownKeys(String labelledPrefix)
    {
        return ownKeys().stream().filter(k -> k.startsWith(labelledPrefix)).collect(Collectors.toCollection(TreeSet::new));
    }

    /** 이 테스트가 지은 방과 사용자의 키를 전부 지운다 — 반복하는 동시성 테스트가 매 바퀴 처음부터 시작할 때 쓴다 */
    protected void deleteOwnKeys()
    {
        redisTemplate.delete(ownLabelledKeys().stream().map(this::key).toList());
    }

    private List<String> ownLabelledKeys()
    {
        List<String> all = new ArrayList<>();
        for(String room : roomIds.keySet())
        {
            all.add("qm:room:" + room + ":host");
            all.add("qm:room:" + room + ":members");
            all.add("qm:room:" + room + ":confirmed");
        }
        for(String user : userIds.keySet())
        {
            all.add("qm:user:active-room:" + user);
            all.add("qm:user:active-request:" + user);
        }
        return all;
    }

    @AfterEach
    void deleteRoomKeys()
    {
        deleteOwnKeys();
        // r() 가 넣은 글은 그 방장(insertUser)을 지울 때 FK 의 ON DELETE CASCADE 로 딸려 지워진다 — 방장 확정을 HTTP 로 부른 테스트가
        // 적은 파티 · 파티원까지 같이다(ApiTestSupport#deleteCreatedUsers)
        userIds.clear();
        roomIds.clear();
        labels.clear();
    }

    /** 모든 스레드를 출발선에 세웠다가 한꺼번에 푼다. 그래야 실제로 겹친다 */
    protected static void runConcurrently(int threads, IntConsumer task) throws InterruptedException
    {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for(int i = 0; i < threads; i++)
        {
            int index = i;
            Thread.startVirtualThread(() -> {
                ready.countDown();
                try
                {
                    start.await();
                    task.accept(index);
                }
                catch(Exception ignored)
                {
                    // 실패한 시도는 세지 않는다. 개수가 모자라면 단언이 잡는다
                }
                finally
                {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
    }
}
