package com.tianji.learning.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * &#064;create  2025/7/24 10:44
 * &#064;ClassName:  ISignRecordServiceImpl
 * &#064;Package:  com.tianji.learning.service.impl
 * &#064;Description:
 * &#064;Version  1.0
 *
 * @author chake
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecords() {
        // 1. 获取用户id
        Long userId = UserContext.getUser();
        // 2. 拼接key
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        // 3. 利用bitset命令 将签到记录保存到redis的bitmap中 需要校验是否已经签到过
        Boolean setBit = redisTemplate.opsForValue().setBit(key, now.getDayOfMonth() - 1, true);
        if (Boolean.TRUE.equals(setBit)) {
            throw new BizIllegalException(ErrorInfo.Msg.SIGN_RECORD_ALREADY_EXISTS);
        }
        // 4. 计算连续签到天数
        int days = countDays(key, now.getDayOfMonth());
        // 5. 计算连续签到奖励积分
        int rewardPoints = 0;
        switch (days) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
        }

        // 6. 保存积分 发送消息
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));
        // 7. 封装vo返回签到结果
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(days);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    @Override
    public Byte[] getSignRecords() {
        // 1. 获取用户id
        Long userId = UserContext.getUser();
        // 2. 拼接key
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        log.info("获取用户{}本月签到记录的key：{}", userId, key);
        // 3. 利用bitfield命令 获取本月1号到现在所有的签到数据
        // bitfield key get u天数 0
        int dayOfMonth = now.getDayOfMonth();
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtil.isEmpty(bitField)) {
            return new Byte[0];
        }
        Long num = bitField.get(0);
        int offset = dayOfMonth - 1;
        // 4. 利用与运算& 位移 封装
        Byte[] bytes = new Byte[dayOfMonth];
        while (offset >= 0) {
            bytes[offset] = (byte) (num & 1);
            offset--;
            num = num >>> 1;
        }
        log.info("本月1号到现在所有的签到数据 拿到的是二进制：{}", (Object) bytes);
        return bytes;
    }

    // 计算连续签到天数
    private int countDays(String key, int dayOfMonth) {
        // 1. 求本月1号到现在所有的签到数据 bitfield 得到十进制
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtil.isEmpty(bitField)) {
            return 0;
        }
        //  本月1号到现在所有的签到数据 拿到的是十进制
        Long num = bitField.get(0);
        log.info("本月1号到现在所有的签到数据 拿到的是十进制：{}", num);
        // 2. num 转二进制 从后往前推共有多少个1 与运算& 右移
        int counter = 0;
        while ((num & 1) == 1) {
            counter++;
            // 右移一位
            num = num >>> 1;
        }
        return counter;
    }
}
