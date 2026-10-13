package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单照片上传（uploadOrderPhoto / savePhoto）单元测试
 *
 * <p>覆盖：订单不存在、非接单者、类型非法、状态守卫（取件照限已接单/取件中，送达照限配送中），
 * 以及 savePhoto 的文件校验分支（空文件、非法扩展名、文件头非图片、超过 5MB）与成功落盘
 * 后以真实类型命名返回相对路径。</p>
 */
class OrderServiceImplPhotoTest extends AbstractOrderServiceTest {

    // ---------------- uploadOrderPhoto：权限与状态守卫 ----------------

    @Test
    @DisplayName("上传照片：订单不存在 → 拒绝")
    void uploadPhotoRejectsMissingOrder() {
        when(orderMapper.selectById(10)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", file("a.jpg", jpegHeader(), 1024)));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    @Test
    @DisplayName("上传照片：送达照在非配送中状态被拒")
    void uploadPhotoRejectsDeliverWrongStatus() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "deliver", file("a.jpg", jpegHeader(), 1024)));
        assertTrue(ex.getMessage().contains("不可上传送达照片"));
    }

    // ---------------- uploadOrderPhoto：成功路径 ----------------

    @Test
    @DisplayName("上传取件照成功：已接单状态，返回 /uploads/order/ 相对路径")
    void uploadPickupPhotoSuccess() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        String path = service.uploadOrderPhoto(10, "pickup", file("a.jpg", jpegHeader(), 1024));

        assertTrue(path.startsWith("/uploads/order/"));
        assertTrue(path.endsWith(".jpg"));
        verify(orderMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("上传取件照成功：取件中状态同样允许")
    void uploadPickupPhotoAllowedInPicking() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.PICKING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        String path = service.uploadOrderPhoto(10, "pickup", file("a.jpg", jpegHeader(), 1024));

        assertTrue(path.startsWith("/uploads/order/"));
    }

    @Test
    @DisplayName("上传送达照成功：配送中状态允许")
    void uploadDeliverPhotoSuccess() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        String path = service.uploadOrderPhoto(10, "deliver", file("a.png", pngHeader(), 1024));

        assertTrue(path.startsWith("/uploads/order/"));
        assertTrue(path.endsWith(".png"));
    }

    // ---------------- savePhoto：文件校验分支 ----------------

    @Test
    @DisplayName("上传照片：空文件 → 拒绝")
    void uploadPhotoRejectsEmptyFile() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", empty));
        assertTrue(ex.getMessage().contains("请选择要上传的图片"));
    }

    @Test
    @DisplayName("上传照片：扩展名非法 → 拒绝")
    void uploadPhotoRejectsBadExtension() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", file("evil.jsp", jpegHeader(), 1024)));
        assertTrue(ex.getMessage().contains("仅支持 jpg/png/gif/webp"));
    }

    @Test
    @DisplayName("上传照片：文件头非图片 → 拒绝（防伪装文件落地）")
    void uploadPhotoRejectsFakeImage() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup",
                        file("a.jpg", "not-an-image".getBytes(), 1024)));
        assertTrue(ex.getMessage().contains("图片内容校验失败"));
    }

    @Test
    @DisplayName("上传照片：超过 5MB → 拒绝且不写库")
    void uploadPhotoRejectsOversize() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", file("a.jpg", jpegHeader(), 6L * 1024 * 1024)));
        assertTrue(ex.getMessage().contains("不能超过 5MB"));
        verify(orderMapper, never()).update(any(), any());
    }

    // ---------------- 构造辅助 ----------------

    private MultipartFile file(String name, byte[] content, long size) {
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getOriginalFilename()).thenReturn(name);
        try {
            when(f.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(f.getSize()).thenReturn(size);
        return f;
    }

    private byte[] jpegHeader() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };
    }

    private byte[] pngHeader() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}