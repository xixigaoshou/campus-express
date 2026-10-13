package com.campus.express.dto;

import jakarta.validation.constraints.NotNull;
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
    private String reason;
}