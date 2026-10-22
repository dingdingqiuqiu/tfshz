--[[
  预扣库存回滚：异步落库阶段发现“DB 库存已经不够”或“用户其实已经下过单”时，
  把这笔预扣原路退回，保证 Redis 库存不会因为失败订单被吃掉。

  KEYS[1] = seckill:stock:{voucherId}
  KEYS[2] = seckill:order:{voucherId}
  ARGV[1] = userId

  返回 1 表示回滚完成。INCR 与 SREM 在同一脚本里执行，不会出现“只退库存、没删用户”的半截状态。
]]

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userId = ARGV[1]

redis.call('incr', stockKey)
redis.call('srem', orderKey, userId)

return 1
