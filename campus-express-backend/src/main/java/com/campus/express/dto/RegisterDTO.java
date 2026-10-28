package com.campus.express.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数
 */
@Data
public class RegisterDTO {

    /** 学号/工号，唯一 */
    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "学号格式不正确（4-20位字母数字）")
    private String studentNo;

    /** 用户姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String username;

    /** 手机号，唯一 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 短信验证码（先调 /api/user/register/code 获取，用于校验手机号真实性，防止随意注册他人手机号） */
    @NotBlank(message = "短信验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码为 6 位数字")
    private String smsCode;

    /** 密码（明文，Service 内 BCrypt 加盐加密后存储） */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{6,20}$", message = "密码需 6-20 位，且同时包含字母和数字")
    private String password;

    /** 角色：1 需求方 2 接单方（不填默认需求方） */
    @Min(value = 1, message = "角色取值不合法")
    @Max(value = 2, message = "角色取值不合法")
    private Integer role;

    /** 宿舍区域 */
    @Size(max = 50, message = "宿舍区域不能超过 50 个字符")
    private String dormArea;
}
