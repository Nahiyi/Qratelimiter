-- sliding_window_log.lua
-- Redis 滑动窗口日志算法限流脚本
-- 使用 ZSet 存储时间戳，Score = 时间戳，Member = 时间戳:唯一ID
--
-- 参数说明：
-- KEYS[1]: 限流键 (如: qratelimiter:sliding_window_log:123)，如 userId=123
-- ARGV[1]: 窗口起始时间戳
-- ARGV[2]: 频率限制
-- ARGV[3]: 当前时间戳（Score）
-- ARGV[4]: 过期时间（秒）
-- ARGV[5]: 唯一标识（解决同一毫秒并发问题）

local key = KEYS[1]
local windowStart = tonumber(ARGV[1])
local freq = tonumber(ARGV[2])
local currentTime = tonumber(ARGV[3])
local expireTime = tonumber(ARGV[4])
local uniqueId = ARGV[5]

-- 1. 删除窗口外的数据（ZREMRANGEBYSCORE 移除 score < windowStart 的元素）
redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)

-- 2. 获取当前窗口内的请求数
local count = redis.call('ZCARD', key)

-- 3. 判断是否超限
if count < freq then
    -- 4. Member = 时间戳:唯一标识，解决ZSet Member唯一性问题，允许同一毫秒多个请求
    local member = currentTime .. ":" .. uniqueId
    redis.call('ZADD', key, currentTime, member)
    -- 5. 设置过期时间（防止内存泄漏）
    redis.call('EXPIRE', key, expireTime)
    return 1  -- 允许通过
else
    return 0  -- 拒绝
end
