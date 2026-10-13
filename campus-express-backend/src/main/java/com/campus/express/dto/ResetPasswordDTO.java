package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 找回密码第二步：重置密码参数（免登录）
 * 须携带第一步下发到注册手机号的短信验证码（证明手机号归属），校验通过后方可重置
 */
@Data
public class ResetPasswordDTO {

    /** 学号/工号（与手机号同时匹配才允许重置） */
    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "学号格式不正确（4-20位字母数字）")
    private String studentNo;

    /** 注册手机号（验证码接收号码） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 第一步下发到注册手机号的短信验证码 */
    @NotBlank(message = "短信验证码不能为空，请先获取验证码")
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码为 6 位数字")
    private String smsCode;

    /** 新密码（明文，Service 内 BCrypt 加盐加密后存储） */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{6,20}$", message = "新密码需 6-20 位，且同时包含字母和数字")
    private String newPassword;
}