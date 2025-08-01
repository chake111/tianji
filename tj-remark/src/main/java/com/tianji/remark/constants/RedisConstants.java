package com.tianji.remark.constants;

/**
 * @author: chake
 * @create 2025/7/23 16:16
 * @ClassName: RedisContants
 * @Package: com.tianji.remark.constants
 * @Description:
 * @Version 1.0
 */

public interface RedisConstants {
    /*给业务点赞的用户集合的KEY前缀，后缀是业务id*/
    String LIKE_BIZ_KEY_PREFIX = "likes:set:type:{}:biz:{}";
    /*业务点赞数统计的KEY前缀，后缀是业务类型*/
    String LIKES_TIMES_KEY_PREFIX = "likes:times:type:";
}
