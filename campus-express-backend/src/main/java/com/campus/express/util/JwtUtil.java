package com.campus.express.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类：无状态登录令牌的生成与解析（jjwt 0.12.x API）
 * 令牌载荷：userId / username / role，有效期由 application.yml 配置
 */
@Component
public class JwtUtil {

    /** 签名密钥（Base64 编码字符串，配置在 application.yml） */
    @Value("${jwt.secret}")
    private String secret;

    /** 有效期（分钟） */
    @Value("${jwt.expire-minutes}")
    private long expireMinutes;

    /** 令牌前缀 */
    @Value("${jwt.prefix}")
    private String prefix;

    /** 由 Base64 密钥派生 HS256 签名密钥 */
    private SecretKey getKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成令牌
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色：1 需求方 2 接单方
     */
    public String generateToken(Integer userId, String username, Integer role) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireMinutes * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expire)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析令牌（校验签名与有效期）
     *
     * @param token 去除前缀后的原始令牌
     * @return Claims 载荷；无效或过期返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // 签名非法 / 令牌过期 / 格式错误，统一视为未登录
            return null;
        }
    }

    /** 取出请求头中的原始令牌（含 Bearer 前缀），无效格式返回 null */
    public String extractToken(String header) {
        if (header == null || !header.startsWith(prefix)) {
            return null;
        }
        return header.substring(prefix.length()).trim();
    }

    /** 从令牌中取用户 ID */
    public Integer getUserId(Claims claims) {
        return Integer.valueOf(claims.getSubject());
    }
}
