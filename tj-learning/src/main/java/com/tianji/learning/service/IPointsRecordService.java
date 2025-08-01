package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.msg.SignInMessage;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author chake
 * @since 2025-07-24
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointRecord(SignInMessage msg, PointsRecordType type);

    List<PointsStatisticsVO> queryMyTodayPoints();
}
