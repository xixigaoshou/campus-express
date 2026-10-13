package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.dto.RegisterDTO;
import com.campus.express.dto.ResetPasswordCodeDTO;
import com.campus.express.dto.ResetPasswordDTO;
import com.campus.express.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户「短信验证码 / 找回密码」单元测试
 *
 * <p>重点覆盖：验证码发送间隔与每日次数限流、场景隔离（注册码不能用于找回密码）、
 * 验证码时效与错误次数作废、一次性失效、找回密码的学号+手机号匹配与时间窗内限次。</p>
 */
class UserServiceImplSmsResetTest extends AbstractUserServiceTest {

    // ---------------- 发送注册验证码 ----------------

    @Test
    @DisplayName("发送注册验证码：返回 6 位数字验证码")
    void sendRegisterCodeReturnsSixDigitCode() {
        String code = service.sendRegisterCode("13800000000");

        assertNotNull(code);
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    @DisplayName("发送注册验证码：演示开关关闭时不回传验证码")
    void sendRegisterCodeHiddenWhenExposeDisabled() {
        rules.getSms().setExposeCode(false);

        assertNull(service.sendRegisterCode("13800000000"));
    }

    @Test
    @DisplayName("发送注册验证码：同号 60 秒内重复发送 → 拒绝（防刷）")
    void sendRegisterCodeRejectsTooFrequent() {
        service.sendRegisterCode("13800000000");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendRegisterCode("13800000000"));
        assertTrue(ex.getMessage().contains("过于频繁"));
    }

