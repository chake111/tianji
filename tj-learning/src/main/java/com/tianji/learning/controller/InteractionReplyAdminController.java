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
@RequestMapping("/admin/replies")
@Api(tags = "回答或评论相关接口-管理端")
@RequiredArgsConstructor
public class InteractionReplyAdminController {

    private final IInteractionReplyService replyService;

    @ApiOperation("分页查询回答或评论列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> getRepliesAdmin(ReplyPageQuery query){
        return replyService.getRepliesAdmin(query);
    }


    @ApiOperation("显示或隐藏评论")
    @PutMapping("/{id}/hidden/{hidden}")
    public void updateHidden(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        replyService.updateHidden(id, hidden);
    }

    @ApiOperation("根据id查询回答或评论")
    @GetMapping("/{id}")
    public ReplyVO getReply(@PathVariable("id") Long id) {
        return replyService.getReply(id);
    }


}
