-- 확정된 제안 뒷정리. 수락으로 확정된 직후 자바가 한 번 부른다.
--
-- **확정을 찍는 accept-proposal.lua 와 합치지 않는다.** 그 스크립트는 수락자 집합을 다시 세어
-- 판단하는 것으로 멱등성을 얻는다. 거기서 같이 지워 버리면 재시도가 셀 근거를 잃는다.
-- 확정과 정리는 실패했을 때 할 일도 다르다 — 정리가 실패해도 확정을 되돌리면 안 된다.
--
-- 대신 이 스크립트가 스스로 status 를 확인한다. 몇 번 불려도, 페일오버로 다시 실행돼도
-- 결과가 같다.
--
-- ── 확정된 파티는 누가 읽나 (docs/11 D-42, 2026-09-27) ─────────────────
-- 확정된 파티를 DB 에 만드는 것은 app:platform 이고, 그쪽은 outbox → SQS 가 아니라
-- **이 앱의 파티 HASH `qm:party:{partyId}` 를 Redis 에서 직접 읽는다** (gameconfig 를 읽는 D-29,
-- 활성 요청 키를 EXISTS 로 보는 D-19 와 같은 방식이다). 프런트가 MATCH_CONFIRMED {partyId} 를
-- 받아 platform 을 부르고, platform 이 그 partyId 로 여기를 읽어 파티 · 방을 만든다.
--
-- 그래서 이 스크립트가 할 일은 둘이다.
--   ① 파티 HASH 를 **혼자 읽어도 되게** 만든다. 조건은 활성 요청에만 있고 파티 HASH 에는 없었다 —
--      배정 색인 키 이름에 조건이 들어가서(INV-8) 파티 안에는 적을 필요가 없었기 때문이다.
--      platform 이 키 하나로 끝내게, 파티원 한 명의 활성 요청에서 조건 넷을 베껴 파티 HASH 에 적는다
--      (파티원 전원이 같은 값이다 — 같은 needs 색인에서 만났으므로 구조적으로 같다).
--   ② **영원히 남기지 않는다.** 전에는 확정된 활성 요청과 파티 HASH 에 TTL 이 없었고, 푸는 것은
--      PartyClosed 를 소비할 미래의 코드였다(그 큐는 D-13 으로 platform 만 읽게 되어 길이 닫혔다).
--      그 결과 확정된 사용자는 큐에 다시 들어올 방법이 없었다(HANDOFF §0-1 ①). 지금은 둘 다 TTL 을 건다.
--
-- ── platform 과의 약속 — 파티 HASH 의 필드 ────────────────────────────
-- 아래 필드 이름이 앱 사이의 계약이다. **이름을 바꾸면 컴파일도 테스트도 통과한 채로 platform 이
-- 파티를 못 만든다** (SharedKeys 의 접두사 경고와 같은 종류다). 바꾸려면 platform 과 같이 바꾼다.
--   status           'CONFIRMED'. platform 은 이 값일 때만 파티를 만든다
--   confirmedAt      확정 시각(epoch millis). 이 스크립트가 HSETNX 로 적는다 — 재실행이 시각을 옮기지 않는다
--   game             LOL / VALORANT / PUBG          ┐
--   modeKey          예 RANKED_SOLO                 │ 이 스크립트가 활성 요청에서 베껴 적는다
--   voicePreference  REQUIRED / NO_VOICE            │
--   playPurpose      RANK_UP / TRYHARD / FUN        ┘  (TRYHARD 는 옛 NORMAL — 2026-09-29, docs/11 D-49)
--   target           정원. 배정 스크립트가 적는다
--   member:{userId}  파티원. 값은 keyValue(포지션 · 역할군 · 플랫폼). 배정 스크립트가 적는다
--   tierLo / tierHi  티어 모드만. 사다리 순번(ZRANK, 0 부터). 배정 스크립트가 적는다
--
-- ── TTL — 무엇이 언제 사라지나 ───────────────────────────────────────
--   파티 HASH    ARGV[4] 초 (기본 600). platform 은 이 안에 읽어 가야 한다. 지나면 파티가 증발하고
--                그때 platform 이 읽으러 오면 없다 — 받아들인 절충이다(D-42). 조회와 수락 재전송도
--                이 안에서만 답한다.
--   활성 요청    status = 'PARTY' 를 찍고 ARGV[2] 초 (기본 60). 이 동안은 INV-1 선점이 그대로라 새 매칭을
--                못 건다(조회는 MATCHED). **지나면 그 사용자는 다시 큐에 들어올 수 있다.** "한 번에 하나"를
--                지키는 자리는 platform 의 입장 표시 키 qm:user:active-room:{userId} 로 넘어간다(D-19) —
--                claim-request.lua 가 KEYS[2] 로 이미 본다. 그러니 platform 은 이 파티로 방을 만들 때 그 키를
--                파티원 전원에게 세워야 한다(D-42 의 전제). 방이 서기 전에 60 초가 지나면 그 사람은 큐에 다시
--                들어올 수 있다 — 이것도 받아들인 절충이다.
--   수락자 집합  ARGV[2] 초. 확정 판정에 다 쓰였고, 재전송한 수락은 파티의 status 만 보면 된다
--
--   needs 색인은 정원이 찰 때 이미 빠졌고, 만료 대기 목록은 확정 시 accept-proposal.lua 가 뺀다
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- KEYS[1] = qm:party:{partyId}              HASH. status / target / member:{userId} / (tierLo, tierHi)
-- KEYS[2] = qm:proposal:accepts:{partyId}   SET.  수락한 userId
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- ARGV[1] = 활성 요청 키 접두사 (qm:user:active-request:)
-- ARGV[2] = 활성 요청과 수락자 집합을 남겨 둘 시간(초)  — queuemate.proposal.confirmed-retention-seconds
-- ARGV[3] = now (epoch millis). confirmedAt 에 적는다
-- ARGV[4] = 파티 HASH 를 남겨 둘 시간(초)               — queuemate.proposal.confirmed-party-ttl-seconds
--
-- ── 반환 ─────────────────────────────────────────────────────────────
--   파티원 userId 목록. 확정된 제안이 아니면 빈 목록이다
--   (이미 정리됐거나, 확정되지 않은 파티다)

