package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author: chake
 * @create 2025/7/23 16:52
 * @ClassName: LikedTimesCheckTask
 * @Package: com.tianji.remark.task
 * @Description:
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikedRecordCheckTask {
    // 业务类型
    private static final List<String> BIZ_TYPES = List.of("QA", "NOTE");
    private final ILikedRecordService likedRecordService;

    // 每天凌晨两点执行
    @Scheduled(cron = "0 0 2 * * ?")
//    @Scheduled(cron = "0 * * * * ?")
    public void likedRecordCheck() {
        log.info("LikedRecordCheckTask 点赞记录消费任务开始执行");
        // 查询所有业务类型
        for (String bizType : BIZ_TYPES) {
            log.info("业务类型：{}", bizType);
            likedRecordService.persistenceLikedRecordAndSendMessage(bizType);
        }
    }
}
