package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改密码请求参数（登录状态下修改）
 */
@Data
public class ChangePasswordDTO {

    /** 原密码（用于身份复核，比对 BCrypt 哈希） */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码（明文，Service 内 BCrypt 加盐加密后存储） */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{6,20}$", message = "新密码需 6-20 位，且同时包含字母和数字")
    private String newPassword;
}
