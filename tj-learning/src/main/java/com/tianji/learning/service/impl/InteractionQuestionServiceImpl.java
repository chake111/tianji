package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-21
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService interactionReplyService;

    private final UserClient userClient;

    private final SearchClient searchClient;

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO questionFormRequest) {
        // 1. 获取当前登录用户id
        Long userId = UserContext.getUser();
        InteractionQuestion interactionQuestion = BeanUtils.copyBean(questionFormRequest, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);
        this.save(interactionQuestion);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionFormRequest) {
        // 1. 手动校验
        if (StringUtils.isBlank(questionFormRequest.getTitle())
                || StringUtils.isBlank(questionFormRequest.getDescription())
                || questionFormRequest.getAnonymity() == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 校验id
        InteractionQuestion interactionQuestion = this.getById(id);
        if (interactionQuestion == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 只能修改自己的互动问题
        if (!interactionQuestion.getUserId().equals(UserContext.getUser())) {
            throw new BadRequestException(ErrorInfo.Msg.UPDATE_INTERACT_FAILED);
        }
        // 2. dto转po
        interactionQuestion.setTitle(questionFormRequest.getTitle());
        interactionQuestion.setDescription(questionFormRequest.getDescription());
        interactionQuestion.setAnonymity(questionFormRequest.getAnonymity());
        // 3. 修改po
        this.updateById(interactionQuestion);
    }

    @Override
    public PageDTO<QuestionVO> getQuestions(QuestionPageQuery query) {
        // 1. 校验 参数courseId
        if (query.getCourseId() == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 2. 获取当前登录用户id
        Long userId = UserContext.getUser();
        // 3. 分页查询互动问题interaction_question
        // 条件：course_id  onlyMine为ture才会加userId 小节id不为空 hidden为false 分页查询 按提问时间倒序
        Page<InteractionQuestion> page = this.lambdaQuery()
                .select(InteractionQuestion.class, tableFieldInfo -> !tableFieldInfo.getProperty().equals("description"))
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        Set<Long> latestAnswerIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (InteractionQuestion record : records) {
            if (record.getLatestAnswerId() != null) {
                latestAnswerIds.add(record.getLatestAnswerId());
            }
            if (!record.getAnonymity()) {
                userIds.add(record.getUserId());
            }
        }
        // 4. 根据最新回答id 批量查询回答信息 interaction_reply 条件
        Map<Long, InteractionReply> interationReplyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(latestAnswerIds)) {
            List<InteractionReply> interactionReplyList = interactionReplyService.list(Wrappers.<InteractionReply>lambdaQuery()
                    .in(InteractionReply::getId, latestAnswerIds)
                    .eq(InteractionReply::getHidden, false));
            for (InteractionReply interactionReply : interactionReplyList) {
                if (!interactionReply.getAnonymity()) {
                    userIds.add(interactionReply.getUserId());
                }
                interationReplyMap.put(interactionReply.getId(), interactionReply);
            }
        }
        // 5. 远程调用用户服务 获取用户信息 批量
        List<UserDTO> userDTOList = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDTOMap = userDTOList.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        // 6. 封装vo返回
        List<QuestionVO> questionVOList = new ArrayList<>();
        records.forEach(record -> {
            QuestionVO questionVO = BeanUtils.copyBean(record, QuestionVO.class);
            if (!questionVO.getAnonymity()) {
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    questionVO.setUserName(userDTO.getName());
                    questionVO.setUserIcon(userDTO.getIcon());
                }
            }
            InteractionReply latestReply = interationReplyMap.get(record.getLatestAnswerId());
            if (latestReply != null) {
                if (!latestReply.getAnonymity()) { // 最新如果是非匿名，才需要获取用户信息
                    UserDTO userDTO = userDTOMap.get(latestReply.getUserId());
                    if (userDTO != null) {
                        questionVO.setLatestReplyUser(userDTO.getName());
                    }
                }
                questionVO.setLatestReplyContent(latestReply.getContent());
            }
            questionVOList.add(questionVO);
        });
        return PageDTO.of(page, questionVOList);
    }

    @Override
    public QuestionVO getQuestionById(Long id) {
        // 1. 校验
        if (id == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 2. 查询互动问题表 按id查询
        InteractionQuestion interactionQuestion = this.getById(id);
        if (interactionQuestion == null) {
            throw new BadRequestException(ErrorInfo.Msg.QUESTION_NOT_EXIST);
        }
        // 3. 如果该问题管理员设置了隐藏，则返回null
        if (interactionQuestion.getHidden()) {
            return null;
        }
        // 4. 封装vo返回
        QuestionVO questionVO = BeanUtils.copyBean(interactionQuestion, QuestionVO.class);
        // 5. 如果用户是匿名提问，不能查询提问者的昵称和头像
        if (!interactionQuestion.getAnonymity()) {
            // 远程调用用户服务 获取用户信息
            UserDTO userDTO = userClient.queryUserById(interactionQuestion.getUserId());
            if (userDTO != null) {
                questionVO.setUserName(userDTO.getName());
                questionVO.setUserIcon(userDTO.getIcon());
            }
        }
        return questionVO;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query) {
        // 如果用户传了课程的名称 则从es中取出改名称对应的课程id
        String courseName = query.getCourseName();
        List<Long> cIds = null;
        if (StringUtils.isNotBlank(courseName)) {
            cIds = searchClient.queryCoursesIdByName(courseName);
            if (CollUtils.isEmpty(cIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }
        // 1. 查询互动问题表 条件参数前端传条件了接添加 分页 排序按提问时间倒序
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(cIds), InteractionQuestion::getCourseId, cIds)
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .between(query.getBeginTime() != null && query.getEndTime() != null,
                        InteractionQuestion::getCreateTime, query.getBeginTime(), query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> courseIds = new HashSet<>();
        Set<Long> chapterAndSectionIds = new HashSet<>();
        for (InteractionQuestion record : records) {
            userIds.add(record.getUserId());
            courseIds.add(record.getCourseId());
            chapterAndSectionIds.add(record.getChapterId());
            chapterAndSectionIds.add(record.getSectionId());
        }
        // 2. 远程调用用户服务 获取用户信息
        List<UserDTO> userDTOList = userClient.queryUserByIds(userIds);
        if (CollUtils.isEmpty(userDTOList)) {
            throw new BizIllegalException(ErrorInfo.Msg.USER_NOT_EXISTS);
        }
        Map<Long, UserDTO> userDTOMap = userDTOList.stream()
                .collect(Collectors.toMap(UserDTO::getId, c -> c));
        // 3. 远程调用课程服务 获取课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_EXIST);
        }
        Map<Long, CourseSimpleInfoDTO> cInfoMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        // 4. 远程调用课程服务 获取章节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BizIllegalException(ErrorInfo.Msg.CHAPTER_NOT_EXIST);
        }
        Map<Long, String> cataInfoDTOMap = cataSimpleInfoDTOS.stream()
                .collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));

        // 6. po转vo
        List<QuestionAdminVO> adminVOList = new ArrayList<>();
        records.forEach(record -> {
            QuestionAdminVO adminVO = BeanUtils.copyBean(record, QuestionAdminVO.class);
            UserDTO userDTO = userDTOMap.get(record.getUserId());
            if (userDTO != null) {
                adminVO.setUserName(userDTO.getName());
            }
            CourseSimpleInfoDTO cInfoDTO = cInfoMap.get(record.getCourseId());
            if (cInfoDTO != null) {
                adminVO.setCourseName(cInfoDTO.getName());
                // 5. 获取分类信息
                // 一二三及分类id集合
                List<Long> categoryIds = cInfoDTO.getCategoryIds();
                String categoryNames = categoryCache.getCategoryNames(categoryIds);
                adminVO.setCategoryName(categoryNames);
            }
            adminVO.setChapterName(cataInfoDTOMap.get(record.getChapterId()));
            adminVO.setSectionName(cataInfoDTOMap.get(record.getSectionId()));

            adminVOList.add(adminVO);
        });
        return PageDTO.of(page, adminVOList);
    }

    @Override
    public void updateHidden(Long id, Boolean hidden) {
        // 1. 校验
        if (id == null || hidden == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 2. 更新互动问题表 按id更新hidden字段
        this.lambdaUpdate()
                .set(InteractionQuestion::getHidden, hidden)
                .eq(InteractionQuestion::getId, id)
                .update();
    }

    @Override
    public QuestionAdminVO getQuestionAdminVOById(Long id) {
        // 1. 校验
        if (id == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 2. 查询互动问题表 按id查询 更新查看状态
        InteractionQuestion question = this.lambdaQuery()
                .eq(InteractionQuestion::getId, id)
                .one();
        if (question == null) {
            throw new BadRequestException(ErrorInfo.Msg.QUESTION_NOT_EXIST);
        }
        this.lambdaUpdate()
                .set(InteractionQuestion::getStatus, QuestionStatus.CHECKED)
                .eq(InteractionQuestion::getId, id)
                .update();
        // 3. po转vo
        QuestionAdminVO adminVO = BeanUtils.copyBean(question, QuestionAdminVO.class);
        // 4. 远程调用课程服务 获取一二三分类id 根据分类id 远程调用分类服务 获取分类名称 课程名称
        CourseFullInfoDTO cInfoDTO = courseClient.getCourseInfoById(question.getCourseId(), true, true);
        if (cInfoDTO == null) {
            throw new BizIllegalException(ErrorInfo.Msg.COURSE_NOT_EXIST);
        }
        // 课程名称
        adminVO.setCourseName(cInfoDTO.getName());
        // 课程所属章节
        List<CataSimpleInfoDTO> cInfoDTOList = catalogueClient.batchQueryCatalogue(Arrays.asList(question.getChapterId(), question.getSectionId()));
        if (CollUtils.isEmpty(cInfoDTOList)) {
            throw new BizIllegalException(ErrorInfo.Msg.CHAPTER_NOT_EXIST);
        }
        adminVO.setChapterName(cInfoDTOList.get(0).getName());
        // 一二三及分类id集合
        List<Long> categoryIds = cInfoDTO.getCategoryIds();
        if (CollUtils.isNotEmpty(categoryIds)) {
            String categoryNames = categoryCache.getCategoryNames(categoryIds);
            if (StringUtils.isNotBlank(categoryNames)) {
                adminVO.setCategoryName(categoryNames);
            }
        }
        // 5. 获取教师id 根据教师id 远程调用用户服务 获取教师名称
        List<Long> teacherIds = cInfoDTO.getTeacherIds();
        if (CollUtils.isNotEmpty(teacherIds)) {
            List<UserDTO> userDTOList = userClient.queryUserByIds(teacherIds);
            if (CollUtils.isNotEmpty(userDTOList)) {
                String teacherNames = userDTOList.stream()
                        .map(UserDTO::getName)
                        .collect(Collectors.joining(","));
                adminVO.setTeacherName(teacherNames);
            }
        }
        // 6. 远程调用用户服务 根据用户id 获取用户昵称和头像
        Long userId = question.getUserId();
        if (userId != null) {
            UserDTO userDTO = userClient.queryUserById(userId);
            if (userDTO != null) {
                adminVO.setUserName(userDTO.getName());
                adminVO.setUserIcon(userDTO.getIcon());
            }
        }
        return adminVO;
    }
}