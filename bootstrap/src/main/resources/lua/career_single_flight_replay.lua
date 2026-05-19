-- 查询 Career single-flight 是否已有可回放成功结果
-- KEYS[1]: single-flight 状态 HASH
local key = KEYS[1]
if redis.call('EXISTS', key) == 0 then return {'NONE'} end
if redis.call('HGET', key, 'status') ~= 'SUCCESS' then return {'NONE'} end
if (redis.call('HGET', key, 'resultJson') or '') == '' then return {'NONE'} end

return {
    'REPLAY',
    redis.call('HGET', key, 'status') or '',
    redis.call('HGET', key, 'ownerId') or '',
    redis.call('HGET', key, 'fencingToken') or '0',
    redis.call('HGET', key, 'requestCount') or '0',
    redis.call('HGET', key, 'resultJson') or '',
    redis.call('HGET', key, 'errorType') or '',
    redis.call('HGET', key, 'traceId') or '',
    redis.call('HGET', key, 'heartbeatMillis') or '0',
    redis.call('HGET', key, 'scene') or '',
    redis.call('HGET', key, 'singleFlightKey') or ''
}
