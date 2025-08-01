package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
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
 * @create 2025/7/19 10:11
 * @ClassName: LessonChangeListener
 * @Package: com.tianji.learning.mq
 * @Description:
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService lessonService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void onMsg(OrderBasicDTO dto) {
        log.info("LessonChangeListener接收到了消息: 用户{}，添加课程{}", dto.getUserId(), dto.getCourseIds());
        if (dto.getUserId() == null
                || dto.getOrderId() == null
                || CollUtils.isEmpty(dto.getCourseIds())) {
            return;
        }
        lessonService.addUserLesson(dto.getUserId(), dto.getCourseIds());
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void onMsg1(OrderBasicDTO dto) {
        log.info("LessonChangeListener接收到了消息: 用户{}，删除课程{}", dto.getUserId(), dto.getCourseIds());
        if (dto.getUserId() == null
                || dto.getOrderId() == null
                || CollUtils.isEmpty(dto.getCourseIds())) {
            return;
        }
        lessonService.deleteUserLesson(dto.getUserId(), dto.getCourseIds());
    }
}
