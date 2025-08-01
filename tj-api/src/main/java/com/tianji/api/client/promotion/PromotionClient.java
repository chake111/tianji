package com.tianji.api.client.promotion;

import com.tianji.api.client.promotion.fallback.PromotionFallback;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * @author chake
 * @description 促销管理客户端接口
 * @since 2025/7/31
 */
@FeignClient(value = "promotion-service", fallbackFactory = PromotionFallback.class)
public interface PromotionClient {
    @PostMapping("/user-coupons/available")
    List<CouponDiscountDTO> listDiscountSolution(@RequestBody List<OrderCourseDTO> dtoList);
}
