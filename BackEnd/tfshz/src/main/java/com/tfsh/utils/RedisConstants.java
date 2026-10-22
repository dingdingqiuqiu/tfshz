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
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
