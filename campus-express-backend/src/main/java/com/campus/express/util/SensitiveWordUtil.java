package com.campus.express.util;

import java.util.List;

/**
 * 敏感词过滤工具（3.6 消息留言：简单敏感词过滤）
 * 敏感词命中后替换为 *，替换数量与词长一致
 */
public final class SensitiveWordUtil {

    /** 敏感词库（课程演示用基础词库，可按需扩充） */
    private static final List<String> WORDS = List.of(
            "赌博", "诈骗", "传销", "毒品", "代考", "作弊", "办证"
    );

    private SensitiveWordUtil() {
    }

    /** 过滤：把内容中的敏感词替换为同长度的 * */
    public static String filter(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String result = content;
        for (String word : WORDS) {
            result = result.replace(word, "*".repeat(word.length()));
        }
        return result;
    }

    /** 判断是否包含敏感词 */
    public static boolean containsSensitive(String content) {
        if (content == null) {
            return false;
        }
        return WORDS.stream().anyMatch(content::contains);
    }
}
