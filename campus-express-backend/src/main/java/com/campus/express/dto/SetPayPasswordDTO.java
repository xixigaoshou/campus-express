package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 设置 / 修改支付密码请求参数（PUT /api/user/pay-password）
 * 首次设置无需原支付密码；已设置后修改须校验原支付密码
 */
@Data
public class SetPayPasswordDTO {

    /** 原支付密码（首次设置时为空；已设置后修改必填，Service 层校验） */
    @Pattern(regexp = "^$|^\\d{6}$", message = "原支付密码为 6 位数字")
    private String oldPayPassword;

    /** 新支付密码（6 位数字） */
    @NotBlank(message = "请输入新支付密码")
    @Pattern(regexp = "^\\d{6}$", message = "支付密码为 6 位数字")
    private String newPayPassword;
}