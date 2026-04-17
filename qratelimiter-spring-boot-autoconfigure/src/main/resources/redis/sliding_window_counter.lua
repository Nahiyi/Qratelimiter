-- sliding_window_counter.lua
-- Redis 滑动窗口计数器近似算法
-- KEYS[1]: 业务 key 前缀（不含窗口起点）
-- ARGV[1]: 窗口长度（毫秒）
-- ARGV[2]: 频率阈值
-- ARGV[3]: 过期时间（毫秒）

local keyPrefix = KEYS[1]
local interval = tonumber(ARGV[1])
local freq = tonumber(ARGV[2])
local expireMillis = tonumber(ARGV[3])

-- 统一使用 Redis 服务器时间，避免多实例本地时间偏差导致窗口错位
local timeParts = redis.call('TIME')
local currentTime = math.floor((tonumber(timeParts[1]) * 1000) + (tonumber(timeParts[2]) / 1000))
local currentWindowStart = currentTime - (currentTime % interval)
local previousWindowStart = currentWindowStart - interval
local elapsedInCurrentWindow = currentTime - currentWindowStart
local previousWeight = 1 - (elapsedInCurrentWindow / interval)

local currentKey = keyPrefix .. ":" .. currentWindowStart
local previousKey = keyPrefix .. ":" .. previousWindowStart

local currentCount = tonumber(redis.call('GET', currentKey) or '0')
local previousCount = tonumber(redis.call('GET', previousKey) or '0')
local estimate = currentCount + (previousCount * previousWeight)

if estimate >= freq then
    return 0
end

currentCount = redis.call('INCR', currentKey)
redis.call('PEXPIRE', currentKey, expireMillis)
return 1
