//package com.tianji.remark.service.impl;
//
//import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
//import com.tianji.common.constants.MqConstants;
//import com.tianji.common.utils.CollUtils;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.remark.domain.dto.LikeRecordFormDTO;
//import com.tianji.api.dto.msg.LikedTimesDTO;
//import com.tianji.remark.domain.po.LikedRecord;
//import com.tianji.remark.mapper.LikedRecordMapper;
//import com.tianji.remark.service.ILikedRecordService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * <p>
// * 点赞记录表 服务实现类
// * </p>
// *
// * @author chake
// * @since 2025-07-23
// */
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//    private final RabbitMqHelper rabbitMqHelper;
//
//    @Override
//    public void addLikedRecord(LikeRecordFormDTO dto) {
//        // 1. 获取当前登录用户id
//        Long userId = UserContext.getUser();
//        // 2. 判断是否点赞 dto.liked 为true则点赞
//        boolean flag = dto.getLiked() ? liked(dto, userId) : unliked(dto, userId);
//        if (!flag) {
//            // 点赞或取消点赞失败
//            return;
//        }
//        // 3. 统计该业务id下的总点赞数
//        Long totalLikesNum = this.lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .count();
//        // 4. 发消息到mq
//        String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//        LikedTimesDTO msg = LikedTimesDTO.of(dto.getBizId(), Math.toIntExact(totalLikesNum));
//        log.debug("发送点赞消息 消息内容{}", msg);
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                routingKey,
//                msg);
//    }
//
//    @Override
//    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
//        if (CollUtils.isEmpty(bizIds)) {
//            return CollUtils.emptySet();
//        }
//        // 1. 获取当前登录用户id
//        Long userId = UserContext.getUser();
//        // 2. 查询点赞记录 in (bizIds)
//        List<LikedRecord> recordList = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .in(LikedRecord::getBizId, bizIds)
//                .list();
//        // 3. 将查询到的bizId转换为set返回
//        return recordList.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
//    }
//
//    private boolean unliked(LikeRecordFormDTO dto, Long userId) {
//        LikedRecord record = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if (record == null) {
//            // 没有点赞过
//            return false;
//        }
//        // 删除点赞记录
//        return this.removeById(record.getId());
//    }
//
//    private boolean liked(LikeRecordFormDTO dto, Long userId) {
//        LikedRecord record = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if (record != null) {
//            // 已经点赞过
//            return false;
//        }
//        // 保存点赞记录
//        LikedRecord likedRecord = new LikedRecord();
//        likedRecord.setBizId(dto.getBizId());
//        likedRecord.setUserId(userId);
//        likedRecord.setBizType(dto.getBizType());
//        return this.save(likedRecord);
//    }
//}
