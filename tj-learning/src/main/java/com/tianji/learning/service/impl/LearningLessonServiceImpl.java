package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper recordMapper;

    @Override
    public void addUserLesson(Long userId, List<Long> courseIds) {
        // 1. 远程调用课程服务，获取课程信息
        List<CourseSimpleInfoDTO> cInfoDTOList = courseClient.getSimpleInfoList(courseIds);
        // 2. 封装po
        List<LearningLesson> lessonList = new ArrayList<>();
        cInfoDTOList.forEach(cInfoDTO -> {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(userId);
            lesson.setCourseId(cInfoDTO.getId());
            Integer validDuration = cInfoDTO.getValidDuration();
            if (validDuration != null) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            lessonList.add(lesson);
        });
        // 3. 保存到数据库
        this.saveBatch(lessonList);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        //  1. 获取当前登录用户的id
        Long userId = UserContext.getUser();
        //  2. 分页查询我的课表
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3. 远程调用课程服务，给vo中课程名 封面 章节数赋值
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfoDTOList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoDTOList)) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_EXIST);
        }
        // 将cInfoDTOList 转换为map，方便根据courseId查找课程名 封面 章节数
        Map<Long, CourseSimpleInfoDTO> cInfoDTOMap = cInfoDTOList
                .stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, cInfoDTO -> cInfoDTO));
        // 4. 将po中的数据，封装到vo中
        List<LearningLessonVO> voList = new ArrayList<>();
        records.forEach(record -> {
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            CourseSimpleInfoDTO cInfoDTO = cInfoDTOMap.get(record.getCourseId());
            if (cInfoDTO != null) {
                vo.setCourseName(cInfoDTO.getName());
                vo.setCourseCoverUrl(cInfoDTO.getCoverUrl());
                vo.setSections(cInfoDTO.getSectionNum());
            }
            voList.add(vo);
        });
        // 5. 返回
        return PageDTO.of(page, voList);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        //  1. 获取当前登录用户id
        Long userId = UserContext.getUser();
        //  2. 查询当前用户最近学习课程 按latest_learn_time降序排序 取第一条 正在学习中的status为1的记录|
        // sql: select * from learning_lesson where user_id = ? and status = 1 order by latest_learn_time desc limit 1
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }
        //  3. 远程调用课程服务，给vo中课程名 封面 章节数赋值
        CourseFullInfoDTO cInfoDTO = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfoDTO == null) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_EXIST);
        }
        //  4. 查询当前用户课表中 已报名 总的课程数
        Long count = this.lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        //  5. 通过feign远程调用课程服务 获取小节名称 和 小节编号
        Long latestSectionId = lesson.getLatestSectionId();
        List<Long> ids = CollUtils.singletonList(latestSectionId);
        List<CataSimpleInfoDTO> cInfoDTOList = catalogueClient.batchQueryCatalogue(ids);
        if (CollUtils.isEmpty(cInfoDTOList)) {
            throw new BizIllegalException(ErrorInfo.Msg.SECTION_NOT_EXIST);
        }
        //  6. 封装到vo返回
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(cInfoDTO.getName());
        vo.setCourseCoverUrl(cInfoDTO.getCoverUrl());
        vo.setSections(cInfoDTO.getSectionNum());
        // 总的课程数
        vo.setCourseAmount(Math.toIntExact(count));

        CataSimpleInfoDTO infoDTO = cInfoDTOList.get(0);
        // 最新章节名称
        vo.setLatestSectionName(infoDTO.getName());
        // 最新章节索引
        vo.setLatestSectionIndex(infoDTO.getCIndex());
        return vo;
    }

    @Override
    public void deleteUserLesson(Long userId, List<Long> courseIds) {
        // 检查courseIds是否为空
        if (CollUtils.isEmpty(courseIds)) {
            log.warn("用户{}尝试删除课程时，课程ID列表为空", userId);
            return;
        }

        try {
            List<Long> lessonIds = this.lambdaQuery()
                    .eq(LearningLesson::getUserId, userId)
                    .in(LearningLesson::getCourseId, courseIds)
                    .list()
                    .stream()
                    .map(LearningLesson::getId)
                    .collect(Collectors.toList());

            if (CollUtils.isNotEmpty(lessonIds)) {
                this.removeByIds(lessonIds);
            } else {
                log.info("用户{}尝试删除不存在的课程{}", userId, courseIds);
                throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_EXIST);
            }
        } catch (Exception e) {
            log.error("用户{}尝试删除{}课程时，出现异常", userId, courseIds, e);
            throw new BizIllegalException(ErrorInfo.Msg.DELETE_LESSON_FAILED);
        }
    }

    @Override
    public Long isLessonValid(Long courseId) {
        Long user = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

        if (lesson == null) {
            return null;
        }

        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && now.isAfter(expireTime)) {
            return null;
        }

        return lesson.getId();
    }

    @Override
    public LearningLessonVO queryUserLessonStatus(Long courseId) {
        Long userId = UserContext.getUser();

        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

        if (lesson == null) {
            return null;
        }

        return BeanUtils.copyBean(lesson, LearningLessonVO.class);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return Math.toIntExact(this.lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .count());
    }

    @Override
    public void createPlan(LearningPlanDTO dto) {
        // 1. 获取当前登录用户的id
        Long userId = UserContext.getUser();
        // 2. 查询课表lesson_id 条件 user_id course_id
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();
        if (lesson == null) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_JOINED_TABLE);
        }
        // 3.更新课表

        this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq, dto.getFreq())
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        // 1. 获取当前登录用户的id
        Long userId = UserContext.getUser();
        // 2. TODO 查询积分
        // 3. 查询本周学习计划总数据 learning_lesson 条件 userId status=1 in（0，1） plan_status=0 查询sum(week_freq)
        QueryWrapper<LearningLesson> wapper = new QueryWrapper<>();
        wapper.select("sum(week_freq) as plansTotal");
        wapper.eq("user_id", userId);
        wapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = this.getMap(wapper);
        Integer plansTotal = 0;
        if (map != null && map.get("plansTotal") != null) {
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }
        // 4. 查询本周 实际 已学习的计划总数据 learning_record 条件 userId finish_time 在本周区间之内 finished为true 查询count(*)
        // sql: select count(*) from learning_record where user_id = ? and finished = true and finish_time between ? and ?
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);
        Integer weekFinishedPlanNum = Math.toIntExact(recordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime)));
        // 5. 查询课表数据 learning_lesson 条件 userId status=1 in（0，1） plan_status=1 分页
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtils.emptyList());
            return vo;
        }
        // 6. 远程调用课程服务 获取课程信息
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfoDTOList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoDTOList)) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_EXIST);
        }

        Map<Long, CourseSimpleInfoDTO> cInfoDTOMap = cInfoDTOList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, cInfoDTO -> cInfoDTO));
        // 7. 查询学习记录表 当前用户下 每一门课下 已学习的小节数
        QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("user_id", userId);
        recordWrapper.eq("finished", true);
        recordWrapper.between("finish_time", weekBeginTime, weekEndTime);
        recordWrapper.groupBy("lesson_id");
        // 使用userId暂存已学习的小节数
        recordWrapper.select("lesson_id as lessonId, count(*) as userId");
        List<LearningRecord> learningRecords = recordMapper.selectList(recordWrapper);
        // map中的key为lessonId，value为当前用户下该课程已学习的小节数
        Map<Long, Long> courseWeekFinishedNumMap = learningRecords.stream()
                .collect(Collectors.toMap(LearningRecord::getLessonId, LearningRecord::getUserId));
        // 8. 封装vo返回
        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekTotalPlan(plansTotal);
        vo.setWeekFinished(weekFinishedPlanNum);
        List<LearningPlanVO> voList = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningPlanVO planVO = BeanUtils.copyBean(record, LearningPlanVO.class);
            CourseSimpleInfoDTO infoDTO = cInfoDTOMap.get(record.getCourseId());
            if (infoDTO != null) {
                planVO.setCourseName(infoDTO.getName());
                planVO.setSections(infoDTO.getSectionNum());
            }
            planVO.setWeekLearnedSections(courseWeekFinishedNumMap
                    .getOrDefault(record.getId(), 0L).intValue());
            voList.add(planVO);
        }

        return vo.pageInfo(page.getTotal(), page.getPages(), voList);
    }
}
