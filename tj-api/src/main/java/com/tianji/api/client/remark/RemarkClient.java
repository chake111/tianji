package com.tianji.api.client.remark;

import com.tianji.api.client.remark.fallback.RemarkClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author: chake
 * @create 2025/7/23 15:13
 * @ClassName: RemarkClient
 * @Package: com.tianji.api.client.remark
 * @Description:
 * @Version 1.0
 */
@FeignClient(value = "remark-service", fallbackFactory = RemarkClientFallback.class)
public interface RemarkClient {

    @GetMapping("/likes/list")
    Set<Long> getLikesStatusByBizIds(@RequestParam("bizIds") Collection<Long> bizIds);
}
