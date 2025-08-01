package com.tianji.learning.controller;


import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.ILearningLessonService;
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
@Api(tags = "互动问题相关接口")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService interactionQuestionService;

    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@RequestBody @Validated QuestionFormDTO questionFormRequest) {
        interactionQuestionService.saveQuestion(questionFormRequest);
    }

    @ApiOperation("修改互动问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable Long id,
                               @RequestBody QuestionFormDTO questionFormRequest) {
        interactionQuestionService.updateQuestion(id, questionFormRequest);
    }

    @ApiOperation("分页查询互动问题-用户端")
    @GetMapping("/page")
    public PageDTO<QuestionVO> getQuestions(QuestionPageQuery query){
        return interactionQuestionService.getQuestions(query);
    }

    @ApiOperation("查询问题详情-用户端")
    @GetMapping("/{id}")
    public QuestionVO getQuestionById(@PathVariable Long id) {
        return interactionQuestionService.getQuestionById(id);
    }
}
