package com.itheima;

import com.tianji.api.client.remark.RemarkClient;
import com.tianji.learning.LearningApplication;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;


/**
 * @author: chake
 * @create 2025/7/23 15:33
 * @ClassName: RemarkClientFeignTest
 * @Package: com.itheima
 * @Description:
 * @Version 1.0
 */
@SpringBootTest(classes = LearningApplication.class)
public class RemarkClientFeignTest {
    @Autowired
    private RemarkClient remarkClient;
    @Test
    public void test(){
        Set<Long> bizLied = remarkClient.getLikesStatusByBizIds(Lists.list(1947609877564280833L));
        System.out.println(bizLied);
    }
}
