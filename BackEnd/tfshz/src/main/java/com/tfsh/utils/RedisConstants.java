package com.tfsh.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;

    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    /** refresh token 白名单：login:refresh:{userId}:{jti} -> "1"，存活时间与 refresh 有效期一致 */
    public static final String LOGIN_REFRESH_KEY = "login:refresh:";

    /** （可选增强）access 黑名单：login:blacklist:{jti}，需要“秒级踢人”时使用，见附录 9.2 */
    public static final String LOGIN_BLACKLIST_KEY = "login:blacklist:";
    
    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    /** 评论点赞：comment:liked:{commentId}，ZSet 存 userId，用来做“一人一赞”去重 */
    public static final String COMMENT_LIKED_KEY = "comment:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    // ============ 秒杀：Redis 预扣库存 + MQ（Kafka / Redis Stream）异步下单 ============
    /** 一人一单集合：seckill:order:{voucherId} -> Set<userId>，Lua 里做资格校验 */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    /** 活动元信息：seckill:meta:{voucherId} -> hash{begin,end}，毫秒时间戳，Lua 里校验时间窗 */
    public static final String SECKILL_META_KEY = "seckill:meta:";
    /** 订单消息流（seckill.mq=stream 时使用；默认走 Kafka） */
    public static final String SECKILL_ORDER_STREAM = "stream.orders";
    /** 消费者组名 */
    public static final String SECKILL_ORDER_GROUP = "seckill-group";
    /** 死信队列：重试耗尽或业务失败（DB 库存不足/重复下单）的消息落这里，便于人工对账 */
    public static final String SECKILL_ORDER_DLQ = "stream.orders.dlq";
    /** 幂等回滚标记：seckill:rolledback:{orderId}，保证同一条消息只回滚一次预扣 */
    public static final String SECKILL_ROLLBACK_KEY = "seckill:rolledback:";
    /** 下单锁（Redisson）：lock:order:{voucherId}:{userId}，落库侧的一人一单兜底 */
    public static final String SECKILL_LOCK_KEY = "lock:order:";
    /** 单条消息最多投递次数，超过进死信 */
    public static final long SECKILL_MAX_DELIVERY = 5L;
    /** pending 消息空闲多久才允许认领（秒）：必须大于单条处理耗时，否则会抢走别人正在处理的消息 */
    public static final long SECKILL_PENDING_IDLE_SECONDS = 60L;

    // ============ 多级缓存（秒杀券列表：本地 Caffeine + Redis 逻辑过期） ============
    public static final String CACHE_VOUCHER_KEY = "cache:voucher:";
    /** Redis 层逻辑过期时间：30 分钟 */
    public static final Long CACHE_VOUCHER_TTL = 30L;
    /** Caffeine 本地过期时间：5 秒。用短 TTL 换集群一致性，不做广播失效 */
    public static final Long CACHE_VOUCHER_LOCAL_TTL = 5L;

    // ============ 缓存删除补偿队列（Redis Stream） ============
    public static final String CACHE_DELETE_STREAM = "stream.cache-delete";
    public static final String CACHE_DELETE_GROUP = "g-cache";
    /** 最大重试次数，耗尽后记录告警日志，人工介入 */
    public static final int CACHE_DELETE_MAX_RETRY = 10;
}
