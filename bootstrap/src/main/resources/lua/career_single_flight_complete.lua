-- 原子完成 Career single-flight，只有当前 owner 与 fencing token 匹配才能写终态
-- KEYS[1]: single-flight 状态 HASH
-- ARGV[1]: ownerId
-- ARGV[2]: fencingToken
-- ARGV[3]: status
-- ARGV[4]: resultJson
-- ARGV[5]: errorType
-- ARGV[6]: nowMillis
-- ARGV[7]: redisTtlMillis
local key = KEYS[1]
if redis.call('HGET', key, 'status') ~= 'RUNNING' then return {0} end
if redis.call('HGET', key, 'ownerId') ~= ARGV[1] then return {0} end
if redis.call('HGET', key, 'fencingToken') ~= ARGV[2] then return {0} end

redis.call('HSET', key,
    'status', ARGV[3],
    'resultJson', ARGV[4],
    'errorType', ARGV[5],
    'heartbeatMillis', ARGV[6])
redis.call('PEXPIRE', key, tonumber(ARGV[7]))
return {1}
