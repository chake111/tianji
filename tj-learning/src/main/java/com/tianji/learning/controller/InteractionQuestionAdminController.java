package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author chake
 * @since 2025-07-21
 */
@Api(tags = "互动问题相关接口-管理端")
@RestController
@RequestMapping("admin/questions")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {
    private final IInteractionQuestionService interactionQuestionService;

    @ApiOperation("分页查询互动问题列表-管理端")
    @GetMapping("/page")
    public PageDTO<QuestionAdminVO> queryQuestionPage(QuestionAdminPageQuery query) {
        return interactionQuestionService.queryQuestionAdminVOPage(query);
    }

    @ApiOperation("隐藏或显示问题-管理端")
    @PutMapping("/{id}/hidden/{hidden}")
    public void updateHidden(@PathVariable Long id, @PathVariable Boolean hidden) {
        interactionQuestionService.updateHidden(id, hidden);
    }

    @ApiOperation("根据id查询问题详情-管理端")
    @GetMapping("/{id}")
    public QuestionAdminVO getQuestion(@PathVariable Long id) {
        return interactionQuestionService.getQuestionAdminVOById(id);
    }
}
