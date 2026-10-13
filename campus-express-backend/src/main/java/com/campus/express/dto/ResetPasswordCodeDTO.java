package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 找回密码第一步：发送短信验证码参数（免登录）
 * 学号 + 注册手机号匹配通过后，向该手机号下发短信验证码（证明手机号归属）
 */
@Data
public class ResetPasswordCodeDTO {

    /** 学号/工号（唯一标识，用于定位账号） */
    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "学号格式不正确（4-20位字母数字）")
    private String studentNo;

    /** 注册手机号（与学号同时匹配才允许发送验证码） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}