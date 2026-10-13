package com.campus.express.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 图片真实性校验工具
 * 仅凭文件扩展名无法判断真实类型（把 evil.jsp 改名为 evil.jpg 即可绕过），
 * 故读取文件头（magic number）识别真实图片格式，拒绝伪造扩展名的文件落地。
 */
public final class ImageUtil {

    private ImageUtil() {
    }

    /**
     * 读取文件头识别真实图片类型
     * @return 归一化后的扩展名（".jpg" / ".png" / ".gif" / ".webp"）；无法识别返回 null
     */
    public static String detectImageType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = new byte[12];
            int read = in.read(head);
            if (read < 4) {
                return null;
            }
            // JPEG: FF D8 FF
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return ".jpg";
            }
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if ((head[0] & 0xFF) == 0x89 && (head[1] & 0xFF) == 0x50
                    && (head[2] & 0xFF) == 0x4E && (head[3] & 0xFF) == 0x47) {
                return ".png";
            }
            // GIF: "GIF8"
            if ((head[0] & 0xFF) == 0x47 && (head[1] & 0xFF) == 0x49
                    && (head[2] & 0xFF) == 0x46 && (head[3] & 0xFF) == 0x38) {
                return ".gif";
            }
            // WEBP: "RIFF"...."WEBP"
            if (read >= 12
                    && (head[0] & 0xFF) == 0x52 && (head[1] & 0xFF) == 0x49
                    && (head[2] & 0xFF) == 0x46 && (head[3] & 0xFF) == 0x46
                    && (head[8] & 0xFF) == 0x57 && (head[9] & 0xFF) == 0x45
                    && (head[10] & 0xFF) == 0x42 && (head[11] & 0xFF) == 0x50) {
                return ".webp";
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}