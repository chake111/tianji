package com.tianji.remark.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author: chake
 * @create 2025/7/23 11:34
 * @ClassName: LikedRecordListener
 * @Package: com.tianji.remark.mq
 * @Description: QA点赞记录 消费者
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikedRecordListener {

    private final ILikedRecordService likedRecordService;

    /**
     * 监听到点赞记录
     */

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "qa.liked.record.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.QA_LIKED_RECORD_KEY))
    public void onMsg(List<LikedRecord> recordList) {
        log.debug("LikedRecordListener 监听到点赞记录消息：{}", recordList);
        likedRecordService.saveBatch(recordList);
    }
}
