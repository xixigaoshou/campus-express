package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送留言请求参数（3.6 消息留言）
 */
@Data
public class SendMessageDTO {

    @NotNull(message = "订单ID不能为空")
    private Integer orderId;

    /** 留言内容（Service 内做敏感词过滤） */
    @NotBlank(message = "留言内容不能为空")
    @Size(max = 1000, message = "留言内容不能超过 1000 字")
    private String content;
}
