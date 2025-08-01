package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService couponScopeService;

    private final IExchangeCodeService exchangeCodeService;

    private final IUserCouponService userCouponService;

    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        // 1. dto -> po 保存优惠券 coupon 表
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        this.save(coupon);
        // 2. 判断是否限定了范围 dto.specific 如果为false直接return
        if (!dto.getSpecific()) {
            // 无范围限制
            return;
        }
        // 3. 如果dto.specific为true 则需要校验dto.scopes
        List<Long> scopes = dto.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException(ErrorInfo.Msg.COUPON_SCOPE_NOT_EMPTY);
        }
        // 4. 保存优惠券的限定范围 批量新增
        List<CouponScope> couponScopeList = scopes.stream()
                .map(scope -> new CouponScope().setCouponId(coupon.getId()).setBizId(scope).setType(1))
                .collect(Collectors.toList());
        couponScopeService.saveBatch(couponScopeList);
    }

    @Override
    public PageDTO<CouponPageVO> listCoupon(CouponQuery query) {
        // 1. 分页条件查询优惠券表 Coupon
        Page<Coupon> page = this.lambdaQuery()
                .eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 2. 封装vo返回
        List<CouponPageVO> voList = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page, voList);
    }

    @Override
    public void issueCoupon(CouponIssueFormDTO dto) {
        log.debug("发放优惠券，线程名：{}", Thread.currentThread().getName());
        // 1. 校验优惠券id是否存在
        Coupon coupon = this.getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException(ErrorInfo.Msg.COUPON_NOT_EXIST);
        }
        // 2. 校验优惠券状态 只用待发放和暂停状态才能发放
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BizIllegalException(ErrorInfo.Msg.COUPON_STATUS_NOT_ALLOW_ISSUE);
        }
        LocalDateTime now = LocalDateTime.now();
        // 该变量用于判断是否立刻发放优惠券
        boolean isBeginIssue = dto.getIssueBeginTime() == null || !dto.getIssueBeginTime().isAfter(now);
        // 3. 修改优惠券 领取开始时间和领取结束时间 使用有效期和结束日期 天数 状态
        Coupon tmp = BeanUtils.copyBean(dto, Coupon.class);
        if (isBeginIssue) {
            tmp.setStatus(CouponStatus.ISSUING);
            tmp.setIssueBeginTime(now);
        } else {
            tmp.setStatus(CouponStatus.UN_ISSUE);
        }
        this.updateById(tmp);
        // 4. 如果优惠券是立刻发放，则将优惠券信息（优惠券id、领券开始结束时间、发行总数量、限领数量）存入redis， 采用hash结构存储
        if (isBeginIssue) {
            String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId();
            redisTemplate.opsForHash().put(key, "issueBeginTime", String.valueOf(DateUtils.toEpochMilli(now)));
            redisTemplate.opsForHash().put(key, "issueEndTime", String.valueOf(DateUtils.toEpochMilli(dto.getIssueEndTime())));
            redisTemplate.opsForHash().put(key, "totalNum", String.valueOf(coupon.getTotalNum()));
            redisTemplate.opsForHash().put(key, "userLimit", String.valueOf(coupon.getUserLimit()));
        }
        // 5. 如果优惠券的 领取方式为指定发放， 则需要生成兑换码
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            // 兑换码兑换的截至时间，就是优惠券领取的截止时间 该时间是从前端传过来的封装在tmp中
            coupon.setIssueEndTime(tmp.getIssueEndTime());
            // 异步生成兑换码
            exchangeCodeService.asyncGenerateExchangeCode(coupon);
        }
    }

    @Override
    public List<CouponVO> listIssuingCoupon() {
        // 1. 查询coupon表 条件：发放中 手动领取
        List<Coupon> couponList = this.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }
        // 2. 查询用户券表user_coupon 条件：当前用户 发放中优惠券id
        // 正在发放中的优惠券id集合
        Set<Long> couponIdSet = couponList.stream().map(Coupon::getId).collect(Collectors.toSet());
        // 当前用户，针对发放中的优惠券领取记录
        List<UserCoupon> list = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIdSet)
                .list();
        // 2.1 统计当前用户 针对每一个券 的已经领取数量
        Map<Long, Long> issueMap = list.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.2 统计当前用户 针对每一个券 的已领取且未使用的数量
        Map<Long, Long> usedMap = list.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2. po -> vo返回
        List<CouponVO> voList = new ArrayList<>();
        for (Coupon c : couponList) {
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            // 优惠券有剩余 （issue_num < total_num） 且 （统计用户券表user_coupon取出当前用户已领取数量 < user_limit）
            Long issueNum = issueMap.getOrDefault(c.getId(), 0L);
            boolean available = c.getIssueNum() < c.getTotalNum() && issueNum < c.getUserLimit();
            vo.setAvailable(available);
            // 统计用户券表user_coupon取出当前用户已领取且未使用的券数量
            boolean received = usedMap.getOrDefault(c.getId(), 0L) > 0;
            vo.setReceived(received);
            voList.add(vo);
        }
        return voList;
    }
}
