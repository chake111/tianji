package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author chake
 * @since 2025-07-21
 */
@RestController
@RequestMapping("/replies")
@Api(tags = "回答或评论相关接口")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;

    @ApiOperation("新增回答或评论")
    @PostMapping
    public void addReply(@RequestBody @Validated ReplyDTO dto) {
        replyService.addReply(dto);
    }

    @ApiOperation("分页查询回答或评论列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> getReplies(ReplyPageQuery query) {
        return replyService.getReplies(query);
    }
}
