package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author chake
 * @since 2025-07-23
 */
@RestController
@Api(tags = "点赞相关接口")
@RequestMapping("/likes")
@RequiredArgsConstructor
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;

    @ApiOperation("点赞或取消点赞")
    @PostMapping()
    public void addLikedRecord(@RequestBody @Validated LikeRecordFormDTO dto) {
        likedRecordService.addLikedRecord(dto);
    }

    @ApiOperation("批量查询点赞状态")
    @GetMapping("/list")
    public Set<Long> getLikesStatusByBizIds(@RequestParam("bizIds") List<Long> bizIds){
        return likedRecordService.getLikesStatusByBizIds(bizIds);
    }
}
