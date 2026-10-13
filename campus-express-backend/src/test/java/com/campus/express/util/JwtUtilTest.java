package com.campus.express.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JWT 工具类单元测试（登录会话）
 * 通过 ReflectionTestUtils 注入 @Value 字段，脱离 Spring 容器验证生成/解析/篡改防护
 */
class JwtUtilTest {

    /** 与 application.yml 中 jwt.secret 一致的 Base64 密钥（48 字节，满足 HS256 强度要求） */
    private static final String SECRET =
            "Y2FtcHVzX2V4cHJlc3NfanV0X3NlY3JldF9rZXlfMjAyNl9zdHJpbmdfMTIzNDU2Nzg5MA==";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expireMinutes", 10080L);
        ReflectionTestUtils.setField(jwtUtil, "prefix", "Bearer ");
    }

    @Test
    @DisplayName("生成后解析：userId/username/role 载荷可正确取回")
    void generateThenParse() {
        String token = jwtUtil.generateToken(42, "张三", 2);
        assertNotNull(token);
        Claims claims = jwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals(42, jwtUtil.getUserId(claims));
        assertEquals("张三", claims.get("username"));
        assertEquals(2, ((Number) claims.get("role")).intValue());
    }

    @Test
    @DisplayName("令牌被篡改：签名校验失败返回 null，不抛异常")
    void tamperedTokenRejected() {
        String token = jwtUtil.generateToken(1, "a", 1);
        // 篡改签名段
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "AAAAdeadbeef";
        assertNull(jwtUtil.parseToken(tampered));
    }

    @Test
    @DisplayName("非法字符串令牌返回 null")
    void garbageTokenRejected() {
        assertNull(jwtUtil.parseToken("not-a-jwt"));
    }

    @Test
    @DisplayName("请求头解析：仅识别 Bearer 前缀，缺失/异前缀返回 null")
    void extractTokenFromHeader() {
        assertEquals("abc.def.ghi", jwtUtil.extractToken("Bearer abc.def.ghi"));
        assertNull(jwtUtil.extractToken("abc.def.ghi"));
        assertNull(jwtUtil.extractToken(null));
    }
}