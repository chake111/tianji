package com.tianji.learning.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.tianji.learning.utils.TableInfoContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MybatisConfiguration {

    @Bean
    public DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor() {
        DynamicTableNameInnerInterceptor interceptor = new DynamicTableNameInnerInterceptor();
        interceptor.setTableNameHandler((sql, tableName) -> {
            if ("points_board".equals(tableName)) {
                return TableInfoContext.getInfo() == null ? tableName : TableInfoContext.getInfo();
            }
            return tableName;
        });
        return interceptor;
    }
}