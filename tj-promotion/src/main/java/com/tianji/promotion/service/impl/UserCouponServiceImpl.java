package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.MyLockType;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    private final IExchangeCodeService exchangeCodeService;

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;

    private final ICouponScopeService couponScopeService;

    private final Executor calculateSolutionExecutor;

    @Override
    @MyLock(name = "lock:coupon:uid:#{id}")
    public void receiveCoupon(Long id) {
        // 1. 根据id查询优惠券信息 做相关校验
        if (id == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 从redis中获取优惠券信息
        Coupon coupon = getCouponByCache(id);
        if (coupon == null) {
            throw new BadRequestException(ErrorInfo.Msg.COUPON_NOT_EXIST);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException(ErrorInfo.Msg.COUPON_NOT_ISSUING);
        }
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException(ErrorInfo.Msg.COUPON_NOT_ENOUGH);
        }
        Long userId = UserContext.getUser();
//        // 从aop上下文中 获取当前类的代理对象
//        IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
//        userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);
//
        // 统计用户已领取优惠券数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + id;
        Long increment = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        if (increment > coupon.getUserLimit()) {
            throw new BizIllegalException(ErrorInfo.Msg.COUPON_USER_LIMIT);
        }

        // 修改优惠券的库存数量 -1
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        redisTemplate.opsForHash().increment(couponKey, "totalNum", -1);

        // 发送消息到mq 消息的内容为 用户id couponId
        UserCouponDTO msg = new UserCouponDTO();
        msg.setUserId(userId);
        msg.setCouponId(id);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,
                msg);
    }

    private Coupon getCouponByCache(Long id) {
        // 1. 拼接key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        // 2. 从redis中获取数据
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());
    }

    @Override
    public void exchangeCoupon(String code) {
        // 1. 校验code是否为空
        if (StringUtils.isBlank(code)) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 2. 解析兑换码得到自增id
        // 自增id
        long serialNum = CodeUtil.parseCode(code);
        log.debug("自增id: {}", serialNum);
        // 3. 判断兑换码是否已兑换  采用redis的bitmap setBit key offset 1  如果方法返回true代表兑换码已兑换
        boolean result = exchangeCodeService.updateExchangeCodeMark(serialNum, true);
        if (result) {
            // 说明兑换码已兑换
            throw new BizIllegalException(ErrorInfo.Msg.EXCHANGE_CODE_USED);
        }
        // 4. 判断优惠券是否存在  根据自增id 主键查询
        ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
        if (exchangeCode == null) {
            throw new BizIllegalException(ErrorInfo.Msg.EXCHANGE_CODE_NOT_EXIST);
        }
        // 5. 判断是否过期
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = exchangeCode.getExpiredTime();
        if (now.isAfter(expireTime)) {
            throw new BizIllegalException(ErrorInfo.Msg.EXCHANGE_CODE_EXPIRED);
        }

        Long userId = UserContext.getUser();
        Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
        if (coupon == null) {
            throw new BizIllegalException(ErrorInfo.Msg.COUPON_NOT_EXIST);
        }
        // 从aop上下文中 获取当前类的代理对象
        IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
        userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);
    }

    @Override
    @Transactional
    @MyLock(name = "lock:coupon:uid:#{userId}", lockType = MyLockType.RE_ENTRANT_LOCK, lockStrategy = MyLockStrategy.FAIL_FAST)
    public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {
        // 为了节省空间， Long类型 底层采用享元模式：当Long类型加载时会缓存一个数组-128~127，
        // 当基类long赋值Long会触发自动装箱，装箱的时候判断是在-128~127就会返回一个数组里缓存的对象，
        // 超过这个范围 就会产生新的对象 所以 Long 1L == Long 1L 结果为true 但 Long 129L == Long 129L 结果为false
        // Long.toString方法底层是new String 所以还是会产生新的对象
        // Long.toString.intern方法底层是强制使用常量池 所以会使用同一个字符串对象
        // 1.获取当前用户 对该优惠券 已领数量 user_coupon 条件userId couponId 统计数量
        Long count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException(ErrorInfo.Msg.COUPON_USER_LIMIT);
        }
        // 2. 优惠券已发放数量+1
        int num = couponMapper.incrIssueNum(coupon.getId());
        if (num == 0) {
            throw new BadRequestException(ErrorInfo.Msg.COUPON_NOT_ENOUGH);
        }
        // 3.生成用户券
        saveUserCoupon(userId, coupon);
        // 4. 更新兑换码兑换状态
        if (serialNum != null) {
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId, userId)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
    }

    @Override
    @Transactional
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {
        // 1. 从db中查询优惠券信息
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if (coupon == null) {
            return;
        }
        // 2. 优惠券已发放数量+1
        int num = couponMapper.incrIssueNum(coupon.getId());
        if (num == 0) {
            return;
        }
        // 3.生成用户券
        saveUserCoupon(msg.getUserId(), coupon);
    }

    @Override
    public List<CouponDiscountDTO> listDiscountSolution(List<OrderCourseDTO> dtoList) {
        // 1. 查询当前用户可用的优惠券 user_coupon 和 coupon 表  条件：userId 、status=1（未使用） 查 优惠券的规则 优惠券的id 用户券id
        // select c.id, c.discount_type, c.`specific`,c.discount_value, c.threshold_amount, c.max_discount_amount, uc.id
        // from coupon c inner join user_coupon uc on c.id = uc.coupon_id where uc.user_id = #{userId} and uc.status = 1;
        List<Coupon> couponList = getBaseMapper().listMyCoupon(UserContext.getUser());
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }
        log.debug("未经过筛选的优惠券");
        for (Coupon coupon : couponList) {
            log.debug("{}, {}", DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);
        }
        // 2. 初筛
        // 2.1 计算订单的总金额
        int totalAmount = dtoList.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        log.debug("订单的总金额: {}", totalAmount);
        // 2.2 校验优惠券是否可用
        List<Coupon> availableCouponList = couponList.stream()
                .filter(c -> DiscountStrategy.getDiscount(c.getDiscountType()).canUse(totalAmount, c))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCouponList)) {
            return CollUtils.emptyList();
        }
        log.debug("经过筛选的优惠券");
        for (Coupon coupon : availableCouponList) {
            log.debug("{}, {}", DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);
        }
        // 3. 细筛（需要考虑优惠券的限定范围） 排列组合
        Map<Coupon, List<OrderCourseDTO>> avaMap = listAvailableCoupon(availableCouponList, dtoList);
        if (avaMap.isEmpty()) {
            return CollUtils.emptyList();
        }
        Set<Map.Entry<Coupon, List<OrderCourseDTO>>> entries = avaMap.entrySet();
        log.debug("经过细筛的优惠券");
        for (Map.Entry<Coupon, List<OrderCourseDTO>> entry : entries) {
            log.debug("{}, {}", DiscountStrategy.getDiscount(entry.getKey().getDiscountType()).getRule(entry.getKey()),
                    entry.getKey());
            List<OrderCourseDTO> value = entry.getValue();
            for (OrderCourseDTO dto : value) {
                log.debug("可用课程: {}", dto);
            }
        }
        availableCouponList = new ArrayList<>(avaMap.keySet());
        log.debug("经过细筛的可用优惠券个数: {}", availableCouponList.size());
        for (Coupon coupon : availableCouponList) {
            log.debug("{},{}", DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon)
                    , coupon);
        }
        // 排列组合
        List<List<Coupon>> solutionList = PermuteUtil.permute(availableCouponList);
        for (Coupon coupon : availableCouponList) {
            solutionList.add(List.of(coupon));
        }
        log.debug("排列组合");
        for (List<Coupon> solution : solutionList) {
            List<Long> cIds = solution.stream().map(Coupon::getId).collect(Collectors.toList());
            log.debug("{}", cIds);
        }
        // 4. 计算每一种组合的优惠明细
        log.debug("多线程计算优惠明细");
        // 线程不安全
