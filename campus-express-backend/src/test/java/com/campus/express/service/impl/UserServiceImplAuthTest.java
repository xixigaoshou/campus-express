package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.dto.ChangePasswordDTO;
import com.campus.express.dto.LoginDTO;
import com.campus.express.dto.RegisterDTO;
import com.campus.express.dto.UpdateUserDTO;
import com.campus.express.entity.User;
import com.campus.express.entity.Wallet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户「注册 / 登录 / 当前用户 / 资料 / 登录密码」单元测试
 *
 * <p>重点覆盖：登录的账号不存在/密码错误/禁用分支与响应脱敏、注册的短信验证码前置校验与
 * 学号/手机号唯一性、角色到发布权限的映射、当前用户鉴权、手机号唯一性、修改密码的原密码复核。</p>
 */
class UserServiceImplAuthTest extends AbstractUserServiceTest {

    // ---------------- 登录 ----------------

    @Test
    @DisplayName("登录：账号不存在 → 统一提示「账号或密码错误」，不泄露账号是否存在")
    void loginRejectsUnknownAccount() {
        LoginDTO dto = loginDto("2021001", "Abc123");
        when(userMapper.selectByLoginName("2021001")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.login(dto));
        assertTrue(ex.getMessage().contains("账号或密码错误"));
    }

