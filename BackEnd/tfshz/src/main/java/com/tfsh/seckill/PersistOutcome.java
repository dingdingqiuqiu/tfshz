package com.tfsh.seckill;

/**
 * 订单落库的结果分类。调用方（消费者/同步兜底）根据它决定 ACK、回滚还是重试。
 */
public enum PersistOutcome {

    /** 落库成功 */
    SUCCESS,

    /** 同一个 orderId 已经落过库：重复投递或重试，属于幂等成功 */
    ALREADY_EXISTS,

    /** 该用户在这张券上已经有订单了（DB 侧一人一单兜底命中） */
    USER_DUPLICATE,

    /** DB 条件更新失败：数据库库存已经为 0，说明 Redis 预扣和 DB 库存不同步 */
    STOCK_EXHAUSTED,

    /** 消息字段缺失，无法落库 */
    INVALID
}
