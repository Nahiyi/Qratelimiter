-- sliding_window_counter.lua
-- Redis 滑动窗口计数器近似算法
-- KEYS[1]: 当前整窗口 Key
-- KEYS[2]: 上一整窗口 Key
-- ARGV[1]: 上一窗口权重 (0~1)
-- ARGV[2]: 频率阈值
-- ARGV[3]: 过期时间（毫秒）

local currentKey = KEYS[1]
local previousKey = KEYS[2]
local previousWeight = tonumber(ARGV[1])
local freq = tonumber(ARGV[2])
local expireMillis = tonumber(ARGV[3])

local currentCount = tonumber(redis.call('GET', currentKey) or '0')
local previousCount = tonumber(redis.call('GET', previousKey) or '0')
local estimate = currentCount + (previousCount * previousWeight)

if estimate >= freq then
    return 0
end

currentCount = redis.call('INCR', currentKey)
redis.call('PEXPIRE', currentKey, expireMillis)
return 1
