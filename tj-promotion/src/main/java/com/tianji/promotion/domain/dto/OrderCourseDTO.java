package com.tianji.promotion.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author chake
 * @since 2025-07-30
 */
@Data
@Accessors(chain = true)
@ApiModel(description = "订单中的课程信息")
public class OrderCourseDTO {
    @ApiModelProperty("课id")
    private Long id;
    @ApiModelProperty("课程的三级分类id")
    private Long cateId;
    @ApiModelProperty("课程价格")
    private Integer price;
}
