package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import javax.validation.constraints.Min;
import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author chake
 * @since 2025-07-24
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO listPointsBoard(PointsBoardQuery query);

    List<PointsBoard> listCurrentBoard(String key,
                                       @Min(value = 1, message = "页码不能小于1") Integer pageNo,
                                       @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize);
}
