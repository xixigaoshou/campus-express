package com.campus.express.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现请求参数（POST /api/wallet/withdraw）
 * 模拟提现，不接入真实支付通道；金额 0.5 ~ 100 元，且不得超过可用余额（Service 层校验）
 */
@Data
public class WalletWithdrawDTO {

    /** 提现金额（0.5 ~ 100 元） */
    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "0.5", message = "提现金额不能小于 0.5 元")
    @DecimalMax(value = "100", message = "提现金额不能大于 100 元")
    private BigDecimal withdrawAmount;

    /** 支付密码（6 位数字，Service 层校验；未设置时引导先到个人中心设置） */
    private String payPassword;
}