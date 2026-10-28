package com.campus.express.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建评价请求参数（3.9 评价与信誉管理）
 */
@Data
public class EvaluationCreateDTO {

    @NotNull(message = "订单ID不能为空")
    private Integer orderId;

    /** 满意度星级 1-5（R-22：>=4 好评 +1 分；<=3 差评 -2 分） */
    @NotNull(message = "星级不能为空")
    @Min(value = 1, message = "星级最低 1 星")
    @Max(value = 5, message = "星级最高 5 星")
    private Integer starLevel;

    /** 文字评价内容 */
    @Size(max = 500, message = "评价内容不能超过 500 字")
    private String content;
}
