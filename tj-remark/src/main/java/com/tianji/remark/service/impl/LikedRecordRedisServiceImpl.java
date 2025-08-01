package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikedRecord(LikeRecordFormDTO dto) {
        // 1. 获取当前登录用户id
        Long userId = UserContext.getUser();
        // 2. 判断是否点赞 dto.liked 为true则点赞
        boolean flag = dto.getLiked() ? liked(dto, userId) : unliked(dto, userId);
        if (!flag) {
            // 点赞或取消点赞失败
            return;
        }
        // 3. 统计该业务id下的总点赞数
        // 基于redis统计 业务id总点赞数
        // 拼接key likes:set:biz:bizId:type:QA likes:set:biz:bizId:type:NOTE
        String key = StringUtils.format(RedisConstants.LIKE_BIZ_KEY_PREFIX, dto.getBizType(), dto.getBizId());
        Long totalLikesNum = redisTemplate.opsForSet().size(key);
        if (totalLikesNum == null) {
            return;
        }
        // 4.采用zSet缓存点赞总数
        String bizTypeTotalLikeKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet().add(bizTypeTotalLikeKey, dto.getBizId().toString(), totalLikesNum);
    }

    @Override
    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
        // 1. 获取当前登录用户id
        Long userId = UserContext.getUser();
        if (CollUtils.isEmpty(bizIds)) {
            return CollUtils.emptySet();
        }
        // 2. 循环bizIds
        Set<Long> likedBizIds = new HashSet<>();
        for (Long bizId : bizIds) {
            // 判断该业务id 的点赞用户集合中是否包含当前用户
            String key = StringUtils.format(RedisConstants.LIKE_BIZ_KEY_PREFIX, "QA", bizId);
            // 3. 判断当前业务， 当前用户是否点赞
            Boolean member = redisTemplate.opsForSet().isMember(key, userId.toString());
            if (Boolean.TRUE.equals(member)) {
                // 4. 如果有当前用户id则存入新集合返回
                likedBizIds.add(bizId);
                continue;
            }
            LikedRecord likedRecord = this.lambdaQuery()
                    .eq(LikedRecord::getBizId, bizId)
                    .eq(LikedRecord::getUserId, userId)
                    .one();
            if (likedRecord != null) {
                // 5. 如果有记录则存入新集合返回
                likedBizIds.add(bizId);
                redisTemplate.opsForSet()
                        .add(StringUtils.format(RedisConstants.LIKE_BIZ_KEY_PREFIX, "QA", bizId), userId.toString());
            }
        }
        return likedBizIds;
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 1. 拼接key likes:times:type:QA likes:times:type:NOTE
        String bizTypeTotalLikeKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        // 2. 从redis的zSet结构中 按分数排序取 maxBizSize 的 业务点赞信息 popMin
        List<LikedTimesDTO> list = new ArrayList<>();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().popMin(bizTypeTotalLikeKey, maxBizSize);
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String bizId = typedTuple.getValue();
            Double likedTimes = typedTuple.getScore();
            if (StringUtils.isBlank(bizId) || likedTimes == null) {
                continue;
            }
            // 3. 封装LikedTimesDTO 消息数据
            LikedTimesDTO msg = LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue());
            list.add(msg);
        }
        // 4. 发送消息到mq
        if (CollUtils.isNotEmpty(list)) {
            log.debug("批量发送点赞消息 消息内容{}", list);
            String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType);
            rabbitMqHelper.send(
                    MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                    routingKey,
                    list);
        }
    }

    @Override
    public void persistenceLikedRecordAndSendMessage(String bizType) {
        log.info("开始执行点赞记录检查任务");
        // 1. 查询redis缓存中的点赞记录
        // 查询与prefix匹配的key 存入set
        String pattern = RedisConstants.LIKE_BIZ_KEY_PREFIX.substring(0, 14) + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        log.info("查询redis缓存中的点赞记录，key列表：{}", keys);
        if (CollUtils.isEmpty(keys)) {
            log.info("redis缓存中没有点赞记录");
            return;
        }
        // 2.再通过key查询redis缓存中的点赞记录
        List<LikedRecord> recordList = new ArrayList<>();
        keys.forEach(key -> {
            Set<String> members = redisTemplate.opsForSet().members(key);
            log.info("key:{}, members:{}", key, members);
            // 3. 转为list<LikedRecord>
            if (CollUtils.isNotEmpty(members)) {
                members.forEach(member -> {
                    LikedRecord likedRecord = new LikedRecord();
                    // 需要从key中解析bizType和bizId
                    // redis key前缀 likes:set:type:{bizType}:biz:{bizId}
                    String[] arr = key.split(":");
                    likedRecord.setBizType(arr[3]);
                    likedRecord.setBizId(Long.valueOf(arr[5]));
                    likedRecord.setUserId(Long.valueOf(member));
                    log.info("key:{}, 解析后的点赞记录：{}", key, likedRecord);
                    recordList.add(likedRecord);
                });
            }
        });
        log.info("redis查询到的点赞记录：{}", recordList);
        // 4. 批量插入到数据库
        String routingKey = StringUtils.format(MqConstants.Key.LIKED_RECORD_KEY_TEMPLATE, bizType);
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                routingKey,
                recordList);
        // 5. 清除redis缓存
        redisTemplate.delete(pattern);
    }

    private boolean unliked(LikeRecordFormDTO dto, Long userId) {
        // 拼接key
        String key = StringUtils.format(RedisConstants.LIKE_BIZ_KEY_PREFIX, dto.getBizType(), dto.getBizId());
        // 从当前结构中删除 当前userId
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        if (result != null && result > 0) {
            // 数据库删除点赞记录
            this.remove(Wrappers.<LikedRecord>lambdaQuery()
                    .eq(LikedRecord::getBizId, dto.getBizId())
                    .eq(LikedRecord::getUserId, userId));
        }

        return result != null && result > 0;
    }

    private boolean liked(LikeRecordFormDTO dto, Long userId) {
        // 基于redis做点赞
        // 拼接key likes:set:biz:bizId:type:QA likes:set:biz:bizId:type:NOTE
        String key = StringUtils.format(RedisConstants.LIKE_BIZ_KEY_PREFIX, dto.getBizType(), dto.getBizId());
        // redisTemplate 往redis 的set结构添加 点赞记录
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;
    }
}


