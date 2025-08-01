package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author chake
 * @since 2025-07-24
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    void createPointsBoardTable(Integer id);
}
