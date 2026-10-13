package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户「头像上传」单元测试
 *
 * <p>重点覆盖：空文件与非法扩展名拦截、文件头魔数真实性校验（拒绝改扩展名的伪装文件）、
 * 大小上限、成功落盘后以真实类型命名并回写头像字段。</p>
 */
class UserServiceImplAvatarTest extends AbstractUserServiceTest {

    @Test
    @DisplayName("头像上传：文件为空 → 拒绝")
    void updateAvatarRejectsNullFile() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateAvatar(null));
        assertTrue(ex.getMessage().contains("请选择要上传的图片"));
    }

    @Test
    @DisplayName("头像上传：空文件 → 拒绝")
    void updateAvatarRejectsEmptyFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateAvatar(file));
        assertTrue(ex.getMessage().contains("请选择要上传的图片"));
    }

    @Test
    @DisplayName("头像上传：扩展名不在白名单 → 拒绝")
    void updateAvatarRejectsBadExtension() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("evil.jsp");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateAvatar(file));
        assertTrue(ex.getMessage().contains("仅支持 jpg/png/gif/webp"));
    }

    @Test
    @DisplayName("头像上传：扩展名合法但文件头非图片 → 拒绝（防伪装文件落地）")
    void updateAvatarRejectsFakeImage() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("evil.jpg");
        when(file.getInputStream())
                .thenReturn(new ByteArrayInputStream("not-an-image".getBytes()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateAvatar(file));
        assertTrue(ex.getMessage().contains("图片内容校验失败"));
    }

    @Test
    @DisplayName("头像上传：图片超过 5MB → 拒绝")
    void updateAvatarRejectsOversize() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("big.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(jpegHeader()));
        when(file.getSize()).thenReturn(6L * 1024 * 1024);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateAvatar(file));
        assertTrue(ex.getMessage().contains("不能超过 5MB"));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("头像上传成功：以真实类型命名落盘并回写头像相对路径")
    void updateAvatarSuccess() throws Exception {
        loggedInAs(1);
        User u = user(1, "张三", 100, 1);
        u.setAvatar(null);
        when(userMapper.selectById(1)).thenReturn(u);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("avatar.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(jpegHeader()));
        when(file.getSize()).thenReturn(1024L);

        String path = service.updateAvatar(file);

        assertTrue(path.startsWith("/uploads/avatar/"));
        assertTrue(path.endsWith(".jpg"));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getUserId());
        assertEquals(path, captor.getValue().getAvatar());
    }

    /** 最小 JPEG 文件头：FF D8 FF E0 ... 共 12 字节，满足魔数识别读取长度 */
    private byte[] jpegHeader() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };
    }
}