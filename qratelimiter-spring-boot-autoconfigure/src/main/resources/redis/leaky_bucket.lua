-- leaky_bucket.lua
-- Redis 漏桶算法
-- KEYS[1]: 桶状态 key
-- ARGV[1]: 每个 interval 周期泄放的请求数
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
local water = tonumber(redis.call('HGET', bucketKey, 'water'))
local lastLeakTime = tonumber(redis.call('HGET', bucketKey, 'lastLeakTime'))

if water == nil or lastLeakTime == nil then
    water = 0
    lastLeakTime = currentTime
else
    if currentTime < lastLeakTime then
        currentTime = lastLeakTime
    end

    local elapsedMillis = currentTime - lastLeakTime
    if elapsedMillis > 0 then
        local leakRatePerMillis = freq / interval
        local leaked = elapsedMillis * leakRatePerMillis
        water = math.max(0, water - leaked)
        lastLeakTime = currentTime
    end
end

if water + 1 > capacity then
    redis.call('HSET', bucketKey, 'water', water, 'lastLeakTime', lastLeakTime)
    redis.call('PEXPIRE', bucketKey, expireMillis)
    return 0
end

water = water + 1
redis.call('HSET', bucketKey, 'water', water, 'lastLeakTime', lastLeakTime)
redis.call('PEXPIRE', bucketKey, expireMillis)
return 1