local partyKey   = KEYS[1]
local acceptsKey = KEYS[2]

local activeRequestPrefix = ARGV[1]
local retentionSeconds    = tonumber(ARGV[2])
local now                 = ARGV[3]
local partyTtlSeconds     = tonumber(ARGV[4])

-- 확정된 제안만 정리한다. 재실행·동시 호출에도 답이 같은 이유가 이 한 줄이다
if redis.call('HGET', partyKey, 'status') ~= 'CONFIRMED' then
    return {}
end

-- 파티원은 파티 HASH 의 member: 필드가 원본이다. 수락자 집합을 쓰지 않는 이유는
-- 그 집합이 "누가 눌렀나"이지 "누가 파티원인가"가 아니기 때문이다
local fields = redis.call('HKEYS', partyKey)
local members = {}
for i = 1, #fields do
    if string.sub(fields[i], 1, 7) == 'member:' then
        table.insert(members, string.sub(fields[i], 8))
    end
end

-- ① 조건 넷을 파티 HASH 로 베낀다. 어느 파티원의 것이든 같다(머리말). 활성 요청이 이미 사라졌으면
--    (지난 실행이 건 TTL 이 끝난 뒤의 재실행) 건너뛴다 — 그때는 먼저 적은 값이 남아 있다.
--    HSET 은 같은 값을 다시 적는 것이라 몇 번 해도 같다
if #members > 0 then
    local source = redis.call('HMGET', activeRequestPrefix .. members[1],
            'game', 'modeKey', 'voicePreference', 'playPurpose')
    if source[1] and source[2] and source[3] and source[4] then
        redis.call('HSET', partyKey,
                'game', source[1],
                'modeKey', source[2],
                'voicePreference', source[3],
                'playPurpose', source[4])
    end
end
-- 확정 시각은 첫 실행의 것이다. HSET 이면 재실행마다 시각이 뒤로 밀린다
redis.call('HSETNX', partyKey, 'confirmedAt', now)

-- ② 활성 요청 — "파티 중" 을 찍고 TTL 을 건다. 지우면 그 순간 새 매칭을 걸 수 있어 한 사람이
--    두 파티에 속하므로(INV-2) 지우지 않고, 대신 TTL 로 푼다(머리말 "TTL").
--    이미 사라진 활성 요청은 건드리지 않는다 — HSET 은 없는 키를 만들기 때문에, TTL 이 끝난 뒤의
--    재실행이 status 하나만 든 반쪽짜리 요청을 되살려 그 사용자를 60 초 더 막게 된다
for _, userId in ipairs(members) do
    local userKey = activeRequestPrefix .. userId
    if redis.call('EXISTS', userKey) == 1 then
        redis.call('HSET', userKey, 'status', 'PARTY')
        redis.call('EXPIRE', userKey, retentionSeconds)
    end
end

redis.call('EXPIRE', acceptsKey, retentionSeconds)
redis.call('EXPIRE', partyKey, partyTtlSeconds)

return members
