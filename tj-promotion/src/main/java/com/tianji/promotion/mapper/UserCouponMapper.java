package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author chake
 * @since 2025-07-28
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("select c.id, c.discount_type, c.`specific`, c.discount_value, c.threshold_amount, c.max_discount_amount, uc.id as creater " +
            "from coupon c inner join user_coupon uc on c.id = uc.coupon_id " +
            "where uc.user_id = #{userId} and uc.status = 1")
    List<Coupon> listMyCoupon(Long userId);
}
