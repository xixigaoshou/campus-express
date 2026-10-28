package com.campus.express.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起转单接力请求
 */
@Data
public class OrderTransferDTO {

    /** 订单 ID */
    @NotNull(message = "订单ID不能为空")
    private Integer orderId;

    /** 转单原因（须为 TransferReason 清单内文案） */
    @Size(max = 200, message = "转单原因不能超过 200 个字符")
    private String reason;
}