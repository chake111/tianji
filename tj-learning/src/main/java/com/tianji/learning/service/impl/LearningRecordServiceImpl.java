package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-20
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler taskHandler;


    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 1. 获取当前登录的用户ID
        Long userId = UserContext.getUser();
        // 2. 查询课表信息 条件userId 和 courseId
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_JOINED_TABLE);
        }
        // 3. 查询学习记录 条件lessonId 和 userId
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        // 4. 封装结果返回
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        List<LearningRecordDTO> dtoList = BeanUtils.copyList(recordList, LearningRecordDTO.class);
        dto.setRecords(dtoList);
        return dto;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        // 1. 获取当前登录的用户ID
        Long userId = UserContext.getUser();
        // 2. 处理学习记录
        boolean isFinished = false;
        if (dto.getSectionType().equals(SectionType.VIDEO)) {
            // 2.1 提交视频观看记录
            isFinished = handleVideoRecord(userId, dto);
        } else {
            // 2.2 提交考试记录
            isFinished = handleExamRecord(userId, dto);
        }
        // 3. 处理课表信息
        if (!isFinished) {
            return;
        }
        handleLessonData(dto);
    }

    // 处理课表信息
    private void handleLessonData(LearningRecordFormDTO dto) {
        // 1. 查询课表信息 条件lesson_id主键
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_EXIST);
        }
        // 2. 判断是否第一次学完 isFinished是不是true
        boolean allFinished = false;

        // 3. 远程调用获取课程信息 小节总数
        CourseFullInfoDTO cInfoDTO = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfoDTO == null) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_EXIST);
        }
        Integer sectionNum = cInfoDTO.getSectionNum();
        // 4. 如果isFinished是true 小节是第一次学完 判断该用户对该课程下的所有小节是否都学完
        Integer learnedSections = lesson.getLearnedSections();
        allFinished = learnedSections + 1 >= sectionNum;

        // 5. 更新课表数据
        lessonService.lambdaUpdate()
                .set(lesson.getStatus() == LessonStatus.NOT_BEGIN, LearningLesson::getStatus, LessonStatus.LEARNING)
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestSectionId, dto.getSectionId())
                .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();


    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        // 1. 查询旧的学习记录 learning_record 条件userId lessonId
        LearningRecord learningRecord = queryOldRecord(dto.getLessonId(), dto.getSectionId());
        // 2. 判断是否存在
        if (learningRecord == null) {
            // 3. 如果不存在，新增学习记录
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);
            // 保存学习记录
            boolean result = this.save(record);
            if (!result) {
                throw new DbException(ErrorInfo.Msg.SAVE_RECORD_FAILED);
            }
            // 未学完
            return false;
        }
        // 4. 如果存在，更新学习记录 更新字段 moment
        // 判断本小节是否是第一次学完
        boolean isFinished = !learningRecord.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        if (!isFinished) {
            LearningRecord record = new LearningRecord();
            record.setId(learningRecord.getId());
            record.setLessonId(dto.getLessonId());
            record.setSectionId(dto.getSectionId());
            record.setMoment(dto.getMoment());
            record.setFinished(learningRecord.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;
        }
        // update learning_record set moment = ?, finished = ?, finish_time = ? where id = ?
        boolean result = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(isFinished, LearningRecord::getFinished, true)
                .set(isFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId())
                .update();
        if (!result) {
            throw new DbException(ErrorInfo.Msg.UPDATE_RECORD_FAILED);
        }
        // 清理redis缓存
        taskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());

        return isFinished;
    }

    // 查询旧的学习记录
    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1. 查询缓存
        LearningRecord cache = taskHandler.readRecordCache(lessonId, sectionId);
        // 2. 如果命中直接返回
        if (cache != null) {
            return cache;
        }
        // 3. 如果未命中查询数据库
        LearningRecord dbRecord = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        if (dbRecord == null) {
            return null;
        }
        // 4. 放入缓存
        taskHandler.writeRecordCache(dbRecord);

        return dbRecord;
    }

    // 处理考试记录
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        // 1.将dto转换为po
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(dto.getCommitTime());
        // 保存学习记录
        boolean result = this.save(record);
        if (!result) {
            throw new DbException(ErrorInfo.Msg.SAVE_RECORD_FAILED);
        }
        return true;
    }
}
