package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 604800L; // 7天

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String LOCK_SECKILL_KEY = "lock:seckill:";
    public static final Long LOCK_SECKILL = 10L;

    public static final String  SECKILL_ORDER_KEY =  "seckill:order:";


    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";


    public static final String USER_FOLLOW_KEY =  "user:follow:";
    public static final String USER_FANS_KEY =  "user:fans:";

    /**ƒ
     * 共同关注
     */
    public static final String USER_COMMON_FOLLOW_KEY =  "user:common:follow:";

    /**
     * 用户收件箱
     */
    public static final String USER_LETTERBOX_KEY =  "user:letterBox:";

}
