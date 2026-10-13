package com.campus.express.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟充值请求参数（POST /api/wallet/recharge）
 * 仅模拟充值，不接入真实支付；金额 0.5 ~ 100 元
 */
@Data
public class WalletRechargeDTO {

    /** 充值金额（0.5 ~ 100 元） */
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.5", message = "充值金额不能小于 0.5 元")
    @DecimalMax(value = "100", message = "充值金额不能大于 100 元")
    private BigDecimal rechargeAmount;

    /** 支付密码（6 位数字，Service 层校验；未设置时引导先到个人中心设置） */
    private String payPassword;
}
