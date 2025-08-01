package com.tianji.learning.mq;

import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: chake
 * @create 2025/7/23 11:34
 * @ClassName: LikedRecordListener
 * @Package: com.tianji.learning.mq
 * @Description: QA问答系统 消费者
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikedRecordListener {

    private final IInteractionReplyService replyService;

    /**
     * 监听到点赞次数
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.QA_LIKED_TIMES_KEY))
    public void onMsg(List<LikedTimesDTO> list) {
        log.debug("LikedRecordListener 监听到点赞次数消息：{}", list);
        // 消息vo转po
        List<InteractionReply> replyList = new ArrayList<>();
        for (LikedTimesDTO dto : list) {
            InteractionReply reply = new InteractionReply();
            reply.setId(dto.getBizId());
            reply.setLikedTimes(dto.getLikedTimes());
            replyList.add(reply);
        }
        replyService.updateBatchById(replyList);
    }
}