    @Test
    @DisplayName("发送注册验证码：达到每日次数上限 → 拒绝")
    void sendRegisterCodeRejectsDailyLimit() {
        rules.getSms().setResendSeconds(0);
        rules.getSms().setDailyLimit(2);

        service.sendRegisterCode("13800000000");
        service.sendRegisterCode("13800000000");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendRegisterCode("13800000000"));
        assertTrue(ex.getMessage().contains("今日验证码发送次数已达上限"));
    }

    // ---------------- 注册时校验验证码 ----------------

    @Test
    @DisplayName("注册：验证码不正确 → 拒绝")
    void registerRejectsWrongSmsCode() {
        String real = service.sendRegisterCode("13800000000");
        String wrong = "000000".equals(real) ? "111111" : "000000";
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", wrong);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
        assertTrue(ex.getMessage().contains("短信验证码不正确"));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册：验证码已过期 → 拒绝")
    void registerRejectsExpiredSmsCode() {
        rules.getSms().setCodeMinutes(-1);
        String code = service.sendRegisterCode("13800000000");
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", code);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
        assertTrue(ex.getMessage().contains("已过期"));
    }

    @Test
    @DisplayName("注册：验证码连续错误达上限 → 作废，提示次数过多")
    void registerInvalidatesCodeAfterTooManyFailures() {
        String real = service.sendRegisterCode("13800000000");
        String wrong = "000000".equals(real) ? "111111" : "000000";
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", wrong);

        for (int i = 0; i < rules.getSms().getMaxVerifyAttempts() - 1; i++) {
            BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
            assertTrue(ex.getMessage().contains("短信验证码不正确"));
        }
        BusinessException last = assertThrows(BusinessException.class, () -> service.register(dto));
        assertTrue(last.getMessage().contains("错误次数过多"));
    }

    @Test
    @DisplayName("注册：验证码一次性，成功使用后不可复用")
    void registerSmsCodeIsSingleUse() {
        String code = service.sendRegisterCode("13800000000");
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", code);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Abc123")).thenReturn("$encoded");

        service.register(dto);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
        assertTrue(ex.getMessage().contains("请先获取短信验证码"));
    }

    // ---------------- 找回密码第一步：发送验证码 ----------------

    @Test
    @DisplayName("找回密码发码：学号+手机号不匹配 → 统一提示，不泄露账号是否存在")
    void sendResetCodeRejectsMismatch() {
        when(userMapper.selectOne(any())).thenReturn(null);
        ResetPasswordCodeDTO dto = resetCodeDto("2021001", "13800000000");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendResetCode(dto));
        assertTrue(ex.getMessage().contains("信息不匹配"));
    }

    @Test
    @DisplayName("找回密码发码：学号+手机号匹配 → 下发验证码")
    void sendResetCodeSuccess() {
        when(userMapper.selectOne(any())).thenReturn(userWithPhone(1, "2021001", "13800000000"));

        String code = service.sendResetCode(resetCodeDto("2021001", "13800000000"));

        assertNotNull(code);
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    @DisplayName("找回密码发码：时间窗内验证失败次数达上限 → 拒绝（防暴力枚举）")
    void sendResetCodeRejectsAfterTooManyAttempts() {
        rules.getPasswordReset().setMaxAttempts(3);
        when(userMapper.selectOne(any())).thenReturn(null);
        ResetPasswordCodeDTO dto = resetCodeDto("2021001", "13800000000");

        for (int i = 0; i < 3; i++) {
            BusinessException ex = assertThrows(BusinessException.class, () -> service.sendResetCode(dto));
            assertTrue(ex.getMessage().contains("信息不匹配"));
        }
        BusinessException last = assertThrows(BusinessException.class, () -> service.sendResetCode(dto));
        assertTrue(last.getMessage().contains("身份验证尝试次数过多"));
    }

    // ---------------- 找回密码第二步：重置密码 ----------------

    @Test
    @DisplayName("重置密码：未先获取验证码 → 拒绝")
    void resetPasswordRejectsWithoutCode() {
        ResetPasswordDTO dto = resetDto("2021001", "13800000000", "123456", "New456");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resetPassword(dto));
        assertTrue(ex.getMessage().contains("请先获取短信验证码"));
    }

    @Test
    @DisplayName("重置密码：注册场景验证码不能跨场景用于找回密码")
    void resetPasswordRejectsCrossSceneCode() {
        String registerCode = service.sendRegisterCode("13800000000");
        ResetPasswordDTO dto = resetDto("2021001", "13800000000", registerCode, "New456");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resetPassword(dto));
        assertTrue(ex.getMessage().contains("请先获取短信验证码"));
    }

    @Test
    @DisplayName("重置密码成功：验证码校验通过后写入新密码哈希")
    void resetPasswordSuccess() {
        when(userMapper.selectOne(any())).thenReturn(userWithPhone(1, "2021001", "13800000000"));
        String code = service.sendResetCode(resetCodeDto("2021001", "13800000000"));
        when(passwordEncoder.encode("New456")).thenReturn("$new");

        service.resetPassword(resetDto("2021001", "13800000000", code, "New456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("$new", captor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("重置密码：验证码正确但学号+手机号不匹配 → 拒绝")
    void resetPasswordRejectsMismatchAfterValidCode() {
        // 第一次查询（发码）匹配，第二次查询（重置）不匹配
        when(userMapper.selectOne(any()))
                .thenReturn(userWithPhone(1, "2021001", "13800000000"))
                .thenReturn(null);
        String code = service.sendResetCode(resetCodeDto("2021001", "13800000000"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resetPassword(resetDto("2021001", "13800000000", code, "New456")));
        assertTrue(ex.getMessage().contains("信息不匹配"));
    }

    // ---------------- 构造辅助 ----------------

    private User userWithPhone(int id, String studentNo, String phone) {
        User u = user(id, "张三", 100, 1);
        u.setStudentNo(studentNo);
        u.setPhone(phone);
        return u;
    }

    private RegisterDTO registerDto(String studentNo, String phone, String pwd, String smsCode) {
        RegisterDTO dto = new RegisterDTO();
        dto.setStudentNo(studentNo);
        dto.setUsername("张三");
        dto.setPhone(phone);
        dto.setPassword(pwd);
        dto.setSmsCode(smsCode);
        dto.setDormArea("A区");
        return dto;
    }

    private ResetPasswordCodeDTO resetCodeDto(String studentNo, String phone) {
        ResetPasswordCodeDTO dto = new ResetPasswordCodeDTO();
        dto.setStudentNo(studentNo);
        dto.setPhone(phone);
        return dto;
    }

    private ResetPasswordDTO resetDto(String studentNo, String phone, String smsCode, String newPwd) {
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setStudentNo(studentNo);
        dto.setPhone(phone);
        dto.setSmsCode(smsCode);
        dto.setNewPassword(newPwd);
        return dto;
    }
}