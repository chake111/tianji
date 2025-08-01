package com.tianji.learning.controller;


import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author chake
 * @since 2025-07-24
 */
@RestController
@Api(tags = "赛季相关接口")
@RequestMapping("/boards/seasons")
@RequiredArgsConstructor
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    @ApiOperation(value = "获取赛季列表")
    @GetMapping("/list")
    public List<PointsBoardSeason> listPointsBoardSeason() {
        return pointsBoardSeasonService.list();
    }
}
