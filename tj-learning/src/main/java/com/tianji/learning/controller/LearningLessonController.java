package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.domain.dto.LearningPlanDTO;

import java.util.Collections;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author chake
 * @since 2025-07-19
 */
@Slf4j
@RestController
@Api(tags = "我的课表相关接口")
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @ApiOperation("分页查询我的课表")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        return lessonService.queryMyLessons(query);
    }

    @ApiOperation("查询正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLesson() {
        return lessonService.queryMyCurrentLesson();
    }

    @ApiOperation("删除课表中课程")
    @DeleteMapping("/{id}")
    public void deleteMyCurrentLesson(@ApiParam(value = "课程id", required = true) @PathVariable("id") Long id) {
        Long userId = UserContext.getUser();
        lessonService.deleteUserLesson(userId, Collections.singletonList(id));
    }


    @ApiOperation("检查课程是否有效")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@ApiParam(value = "课程id", required = true) @PathVariable("courseId") Long courseId) {
        return lessonService.isLessonValid(courseId);
    }

    @ApiOperation("查询用户课表中指定课程状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO queryUserLessonStatus(@ApiParam(value = "课程id", required = true) @PathVariable("courseId") Long courseId) {
        return lessonService.queryUserLessonStatus(courseId);
    }


    @ApiOperation("统计课程的学习人数")
    @GetMapping("/{courseId}/count")
    Integer countLearningLessonByCourse(@ApiParam(value = "课程id", required = true) @PathVariable("courseId") Long courseId) {
        return lessonService.countLearningLessonByCourse(courseId);
    }

    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createPlan(@RequestBody @Validated LearningPlanDTO dto) {
        log.info("创建学习计划 courseId:{}, freq:{}", dto.getCourseId(), dto.getFreq());
        lessonService.createPlan(dto);
    }

    @ApiOperation("查询我的学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        return lessonService.queryMyPlans(query);
    }
}
