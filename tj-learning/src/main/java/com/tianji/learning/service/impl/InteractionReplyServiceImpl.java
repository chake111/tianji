package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.*;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-21
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;

    private final UserClient userClient;

    private final RemarkClient remarkClient;

    @Override
    public void addReply(ReplyDTO dto) {
        // 1. 获取当前登录用户id
        Long userId = UserContext.getUser();
        // 2. 保存互动回复
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        this.save(reply);
        // 3. 判断是否是回答
        if (dto.getAnswerId() == null) {
            // 是回答
            handleAnswer(dto);
        }
        // 是评论
        handleComment(dto);
        // 2. 判断是否学生提交
        // 如果为学生提交，则标记问题状态为未查看
        questionMapper.update(null, Wrappers.<InteractionQuestion>lambdaUpdate()
                .set(dto.getIsStudent(), InteractionQuestion::getStatus, QuestionStatus.UN_CHECK)
                .set(InteractionQuestion::getLatestAnswerId, reply.getId())
                .eq(InteractionQuestion::getId, dto.getQuestionId()));
    }

    @Override
    public PageDTO<ReplyVO> getReplies(ReplyPageQuery query) {
        // 1. 校验参数
        if (query.getAnswerId() == null && query.getQuestionId() == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 2. 分页查询回答或评论
        Page<InteractionReply> page = this.lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPage(
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)));

        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        // 3. 补全其它用户信息
        Set<Long> uIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        HashSet<Long> targetReplyIds = new HashSet<>();
        for (InteractionReply record : records) {
            if (!record.getAnonymity()) {
                uIds.add(record.getUserId());
                uIds.add(record.getTargetUserId());
            }
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                targetReplyIds.add(record.getTargetReplyId());
            }
            answerIds.add(record.getId());
        }
        Set<Long> bizLiked = remarkClient.getLikesStatusByBizIds(answerIds);
        //查询目标回复，如果目标回复不是匿名，则需要查询出目标回复的用户信息
        if (!targetReplyIds.isEmpty()) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            uIds.addAll(targetUserIds);
        }

        List<UserDTO> userDTOList = userClient.queryUserByIds(uIds);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (userDTOList != null) {
            userDTOMap = userDTOList.stream().collect(Collectors.toMap(UserDTO::getId, userDTO -> userDTO));
        }
        // 4. 封装vo返回
        List<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            if (!vo.getAnonymity()) {
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserType(userDTO.getType());
                }
            }
            UserDTO targetUserDTO = userDTOMap.get(record.getTargetUserId());
            if (targetUserDTO != null) {
                vo.setTargetUserName(targetUserDTO.getName());
            }
            vo.setLiked(bizLiked.contains(record.getId()));
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public PageDTO<ReplyVO> getRepliesAdmin(ReplyPageQuery query) {
        // 1. 校验参数
        if (query.getAnswerId() == null && query.getQuestionId() == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 2. 分页查询回答或评论
        Page<InteractionReply> page = this.lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
                .page(query.toMpPage(
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)));

        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        // 3. 补全其它用户信息
        Set<Long> uIds = new HashSet<>();
        HashSet<Long> targetReplyIds = new HashSet<>();
        for (InteractionReply record : records) {
            uIds.add(record.getUserId());
            uIds.add(record.getTargetUserId());
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                targetReplyIds.add(record.getTargetReplyId());
            }
        }
        //查询目标回复，如果目标回复不是匿名，则需要查询出目标回复的用户信息
        if (!targetReplyIds.isEmpty()) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            uIds.addAll(targetUserIds);
        }

        List<UserDTO> userDTOList = userClient.queryUserByIds(uIds);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (userDTOList != null) {
            userDTOMap = userDTOList.stream().collect(Collectors.toMap(UserDTO::getId, userDTO -> userDTO));
        }
        // 4. 封装vo返回
        List<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            UserDTO userDTO = userDTOMap.get(record.getUserId());
            if (userDTO != null) {
                vo.setUserName(userDTO.getName());
                vo.setUserIcon(userDTO.getIcon());
                vo.setUserType(userDTO.getType());
            }
            UserDTO targetUserDTO = userDTOMap.get(record.getTargetUserId());
            if (targetUserDTO != null) {
                vo.setTargetUserName(targetUserDTO.getName());
            }
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public void updateHidden(Long id, Boolean hidden) {
//        // 1. 校验参数
//        if (hidden == null || id == null) {
//            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
//        }
//        // 2.查询互动回答，判断是评论还是回答
//        InteractionReply reply = this.getById(id);
//        if (reply == null) {
//            throw new BadRequestException(ErrorInfo.Msg.REPLY_NOT_EXIST);
//        }
//        Long answerId = reply.getAnswerId();
//        if (answerId != 0) {
//            // 是评论
//            // 3. 修改评论的隐藏状态
//            this.lambdaUpdate()
//                    .set(InteractionReply::getHidden, hidden)
//                    .eq(InteractionReply::getId, id)
//                    .update();
//            return;
//        }
//        // 3.是回答 需要把回答下的评论也隐藏
//        this.lambdaUpdate()
//                .set(InteractionReply::getHidden, hidden)
//                .eq(InteractionReply::getId, id)
//                .update();
//        this.lambdaUpdate()
//                .set(InteractionReply::getHidden, hidden)
//                .eq(InteractionReply::getAnswerId, id)
//                .update();

        // 1. 校验参数
        if (hidden == null || id == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }

        // 2. 查询互动回答，判断是评论还是回答
        InteractionReply reply = this.getById(id);
        if (reply == null) {
            throw new BadRequestException(ErrorInfo.Msg.REPLY_NOT_EXIST);
        }

        // 3. 修改隐藏状态
        this.lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getId, id)
                .update();

        Long answerId = reply.getAnswerId();
        if (answerId != 0) {
            // 是评论，无需进一步处理
            return;
        }

        // 4. 是回答，需要把回答下的评论也隐藏
        this.lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getAnswerId, id)
                .update();
    }

    @Override
    public ReplyVO getReply(Long id) {
        // 1. 校验参数
        if (id == null) {
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        // 2. 查询互动回答
        InteractionReply reply = this.getById(id);
        if (reply == null) {
            throw new BadRequestException(ErrorInfo.Msg.REPLY_NOT_EXIST);
        }
        // 3. 补全其它用户信息
        UserDTO userDTO = userClient.queryUserById(reply.getUserId());
        if (userDTO == null) {
            throw new BizIllegalException(ErrorInfo.Msg.USER_NOT_EXIST);
        }
        ReplyVO vo = BeanUtils.copyBean(reply, ReplyVO.class);
        vo.setUserName(userDTO.getName());
        vo.setUserIcon(userDTO.getIcon());
        vo.setUserType(userDTO.getType());
        return vo;
    }


    private void handleComment(ReplyDTO dto) {
        // 累加回答下评论数量
        this.lambdaUpdate().setSql("reply_times = reply_times + 1")
                .eq(InteractionReply::getId, dto.getAnswerId())
                .update();
    }

    private void handleAnswer(ReplyDTO dto) {
        // 修改问题表最近一次回答id 累加问题表回答数量
//        questionService.lambdaUpdate()
//                .set(InteractionQuestion::getLatestAnswerId, dto.getAnswerId())
//                .setSql("answer_times = answer_times + 1")
//                .eq(InteractionQuestion::getId, dto.getQuestionId())
//                .update();
        questionMapper.update(null, Wrappers.<InteractionQuestion>lambdaUpdate()
                .set(InteractionQuestion::getLatestAnswerId, dto.getAnswerId())
                .setSql("answer_times = answer_times + 1")
                .eq(InteractionQuestion::getId, dto.getQuestionId()));
    }
}
