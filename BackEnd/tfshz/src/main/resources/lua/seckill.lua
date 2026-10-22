--[[
  秒杀资格校验 + 库存预扣（一次 Lua 调用内完成，Redis 单线程执行 => 天然原子）
  订单消息的投递不在这里：由 Java 侧根据 seckill.mq 选择 Kafka 或 Redis Stream，
  投递失败时调用方回滚预扣（见 VoucherOrderServiceImpl#seckillVoucher）。

  KEYS[1] = seckill:stock:{voucherId}    库存（String，预扣在这里）
  KEYS[2] = seckill:order:{voucherId}    已抢到资格的用户集合（Set，一人一单）
  KEYS[3] = seckill:meta:{voucherId}     活动元信息（Hash：begin / end，毫秒时间戳）

  ARGV[1] = userId
  ARGV[2] = voucherId
  ARGV[3] = orderId（脚本不再使用，保留参数位以便日志/排查对账）
  ARGV[4] = 当前时间戳（毫秒，由 Java 传入，避免 Lua 里 now 不可控）

  返回值：
    0  抢到资格（库存已预扣、用户已记录，等待调用方投递消息）
    1  库存不足
    2  重复下单（一人一单）
    3  秒杀尚未开始
    4  秒杀已经结束
   -2  库存未预热（key 不存在），调用方需要先从 DB 加载
]]

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local metaKey = KEYS[3]

local userId = ARGV[1]
local voucherId = ARGV[2]
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

-- 7. 订单消息的投递由 Java 侧完成（Kafka / Redis Stream 可切换），脚本只负责"抢到资格"。
--    若投递失败，调用方必须回滚上面的预扣（stock +1、SREM）—— 这是"MQ 与 Redis 解耦"的代价，
--    换来的是 Kafka 可替换：分区顺序、消费组横向扩展、DLQ 与重试策略交给 Kafka 原生能力。
return 0
