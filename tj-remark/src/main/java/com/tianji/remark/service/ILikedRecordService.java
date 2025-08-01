package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author chake
 * @since 2025-07-23
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    void addLikedRecord(LikeRecordFormDTO dto);

    Set<Long> getLikesStatusByBizIds(List<Long> bizIds);

    void readLikedTimesAndSendMessage(String bizType, int maxBizSize);


    /**
     * 点赞记录持久化
     */
    void persistenceLikedRecordAndSendMessage(String bizType);
}
