package com.campus.express.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.common.Result;
import com.campus.express.dto.ChangePasswordDTO;
import com.campus.express.dto.LoginDTO;
import com.campus.express.dto.RegisterDTO;
import com.campus.express.dto.ResetPasswordCodeDTO;
import com.campus.express.dto.ResetPasswordDTO;
import com.campus.express.dto.SendSmsCodeDTO;
import com.campus.express.dto.SetPayPasswordDTO;
import com.campus.express.dto.UpdateUserDTO;
import com.campus.express.entity.CreditLog;
import com.campus.express.entity.User;
import com.campus.express.service.UserService;
import com.campus.express.vo.UserStatsVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户接口（/api/user）
 * 注册与登录免鉴权（WebMvcConfig 已放行），其余接口需携带 JWT
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户注册（免登录）
     * 权限：公开；须携带 sendRegisterCode 下发的短信验证码（校验手机号真实性）；
     * 学号/手机号唯一，密码 BCrypt 加盐加密，初始化信誉分 100
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO dto) {
        userService.register(dto);
        return Result.success("注册成功", null);
    }

    /**
     * 发送注册短信验证码（免登录）
     * 权限：公开；同号发送间隔限制、每日次数上限、验证码 5 分钟有效；
     * 演示模式下（business.sms.expose-code=true）响应体回传验证码，生产环境走短信通道返回 null
     */
    @PostMapping("/register/code")
    public Result<String> sendRegisterCode(@RequestBody @Valid SendSmsCodeDTO dto) {
        String code = userService.sendRegisterCode(dto.getPhone());
        String msg = code == null ? "验证码已发送" : "验证码已发送（演示环境验证码：" + code + "）";
        return Result.success(msg, code);
    }

    /**
     * 用户登录（免登录）
     * 权限：公开；学号/手机号 + 密码校验，签发 JWT 令牌
     * @return { token, user }
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    /**
     * 获取当前登录用户信息（含信誉分）
     * 权限：已登录；从 JWT 解析身份返回，密码哈希脱敏
     */
    @GetMapping("/info")
    public Result<User> info() {
        User user = userService.getCurrentUser();
        user.setPasswordHash(null); // 脱敏：不返回密码哈希
        // 支付密码只返回「是否已设置」标记，哈希一律不下发
        user.setPayPasswordSet(user.getPayPasswordHash() != null);
        user.setPayPasswordHash(null);
        return Result.success(user);
    }

    /**
     * 更新个人资料（宿舍区域、手机号）
     * 权限：已登录；仅允许修改本人资料，学号不可修改
     */
    @PutMapping("/info")
    public Result<Void> updateInfo(@RequestBody @Valid UpdateUserDTO dto) {
        userService.updateUserInfo(dto);
        return Result.success("资料更新成功", null);
    }

    /**
     * 修改密码（登录状态）
     * 权限：已登录；校验原密码正确后更新为新密码（BCrypt 哈希存储）
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success("密码修改成功", null);
    }

    /**
     * 设置 / 修改支付密码（登录状态）
     * 权限：已登录；支付密码为 6 位数字，BCrypt 加盐存储，不得与登录密码相同；
     * 首次设置无需原支付密码，已设置后修改须校验原支付密码
     */
    @PutMapping("/pay-password")
    public Result<Void> setPayPassword(@RequestBody @Valid SetPayPasswordDTO dto) {
        userService.setPayPassword(dto);
        return Result.success("支付密码设置成功", null);
    }

    /**
     * 找回密码第一步：发送短信验证码（免登录）
     * 权限：公开；学号 + 注册手机号匹配后向该手机号下发验证码（证明手机号归属）；
     * 连续输错将临时限制尝试；演示模式下响应体回传验证码
     * @return 验证码明文（仅演示环境），生产环境为 null
     */
    @PostMapping("/password/reset/code")
    public Result<String> sendResetCode(@RequestBody @Valid ResetPasswordCodeDTO dto) {
        String code = userService.sendResetCode(dto);
        String msg = code == null ? "验证码已发送至注册手机号" : "验证码已发送（演示环境验证码：" + code + "）";
        return Result.success(msg, code);
    }

    /**
     * 找回密码第二步：重置密码（免登录）
     * 权限：公开；须携带第一步下发到注册手机号的短信验证码（一次性、带时效），校验通过后重置
     */
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@RequestBody @Valid ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return Result.success("密码重置成功，请使用新密码登录", null);
    }

    /**
     * 上传头像
     * 权限：已登录；multipart 表单字段名为 file；仅图片格式且不超过 5MB；
     * 保存后返回可访问相对路径（/uploads/avatar/xxx），经静态资源映射访问
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success("头像上传成功", userService.updateAvatar(file));
    }

    /**
     * 信誉分明细分页查询
     * 权限：已登录；仅返回本人流水（评价好评/差评、违约取消、接单超时等），按时间倒序
     */
    @GetMapping("/credit-logs")
    public Result<IPage<CreditLog>> creditLogs(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") int pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页条数不能超过 100") int pageSize) {
        return Result.success(userService.pageCreditLogs(pageNum, pageSize));
    }

    /**
     * 个人中心统计（全量聚合）
     * 权限：已登录；返回本人发布总数/取消数、接单完成数/取消数、累计收入/支出，
     * 由后端 COUNT / SUM 全量统计，不受分页条数上限影响
     */
    @GetMapping("/stats")
    public Result<UserStatsVO> stats() {
        return Result.success(userService.getUserStats());
    }
}
