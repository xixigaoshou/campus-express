package com.campus.express.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 图片真实性校验单元测试（安全约束：凭文件头 magic number 识别真实类型）
 * 关键点：伪装扩展名的文件必须被拒绝，不能只信扩展名
 */
class ImageUtilTest {

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    @Test
    @DisplayName("真实文件头识别：JPEG/PNG/GIF/WEBP 均能正确归一化")
    void detectRealTypes() {
        assertEquals(".jpg", ImageUtil.detectImageType(
                file("a.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0})));
        assertEquals(".png", ImageUtil.detectImageType(
                file("a.png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})));
        assertEquals(".gif", ImageUtil.detectImageType(
                file("a.gif", new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61})));
        // WEBP: RIFF....WEBP
        assertEquals(".webp", ImageUtil.detectImageType(
                file("a.webp", new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0,
                        0x57, 0x45, 0x42, 0x50})));
    }

    @Test
    @DisplayName("伪装扩展名被拒绝：.jpg 后缀但内容是脚本/文本，识别为 null")
    void rejectSpoofedExtension() {
        assertNull(ImageUtil.detectImageType(file("evil.jpg", "<?php echo 1; ?>".getBytes())));
        assertNull(ImageUtil.detectImageType(file("evil.png", "not an image".getBytes())));
    }

    @Test
    @DisplayName("空文件与过短文件返回 null")
    void rejectEmptyAndTooShort() {
        assertNull(ImageUtil.detectImageType(file("empty.jpg", new byte[0])));
        assertNull(ImageUtil.detectImageType(file("short.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8})));
        assertNull(ImageUtil.detectImageType(null));
    }
}