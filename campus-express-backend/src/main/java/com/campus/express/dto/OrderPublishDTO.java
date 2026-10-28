package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发布代取需求请求参数（3.3）
 */
@Data
public class OrderPublishDTO {

    /** 取件码（必填，取件凭据：4-15 位数字或横线，如 8-2-3021） */
    @NotBlank(message = "取件码不能为空")
    @Pattern(regexp = "^[0-9-]{4,15}$", message = "取件码为 4-15 位数字或横线")
    private String pickupCode;

    /** 快递公司 */
    @Size(max = 30, message = "快递公司不能超过 30 个字符")
    private String expressCompany;

    /** 取件地点（必填） */
    @NotBlank(message = "取件地点不能为空")
    @Size(max = 100, message = "取件地点不能超过 100 个字符")
    private String pickupLocation;

    /** 送达地点（必填） */
    @NotBlank(message = "送达地点不能为空")
    @Size(max = 100, message = "送达地点不能超过 100 个字符")
    private String deliverLocation;

    /** 期望取件时间（必填） */
    @NotNull(message = "期望取件时间不能为空")
    private LocalDateTime expectTime;

    /** 包裹备注 */
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    /** 报酬金额（规则 R-17：2 ~ 50 元） */
    @NotNull(message = "报酬金额不能为空")
    private BigDecimal rewardAmount;

    /** 支付密码（6 位数字，发布订单冻结报酬前校验；未设置时引导先到个人中心设置） */
    private String payPassword;
}
