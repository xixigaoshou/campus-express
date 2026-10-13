package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数：学号或手机号 + 密码
 */
@Data
public class LoginDTO {

    /** 学号或手机号（两者均唯一） */
    @NotBlank(message = "登录账号不能为空")
    private String loginName;

    @NotBlank(message = "密码不能为空")
    private String password;
}
