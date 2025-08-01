package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-24
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addPointRecord(SignInMessage msg, PointsRecordType type) {
        // 校验
        if (msg == null || msg.getPoints() == null) {
            return;
        }
        int realPoint = msg.getPoints();
        // 1. 判断该积分类型是否有上限 type.maxPoints 是否大于0
        int maxPoints = type.getMaxPoints();
        if (maxPoints > 0) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
            LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
            // 2. 如果有上限 查询该用户 该积分类型 今日已得积分 points_record 条件 userId type 今天  sum(points)
            // select sum(points) as totalPoints from points_record where user_id = ? and type = ? and create_time between '2022-01-01 00:00:00' and '2022-01-01 23:59:59';
            QueryWrapper<PointsRecord> wrapper = Wrappers
                    .<PointsRecord>query()
                    .eq("user_id", msg.getUserId())
                    .eq("type", type)
                    .between("create_time", dayStartTime, dayEndTime)
                    .select("sum(points) as totalPoints");
            Map<String, Object> map = this.getMap(wrapper);
            int currentPoints = 0;
            if (map != null) {
                BigDecimal totalPoints = (BigDecimal) map.get("totalPoints");
                currentPoints = totalPoints.intValue();
            }
            // 3. 判断已得积分是否达到上限
            if (currentPoints >= maxPoints) {
                // 说明已达到上限
                return;
            }
            // 计算实际得分
            if (currentPoints + realPoint > maxPoints) {
                realPoint = maxPoints - currentPoints;
            }
        }
        // 4. 保存积分记录
        PointsRecord record = new PointsRecord();
        record.setUserId(msg.getUserId());
        record.setPoints(realPoint);
        record.setType(type);
        this.save(record);

        // 5. 累加并保存到积分值到redis 采用zSet 当前赛季的排行榜
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        redisTemplate.opsForZSet().incrementScore(key, msg.getUserId().toString(), realPoint);
    }

    @Override
    public List<PointsStatisticsVO> queryMyTodayPoints() {
        // 1. 获取当前用户id
        Long userId = UserContext.getUser();
        // 2. 查询积分表
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
        LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.select("type, sum(points) as userId");
        wrapper.eq("user_id", userId);
        wrapper.between("create_time", dayStartTime, dayEndTime);
        wrapper.groupBy("type");
        List<PointsRecord> list = this.list(wrapper);
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 3. 封装vo返回
        List<PointsStatisticsVO> voList = new ArrayList<>();
        for (PointsRecord r : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(r.getType().getDesc());
            vo.setMaxPoints(r.getType().getMaxPoints());
            vo.setPoints(r.getUserId().intValue());
            voList.add(vo);
        }
        return voList;
    }
}
