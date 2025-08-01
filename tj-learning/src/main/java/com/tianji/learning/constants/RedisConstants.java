package com.tianji.learning.constants;

/**
 * @author: chake
 * @create 2025/7/24 10:47
 * @ClassName: RedisConstants
 * @Package: com.tianji.learning.constants
 * @Description:
 * @Version 1.0
 */

public interface RedisConstants {
    /**
     * 用户签到记录的key前缀  完整key为 sign:uid:{uid}:{date}
     */
    String SIGN_RECORD_KEY_PREFIX = "sign:uid:";

    /**
     * 积分排行榜的key前缀  完整key为 boards:年月
     */
    String POINTS_BOARD_KEY_PREFIX = "boards:";
}
