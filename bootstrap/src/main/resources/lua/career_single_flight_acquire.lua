-- 原子抢占 Career single-flight owner，或返回可回放/跟随状态
-- KEYS[1]: single-flight 状态 HASH
-- ARGV[1]: scene
-- ARGV[2]: singleFlightKey
-- ARGV[3]: ownerId
-- ARGV[4]: traceId
-- ARGV[5]: ownerTimeoutMillis
-- ARGV[6]: redisTtlMillis
-- ARGV[7]: nowMillis
local key = KEYS[1]
local scene = ARGV[1]
local singleFlightKey = ARGV[2]
local ownerId = ARGV[3]
local traceId = ARGV[4]
local ownerTimeoutMillis = tonumber(ARGV[5])
local redisTtlMillis = tonumber(ARGV[6])
local nowMillis = tonumber(ARGV[7])

local function snapshot(action)
    return {
        action,
        redis.call('HGET', key, 'status') or '',
        redis.call('HGET', key, 'ownerId') or '',
        redis.call('HGET', key, 'fencingToken') or '0',
        redis.call('HGET', key, 'requestCount') or '0',
        redis.call('HGET', key, 'resultJson') or '',
        redis.call('HGET', key, 'errorType') or '',
        redis.call('HGET', key, 'traceId') or '',
        redis.call('HGET', key, 'heartbeatMillis') or '0',
        redis.call('HGET', key, 'scene') or scene,
        redis.call('HGET', key, 'singleFlightKey') or singleFlightKey
    }
end

if redis.call('EXISTS', key) == 0 then
    redis.call('HSET', key,
        'scene', scene,
        'singleFlightKey', singleFlightKey,
        'ownerId', ownerId,
        'fencingToken', '1',
        'status', 'RUNNING',
        'heartbeatMillis', tostring(nowMillis),
        'requestCount', '1',
        'resultJson', '',
        'errorType', '',
        'traceId', traceId)
    redis.call('PEXPIRE', key, redisTtlMillis)
    return snapshot('OWNER')
end

local requestCount = tonumber(redis.call('HGET', key, 'requestCount') or '0') + 1
redis.call('HSET', key, 'requestCount', tostring(requestCount))
redis.call('PEXPIRE', key, redisTtlMillis)

local status = redis.call('HGET', key, 'status') or ''
if status == 'SUCCESS' then
    return snapshot('REPLAY')
end

local heartbeatMillis = tonumber(redis.call('HGET', key, 'heartbeatMillis') or '0')
if status == 'FAILED' or (status == 'RUNNING' and nowMillis - heartbeatMillis > ownerTimeoutMillis) then
    local nextToken = tonumber(redis.call('HGET', key, 'fencingToken') or '0') + 1
    redis.call('HSET', key,
        'scene', scene,
        'singleFlightKey', singleFlightKey,
        'ownerId', ownerId,
        'fencingToken', tostring(nextToken),
        'status', 'RUNNING',
        'heartbeatMillis', tostring(nowMillis),
        'resultJson', '',
        'errorType', '',
        'traceId', traceId)
    redis.call('PEXPIRE', key, redisTtlMillis)
    return snapshot('OWNER')
end

return snapshot('FOLLOWER')
