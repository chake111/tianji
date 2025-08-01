package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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
public class LikedTimesCheckTask {
    // 业务类型
    private static final List<String> BIZ_TYPES = List.of("QA","NOTE");
    // 任务每次取的biz数量 防止一次性往mq发送消息太多
    private static final int MAX_BIZ_SIZE = 30;
    private final ILikedRecordService likedRecordService;

    // 每20秒执行一次 将redis中 业务类型 下面  某业务的点赞总数 发消息通知到mq
    @Scheduled(cron = "0/40 * * * * ?")
    public void checkLikedTimes() {
        log.info("LikedTimesCheckTask 开始执行");
        for (String bizType :BIZ_TYPES) {
            likedRecordService.readLikedTimesAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }
}
