package com.campus.express.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料请求参数：仅允许修改宿舍区域与手机号，学号不可改
 */
@Data
public class UpdateUserDTO {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 宿舍区域 */
    @Size(max = 50, message = "宿舍区域不能超过 50 个字符")
    private String dormArea;
}
