package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @author: chake
 * @create 2025/7/24 17:14
 * @ClassName: LearningPointsListener
 * @Package: com.tianji.learning.mq
 * @Description: 消费 用于保存学习积分
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningPointsListener {

    private final IPointsRecordService pointsRecordService;

    /**
     * 监听签到消息
     *
     * @param msg
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "sign.points.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN))
    public void listenSignInListener(SignInMessage msg) {
        log.info("接收到签到消息: {}", msg);
        pointsRecordService.addPointRecord(msg, PointsRecordType.SIGN);
    }

    /**
     * 监听问答消息
     *
     * @param msg
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "qa.points.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY))
    public void listenWriteReplyListener(SignInMessage msg) {
        log.info("接收到问答消息: {}", msg);
    }
}
