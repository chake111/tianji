package com.tianji.learning.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author chake
 * @since 2025/07/20
 */
@Data
@ApiModel(description = "学习计划表单实体")
public class LearningPlanDTO {
    @NotNull(message = "课程表id不能为空")
    @ApiModelProperty("课程表id")
    @Min(1)
    private Long courseId;
    @NotNull
    @Range(min = 1, max = 50)
    @ApiModelProperty("每周学习频率")
    private Integer freq;
}