    @Test
    @DisplayName("登录：密码不匹配 → 统一提示「账号或密码错误」")
    void loginRejectsWrongPassword() {
        LoginDTO dto = loginDto("2021001", "wrong");
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$hash");
        when(userMapper.selectByLoginName("2021001")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "$hash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.login(dto));
        assertTrue(ex.getMessage().contains("账号或密码错误"));
    }

    @Test
    @DisplayName("登录：账号被禁用 → 拒绝登录")
    void loginRejectsDisabledAccount() {
        LoginDTO dto = loginDto("2021001", "Abc123");
        User u = user(1, "张三", 100, 0);
        u.setPasswordHash("$hash");
        when(userMapper.selectByLoginName("2021001")).thenReturn(u);
        when(passwordEncoder.matches("Abc123", "$hash")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.login(dto));
        assertTrue(ex.getMessage().contains("账号已被禁用"));
    }

    @Test
    @DisplayName("登录成功：签发 JWT，响应脱敏（不下发任何密码哈希，仅回传已设支付密码标记）")
    void loginSuccessSanitizesPasswords() {
        LoginDTO dto = loginDto("2021001", "Abc123");
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$hash");
        u.setPayPasswordHash("$pay");
        when(userMapper.selectByLoginName("2021001")).thenReturn(u);
        when(passwordEncoder.matches("Abc123", "$hash")).thenReturn(true);
        when(jwtUtil.generateToken(1, "张三", 1)).thenReturn("jwt-abc");

        Map<String, Object> result = service.login(dto);

        assertEquals("jwt-abc", result.get("token"));
        User returned = (User) result.get("user");
        assertNull(returned.getPasswordHash());
        assertNull(returned.getPayPasswordHash());
        assertEquals(Boolean.TRUE, returned.getPayPasswordSet());
    }

    @Test
    @DisplayName("登录成功：未设置支付密码时标记为 false")
    void loginMarksPayPasswordUnset() {
        LoginDTO dto = loginDto("2021001", "Abc123");
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$hash");
        when(userMapper.selectByLoginName("2021001")).thenReturn(u);
        when(passwordEncoder.matches("Abc123", "$hash")).thenReturn(true);

        User returned = (User) service.login(dto).get("user");
        assertEquals(Boolean.FALSE, returned.getPayPasswordSet());
    }

    // ---------------- 注册 ----------------

    @Test
    @DisplayName("注册：未先获取短信验证码 → 拒绝")
    void registerRequiresSmsCode() {
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", "123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
        assertTrue(ex.getMessage().contains("请先获取短信验证码"));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册：学号已存在 → 拒绝")
    void registerRejectsDuplicateStudentNo() {
        String code = service.sendRegisterCode("13800000000");
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", code);
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
        assertTrue(ex.getMessage().contains("该学号已注册"));
    }

    @Test
    @DisplayName("注册：手机号已被占用 → 拒绝")
    void registerRejectsDuplicatePhone() {
        String code = service.sendRegisterCode("13800000000");
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", code);
        // 第一次查学号（0 不存在），第二次查手机号（1 已占用）
        when(userMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
        assertTrue(ex.getMessage().contains("该手机号已注册"));
    }

    @Test
    @DisplayName("注册成功：默认角色需求方 → canPublish=true、信誉分初始值、账户正常、创建钱包")
    void registerSuccessDefaultsToPublisher() {
        String code = service.sendRegisterCode("13800000000");
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", code);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Abc123")).thenReturn("$encoded");

        service.register(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals("2021001", saved.getStudentNo());
        assertEquals("$encoded", saved.getPasswordHash());
        assertEquals(1, saved.getRole());
        assertEquals(Boolean.TRUE, saved.getCanPublish());
        assertEquals(rules.getCredit().getInitScore(), saved.getCreditScore());
        assertEquals(1, saved.getStatus());
        verify(walletMapper).insert(any(Wallet.class));
    }

    @Test
    @DisplayName("注册成功：注册为接单方（role=2）→ canPublish=false")
    void registerAsTakerDisablesPublish() {
        String code = service.sendRegisterCode("13800000000");
        RegisterDTO dto = registerDto("2021001", "13800000000", "Abc123", code);
        dto.setRole(2);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Abc123")).thenReturn("$encoded");

        service.register(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getCanPublish());
    }

    // ---------------- 当前用户 ----------------

    @Test
    @DisplayName("获取当前用户：未登录 → 401")
    void getCurrentUserRejectsWhenNotLoggedIn() {
        when(request.getAttribute(com.campus.express.config.JwtInterceptor.ATTR_USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCurrentUser());
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("获取当前用户：令牌用户不存在 → 401")
    void getCurrentUserRejectsWhenUserMissing() {
        loggedInAs(1);
        when(userMapper.selectById(1)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCurrentUser());
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("获取当前用户：正常返回")
    void getCurrentUserReturnsUser() {
        loggedInAs(1);
        when(userMapper.selectById(1)).thenReturn(user(1, "张三", 100, 1));

        assertEquals(1, service.getCurrentUser().getUserId());
    }

    // ---------------- 更新资料 ----------------

    @Test
    @DisplayName("更新资料：手机号已被其他账号使用 → 拒绝")
    void updateUserInfoRejectsDuplicatePhone() {
        loggedInAs(1);
        when(userMapper.selectById(1)).thenReturn(user(1, "张三", 100, 1));
        when(userMapper.selectCount(any())).thenReturn(1L);
        UpdateUserDTO dto = updateDto("13900000000", "A区");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateUserInfo(dto));
        assertTrue(ex.getMessage().contains("已被其他账号使用"));
    }

    @Test
    @DisplayName("更新资料成功：仅更新手机号与宿舍区域")
    void updateUserInfoSuccess() {
        loggedInAs(1);
        when(userMapper.selectById(1)).thenReturn(user(1, "张三", 100, 1));
        when(userMapper.selectCount(any())).thenReturn(0L);

        service.updateUserInfo(updateDto("13900000000", "A区"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getUserId());
        assertEquals("13900000000", captor.getValue().getPhone());
        assertEquals("A区", captor.getValue().getDormArea());
    }

    // ---------------- 修改登录密码 ----------------

    @Test
    @DisplayName("修改密码：原密码不正确 → 拒绝")
    void changePasswordRejectsWrongOld() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$old");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("bad", "$old")).thenReturn(false);
        ChangePasswordDTO dto = changePwdDto("bad", "New456");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.changePassword(dto));
        assertTrue(ex.getMessage().contains("原密码不正确"));
    }

    @Test
    @DisplayName("修改密码：新密码与原密码相同 → 拒绝")
    void changePasswordRejectsSameAsOld() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$old");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("Abc123", "$old")).thenReturn(true);
        ChangePasswordDTO dto = changePwdDto("Abc123", "Abc123");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.changePassword(dto));
        assertTrue(ex.getMessage().contains("新密码不能与原密码相同"));
    }

    @Test
    @DisplayName("修改密码成功：写入新密码哈希")
    void changePasswordSuccess() {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setPasswordHash("$old");
        when(userMapper.selectById(1)).thenReturn(u);
        when(passwordEncoder.matches("Old123", "$old")).thenReturn(true);
        when(passwordEncoder.matches("New456", "$old")).thenReturn(false);
        when(passwordEncoder.encode("New456")).thenReturn("$new");

        service.changePassword(changePwdDto("Old123", "New456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("$new", captor.getValue().getPasswordHash());
    }

    // ---------------- 构造辅助 ----------------

    private LoginDTO loginDto(String name, String pwd) {
        LoginDTO dto = new LoginDTO();
        dto.setLoginName(name);
        dto.setPassword(pwd);
        return dto;
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

    private UpdateUserDTO updateDto(String phone, String dormArea) {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setPhone(phone);
        dto.setDormArea(dormArea);
        return dto;
    }

    private ChangePasswordDTO changePwdDto(String oldPwd, String newPwd) {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword(oldPwd);
        dto.setNewPassword(newPwd);
        return dto;
    }
}