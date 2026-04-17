-- token_bucket.lua
-- Redis 令牌桶算法
-- KEYS[1]: 桶状态 key
-- ARGV[1]: 每个 interval 周期补充的令牌数
-- ARGV[2]: interval（毫秒）
-- ARGV[3]: 桶容量
-- ARGV[4]: 过期时间（毫秒）

local bucketKey = KEYS[1]
local freq = tonumber(ARGV[1])
local interval = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])
local expireMillis = tonumber(ARGV[4])

local timeParts = redis.call('TIME')
local currentTime = math.floor((tonumber(timeParts[1]) * 1000) + (tonumber(timeParts[2]) / 1000))
local tokens = tonumber(redis.call('HGET', bucketKey, 'tokens'))
local lastRefillTime = tonumber(redis.call('HGET', bucketKey, 'lastRefillTime'))

if tokens == nil or lastRefillTime == nil then
    tokens = capacity
    lastRefillTime = currentTime
else
    if currentTime < lastRefillTime then
        currentTime = lastRefillTime
    end

    local elapsedMillis = currentTime - lastRefillTime
    if elapsedMillis > 0 then
        local refillRatePerMillis = freq / interval
        local refill = elapsedMillis * refillRatePerMillis
        tokens = math.min(capacity, tokens + refill)
        lastRefillTime = currentTime
    end
end

if tokens < 1 then
    redis.call('HSET', bucketKey, 'tokens', tokens, 'lastRefillTime', lastRefillTime)
    redis.call('PEXPIRE', bucketKey, expireMillis)
    return 0
end

tokens = tokens - 1
redis.call('HSET', bucketKey, 'tokens', tokens, 'lastRefillTime', lastRefillTime)
redis.call('PEXPIRE', bucketKey, expireMillis)
return 1
