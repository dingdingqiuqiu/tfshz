--[[
  秒杀资格校验 + 库存预扣 + 订单入队（一次 Lua 调用内全部完成，Redis 单线程执行 => 天然原子）

  KEYS[1] = seckill:stock:{voucherId}    库存（String，预扣在这里）
  KEYS[2] = seckill:order:{voucherId}    已抢到资格的用户集合（Set，一人一单）
  KEYS[3] = seckill:meta:{voucherId}     活动元信息（Hash：begin / end，毫秒时间戳）
  KEYS[4] = stream.orders                订单消息流（XADD 入队）

  ARGV[1] = userId
  ARGV[2] = voucherId
  ARGV[3] = orderId
  ARGV[4] = 当前时间戳（毫秒，由 Java 传入，避免 Lua 里 now 不可控）

  返回值：
    0  抢到资格（库存已预扣、用户已记录、订单消息已入队）
    1  库存不足
    2  重复下单（一人一单）
    3  秒杀尚未开始
    4  秒杀已经结束
   -2  库存未预热（key 不存在），调用方需要先从 DB 加载
]]

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local metaKey = KEYS[3]
local streamKey = KEYS[4]

local userId = ARGV[1]
local voucherId = ARGV[2]
local orderId = ARGV[3]
local now = tonumber(ARGV[4])

-- 1. 库存：key 不存在时 GET 返回 false，tonumber(false) 得到 nil，
--    后面直接比较会抛 "attempt to compare nil with number"，所以必须显式兜成 -1
local stock = tonumber(redis.call('get', stockKey) or '-1')
if stock == nil or stock < 0 then
    return -2
end

-- 2. 时间窗：活动没配置时间就不做限制，交给 DB 侧兜底
local beginTime = redis.call('hget', metaKey, 'begin')
local endTime = redis.call('hget', metaKey, 'end')
if beginTime and now < tonumber(beginTime) then
    return 3
end
if endTime and now > tonumber(endTime) then
    return 4
end

-- 3. 一人一单：同一个用户在这张券上只能抢到一次
if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

-- 4. 库存不足直接返回，不扣减
if stock <= 0 then
    return 1
end

-- 5. 扣库存（DECR 是原子的，Lua 内部无需再加锁）
redis.call('decr', stockKey)
-- 6. 记录下单用户，供下一次资格校验
redis.call('sadd', orderKey, userId)
-- 7. 订单消息入队：后面由消费者组异步落库，请求线程不再等 DB
--    XADD 的 * 由 Redis 生成时间序列 ID；Redis 7 用效果复制，脚本里可以用
redis.call('xadd', streamKey, '*',
        'orderId', orderId,
        'userId', userId,
        'voucherId', voucherId,
        'createTime', now)

return 0
