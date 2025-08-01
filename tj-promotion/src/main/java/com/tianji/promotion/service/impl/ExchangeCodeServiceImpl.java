package com.tianji.promotion.service.impl;

import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;

    @Async("generateExchangeCodeExecutor")
    @Override
    public void asyncGenerateExchangeCode(Coupon coupon) {
        log.debug("生成兑换码，线程名：{}，优惠券id：{}", Thread.currentThread().getName(), coupon.getId());
        Integer totalNum = coupon.getTotalNum();
        // 1.生成自增id 借助redis incr命令
        Long increment = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if (increment == null) {
            return;
        }
        // 最大本地自增id最大值
        int maxSerialNum = increment.intValue();
        // 自增id起始值
        int begin = maxSerialNum - totalNum + 1;
        // 2. 调用工具类生成兑换码
        List<ExchangeCode> list = new ArrayList<>();
        for (int serialNum = begin; serialNum <= maxSerialNum; serialNum++) {
            // 参数1：自增id值，参数2：优惠券id
            String code = CodeUtil.generateCode(serialNum, coupon.getId());
            ExchangeCode exchangeCode = new ExchangeCode();
            exchangeCode.setId(serialNum);
            exchangeCode.setCode(code);
            exchangeCode.setExchangeTargetId(coupon.getId());
            exchangeCode.setExpiredTime(coupon.getIssueEndTime());
            list.add(exchangeCode);
        }
        // 3. 将兑换码信息保存db exchange_code 批量保存
        this.saveBatch(list);
        // 4. 写入Redis缓存，member：couponId，score：兑换码最大序列号 （）
        redisTemplate.opsForZSet().add(PromotionConstants.COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
    }

    @Override
    public boolean updateExchangeCodeMark(long serialNum, boolean flag) {
        String key = PromotionConstants.COUPON_CODE_MAP_KEY;
        // 修改兑换码的自增id 对应的offset 的值
        Boolean result = redisTemplate.opsForValue().setBit(key, serialNum, flag);
        return result != null && result;
    }
}
