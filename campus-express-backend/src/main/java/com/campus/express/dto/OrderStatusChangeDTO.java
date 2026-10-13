package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单状态变更请求参数（3.5 状态流转）
 */
@Data
public class OrderStatusChangeDTO {

    @NotNull(message = "订单ID不能为空")
    private Integer orderId;

    /** 目标状态：已接单/取件中/配送中/已完成（终态由取消接口处理） */
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    /** 变更说明 */
    private String changeNote;

    /** 无法拍照跳过标记：流转到「待收货」时未上传送达照片，需显式置 true 才允许继续（默认 false） */
    private Boolean skipPhoto;
}