//        List<CouponDiscountDTO> discountDTOList = new ArrayList<>();
        List<CouponDiscountDTO> discountDTOList = Collections.synchronizedList(new ArrayList<>(solutionList.size()));
        CountDownLatch latch = new CountDownLatch(solutionList.size());
        for (List<Coupon> solution : solutionList) {
            CompletableFuture.supplyAsync(() -> calculateSolutionDiscount(avaMap, dtoList, solution), calculateSolutionExecutor)
                    .thenAccept(dto -> {
                        log.debug("最终优惠 {} 方案中优惠券id {}  规则 {}", dto.getDiscountAmount(), dto.getIds(), dto.getRules());
                        discountDTOList.add(dto);
                        latch.countDown();
                    });
        }
        try {
            boolean isDone = latch.await(2, TimeUnit.SECONDS);
            if (!isDone) {
                log.warn("等待超时，部分解决方案可能未计算完成");
            }
        } catch (InterruptedException e) {
            log.error("多线程计算优惠明细异常", e);
        }
        // 5. 筛选最优解
        return listBestSolution(discountDTOList);
    }

    // 计算最优解
    private List<CouponDiscountDTO> listBestSolution(List<CouponDiscountDTO> solutionList) {
        // 1. 创建两个map分别记录 用券相同，金额最高 金额相同，用券最少
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();
        // 2. 循环方案 向map中添加记录
        for (CouponDiscountDTO solution : solutionList) {
            // 2.1 对优惠券id 排序 转字符串 以逗号分隔
            String ids = solution.getIds().stream()
                    .sorted(Comparator.comparing(Long::longValue))
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            // 2.2 从moreDiscountMap中取出旧的记录 判断  如果当前方案的优惠金额 小于旧的方案金额 则方案忽略 处理下一个方案
            CouponDiscountDTO old = moreDiscountMap.get(ids);
            if (old != null && old.getDiscountAmount() >= solution.getDiscountAmount()) {
                continue;
            }
            // 添加更优方案到map中
            moreDiscountMap.put(ids, solution);
            // 2.3 从lessCouponMap中取出旧的记录 判断 如果当前方案的优惠券数量 大于旧的方案数量 则方案忽略 处理下一个方案
            old = lessCouponMap.get(solution.getDiscountAmount());
            if (old != null
                    && solution.getIds().size() > 1
                    && old.getIds().size() <= solution.getIds().size()) {
                continue;
            }
            // 添加更优方案到map中
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }
        // 3. 求两个map的交集
        Collection<CouponDiscountDTO> bestSolution = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());
        // 4. 队最终的方案结果 按优惠金额 倒序
        return bestSolution.stream()
                .sorted(Comparator.comparing(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
    }

    // 计算每一种组合的优惠明细
    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> avaMap,
                                                        List<OrderCourseDTO> courseList,
                                                        List<Coupon> solution) {
        // 1. 创建方案结果dto对象
        CouponDiscountDTO dto = new CouponDiscountDTO();
        // 2. 初始化商品id和商品折扣明细映射， 初始值为0
        Map<Long, Integer> detailMap = courseList.stream().collect(Collectors.toMap(OrderCourseDTO::getId, o -> 0));
        // 3. 计算该方案的优惠信息
        // 3.1 循环方案中优惠券
        for (Coupon coupon : solution) {
            // 3.2 取出该优惠券对应的可用课程
            List<OrderCourseDTO> availableCourseList = avaMap.get(coupon);
            // 3.3 计算可用课程的总金额 （商品价格-折扣明细）
            int totalAmount = availableCourseList.stream().mapToInt(c -> c.getPrice() - detailMap.get(c.getId())).sum();
            // 3.4 判断优惠券是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)) {
                // 优惠券不可用则跳过
                continue;
            }
            // 3.5 计算该优惠券使用后的折扣值
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            // 3.6 计算商品折扣明细， 更新到detailMap
            calculateDetailDiscount(detailMap, availableCourseList, totalAmount, discountAmount);
            // 3.7 累加每一个优惠券的优惠金额 赋值给方案结果dto对象
            // 只要执行到这里 说明该优惠券可用
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(dto.getDiscountAmount() + discountAmount);
        }
        return dto;
    }

    // 计算商品折扣明细
    private void calculateDetailDiscount(Map<Long, Integer> detailMap,
                                         List<OrderCourseDTO> availableCourseList,
                                         int totalAmount,
                                         int discountAmount) {
        // 目的；本方法就是优惠券使用后，计算每个商品的折扣明细
        // 规则： 前面的商品按比例计算，最后一个商品折扣明细 = 总金额 - 前面商品折扣明细之和
        // 循环可用商品
        int times = 0;
        // 剩余折扣金额
        int remainDiscount = discountAmount;
        for (OrderCourseDTO course : availableCourseList) {
            times++;
            int discount;
            if (times == availableCourseList.size()) {
                // 说明是最后一个商品
                discount = remainDiscount;
            } else {
                // 是前面商品，按比例计算
                // 此处的discountAmount是优惠券的折扣金额，totalAmount是订单的总金额，先乘后除 否则结果是零
                discount = course.getPrice() * discountAmount / totalAmount;
                remainDiscount -= discount;
            }
            detailMap.put(course.getId(), discount + detailMap.get(course.getId()));
        }
    }

    // 细筛（需要考虑优惠券的限定范围）
    private Map<Coupon, List<OrderCourseDTO>> listAvailableCoupon(List<Coupon> couponList, List<OrderCourseDTO> orderCourseList) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>();
        // 1. 循环遍历初筛的优惠券集合
        for (Coupon coupon : couponList) {
            // 2. 找出每一个优惠券的可用课程
            List<OrderCourseDTO> availableCourseList = orderCourseList;
            // 2.1 判断优惠券是否了限定范围 coupon.getSpecific为true
            if (coupon.getSpecific()) {
                // 2.2 查询限定范围 查询coupon_scope表 条件：coupon_id
                List<CouponScope> scopeList = couponScopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId())
                        .list();
                // 2.3 得到限定范围的课程id集合
                List<Long> scopeIdList = scopeList.stream()
                        .map(CouponScope::getBizId)
                        .collect(Collectors.toList());
                // 2.4 从orderCourse 订单中所有课程 筛选 该范围内的课程
                availableCourseList = orderCourseList.stream()
                        .filter(oc -> scopeIdList.contains(oc.getCateId()))
                        .collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourseList)) {
                continue;
            }
            // 3. 计算该优惠券 可用课程的中金额
            int totalAmount = availableCourseList.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            log.debug("该优惠券 可用课程的中金额: {}", totalAmount);
            // 4. 判断该优惠券是否可用 如果可用 则放入map中
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmount, coupon)) {
                map.put(coupon, availableCourseList);
            }
        }

        return map;
    }

    // 生成用户券
    private void saveUserCoupon(Long userId, Coupon coupon) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        // 优惠券有效期
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();

        if (termBeginTime == null && termEndTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = LocalDateTime.now().plusDays(coupon.getTermDays());
        }

        userCoupon.setTermBeginTime(termBeginTime);
        userCoupon.setTermEndTime(termEndTime);
        userCoupon.setCreateTime(LocalDateTime.now());
        userCoupon.setUpdateTime(LocalDateTime.now());

        this.save(userCoupon);
    }
}
