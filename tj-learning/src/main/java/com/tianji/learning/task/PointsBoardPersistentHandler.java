package com.tianji.learning.task;

import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author: chake
 * @create 2025/7/26 14:36
 * @ClassName: PointsBoardPersistentHandler
 * @Package: task
 * @Description:
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建上赛季（上个月） 榜单表
     */
//    @Scheduled(cron = "0 0 3 1 * ?")
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        log.debug("创建上赛季榜单表任务开始执行");
        // 1. 获取上个月当前时间点
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        // 2. 查询赛季表获取赛季id 条件 lastMonth >= beginTime && lastMonth <= endTime
        PointsBoardSeason season = pointsBoardSeasonService
                .lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, lastMonth)
                .ge(PointsBoardSeason::getEndTime, lastMonth)
                .one();
        log.debug("上赛季信息: {}", season);
        if (season == null) {
            return;
        }

        // 3. 创建上赛季榜单表
        pointsBoardSeasonService.createPointsBoardTable(season.getId());
    }


    // 保存上赛季榜单到数据库
    @XxlJob("savePointsBoard2DB")
    public void savePointsBoardToDb() {
        // 1. 获取上个月当前时间点
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        // 2. 查询赛季表points_board_season 获取赛季信息
        PointsBoardSeason season = pointsBoardSeasonService
                .lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, lastMonth)
                .ge(PointsBoardSeason::getEndTime, lastMonth)
                .one();
        log.debug("上赛季信息: {}", season);
        if (season == null) {
            return;
        }
        // 3. 计算动态表名 并存入threadLocal
        String tableName = LearningConstants.POINTS_BOARD_TABLE_PREFIX + season.getId();
        log.debug("动态表名: {}", tableName);
        TableInfoContext.setInfo(tableName);
        // 4. 获取redis上赛季积分排行榜单数据
        String format = lastMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;


        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        int pageNo = shardIndex + 1;
        int pageSize = 5;
        while (true) {
            log.debug("开始处理第{}页数据", pageNo);
            List<PointsBoard> pointsBoardList = pointsBoardService.listCurrentBoard(key, pageNo, pageSize);
            if (pointsBoardList.isEmpty()) {
                break;
            }
            pageNo += shardTotal;
            // 5. 持久化到db相应的赛季表中
            for (PointsBoard board : pointsBoardList) {
                board.setId(Long.valueOf(board.getRank()));
                board.setRank(null);
            }
            pointsBoardService.saveBatch(pointsBoardList);
        }
        // 清空threadLocal中的数据
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        // 1.获取上月时间
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String format = lastMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        // 3.删除
        redisTemplate.unlink(key);
    }
}
