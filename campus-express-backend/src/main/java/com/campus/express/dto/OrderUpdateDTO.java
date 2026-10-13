package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 修改待接单订单请求参数（规则 R-11：仅待接单状态可修改）
 */
@Data
public class OrderUpdateDTO {

    @NotNull(message = "订单ID不能为空")
    private Integer orderId;

    /** 取件码（必填，取件凭据：4-15 位数字或横线，如 8-2-3021） */
    @NotBlank(message = "取件码不能为空")
    @Pattern(regexp = "^[0-9-]{4,15}$", message = "取件码为 4-15 位数字或横线")
    private String pickupCode;

    /** 快递公司 */
    private String expressCompany;

    @NotBlank(message = "取件地点不能为空")
    private String pickupLocation;

    @NotBlank(message = "送达地点不能为空")
    private String deliverLocation;

    @NotNull(message = "期望取件时间不能为空")
    private LocalDateTime expectTime;

    private String remark;

    @NotNull(message = "报酬金额不能为空")
    private BigDecimal rewardAmount;

    /** 支付密码：仅当提高报酬（追加冻结、需付更多钱）时必填，Service 层按此校验 */
    private String payPassword;
}
