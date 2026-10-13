package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送注册短信验证码请求参数（POST /api/user/register/code）
 * 免登录：注册前先校验手机号可接收验证码，避免随意用他人手机号注册
 */
@Data
public class SendSmsCodeDTO {

    /** 手机号（接收注册验证码） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}