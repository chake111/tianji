package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.C;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author chake
 * @since 2025-07-27
 */
@RestController
@Api(tags = "优惠券相关接口")
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final ICouponService couponService;

    @PostMapping
    @ApiOperation("新增优惠券-管理端")
    public void saveCoupon(@RequestBody @Validated CouponFormDTO dto) {
        couponService.saveCoupon(dto);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询优惠券-管理端")
    public PageDTO<CouponPageVO> listCoupon(CouponQuery query) {
        return couponService.listCoupon(query);
    }

    @PutMapping("/{id}/issue")
    @ApiOperation("发放优惠券-管理端")
    public void issueCoupon(@RequestBody @Validated CouponIssueFormDTO dto) {
        couponService.issueCoupon(dto);
    }

    @GetMapping("/list")
    @ApiOperation("查询发放中的优惠券-用户端")
    public List<CouponVO> listIssuingCoupon() {
        return couponService.listIssuingCoupon();
    }


}
