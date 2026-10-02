-- KEYS: host, members, needs, confirmed, caller's active-room.
-- ARGV: caller, roomId, new capacity, host position (empty for no positions), metadata-only, wanted positions...
-- Validate the current roster before writing. Entry uses the same DB row lock as settings updates.
local host = redis.call('GET', KEYS[1])
if not host then return {-1} end
if host ~= ARGV[1] or redis.call('GET', KEYS[5]) ~= ARGV[2]
    or redis.call('HEXISTS', KEYS[2], host) == 0 then return {-2} end
if ARGV[5] == '1' then return {1, unpack(redis.call('HGETALL', KEYS[2]))} end
if redis.call('EXISTS', KEYS[4]) == 1 then return {-3} end
local members = redis.call('HGETALL', KEYS[2])
if #members / 2 > tonumber(ARGV[3]) then return {-4} end
local needs = {}
for i = 6, #ARGV do needs[ARGV[i]] = true end
local occupied = {}
for i = 1, #members, 2 do
    if members[i] ~= host then
        local position = members[i + 1]
        -- Do not silently clear or assign another member's position on a mode change.
        if ARGV[4] == '' then
            if position ~= '' then return {-5} end
        elseif position == '' or position == ARGV[4] or not needs[position] or occupied[position] then
            return {-5}
        end
        occupied[position] = true
    end
end
for position in pairs(occupied) do needs[position] = nil end
local ttl = redis.call('PTTL', KEYS[1])
redis.call('HSET', KEYS[2], host, ARGV[4])
redis.call('DEL', KEYS[3])
for position in pairs(needs) do redis.call('SADD', KEYS[3], position) end
if ttl > 0 then redis.call('PEXPIRE', KEYS[3], ttl) end
return {1, unpack(redis.call('HGETALL', KEYS[2]))}
