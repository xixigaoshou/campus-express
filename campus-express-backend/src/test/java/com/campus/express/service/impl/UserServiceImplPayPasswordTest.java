package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.dto.SetPayPasswordDTO;
import com.campus.express.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户「支付密码」单元测试
 *
 * <p>重点覆盖：首次设置与已设置后修改的分支差异、支付密码不得与登录密码相同、
 * 原支付密码复核、连续错误 5 次锁定 15 分钟的暴力枚举防护。</p>
 */
class UserServiceImplPayPasswordTest extends AbstractUserServiceTest {

    // ---------------- 设置 / 修改支付密码 ----------------

    @Test
    @DisplayName("设置支付密码：新密码与登录密码相同 → 拒绝")
    void setPayPasswordRejectsSameAsLoginPassword() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$login");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("123456", "$login")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.setPayPassword(payPwd("123456", null)));
        assertTrue(ex.getMessage().contains("支付密码不能与登录密码相同"));
    }

    @Test
    @DisplayName("设置支付密码：首次设置却传了原支付密码 → 拒绝")
    void setPayPasswordRejectsOldOnFirstSetup() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$login");
        u.setPayPasswordHash(null);
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("123456", "$login")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.setPayPassword(payPwd("123456", "654321")));
        assertTrue(ex.getMessage().contains("首次设置支付密码无需输入原支付密码"));
    }

    @Test
    @DisplayName("设置支付密码：首次设置成功 → 写入支付密码哈希")
    void setPayPasswordFirstSetupSuccess() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$login");
        u.setPayPasswordHash(null);
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("123456", "$login")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("$pay");

        service.setPayPassword(payPwd("123456", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getUserId());
        assertEquals("$pay", captor.getValue().getPayPasswordHash());
    }

    @Test
    @DisplayName("修改支付密码：已设置却未传原支付密码 → 拒绝")
    void setPayPasswordRequiresOldWhenAlreadySet() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$login");
        u.setPayPasswordHash("$payOld");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("123456", "$login")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.setPayPassword(payPwd("123456", null)));
        assertTrue(ex.getMessage().contains("请输入原支付密码"));
    }

    @Test
    @DisplayName("修改支付密码：原支付密码不正确 → 拒绝并记录一次失败")
    void setPayPasswordRejectsWrongOld() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$login");
        u.setPayPasswordHash("$payOld");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("123456", "$login")).thenReturn(false);
        when(passwordEncoder.matches("000000", "$payOld")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.setPayPassword(payPwd("123456", "000000")));
        assertTrue(ex.getMessage().contains("原支付密码不正确"));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("修改支付密码：新支付密码与原支付密码相同 → 拒绝")
    void setPayPasswordRejectsSameAsOld() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$login");
        u.setPayPasswordHash("$payOld");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("123456", "$login")).thenReturn(false);
        when(passwordEncoder.matches("123456", "$payOld")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.setPayPassword(payPwd("123456", "123456")));
        assertTrue(ex.getMessage().contains("新支付密码不能与原支付密码相同"));
    }

    @Test
    @DisplayName("修改支付密码成功：校验原密码通过后写入新哈希")
    void setPayPasswordModifySuccess() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$login");
        u.setPayPasswordHash("$payOld");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("654321", "$login")).thenReturn(false);
        when(passwordEncoder.matches("123456", "$payOld")).thenReturn(true);
        when(passwordEncoder.matches("654321", "$payOld")).thenReturn(false);
        when(passwordEncoder.encode("654321")).thenReturn("$payNew");

        service.setPayPassword(payPwd("654321", "123456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("$payNew", captor.getValue().getPayPasswordHash());
    }

    // ---------------- 校验支付密码 ----------------

    @Test
    @DisplayName("校验支付密码：用户不存在 → 拒绝")
    void verifyPayPasswordRejectsUnknownUser() {
        when(userMapper.selectById(1)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyPayPassword(1, "123456"));
        assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    @DisplayName("校验支付密码：尚未设置 → 引导先去设置")
    void verifyPayPasswordRejectsWhenUnset() {
        User u = user(1, "张三", 100, 1);
        u.setPayPasswordHash(null);
        when(userMapper.selectById(1)).thenReturn(u);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyPayPassword(1, "123456"));
        assertTrue(ex.getMessage().contains("尚未设置支付密码"));
    }

    @Test
    @DisplayName("校验支付密码：未输入 → 拒绝")
    void verifyPayPasswordRejectsEmptyInput() {
        User u = user(1, "张三", 100, 1);
        u.setPayPasswordHash("$pay");
        when(userMapper.selectById(1)).thenReturn(u);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyPayPassword(1, ""));
        assertTrue(ex.getMessage().contains("请输入支付密码"));
    }

    @Test
    @DisplayName("校验支付密码：密码错误 → 拒绝")
    void verifyPayPasswordRejectsWrong() {
        User u = user(1, "张三", 100, 1);
        u.setPayPasswordHash("$pay");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("000000", "$pay")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyPayPassword(1, "000000"));
        assertTrue(ex.getMessage().contains("支付密码不正确"));
    }

    @Test
    @DisplayName("校验支付密码：正确 → 通过")
    void verifyPayPasswordSuccess() {
        User u = user(1, "张三", 100, 1);
        u.setPayPasswordHash("$pay");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("123456", "$pay")).thenReturn(true);

        assertDoesNotThrow(() -> service.verifyPayPassword(1, "123456"));
    }

    @Test
    @DisplayName("校验支付密码：连续错误 5 次 → 锁定，第 6 次直接拒绝")
    void verifyPayPasswordLocksAfterFiveFailures() {
        User u = user(1, "张三", 100, 1);
        u.setPayPasswordHash("$pay");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches(any(), eq("$pay"))).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.verifyPayPassword(1, "000000"));
            assertTrue(ex.getMessage().contains("支付密码不正确"));
        }

        BusinessException locked = assertThrows(BusinessException.class,
                () -> service.verifyPayPassword(1, "000000"));
        assertTrue(locked.getMessage().contains("错误次数过多"));
    }

    @Test
    @DisplayName("校验支付密码：锁定期间设置支付密码同样被拒绝")
    void setPayPasswordRejectedWhenLocked() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$login");
        u.setPayPasswordHash("$pay");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches(any(), eq("$pay"))).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThrows(BusinessException.class, () -> service.verifyPayPassword(1, "000000"));
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.setPayPassword(payPwd("123456", "654321")));
        assertTrue(ex.getMessage().contains("错误次数过多"));
    }

    private SetPayPasswordDTO payPwd(String newPwd, String oldPwd) {
        SetPayPasswordDTO dto = new SetPayPasswordDTO();
        dto.setNewPayPassword(newPwd);
        dto.setOldPayPassword(oldPwd);
        return dto;
    }
}