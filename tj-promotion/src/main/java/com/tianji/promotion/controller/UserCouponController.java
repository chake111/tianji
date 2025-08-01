package com.tianji.promotion.controller;


import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author chake
 * @since 2025-07-28
 */
@RestController
@Api(tags = "用户优惠券相关接口")
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
public class UserCouponController {

    private final IUserCouponService userCouponService;

    @PostMapping("/{id}/receive")
    @ApiOperation("领取优惠券")
    public void receiveCoupon(@PathVariable Long id) {
        userCouponService.receiveCoupon(id);
    }

    @ApiOperation("兑换码兑换优惠券")
    @PostMapping("/{code}/exchange")
    public void exchangeCoupon(@PathVariable String code) {
        userCouponService.exchangeCoupon(code);
    }

    @ApiOperation("查询可用优惠券方案")
    @PostMapping("/available")
    public List<CouponDiscountDTO> listDiscountSolution(@RequestBody List<OrderCourseDTO> dtoList) {
        return userCouponService.listDiscountSolution(dtoList);
    }
}
