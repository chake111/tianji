package com.tianji.api.config;

import com.tianji.api.client.learning.fallback.LearningClientFallback;
import com.tianji.api.client.promotion.fallback.PromotionFallback;
import com.tianji.api.client.remark.fallback.RemarkClientFallback;
import com.tianji.api.client.trade.fallback.TradeClientFallback;
import com.tianji.api.client.user.fallback.UserClientFallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FallbackConfig {
    @Bean
    public LearningClientFallback learningClientFallback() {
        return new LearningClientFallback();
    }

    @Bean
    public TradeClientFallback tradeClientFallback() {
        return new TradeClientFallback();
    }

    @Bean
    public UserClientFallback userClientFallback() {
        return new UserClientFallback();
    }

    // remark服务降级类对象
    @Bean
    public RemarkClientFallback remarkClientFallback() {
        return new RemarkClientFallback();
    }

    // promotion服务降级类对象
    @Bean
    public PromotionFallback promotionFallback() {
        return new PromotionFallback();
    }
}
