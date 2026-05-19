-- 原子刷新 Career single-flight owner 心跳
-- KEYS[1]: single-flight 状态 HASH
-- ARGV[1]: ownerId
-- ARGV[2]: fencingToken
-- ARGV[3]: nowMillis
-- ARGV[4]: redisTtlMillis
local key = KEYS[1]
if redis.call('HGET', key, 'status') ~= 'RUNNING' then return {0} end
if redis.call('HGET', key, 'ownerId') ~= ARGV[1] then return {0} end
if redis.call('HGET', key, 'fencingToken') ~= ARGV[2] then return {0} end

redis.call('HSET', key, 'heartbeatMillis', ARGV[3])
redis.call('PEXPIRE', key, tonumber(ARGV[4]))
return {1}
